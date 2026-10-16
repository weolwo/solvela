package solvela.member.risk.rule.impl;

import org.springframework.stereotype.Component;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.member.device.DeviceDispositionService;

/**
 * 重置密码场景的设备封禁。判据同登录那条（{@link DeviceBannedRule}）。
 *
 * <h3>🔴 这一条要想清楚再开：它可能挡住一个真的号主</h3>
 * 设备封禁是人工签的字，而「这台设备被封了」与「这个账号不是他的」不是同一件事 ——
 * 一台被封的设备上也可能坐着一个真的号主（比如他的手机被判成了刷子设备）。
 *
 * <p>仍然开着，理由是：走到重置密码这一步的人<b>已经进不去账号了</b>，
 * 而他还有别的出路（换一台设备、找客服）；而被封设备上的重置密码请求，
 * 正是接管账号最后那一下。两种错里，这一种能挽回。
 *
 * @Date 2026-10-16
 */
@Component
public class ResetDeviceBannedRule extends DeviceBannedRule {

    public ResetDeviceBannedRule(DeviceDispositionService dispositionService) {
        super(dispositionService);
    }

    @Override
    public String code() {
        return "RESET_DEVICE_BANNED";
    }

    @Override
    public MemberRiskEventTypeEnum eventType() {
        return MemberRiskEventTypeEnum.RESET_PASSWORD;
    }
}
