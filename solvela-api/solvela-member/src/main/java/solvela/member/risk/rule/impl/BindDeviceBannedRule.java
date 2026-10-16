package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.device.DeviceDispositionService;

/**
 * 换绑场景的设备封禁。判据同登录那条（{@link DeviceBannedRule}）。
 *
 * <p>一台被人工封掉的设备上改联系方式，是接管账号的最后一下 —— 和资产出口同一类。
 *
 * @Date 2026-10-16
 */
@Component
public class BindDeviceBannedRule extends DeviceBannedRule {

    public BindDeviceBannedRule(DeviceDispositionService dispositionService) {
        super(dispositionService);
    }

    @Override
    public String code() {
        return "BIND_DEVICE_BANNED";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.CONTACT_BIND;
    }
}
