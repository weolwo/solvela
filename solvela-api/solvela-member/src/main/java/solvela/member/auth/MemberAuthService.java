package solvela.member.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.trace.Trace;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.LoginLogResultEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberStatusEnum;
import solvela.member.Member;
import solvela.member.MemberLoginLog;
import solvela.member.MemberOperationLimit;
import solvela.member.api.AuthFailReason;
import solvela.member.api.MemberAuthApi;
import solvela.member.api.MemberAuthCmd;
import solvela.member.api.MemberAuthResult;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberLogoutCmd;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.util.MemberPhoneUtil;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会员认证：验明身份，仅此而已。{@link MemberAuthApi} 的实现。
 *
 * <h3>职责边界：这里没有令牌</h3>
 * 本服务回答的是「这个人是不是他声称的那个会员，现在能不能用」。
 * <b>令牌怎么签、存多久、放哪、怎么吊销，一概不在这里</b> —— 那是接入层的决定，
 * 而且各端可以不同（C 端用不透明令牌进 Redis，内部工具可能压根不需要令牌）。
 * 把发令牌焊进认证，等于让所有调用方都得接受同一套会话模型。
 *
 * <h3>为什么从 solvela-app 搬下来</h3>
 * 这段逻辑原先整个长在 C 端网关里（{@code solvela.app.service.MemberLoginService}）。
 * 结果是：短信验证码登录、第三方登录、内部工具想复用同一套「查人 → 判状态 → 验密码 →
 * 记失败」，一行都用不上，只能各写一遍 —— 而三份实现漂移的第一天不会有任何报错。
 * 现在网关只剩「翻译成 HTTP」这一件事。
 *
 * <h3>失败一律用返回值，不抛异常</h3>
 * 见 {@link AuthFailReason} 的类注释。域只说原因，措辞由调用方定。
 *
 * @Date 2026-08-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAuthService implements MemberAuthApi {

    private static final String DEFAULT_DEVICE_TYPE = "H5";

    private static final int TRACE_ID_MAX_LENGTH = 64;

    private static final int REMARK_MAX_LENGTH = 128;

    private final MemberAuthDao memberAuthDao;
    private final MemberLoginLogDao memberLoginLogDao;
    private final MemberOperationLimitService operationLimitService;
    private final PiiHasher piiHasher;

    /**
     * 手机号 + 密码认证。
     *
     * <p>分支顺序是<b>有讲究的</b>，别调换：账号状态在验密码之前（被冻结的账号不该还能
     * 用来试探密码对不对），限制检查也在验密码之前（被限制期间连试的机会都没有，
     * 否则限制形同虚设）。
     */
    @Override
    public MemberAuthResult authenticate(MemberAuthCmd cmd) {

        // ---------- 规范化手机号 ----------
        // 必须先规范化再算摘要，否则 "138 0000 0000" 与 "13800000000" 是两个不同的 hash
        String phone = MemberPhoneUtil.normalize(cmd.phone());
        if (phone == null) {
            return MemberAuthResult.fail(AuthFailReason.BAD_PHONE_FORMAT);
        }

        // ---------- 按摘要找人 ----------
        Member member = memberAuthDao.selectForLogin(piiHasher.hash(phone));
        if (member == null) {
            // 这里刻意不写登录日志：t_member_login_log.member_id 是 NOT NULL，
            // 没有会员就没有可写的行。「不存在的手机号被反复尝试」属于风控范畴，
            // 要防的话得另建一张按 IP/手机号聚合的表，不是往会员日志里塞假 member_id。
            return MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
        }

        // ---------- 账号状态 ----------
        if (member.getStatus() == MemberStatusEnum.CANCELLED) {
            // 正常走不到：注销会把 phone_hash 置 NULL，上一步就查不到人。
            // 留着是因为「查不到」依赖的是另一处代码写对，而这一行只值三行代价。
            return MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
        }
        if (member.getStatus() == MemberStatusEnum.FROZEN) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "账号已冻结");
            return MemberAuthResult.fail(AuthFailReason.ACCOUNT_FROZEN);
        }

        // ---------- 风控：连续失败限制 ----------
        // 会员端自己一套（t_member_operation_limit + Redis 计数），不走员工端的三级等保：
        // 那套锁的是账号，而会员的手机号是可猜、可泄露的 —— 等于给了别人一个把你挡在门外的开关。
        MemberOperationLimit activeLimit =
                operationLimitService.getActiveLimit(member.getMemberId(), MemberOperationTypeEnum.LOGIN);
        if (activeLimit != null) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "登录已被限制");
            return MemberAuthResult.limited(remainingSeconds(activeLimit));
        }

        // ---------- 验密码 ----------
        if (SolvelaStringUtil.isEmpty(member.getPassword())) {
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "未设置登录密码");
            return MemberAuthResult.fail(AuthFailReason.NO_PASSWORD);
        }
        if (!PasswordCipher.matches(cmd.password(), member.getPassword())) {
            MemberOperationLimit triggered = operationLimitService.recordFail(
                    member.getMemberId(), MemberOperationTypeEnum.LOGIN, "连续登录失败");
            saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_FAIL, "手机号或密码错误");
            // triggered 非空表示这一次失败刚好把人限制住了，直接返回「还要等多久」，
            // 而不是让他再点一次才发现被限 —— 后者是投诉的主要来源
            if (triggered != null) {
                return MemberAuthResult.limited(remainingSeconds(triggered));
            }
            return MemberAuthResult.fail(AuthFailReason.BAD_CREDENTIALS);
        }

        // ---------- 认证通过 ----------
        operationLimitService.clearFail(member.getMemberId(), MemberOperationTypeEnum.LOGIN);
        saveLoginLog(member.getMemberId(), cmd, LoginLogResultEnum.LOGIN_SUCCESS, null);
        return MemberAuthResult.ok(toIdentity(member));
    }

    /**
     * 按会员号取<b>可用身份</b>；会员不存在或状态不正常返回 null。
     *
     * <p>🔴 状态判断收在这里，而不是把 status 交给调用方自己判：
     * 「什么算一个可用身份」只该有一个定义。多一处判断就多一次判漏的机会，
     * 而判漏的表现是「被冻结的人还能正常用」—— 不会有任何报错。
     */
    @Override
    public MemberIdentity getAuthIdentity(Long memberId) {
        if (memberId == null) {
            return null;
        }
        Member member = memberAuthDao.selectForAuth(memberId);
        if (member == null || member.getStatus() != MemberStatusEnum.NORMAL) {
            return null;
        }
        return toIdentity(member);
    }

    /**
     * 记一次退出登录。
     *
     * <p>吊销令牌是接入层的事（会话模型归它），本方法只负责留痕 ——
     * 于是「退出」和登录成功/失败在同一张表、同一套字段里，查一个人的登录轨迹不用 join。
     */
    @Override
    public void recordLogout(MemberLogoutCmd cmd) {
        saveLoginLog(cmd.memberId(), cmd.clientIp(), null, LoginLogResultEnum.LOGIN_OUT, null);
    }

    private static MemberIdentity toIdentity(Member member) {
        return new MemberIdentity(
                member.getMemberId(),
                member.getMemberName(),
                member.getNickname(),
                member.getAvatarFileId(),
                member.getGender());
    }

    /** 限制剩余秒数。已过期时按 0 算，不返回负数 —— 负数会被调用方格式化成「请 -1 分钟后重试」。 */
    private static long remainingSeconds(MemberOperationLimit limit) {
        return Math.max(0L, Duration.between(LocalDateTime.now(), limit.getExpireTime()).toSeconds());
    }

    private void saveLoginLog(Long memberId, MemberAuthCmd cmd, LoginLogResultEnum status, String remark) {
        saveLoginLog(memberId, cmd.clientIp(), cmd.deviceType(), status, remark);
    }

    /**
     * 写登录日志。
     *
     * <p>traceId 从 MDC 取（{@link Trace#id()}），不从参数传：它对每个接口都一样，
     * 进签名是噪音，而且总有调用点会忘了填 —— 忘了的表现只是这一列悄悄变空。
     * 拆成独立服务后由服务端 Filter 把请求头放进 MDC，这一行代码不用改。
     *
     * <p>取值与管理端的 {@code t_login_log.login_result} <b>完全一致</b>，共用
     * {@link LoginLogResultEnum}。这两张表曾经 0/1 相反（本表 1 是成功），
     * 2026-08-29 趁本表还是零行统一了口径 —— 别再往回改。
     *
     * <p>⚠️ 日志失败绝不能影响认证本身 —— 「登不上去是因为日志表满了」这种事排查极其费劲，
     * 而登录日志的价值再高也高不过登录本身。
     */
    private void saveLoginLog(Long memberId, String clientIp, String deviceType,
                              LoginLogResultEnum status, String remark) {
        try {
            MemberLoginLog loginLog = new MemberLoginLog();
            loginLog.setMemberId(memberId);
            loginLog.setClientIp(clientIp);
            loginLog.setIpRegion(SolvelaIpUtil.getRegion(clientIp));
            loginLog.setDeviceType(SolvelaStringUtil.isEmpty(deviceType) ? DEFAULT_DEVICE_TYPE : deviceType);
            // os_name / browser_name 暂不填：后端没有 UA 解析库，
            // 与其用几个 indexOf 猜出一堆不可信的值，不如留空 —— 空值至少不会被拿去做统计。
            loginLog.setStatus(status);
            loginLog.setRemark(truncate(remark, REMARK_MAX_LENGTH));
            loginLog.setTraceId(truncate(Trace.id(), TRACE_ID_MAX_LENGTH));
            loginLog.setCreateTime(LocalDateTime.now());
            memberLoginLogDao.insert(loginLog);
        } catch (Exception e) {
            log.error("会员登录日志写入失败, memberId: {}", memberId, e);
        }
    }

    /**
     * 截断到列宽。MySQL 非严格模式下超长是<b>静默截断</b>，严格模式下直接报错 ——
     * 前者让数据悄悄变形，后者让登录失败，两个都不能接受，所以入库前自己截。
     */
    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
