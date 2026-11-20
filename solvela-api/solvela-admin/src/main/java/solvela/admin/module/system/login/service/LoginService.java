package solvela.admin.module.system.login.service;

import solvela.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.admin.module.system.employee.domain.entity.EmployeeEntity;
import solvela.admin.module.system.employee.service.EmployeeService;
import solvela.admin.module.system.login.domain.LoginForm;
import solvela.admin.module.system.login.domain.LoginResultVO;
import solvela.admin.auth.AccessToken;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.auth.TokenStore;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.admin.module.system.login.manager.LoginManager;
import solvela.admin.module.system.menu.domain.vo.MenuVO;
import solvela.admin.module.system.role.domain.vo.RoleVO;
import solvela.admin.module.system.role.service.RoleEmployeeService;
import solvela.admin.module.system.role.service.RoleMenuService;
import solvela.code.UserErrorCode;
import solvela.base.constant.StringConst;
import solvela.crypto.PasswordCipher;
import solvela.admin.constant.UserTypeEnum;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaEnumUtil;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.util.SolvelaRandomUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.base.constant.LoginDeviceEnum;
import solvela.admin.module.system.apiencrypt.service.ApiEncryptService;
import solvela.base.module.config.ConfigKeyEnum;
import solvela.base.module.config.ConfigService;
import solvela.enums.LoginLogResultEnum;
import solvela.admin.module.system.loginlog.LoginLogService;
import solvela.admin.module.system.loginlog.domain.LoginLogEntity;
import solvela.admin.module.system.loginlog.domain.LoginLogVO;
import solvela.base.mail.MailService;
import solvela.base.mail.MailTemplateCodeEnum;
import solvela.base.module.redis.RedisService;
import solvela.admin.module.system.securityprotect.domain.LoginFailEntity;
import solvela.admin.module.system.securityprotect.service.Level3ProtectConfigService;
import solvela.admin.module.system.securityprotect.service.SecurityLoginService;
import solvela.admin.module.system.securityprotect.service.SecurityPasswordService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 登录
 *
 * @Author 1024创新实验室: 卓大
 * @Date 2025-05-03 22:56:34
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Slf4j
@Service
public class LoginService {

    /**
     * 万能密码登录的有效期：<b>30 分钟</b>。
     *
     * <p>原先这里写的是 {@code StpUtil.login(loginId, 180000000)} —— 注释说「只能登录30分钟」，
     * 而那个数字是 <b>180000000 秒 ≈ 5.7 年</b>。一个能以任意员工身份登录的后门，
     * 有效期比系统本身的生命周期还长，且没有任何人会注意到，因为注释写着 30 分钟。
     */
    private static final Duration SUPER_PASSWORD_TOKEN_TTL = Duration.ofMinutes(30);

    /** 邮箱验证码 redis key 的业务前缀 */
    private static final String EMAIL_CODE_KEY_PREFIX = "login:verification-code:";

    /** 验证码位数。四位是权衡：短信/邮件里好念好输，而 5 分钟有效期内穷举 1 万种也够呛 */
    private static final int EMAIL_CODE_LENGTH = 4;

    /** 验证码有效期（秒）。太短用户来不及切到邮箱，太长等于给暴力破解更多时间 */
    private static final int EMAIL_CODE_TTL_SECONDS = 300;

    /** 重发冷却（毫秒）。挡的是连点，也顺带挡住拿这个接口当免费邮件发射器 */
    private static final long EMAIL_CODE_RESEND_INTERVAL_MILLIS = 60_000L;
    // 注入你刚写的配置，默认设为 true 防翻车
    @Resource
    private EmployeeService employeeService;
    @Resource
    private ConfigService configService;

    @Resource
    private LoginLogService loginLogService;

    @Resource
    private RoleEmployeeService roleEmployeeService;

    @Resource
    private RoleMenuService roleMenuService;

    @Resource
    private SecurityLoginService securityLoginService;

