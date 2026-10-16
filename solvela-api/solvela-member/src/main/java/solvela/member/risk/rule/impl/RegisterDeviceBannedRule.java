package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.device.DeviceDispositionService;

/**
 * 注册场景的设备封禁。判据同登录那条（{@link DeviceBannedRule}），只是另一行配置
 * —— 理由见 {@link RegisterIpBlacklistRule}。
 *
 * <h3>🔴 它不是「顺手也加一条」，而是在补回一道已经存在的闸</h3>
 * 改造前 {@code DeviceGuard.checkRegister} 的<b>第一件事</b>就是判封禁，排在计数之前。
 * 把那个方法删掉而不补这条规则，等于一台被人工封掉的设备又能注册了 ——
 * 而这件事不会有任何报错，只会表现为「封了设备，对方照样在建号」。
 *
 * @Date 2026-10-16
 */
@Component
public class RegisterDeviceBannedRule extends DeviceBannedRule {

    public RegisterDeviceBannedRule(DeviceDispositionService dispositionService) {
        super(dispositionService);
    }

    @Override
    public String code() {
        return "REG_DEVICE_BANNED";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.REGISTER;
    }
}
