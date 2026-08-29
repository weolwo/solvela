package solvela.admin.module.system.securityprotect.service;

import solvela.exception.BusinessException;
import jakarta.annotation.Resource;
import solvela.crypto.PasswordCipher;
import solvela.admin.module.system.login.domain.RequestEmployee;
import solvela.base.util.SolvelaStringUtil;
import solvela.admin.module.system.securityprotect.dao.PasswordLogDao;
import solvela.admin.module.system.securityprotect.domain.PasswordLogEntity;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 三级等保 密码 相关
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/10/11 19:25:59
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>，Since 2012
 */

@Service
public class SecurityPasswordService {

    /**
     * 密码长度8-20位且包含大小写字母、数字、特殊符号三种及以上组合
     */
    public static final String PASSWORD_PATTERN = "^(?![a-zA-Z]+$)(?![A-Z0-9]+$)(?![A-Z\\W_!@#$%^&*`~()-+=]+$)(?![a-z0-9]+$)(?![a-z\\W_!@#$%^&*`~()-+=]+$)(?![0-9\\W_!@#$%^&*`~()-+=]+$)[a-zA-Z0-9\\W_!@#$%^&*`~()-+=]*$";

    public static final String PASSWORD_FORMAT_MSG = "密码必须为长度8-20位且必须包含大小写字母、数字、特殊符号（如：@#$%^&*()_+-=）等三种字符";

    private static final int PASSWORD_LENGTH = 8;


    @Resource
    private PasswordLogDao passwordLogDao;

    @Resource
    private Level3ProtectConfigService level3ProtectConfigService;

    /**
     * 校验密码复杂度
     */
    public void validatePasswordComplexity(String password) {

        if (SolvelaStringUtil.isEmpty(password)) {
            throw new BusinessException(PASSWORD_FORMAT_MSG);
        }

        // 密码长度必须大于等于8位
        if (password.length() < PASSWORD_LENGTH) {
            throw new BusinessException(PASSWORD_FORMAT_MSG);
        }

        // 无需校验 密码复杂度
        if (!level3ProtectConfigService.isPasswordComplexityEnabled()) {
            return;
        }

        if (!password.matches(PASSWORD_PATTERN)) {
            throw new BusinessException(PASSWORD_FORMAT_MSG);
        }

    }

    /**
     * 校验密码重复次数
     */
    public void validatePasswordRepeatTimes(RequestEmployee requestUser, String newPassword) {

        // 密码重复次数小于1  无需校验
        if (level3ProtectConfigService.getRegularChangePasswordNotAllowRepeatTimes() < 1) {
            return;
        }

        // 检查最近几次是否有重复密码
        List<String> oldPasswords = passwordLogDao.selectOldPassword(requestUser.getUserType().getValue(), requestUser.getUserId(), level3ProtectConfigService.getRegularChangePasswordNotAllowRepeatTimes());
        boolean isDuplicate = oldPasswords.stream().anyMatch(oldPassword -> PasswordCipher.matches(newPassword, oldPassword));
        if (isDuplicate) {
            throw new BusinessException(String.format("与前%d个历史密码重复，请换个密码!", level3ProtectConfigService.getRegularChangePasswordNotAllowRepeatTimes()));
        }

    }

    /**
     * 随机生成密码
     */
    public String randomPassword() {
        // 未开启密码复杂度，则由8为数字构成
        if (!level3ProtectConfigService.isPasswordComplexityEnabled()) {
            return RandomStringUtils.randomNumeric(PASSWORD_LENGTH);
        }

        // 3位大写字母，2位数字，2位小写字母 + 1位特殊符号
        return RandomStringUtils.randomAlphabetic(3).toUpperCase()
                + RandomStringUtils.randomNumeric(2)
                + RandomStringUtils.randomAlphabetic(2).toLowerCase()
                + (ThreadLocalRandom.current().nextBoolean() ? "#" : "@");
    }


    /**
     * 保存修改密码
     */
    public void saveUserChangePasswordLog(RequestEmployee requestUser, String newPassword, String oldPassword) {

        PasswordLogEntity passwordLogEntity = new PasswordLogEntity();
        passwordLogEntity.setNewPassword(newPassword);
        passwordLogEntity.setOldPassword(oldPassword);
        passwordLogEntity.setUserId(requestUser.getUserId());
        passwordLogEntity.setUserType(requestUser.getUserType().getValue());
        passwordLogDao.insert(passwordLogEntity);
    }

    /**
     * 检查是否需要修改密码
     */
    public boolean checkNeedChangePassword(Integer userType, Long userId) {

        if (level3ProtectConfigService.getRegularChangePasswordDays() < 1) {
            return false;
        }

        PasswordLogEntity passwordLogEntity = passwordLogDao.selectLastByUserTypeAndUserId(userType, userId);
        if (passwordLogEntity == null) {
            return false;
        }

        LocalDateTime nextUpdateTime = passwordLogEntity.getCreateTime().plusDays(level3ProtectConfigService.getRegularChangePasswordDays());
        return nextUpdateTime.isBefore(LocalDateTime.now());
    }

    // 哈希算法本身已下沉到 solvela-base 的 PasswordCipher：那是所有账号体系（员工/会员）
    // 都要用的算法，而本类是「后台可调的等保策略」。原先混在一起，导致会员端为了校验一次密码
    // 就得依赖整个等保服务，连带把 t_login_fail、t_config 的等保配置一起拖进 C 端。

}