    @Resource
    private SecurityPasswordService protectPasswordService;

    @Resource
    private ApiEncryptService apiEncryptService;

    @Resource
    private Level3ProtectConfigService level3ProtectConfigService;

    @Resource
    private MailService mailService;

    @Resource
    private RedisService redisService;

    @Resource
    private LoginManager loginManager;

    @Resource
    private TokenStore tokenStore;
    /**
     * 一次登录尝试的现场：谁、从哪、用什么设备。
     *
     * <p>这四个值在整条链路上原样传给每一次写日志 —— 改造前 {@code saveLoginLog}
     * 有 6 个参数，其中 4 个每次都一样，真正变化的只有「结果」和「备注」。
     * 收成一个值之后，写日志的地方读起来就是它本来的意思：{@code attempt.fail("密码错误")}。
     */
    private record LoginAttempt(EmployeeEntity employee, String ip, String userAgent, LoginDeviceEnum device) {
    }

    /**
     * 员工登录。
     *
     * <p>顺序有约束：<b>账号状态在验密码之前</b>（被禁用的账号不该还能用来试探密码对不对），
     * <b>双因子在验密码之前</b>（否则邮箱验证码就成了密码错误之后才需要过的一道门，
     * 等于没有）。
     *
     * @return 返回用户登录信息
     */
    public LoginResultVO login(LoginForm loginForm, String ip, String userAgent) {
        LoginDeviceEnum loginDeviceEnum = SolvelaEnumUtil.getEnumByValue(loginForm.getLoginDevice(), LoginDeviceEnum.class);
        if (loginDeviceEnum == null) {
            throw new BusinessException("登录设备暂不支持！");
        }
        // 解密前端加密的密码。
        // 🔴 必须在查账号之前：密文解不开会抛「加密数据无法解密」，放在查账号之后，
        // 「账号存在 → 解不开」与「账号不存在 → 登录名或密码错误」两句话就成了探测账号是否存在的开关
        String requestPassword = apiEncryptService.decrypt(loginForm.getPassword());
        EmployeeEntity employeeEntity = employeeService.getByLoginName(loginForm.getLoginName());
        if (null == employeeEntity) {
            // 登录名不存在与密码错误<b>必须给同一句话</b>：分开说等于把「这个账号存在」白送出去
            throw new BusinessException("登录名或密码错误！");
        }
        LoginAttempt attempt = new LoginAttempt(employeeEntity, ip, userAgent, loginDeviceEnum);
        checkAccountUsable(attempt);

        // 万能密码：运维通道，它绕开等保失败锁定与邮箱双因子，所以每一次使用都要在登录日志上留名
        boolean superPasswordFlag = configService.getConfigValue(ConfigKeyEnum.SUPER_PASSWORD).equals(requestPassword);

        // 校验双因子登录（不通过直接抛）
        validateEmailCode(loginForm, employeeEntity, superPasswordFlag);

        AccessToken accessToken = superPasswordFlag
                ? tokenStore.issue(employeeEntity.getEmployeeId(), true,
                        loginDeviceEnum.getDesc(), SUPER_PASSWORD_TOKEN_TTL)
                : authenticateAndIssue(attempt, requestPassword);

        RequestEmployee requestEmployee = loginManager.loadLoginInfo(employeeEntity);
        securityLoginService.removeLoginFail(employeeEntity.getEmployeeId(), UserTypeEnum.ADMIN_EMPLOYEE);
        saveLoginLog(attempt, LoginLogResultEnum.LOGIN_SUCCESS,
                superPasswordFlag ? "万能密码登录" : StringConst.EMPTY);

        LoginResultVO loginResultVO = getLoginResult(requestEmployee, superPasswordFlag);
        loginResultVO.setToken(accessToken.value());
        loginManager.loadUserPermission(employeeEntity.getEmployeeId());
        return loginResultVO;
    }

