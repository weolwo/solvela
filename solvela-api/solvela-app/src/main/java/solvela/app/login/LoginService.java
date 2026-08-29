package solvela.app.login;

import solvela.enums.MemberStatusEnum;
import solvela.enums.LoginLogResultEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.app.auth.AccessToken;
import solvela.app.auth.MemberAuthDao;
import solvela.app.auth.MemberPrincipal;
import solvela.app.auth.MemberPrincipalLoader;
import solvela.app.auth.TokenStore;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.app.web.Trace;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiHasher;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.Member;
import solvela.member.MemberLoginLog;
import solvela.member.MemberOperationLimit;
import solvela.member.constant.MemberConst;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.enums.MemberOperationTypeEnum;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.util.MemberPhoneUtil;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会员登录与退出。
 *
 * <h3>失败一律抛 {@link ApiException}，不返回信封</h3>
 * 上一版每个分支 {@code return ResponseDTO.error(...)}，调用方要判一次 {@code getOk()}，
 * 判漏了就当成功往下走 —— 而漏判没有任何提示。现在失败即抛出，
 * 漏处理会一路冒到统一出口，不会被静默吞掉。
 *
 * <h3>什么时候说真话，什么时候含糊其辞</h3>
 * 这几个分支的措辞是<b>反复权衡过的</b>，改之前先读完：
 * <ul>
 *   <li>手机号<b>格式</b>不对 → 明说。一个非法的串本来就不可能是任何人的手机号，不泄露任何信息；</li>
 *   <li>查无此人 / 已注销 / 密码错 → 统一含糊成「手机号或密码错误」。分开说，
 *       等于免费送出一个「这个号注册过没有」的查询接口；</li>
 *   <li>账号被冻结 → <b>如实告知</b>。能走到这一步说明对方已经证明自己知道这个号是注册过的，
 *       再藏着只会让被误伤的用户一直重试然后打客服电话；</li>
 *   <li>没设过密码 → 明说并引导去验证码登录。这类账号确实存在
 *       （{@code t_member.password} 允许为空正是为验证码注册留的），
 *       含糊其辞会让用户以为自己记错了密码，一直重试直到被限制。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final String BAD_CREDENTIALS_MSG = "手机号或密码错误";

    private static final String LOCKED_MSG = "连续登录失败次数过多，请 %d 分钟后重试；需要提前恢复请联系客服";

    private static final String DEFAULT_DEVICE_TYPE = "H5";

    private static final int TRACE_ID_MAX_LENGTH = 64;

    private static final int REMARK_MAX_LENGTH = 128;

    private final MemberAuthDao memberAuthDao;
    private final MemberLoginLogDao memberLoginLogDao;
    private final MemberPrincipalLoader principalLoader;
    private final MemberOperationLimitService operationLimitService;
    private final TokenStore tokenStore;
    private final PiiHasher piiHasher;

    public LoginResult login(LoginRequest request, String ip) {

        // ---------- 规范化手机号 ----------
        // 必须先规范化再算摘要，否则 "138 0000 0000" 与 "13800000000" 是两个不同的 hash
        String phone = MemberPhoneUtil.normalize(request.phone());
        if (phone == null) {
            throw new ApiException(ApiErrors.INVALID_ARGUMENT, "手机号格式不正确");
        }

        // ---------- 按摘要找人 ----------
        Member member = memberAuthDao.selectForLogin(piiHasher.hash(phone));
        if (member == null) {
            // 这里刻意不写登录日志：t_member_login_log.member_id 是 NOT NULL，
            // 没有会员就没有可写的行。「不存在的手机号被反复尝试」属于风控范畴，
            // 要防的话得另建一张按 IP/手机号聚合的表，不是往会员日志里塞假 member_id。
            throw new ApiException(ApiErrors.BAD_CREDENTIALS, BAD_CREDENTIALS_MSG);
        }

        // ---------- 账号状态 ----------
        if (member.getStatus() == MemberStatusEnum.CANCELLED) {
            // 正常走不到：注销会把 phone_hash 置 NULL，上一步就查不到人。
            // 留着是因为「查不到」依赖的是另一处代码写对，而这一行只值三行代价。
            throw new ApiException(ApiErrors.BAD_CREDENTIALS, BAD_CREDENTIALS_MSG);
        }
        if (member.getStatus() == MemberStatusEnum.FROZEN) {
            saveLoginLog(member.getMemberId(), ip, request.deviceType(),
                    LoginLogResultEnum.LOGIN_FAIL, "账号已冻结");
            throw new ApiException(ApiErrors.ACCOUNT_DISABLED, "账号已被冻结，请联系客服");
        }

        // ---------- 风控：连续失败限制 ----------
        // 会员端自己一套（t_member_operation_limit + Redis 计数），不走员工端的三级等保：
        // 那套锁的是账号，而会员的手机号是可猜、可泄露的 —— 等于给了别人一个把你挡在门外的开关。
        MemberOperationLimit activeLimit =
                operationLimitService.getActiveLimit(member.getMemberId(), MemberOperationTypeEnum.LOGIN);
        if (activeLimit != null) {
            String message = lockedMessage(activeLimit);
            saveLoginLog(member.getMemberId(), ip, request.deviceType(),
                    LoginLogResultEnum.LOGIN_FAIL, message);
            throw new ApiException(ApiErrors.OPERATION_LIMITED, message);
        }

        // ---------- 验密码 ----------
        if (SolvelaStringUtil.isEmpty(member.getPassword())) {
            saveLoginLog(member.getMemberId(), ip, request.deviceType(),
                    LoginLogResultEnum.LOGIN_FAIL, "未设置登录密码");
            throw new ApiException(ApiErrors.BAD_CREDENTIALS, "该账号未设置密码，请使用短信验证码登录");
        }
        if (!PasswordCipher.matches(request.password(), member.getPassword())) {
            MemberOperationLimit triggered = operationLimitService.recordFail(
                    member.getMemberId(), MemberOperationTypeEnum.LOGIN, "连续登录失败");
            saveLoginLog(member.getMemberId(), ip, request.deviceType(),
                    LoginLogResultEnum.LOGIN_FAIL, BAD_CREDENTIALS_MSG);
            // triggered 非空表示这一次失败刚好把人限制住了，直接告诉他要等多久，
            // 而不是让他再点一次才发现被限 —— 后者是投诉的主要来源
            if (triggered != null) {
                throw new ApiException(ApiErrors.OPERATION_LIMITED, lockedMessage(triggered));
            }
            throw new ApiException(ApiErrors.BAD_CREDENTIALS, BAD_CREDENTIALS_MSG);
        }

        // ---------- 签发令牌 ----------
        AccessToken token = tokenStore.issue(member.getMemberId());

        operationLimitService.clearFail(member.getMemberId(), MemberOperationTypeEnum.LOGIN);
        // 资料可能在上次缓存之后被后台改过，登录是重建缓存最自然的时机
        principalLoader.evict(member.getMemberId());
        saveLoginLog(member.getMemberId(), ip, request.deviceType(),
                LoginLogResultEnum.LOGIN_SUCCESS, null);

        MemberPrincipal principal = new MemberPrincipal(
                member.getMemberId(), member.getMemberName(), member.getNickname(),
                member.getAvatarFileId(), member.getGender());
        return new LoginResult(token.value(), token.expiresIn().toSeconds(), principal);
    }

    /**
     * 退出登录。只吊销<b>当前这一个</b>令牌，其它设备不受影响 ——
     * 「退出所有设备」是另一个动作，用户得明确选择。
     */
    public void logout(String tokenValue, Long memberId, String ip) {
        tokenStore.revoke(tokenValue);
        saveLoginLog(memberId, ip, null, LoginLogResultEnum.LOGIN_OUT, null);
    }

    /**
     * 把限制剩余时间拼成人话。
     *
     * <p>向上取整到分钟：剩 10 秒时说「请 0 分钟后重试」比不说还糟。
     */
    private String lockedMessage(MemberOperationLimit limit) {
        long minutes = Math.max(1, (long) Math.ceil(
                Duration.between(LocalDateTime.now(), limit.getExpireTime()).toSeconds() / 60.0));
        return String.format(LOCKED_MSG, minutes);
    }

    /**
     * 写登录日志。
     *
     * <p>取值与管理端的 {@code t_login_log.login_result} <b>完全一致</b>，共用
     * {@link LoginLogResultEnum}。这两张表曾经 0/1 相反（本表 1 是成功），
     * 2026-08-29 趁本表还是零行统一了口径 —— 别再往回改。
     *
     * <p>⚠️ 日志失败绝不能影响登录本身 —— 「登不上去是因为日志表满了」这种事排查极其费劲，
     * 而登录日志的价值再高也高不过登录本身。
     */
    private void saveLoginLog(Long memberId, String ip, String deviceType, LoginLogResultEnum status, String remark) {
        try {
            MemberLoginLog loginLog = new MemberLoginLog();
            loginLog.setMemberId(memberId);
            loginLog.setClientIp(ip);
            loginLog.setIpRegion(SolvelaIpUtil.getRegion(ip));
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
