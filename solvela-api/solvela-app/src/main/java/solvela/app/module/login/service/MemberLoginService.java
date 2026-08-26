package solvela.app.module.login.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import solvela.code.UserErrorCode;
import solvela.base.constant.RequestHeaderConst;
import solvela.base.constant.StringConst;
import solvela.crypto.PiiHasher;
import solvela.base.domain.ResponseDTO;
import solvela.base.enumeration.UserTypeEnum;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.web.SolvelaServletUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PasswordCipher;
import solvela.member.operationlimit.constant.MemberOperationTypeEnum;
import solvela.member.MemberOperationLimit;
import solvela.member.operationlimit.service.MemberOperationLimitService;
import solvela.member.constant.MemberConst;
import solvela.member.Member;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.MemberLoginLog;
import solvela.member.util.MemberPhoneUtil;
import solvela.app.config.StpMemberUtil;
import solvela.app.module.login.dao.MemberLoginDao;
import solvela.app.module.login.domain.RequestMember;
import solvela.app.module.login.domain.vo.MemberLoginResultVO;
import solvela.app.module.login.domain.form.MemberLoginForm;
import solvela.app.module.login.manager.MemberLoginManager;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会员登录。
 *
 * <p>与管理端 {@code LoginService} 的关系：<b>没有关系</b>，一行都没抄。
 * 员工登录要处理万能密码、菜单权限加载、部门数据范围；会员登录只有
 * 「手机号找人 + 验密码 + 发 token」三步。硬凑成一套的代价是两边的安全边界互相渗透。
 *
 * @Date 2026-08-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLoginService {

    /**
     * 🔴 手机号不存在、密码错误、账号已注销 —— 三种情况<b>返回同一句话</b>。
     *
     * <p>分开提示（「该手机号未注册」/「密码错误」）等于免费送出一个<b>账号枚举</b>接口：
     * 拿号段跑一遍就能筛出「哪些手机号是这个平台的用户」，这本身就是可以卖钱的数据，
     * 也是后续撞库、精准诈骗的输入。牺牲的那点体验，用「注册」入口的引导去补。
     *
     * <p>⚠️ <b>这道防线并不严密，别当它是密闭的</b>：连续输错到触发限制时，返回的是
     * 「请 N 分钟后重试」而不是这一句，而不存在的手机号<b>永远</b>只会得到这一句 ——
     * 对方据此仍能分辨账号是否存在，只是成本从「一次请求」抬到了「连错 5 次」。
     * 保留这个差异是<b>刻意的取舍</b>：抹掉它，用户会在毫无预警的情况下被限住，
     * 而这条投诉的量级远大于枚举风险。真要堵死，得给不存在的手机号也伪造一份计数
     * （按 IP + 手机号维度），那是风控模块的事，不是登录接口的事。
     */
    private static final String LOGIN_FAIL_MSG = "手机号或密码错误";

    /**
     * 被限制时的提示。<b>必须说清楚「还要等多久」和「怎么提前恢复」</b> ——
     * 只说「操作过于频繁」的后果是用户反复重试（把计数越刷越满）然后打客服电话。
     */
    private static final String LOGIN_LOCKED_MSG = "连续登录失败次数过多，请 %d 分钟后重试；需要提前恢复请联系客服";

    /** 未传 deviceType 时的默认值。本服务第一个接入方是 H5 */
    private static final String DEFAULT_DEVICE_TYPE = "H5";

    /** {@code t_member_login_log.trace_id} 是 varchar(64)，而 traceId 可由请求头传入（用户可控） */
    private static final int TRACE_ID_MAX_LENGTH = 64;

    /** {@code t_member_login_log.remark} 是 varchar(128) */
    private static final int REMARK_MAX_LENGTH = 128;

    private final MemberLoginDao memberLoginDao;
    private final MemberLoginLogDao memberLoginLogDao;
    private final MemberLoginManager memberLoginManager;
    private final MemberOperationLimitService memberOperationLimitService;
    private final PiiHasher piiHasher;

    /**
     * 手机号 + 密码登录。
     */
    public ResponseDTO<MemberLoginResultVO> login(MemberLoginForm loginForm, String ip, String userAgent) {

        // --------------- 第一步：规范化手机号 ---------------
        // 必须先规范化再算摘要，否则 "138 0000 0000" 和 "13800000000" 是两个不同的 hash。
        // 详见 MemberPhoneUtil 的类注释。
        String phone = MemberPhoneUtil.normalize(loginForm.getPhone());
        if (phone == null) {
            // 格式不对时可以明说 —— 这不泄露「这个号是不是用户」，
            // 因为一个格式非法的串本来就不可能是任何人的手机号。
            return ResponseDTO.userErrorParam("手机号格式不正确");
        }

        // --------------- 第二步：按摘要找人 ---------------
        Member member = memberLoginDao.selectForLoginByPhoneHash(piiHasher.hash(phone));
        if (member == null) {
            // ⚠️ 这里<b>不写登录日志</b>：t_member_login_log.member_id 是 NOT NULL，
            // 没有会员就没有可写的行。「不存在的手机号被反复尝试」属于风控范畴，
            // 要防的话得另建一张按 IP/手机号聚合的表，不是往会员日志里塞假 member_id。
            return ResponseDTO.error(UserErrorCode.PARAM_ERROR, LOGIN_FAIL_MSG);
        }

        // --------------- 第三步：账号状态 ---------------
        if (MemberConst.STATUS_CANCELLED == member.getStatus()) {
            // 正常情况下走不到这里：注销会把 phone_hash 置 NULL，上一步就查不到人了。
            // 留着是因为「查不到」依赖的是另一处代码写对，而这一行的代价只有三行。
            return ResponseDTO.error(UserErrorCode.PARAM_ERROR, LOGIN_FAIL_MSG);
        }
        if (MemberConst.STATUS_FROZEN == member.getStatus()) {
            // 冻结<b>要如实告知</b>，与上面三种含糊其辞的情况相反：
            // 能走到这一步说明密码还没验，但对方已经证明自己知道这个手机号是注册过的，
            // 藏着只会让被风控误伤的用户一直重试、然后打客服电话。
            saveLoginLog(member.getMemberId(), ip, loginForm.getDeviceType(),
                    MemberConst.LOGIN_STATUS_FAIL, "账号已冻结");
            return ResponseDTO.error(UserErrorCode.ACCOUNT_FROZEN);
        }

        // --------------- 第四步：风控 —— 连续失败限制 ---------------
        // 会员端自己一套（t_member_operation_limit + Redis 计数），不再走员工端的三级等保：
        // 那套锁的是账号，而会员的手机号是可猜、可泄露的 —— 等于给了别人一个把你挡在门外的开关。
        MemberOperationLimit activeLimit =
                memberOperationLimitService.getActiveLimit(member.getMemberId(), MemberOperationTypeEnum.LOGIN);
        if (activeLimit != null) {
            String lockedMsg = buildLockedMsg(activeLimit);
            saveLoginLog(member.getMemberId(), ip, loginForm.getDeviceType(),
                    MemberConst.LOGIN_STATUS_FAIL, lockedMsg);
            return ResponseDTO.error(UserErrorCode.PARAM_ERROR, lockedMsg);
        }

        // --------------- 第五步：验密码 ---------------
        if (SolvelaStringUtil.isEmpty(member.getPassword())) {
            // t_member.password 允许为空，是给「只用验证码注册过、从没设过密码」的会员留的。
            // 这类账号确实存在，所以必须明确提示，否则用户会以为自己密码记错了、
            // 一直重试直到被等保锁定。
            saveLoginLog(member.getMemberId(), ip, loginForm.getDeviceType(),
                    MemberConst.LOGIN_STATUS_FAIL, "未设置登录密码");
            return ResponseDTO.userErrorParam("该账号未设置密码，请使用短信验证码登录");
        }
        if (!PasswordCipher.matches(loginForm.getPassword(), member.getPassword())) {
            MemberOperationLimit triggered = memberOperationLimitService.recordFail(
                    member.getMemberId(), MemberOperationTypeEnum.LOGIN, "连续登录失败");
            saveLoginLog(member.getMemberId(), ip, loginForm.getDeviceType(),
                    MemberConst.LOGIN_STATUS_FAIL, LOGIN_FAIL_MSG);
            // triggered != null 表示这一次失败刚好把人限制住了，直接告诉他要等多久，
            // 而不是让他再点一次才发现被限 —— 后者是投诉的主要来源
            return ResponseDTO.error(UserErrorCode.PARAM_ERROR,
                    triggered == null ? LOGIN_FAIL_MSG : buildLockedMsg(triggered));
        }

        // --------------- 第六步：签发 token ---------------
        String deviceType = resolveDeviceType(loginForm.getDeviceType());
        StpMemberUtil.login(buildLoginId(member.getMemberId()), deviceType);

        memberOperationLimitService.clearFail(member.getMemberId(), MemberOperationTypeEnum.LOGIN);
        // 资料可能在上次缓存之后被后台改过，登录是重建缓存最自然的时机
        memberLoginManager.clearRequestMemberCache(member.getMemberId());
        saveLoginLog(member.getMemberId(), ip, deviceType, MemberConst.LOGIN_STATUS_SUCCESS, null);

        MemberLoginResultVO result = new MemberLoginResultVO();
        result.setMemberId(member.getMemberId());
        result.setMemberName(member.getMemberName());
        result.setNickname(member.getNickname());
        result.setAvatarFileId(member.getAvatarFileId());
        result.setGender(member.getGender());
        result.setToken(StpMemberUtil.getTokenValue());
        return ResponseDTO.ok(result);
    }

    /**
     * 退出登录。
     */
    public ResponseDTO<String> logout(RequestMember requestMember) {
        StpMemberUtil.logout();
        memberLoginManager.clearRequestMemberCache(requestMember.getMemberId());
        saveLoginLog(requestMember.getMemberId(), requestMember.getIp(), null,
                MemberConst.LOGIN_STATUS_LOGOUT, null);
        return ResponseDTO.ok();
    }

    /**
     * 由 loginId 还原当前请求的会员身份；无效返回 null。供 {@code MemberInterceptor} 调用。
     */
    public RequestMember getLoginMember(String loginId, HttpServletRequest request) {
        Long memberId = parseMemberId(loginId);
        if (memberId == null) {
            return null;
        }
        RequestMember requestMember = memberLoginManager.getRequestMember(memberId);
        if (requestMember == null) {
            return null;
        }
        // 🔴 冻结/注销要<b>当场生效</b>，不能等 token 过期。
        // 缓存最长 30 分钟陈旧（见 AppCacheConst.Login.REQUEST_MEMBER 的注释），
        // 这是运营点下「冻结」到用户真正被踢出去的窗口。
        if (requestMember.getStatus() == null || MemberConst.STATUS_NORMAL != requestMember.getStatus()) {
            return null;
        }

        // 每个请求各不相同的两项，在缓存之外填 —— 放进缓存对象会串到别人的请求上
        requestMember.setIp(SolvelaServletUtil.getClientIP(request));
        requestMember.setUserAgent(request.getHeader(RequestHeaderConst.USER_AGENT));
        return requestMember;
    }

    /**
     * 拼 sa-token 的 loginId：{@code 2:会员号}。
     *
     * <p>带上 userType 前缀是沿用管理端的约定（那边是 {@code 1:员工id}）——
     * 光看一个 loginId 就知道它是谁，排查线上问题时省一次查库。
     * 🔴 只在这里拼，只在 {@link #parseMemberId} 解析，别在别处手写字符串。
     */
    private String buildLoginId(Long memberId) {
        return UserTypeEnum.MEMBER.getValue() + StringConst.COLON + memberId;
    }

    private Long parseMemberId(String loginId) {
        if (SolvelaStringUtil.isEmpty(loginId)) {
            return null;
        }
        String prefix = UserTypeEnum.MEMBER.getValue() + StringConst.COLON;
        if (!loginId.startsWith(prefix)) {
            // 走到这里说明拿到了一个<b>不是会员</b>的 loginId。
            // 独立 loginType（见 StpMemberUtil）已经让员工 token 根本查不到值，
            // 所以这更可能是历史脏数据或人为构造，记一条 warn 便于发现。
            log.warn("非会员 loginId 出现在会员端: {}", loginId);
            return null;
        }
        try {
            return Long.parseLong(loginId.substring(prefix.length()));
        } catch (NumberFormatException e) {
            log.error("loginId 解析失败: {}", loginId, e);
            return null;
        }
    }

    /**
     * 把等保提示拼到统一文案后面，并剥掉它自带的员工端开头。
     *
     * <p>只剥「开头正好是那句话」的情况：账号被锁时 {@code recordLoginFail} 返回的是
     * 另一条文案（「您已连续登录失败N次，账号锁定M分钟…」），它不该被动到 ——
     * 用 startsWith 而不是「按第一个感叹号切」正是为了这个，后者会把锁定提示切成空串。
     */
    private String buildLockedMsg(MemberOperationLimit limit) {
        // 向上取整到分钟：剩 10 秒时说「请 0 分钟后重试」比不说还糟
        long minutes = Math.max(1, (long) Math.ceil(
                Duration.between(LocalDateTime.now(), limit.getExpireTime()).toSeconds() / 60.0));
        return String.format(LOGIN_LOCKED_MSG, minutes);
    }

    private String resolveDeviceType(String deviceType) {
        return SolvelaStringUtil.isEmpty(deviceType) ? DEFAULT_DEVICE_TYPE : deviceType;
    }

    /**
     * 写登录日志。
     *
     * <p>🔴 {@code status} 的取值口径与 {@code t_login_log.login_result} <b>正好相反</b>
     * （DDL 注释里专门标了这一条）：这里 1 是成功、0 是失败。
     * 照抄管理端 {@code LoginService} 那套判断会正好搞反。
     *
     * <p>⚠️ 日志失败绝不能影响登录本身 —— 用户登不上去的原因是「日志表满了」这种事
     * 排查起来极其费劲，而登录日志的价值再高也高不过登录本身。
     */
    private void saveLoginLog(Long memberId, String ip, String deviceType, int status, String remark) {
        try {
            MemberLoginLog loginLog = new MemberLoginLog();
            loginLog.setMemberId(memberId);
            loginLog.setClientIp(ip);
            loginLog.setIpRegion(SolvelaIpUtil.getRegion(ip));
            loginLog.setDeviceType(resolveDeviceType(deviceType));
            // os_name / browser_name 暂不填：后端没有 UA 解析库，
            // 与其用几个 indexOf 猜出一堆不可信的值，不如留空 —— 空值至少不会被拿去做统计。
            loginLog.setStatus(status);
            loginLog.setRemark(truncate(remark, REMARK_MAX_LENGTH));
            loginLog.setTraceId(truncate(MDC.get("traceId"), TRACE_ID_MAX_LENGTH));
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
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