    /**
     * 账号状态。
     *
     * <p>两种不可用分开写日志、分开给文案：员工看到「已删除」和「已禁用」要找的人不一样。
     * 但两种都<b>先落一条失败日志再抛</b> —— 一个被禁用的账号在被反复尝试登录，
     * 是安全事件，不是噪音。
     */
    private void checkAccountUsable(LoginAttempt attempt) {
        if (attempt.employee().getDeletedFlag()) {
            saveLoginLog(attempt, LoginLogResultEnum.LOGIN_FAIL, "账号已删除");
            throw new BusinessException("您的账号已被删除,请联系工作人员！");
        }
        if (attempt.employee().getDisabledFlag()) {
            saveLoginLog(attempt, LoginLogResultEnum.LOGIN_FAIL, "账号已禁用");
            throw new BusinessException("您的账号已被禁用,请联系工作人员！");
        }
    }

    /**
     * 验密码并签发令牌，全程受<b>等保失败锁定</b>约束。
     *
     * <p>{@code checkLogin} 在已锁定时直接抛，所以进到密码比对这一步的一定是「还能试」的状态。
     */
    private AccessToken authenticateAndIssue(LoginAttempt attempt, String requestPassword) {
        EmployeeEntity employee = attempt.employee();
        LoginFailEntity loginFailEntity = securityLoginService.checkLogin(
                employee.getEmployeeId(), UserTypeEnum.ADMIN_EMPLOYEE);

        String salted = employeeService.generateSaltPassword(requestPassword, employee.getEmployeeUid());
        if (!PasswordCipher.matches(salted, employee.getLoginPwd())) {
            saveLoginLog(attempt, LoginLogResultEnum.LOGIN_FAIL, "密码错误");
            String msg = securityLoginService.recordLoginFail(employee.getEmployeeId(),
                    UserTypeEnum.ADMIN_EMPLOYEE, employee.getLoginName(), loginFailEntity);
            // msg 非空说明等保开了失败锁定，里面写着「还剩几次 / 锁到什么时候」——
            // 这句话必须原样给用户，否则他不知道再错一次会发生什么
            throw msg == null
                    ? new BusinessException("登录名或密码错误！")
                    : new BusinessException(UserErrorCode.LOGIN_FAIL_WILL_LOCK, msg);
        }

        AccessToken accessToken = tokenStore.issue(employee.getEmployeeId(), false, attempt.device().getDesc());
        // 验证码是一次性的：登录成功即作废，否则同一个码在有效期内能再登一次
        deleteEmailCode(employee.getEmployeeId());
        return accessToken;
    }

    /**
     * 获取登录结果信息
     */
    public LoginResultVO getLoginResult(RequestEmployee requestEmployee, boolean superPasswordFlag) {

        // 基础信息
        LoginResultVO loginResultVO = SolvelaBeanUtil.copy(requestEmployee, LoginResultVO.class);

        // 前端菜单和功能点清单
        List<RoleVO> roleList = roleEmployeeService.getRoleIdList(requestEmployee.getEmployeeId());
        List<MenuVO> menuAndPointsList = roleMenuService.getMenuList(roleList.stream().map(RoleVO::getRoleId).collect(Collectors.toList()), requestEmployee.getAdministratorFlag());
        loginResultVO.setMenuList(menuAndPointsList);

        // 上次登录信息
        LoginLogVO loginLogVO = loginLogService.queryLastByUserId(requestEmployee.getEmployeeId(), UserTypeEnum.ADMIN_EMPLOYEE, LoginLogResultEnum.LOGIN_SUCCESS);
        if (loginLogVO != null) {
            loginResultVO.setLastLoginIp(loginLogVO.getLoginIp());
            loginResultVO.setLastLoginIpRegion(loginLogVO.getLoginIpRegion());
            loginResultVO.setLastLoginTime(loginLogVO.getCreateTime());
            loginResultVO.setLastLoginUserAgent(loginLogVO.getUserAgent());
        }

        // 是否需要强制修改密码
        boolean needChangePasswordFlag = protectPasswordService.checkNeedChangePassword(requestEmployee.getUserType().getValue(), requestEmployee.getUserId());
        loginResultVO.setNeedUpdatePwdFlag(needChangePasswordFlag);

        // 万能密码登录，则不需要设置强制修改密码
        if (superPasswordFlag) {
            loginResultVO.setNeedUpdatePwdFlag(false);
        }

        return loginResultVO;
    }


