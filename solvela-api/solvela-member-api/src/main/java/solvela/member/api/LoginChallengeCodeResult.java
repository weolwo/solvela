package solvela.member.api;

/**
 * 登录二次验证发码的结果。
 *
 * @param reason            失败原因；成功时为 null
 * @param retryAfterSeconds 限频时还要等多久
 * @param maskedTarget      码寄到了哪（打过码的邮箱或手机号），客户端展示「已发送到 xxx」
 *
 * @Date 2026-09-27
 */
public record LoginChallengeCodeResult(Reason reason, long retryAfterSeconds, String maskedTarget) {

    public enum Reason {
        /** 凭票无效，要重新登录 */
        CHALLENGE_EXPIRED,
        /** 上一条还在冷却 */
        TOO_FREQUENT,
        /** 今天发得太多了 */
        DAILY_LIMIT_REACHED,
        /** 发不出去 —— 我们自己的问题 */
        SEND_FAILED,
    }

    public boolean success() {
        return reason == null;
    }

    public static LoginChallengeCodeResult sent(String maskedTarget) {
        return new LoginChallengeCodeResult(null, 0L, maskedTarget);
    }

    public static LoginChallengeCodeResult fail(Reason reason) {
        return new LoginChallengeCodeResult(reason, 0L, null);
    }

    public static LoginChallengeCodeResult retryLater(Reason reason, long retryAfterSeconds) {
        return new LoginChallengeCodeResult(reason, retryAfterSeconds, null);
    }
}
