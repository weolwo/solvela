package solvela.member.api;

/**
 * 发码 / 校验的结果。形状与 {@link EmailCodeSendResult} 一致。
 *
 * @param reason            失败原因；成功时为 null
 * @param retryAfterSeconds 还要等多久，仅 {@link StepUpFailReason#TOO_FREQUENT} /
 *                          {@link StepUpFailReason#DAILY_LIMIT_REACHED} 时有意义
 * @param maskedEmail       码寄到了哪（打过码的），仅发码成功时有值。
 *                          客户端要展示出来：「已发送到 a***@x.com」是用户判断
 *                          「这个码是不是发给我的」的唯一依据
 *
 * @Date 2026-09-26
 */
public record StepUpResult(StepUpFailReason reason, long retryAfterSeconds, String maskedEmail) {

    private static final StepUpResult VERIFIED = new StepUpResult(null, 0L, null);

    public boolean success() {
        return reason == null;
    }

    public static StepUpResult sent(String maskedEmail) {
        return new StepUpResult(null, 0L, maskedEmail);
    }

    public static StepUpResult verified() {
        return VERIFIED;
    }

    public static StepUpResult fail(StepUpFailReason reason) {
        return new StepUpResult(reason, 0L, null);
    }

    public static StepUpResult retryLater(StepUpFailReason reason, long retryAfterSeconds) {
        return new StepUpResult(reason, retryAfterSeconds, null);
    }
}