    /**
     * 退出登录
     */
    public void logout(RequestEmployee requestUser) {

        // 吊销本次会话的令牌。<b>只吊销这一个</b> —— 账号禁用/改密走 revokeAll，
        // 不该由「点了退出」顺带把这个人其它设备上的会话也踢掉
        tokenStore.revoke(CurrentEmployee.tokenOrNull());

        // 清除用户登录信息缓存和权限信息
        this.clearLoginEmployeeCache(requestUser.getUserId());

        //保存登出日志
        LoginLogEntity loginEntity = LoginLogEntity.builder()
                .userId(requestUser.getUserId())
                .userType(requestUser.getUserType())
                .userName(requestUser.getUserName())
                .userAgent(requestUser.getUserAgent())
                .loginIp(requestUser.getIp())
                .loginIpRegion(SolvelaIpUtil.getRegion(requestUser.getIp()))
                .loginResult(LoginLogResultEnum.LOGIN_OUT)
                .createTime(LocalDateTime.now())
                .build();
        loginLogService.log(loginEntity);

    }

    /**
     * 保存登录日志
     */
    private void saveLoginLog(LoginAttempt attempt, LoginLogResultEnum result, String remark) {
        LoginLogEntity loginEntity = LoginLogEntity.builder()
                .userId(attempt.employee().getEmployeeId())
                .userType(UserTypeEnum.ADMIN_EMPLOYEE)
                .userName(attempt.employee().getActualName())
                .userAgent(attempt.userAgent())
                .loginIp(attempt.ip())
                .loginIpRegion(SolvelaIpUtil.getRegion(attempt.ip()))
                .remark(remark)
                .loginDevice(attempt.device().getDesc())
                .loginResult(result)
                .createTime(LocalDateTime.now())
                .build();
        loginLogService.log(loginEntity);
    }




    /**
     * 发送邮箱验证码。
     *
     * <p>校验 -> 限频 -> 生成并发送。中间任何一步不通过都直接抛，只有一处例外：
     * <b>账号不存在时静默返回</b>，见 {@link #requireSendableEmployee}。
     */
    public void sendEmailCode(String loginName) {
        if (!level3ProtectConfigService.isTwoFactorLoginEnabled()) {
            throw new BusinessException("无需使用邮箱验证码");
        }

        EmployeeEntity employee = requireSendableEmployee(loginName);
        if (employee == null) {
            return;
        }

        String codeKey = emailCodeKey(employee.getEmployeeId());
        requireCooldownElapsed(codeKey);
        sendAndRemember(employee, codeKey);
    }

    /**
     * 取一个「能收验证码」的员工。<b>账号不存在时返回 null 让调用方静默成功</b> ——
     * 区分「有没有这个账号」等于对外送出一个账号枚举接口。
     *
     * <p>已删除/已禁用/没配邮箱则如实抛：这三种情况用户已经通过了「账号存在」这一关，
     * 含糊其辞只会让他反复重试一件永远不会成功的事。
     */
    private EmployeeEntity requireSendableEmployee(String loginName) {
        EmployeeEntity employee = employeeService.getByLoginName(loginName);
        if (null == employee) {
            return null;
        }
        if (employee.getDeletedFlag()) {
            throw new BusinessException("您的账号已被删除,请联系工作人员！");
        }
        if (employee.getDisabledFlag()) {
            throw new BusinessException("您的账号已被禁用,请联系工作人员！");
        }
        if (SolvelaStringUtil.isBlank(employee.getEmail())) {
            throw new BusinessException("您暂未配置邮箱地址，请联系管理员配置邮箱");
        }
        return employee;
    }

