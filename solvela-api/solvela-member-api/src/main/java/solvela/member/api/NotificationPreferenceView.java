package solvela.member.api;

/**
 * 免打扰设置。
 *
 * <h3>🔴 没有 systemEnabled 这一项</h3>
 * SYSTEM 分类<b>不可关</b>（账号被冻结、条款变更这类东西不该能被静音）。
 * 契约里不给它字段，端上就画不出那个开关 —— 这条约束由类型兜底，
 * 而不是靠前端记得把它置灰。
 */
public record NotificationPreferenceView(
        boolean tradeEnabled,
        boolean marketingEnabled) {
}
