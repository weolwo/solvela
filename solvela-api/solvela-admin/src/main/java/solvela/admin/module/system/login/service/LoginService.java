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
import solvela.admin.module.system.mail.MailService;
import solvela.admin.module.system.mail.constant.MailTemplateCodeEnum;
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
     * 员工登录
     *
     * @return 返回用户登录信息
     */
    public LoginResultVO login(LoginForm loginForm, String ip, String userAgent) {

        LoginDeviceEnum loginDeviceEnum = SolvelaEnumUtil.getEnumByValue(loginForm.getLoginDevice(), LoginDeviceEnum.class);
        if (loginDeviceEnum == null) {
            throw new BusinessException("登录设备暂不支持！");
        }
        // 验证登录名
        EmployeeEntity employeeEntity = employeeService.getByLoginName(loginForm.getLoginName());
        if (null == employeeEntity) {
            throw new BusinessException("登录名或密码错误！");
        }

        // 验证账号状态
        if (employeeEntity.getDeletedFlag()) {
            saveLoginLog(employeeEntity, ip, userAgent, "账号已删除", LoginLogResultEnum.LOGIN_FAIL, loginDeviceEnum);
            throw new BusinessException("您的账号已被删除,请联系工作人员！");
        }

        if (employeeEntity.getDisabledFlag()) {
            saveLoginLog(employeeEntity, ip, userAgent, "账号已禁用", LoginLogResultEnum.LOGIN_FAIL, loginDeviceEnum);
            throw new BusinessException("您的账号已被禁用,请联系工作人员！");
        }

        // 解密前端加密的密码
        String requestPassword = apiEncryptService.decrypt(loginForm.getPassword());

        // 验证密码 是否为万能密码
        String superPassword = configService.getConfigValue(ConfigKeyEnum.SUPER_PASSWORD);
        boolean superPasswordFlag = superPassword.equals(requestPassword);

        // 校验双因子登录（不通过直接抛）
        validateEmailCode(loginForm, employeeEntity, superPasswordFlag);

        AccessToken accessToken;
        if (superPasswordFlag) {

            accessToken = tokenStore.issue(employeeEntity.getEmployeeId(), true,
                    loginDeviceEnum.getDesc(), SUPER_PASSWORD_TOKEN_TTL);

        } else {

            // 按照等保登录要求，进行登录失败次数校验；已锁定会直接抛 LOGIN_FAIL_LOCK
            LoginFailEntity loginFailEntity = securityLoginService.checkLogin(
                    employeeEntity.getEmployeeId(), UserTypeEnum.ADMIN_EMPLOYEE);

            // 密码错误
            if (!PasswordCipher.matches(employeeService.generateSaltPassword(requestPassword, employeeEntity.getEmployeeUid()), employeeEntity.getLoginPwd())) {
                // 记录登录失败
                saveLoginLog(employeeEntity, ip, userAgent, "密码错误", LoginLogResultEnum.LOGIN_FAIL, loginDeviceEnum);
                // 记录等级保护次数
                String msg = securityLoginService.recordLoginFail(employeeEntity.getEmployeeId(),
                        UserTypeEnum.ADMIN_EMPLOYEE, employeeEntity.getLoginName(), loginFailEntity);
                // msg 非空说明等保开了失败锁定，里面写着「还剩几次 / 锁到什么时候」——
                // 这句话必须原样给用户，否则他不知道再错一次会发生什么
                throw msg == null
                        ? new BusinessException("登录名或密码错误！")
                        : new BusinessException(UserErrorCode.LOGIN_FAIL_WILL_LOCK, msg);
            }

            accessToken = tokenStore.issue(employeeEntity.getEmployeeId(), false, loginDeviceEnum.getDesc());

            // 移除邮箱验证码
            deleteEmailCode(employeeEntity.getEmployeeId());
        }

        // 获取员工信息
        RequestEmployee requestEmployee = loginManager.loadLoginInfo(employeeEntity);

        // 移除登录失败
        securityLoginService.removeLoginFail(employeeEntity.getEmployeeId(), UserTypeEnum.ADMIN_EMPLOYEE);

        // 获取登录结果信息
        String token = accessToken.value();
        LoginResultVO loginResultVO = getLoginResult(requestEmployee, superPasswordFlag);

        //保存登录记录
        saveLoginLog(employeeEntity, ip, userAgent, superPasswordFlag ? "万能密码登录" : StringConst.EMPTY, LoginLogResultEnum.LOGIN_SUCCESS, loginDeviceEnum);

        // 设置 token
        loginResultVO.setToken(token);

        // 更新用户权限
        loginManager.loadUserPermission(employeeEntity.getEmployeeId());

        return loginResultVO;
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
    private void saveLoginLog(EmployeeEntity employeeEntity, String ip, String userAgent, String remark, LoginLogResultEnum result, LoginDeviceEnum loginDeviceEnum) {
        LoginLogEntity loginEntity = LoginLogEntity.builder()
                .userId(employeeEntity.getEmployeeId())
                .userType(UserTypeEnum.ADMIN_EMPLOYEE)
                .userName(employeeEntity.getActualName())
                .userAgent(userAgent)
                .loginIp(ip)
                .loginIpRegion(SolvelaIpUtil.getRegion(ip))
                .remark(remark)
                .loginDevice(loginDeviceEnum.getDesc())
                .loginResult(result)
                .createTime(LocalDateTime.now())
                .build();
        loginLogService.log(loginEntity);
    }




    /**
     * 发送 邮箱 验证码
     */
    public void sendEmailCode(String loginName) {

        // 开启双因子登录
        if (!level3ProtectConfigService.isTwoFactorLoginEnabled()) {
            throw new BusinessException("无需使用邮箱验证码");
        }

        // 验证登录名
        EmployeeEntity employeeEntity = employeeService.getByLoginName(loginName);
        if (null == employeeEntity) {
            // 账号不存在也当作成功：区分「有没有这个账号」等于送出一个账号枚举接口
            return;
        }

        // 验证账号状态
        if (employeeEntity.getDeletedFlag()) {
            throw new BusinessException("您的账号已被删除,请联系工作人员！");
        }

        if (employeeEntity.getDisabledFlag()) {
            throw new BusinessException("您的账号已被禁用,请联系工作人员！");
        }

        String mail = employeeEntity.getEmail();
        if (SolvelaStringUtil.isBlank(mail)) {
            throw new BusinessException("您暂未配置邮箱地址，请联系管理员配置邮箱");
        }

        // 校验验证码发送时间，60秒内不能重复发生
        String redisVerificationCodeKey = emailCodeKey(employeeEntity.getEmployeeId());
        String emailCode = redisService.get(redisVerificationCodeKey);
        long sendCodeTimeMills = -1;
        if (!SolvelaStringUtil.isEmpty(emailCode)) {
            sendCodeTimeMills = Long.parseLong(emailCode.split(StringConst.UNDERLINE)[1]);
        }

        if (System.currentTimeMillis() - sendCodeTimeMills < 60 * 1000) {
            throw new BusinessException("邮箱验证码已发送，一分钟内请勿重复发送");
        }

        //生成验证码
        long currentTimeMillis = System.currentTimeMillis();
        String verificationCode = SolvelaRandomUtil.secureRandomNumbers(4);
        redisService.set(redisVerificationCodeKey, verificationCode + StringConst.UNDERLINE + currentTimeMillis, 300);

        // 发送邮件验证码
        HashMap<String, Object> mailParams = new HashMap<>();
        mailParams.put("code", verificationCode);
        mailService.sendMail(MailTemplateCodeEnum.LOGIN_VERIFICATION_CODE, mailParams,
                Collections.singletonList(employeeEntity.getEmail()));
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