    /**
     * 60 秒内不许重发。
     *
     * <p>发送时间是<b>拼在验证码值里</b>的（{@code 验证码_毫秒}），不另存一个键 ——
     * 两个键会各自过期，出现「码还在、时间戳没了」这种半个状态。
     */
    private void requireCooldownElapsed(String codeKey) {
        String stored = redisService.get(codeKey);
        long sentAt = SolvelaStringUtil.isEmpty(stored)
                ? -1
                : Long.parseLong(stored.split(StringConst.UNDERLINE)[1]);
        if (System.currentTimeMillis() - sentAt < EMAIL_CODE_RESEND_INTERVAL_MILLIS) {
            throw new BusinessException("邮箱验证码已发送，一分钟内请勿重复发送");
        }
    }

    /** 生成、存 Redis、发邮件。存的值是 {@code 验证码_发送毫秒}，重发限频靠它 */
    private void sendAndRemember(EmployeeEntity employee, String codeKey) {
        String verificationCode = SolvelaRandomUtil.secureRandomNumbers(EMAIL_CODE_LENGTH);
        redisService.set(codeKey,
                verificationCode + StringConst.UNDERLINE + System.currentTimeMillis(),
                EMAIL_CODE_TTL_SECONDS);

        HashMap<String, Object> mailParams = new HashMap<>();
        mailParams.put("code", verificationCode);
        mailService.sendMail(MailTemplateCodeEnum.LOGIN_VERIFICATION_CODE, mailParams,
                Collections.singletonList(employee.getEmail()));
    }


    /**
     * 校验邮箱验证码
     */
    private void validateEmailCode(LoginForm loginForm, EmployeeEntity employeeEntity, boolean superPasswordFlag) {
        // 万能密码则不校验
        if (superPasswordFlag) {
            return;
        }

        // 未开启双因子登录
        if (!level3ProtectConfigService.isTwoFactorLoginEnabled()) {
            return;
        }

        if (SolvelaStringUtil.isEmpty(loginForm.getEmailCode())) {
            throw new BusinessException("请输入邮箱验证码");
        }

        // 校验验证码
        String redisVerificationCodeKey = emailCodeKey(employeeEntity.getEmployeeId());
        String emailCode = redisService.get(redisVerificationCodeKey);
        if (SolvelaStringUtil.isEmpty(emailCode)) {
            throw new BusinessException("邮箱验证码已失效，请重新发送");
        }

        if (!emailCode.split(StringConst.UNDERLINE)[0].equals(loginForm.getEmailCode().trim())) {
            throw new BusinessException("邮箱验证码错误，请重新填写");
        }

    }

    /**
     * 邮箱验证码的 redis key。
     *
     * <p>发送、校验、删除三处必须算出<b>同一个字符串</b>，以前是三份复制粘贴的拼接表达式 ——
     * 改前缀时漏改一处，表现是「验证码发出去了但怎么填都说失效」。收敛到这里之后改不漏。
     */
    private String emailCodeKey(Long employeeId) {
        return redisService.generateRedisKey(EMAIL_CODE_KEY_PREFIX,
                UserTypeEnum.ADMIN_EMPLOYEE.getValue() + StringConst.COLON + employeeId);
    }

    /**
     * 移除邮箱验证码
     */
    private void deleteEmailCode(Long employeeId) {
        String redisVerificationCodeKey = emailCodeKey(employeeId);
        redisService.delete(redisVerificationCodeKey);
    }

    public void clearLoginEmployeeCache(Long employeeId) {
        loginManager.clearUserPermission(employeeId);
        loginManager.clearUserLoginInfo(employeeId);
    }
}
