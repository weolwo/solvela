package solvela.member.api;

/**
 * 二次验证相关调用的公共入参。
 *
 * @param memberId 当前登录会员，由网关从令牌解析后填入，<b>不接受客户端传</b>
 * @param deviceId 验签通过的设备号，允许为 null（此时一律按 {@link DeviceTrust#NO_DEVICE} 处理）
 * @param clientIp 客户端 IP，发码限频用
 * @param code     用户输入的验证码，只有校验那一步用得到
 *
 * @Date 2026-09-26
 */
public record StepUpCmd(Long memberId, String deviceId, String clientIp, String code) {

    public static StepUpCmd of(Long memberId, String deviceId, String clientIp) {
        return new StepUpCmd(memberId, deviceId, clientIp, null);
    }
}
