package solvela.member.api;

/**
 * 确认 / 释放一张券。
 *
 * @param couponId 会员券 id
 * @param bizType  业务类型，如 {@code MALL}
 * @param bizRefId 单据号。🔴 必须和<b>锁定时一样</b> —— 条件更新是
 *                 {@code WHERE locked_biz_id = ?}，传了别的单号什么都不会发生，
 *                 而且不报错
 * @param remark   释放原因，会进流水给客服看。确认时传 null
 */
public record CouponWriteOffCmd(
        Long couponId,
        String bizType,
        String bizRefId,
        String remark) {
}
