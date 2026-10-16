package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.device.DeviceDispositionService;

/**
 * 资产出口场景的设备封禁。判据同登录那条（{@link DeviceBannedRule}）。
 *
 * <p>一台被人工封掉的设备上<b>最该拦的就是这一下</b>：登录被拦住只是进不来，
 * 而资产出口被放过，东西就真的没了。
 *
 * @Date 2026-10-16
 */
@Component
public class AssetDeviceBannedRule extends DeviceBannedRule {

    public AssetDeviceBannedRule(DeviceDispositionService dispositionService) {
        super(dispositionService);
    }

    @Override
    public String code() {
        return "ASSET_DEVICE_BANNED";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.ASSET_OUT;
    }
}
