package solvela.ledger.coupon.manual.domain;

import java.util.List;

/**
 * 人工发券的入参。
 *
 * @param memberIds  收件人会员号。<b>有硬上限</b>，见 {@code CouponManualGrantService}
 * @param couponCode 券模编码。🔴 <b>必须有启用中的模板</b>，没有直接拒绝
 * @param quantity   每人发几张。也有上限 —— 「每人 1000 张」和「发给 1000 个人」
 *                   是同一件事的两种写法
 * @param bizRefId   工单号 / 批次号。<b>幂等键</b>，库上有唯一索引兜着
 * @param reason     发券原因，会<b>原样显示给用户</b>（「就您 9 月 12 日的问题补偿」）。
 *                   没有它，用户收到的是一张来路不明的券
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record ManualCouponGrantCmd(List<Long> memberIds,
                                   String couponCode,
                                   Integer quantity,
                                   String bizRefId,
                                   String reason) {

    public int quantityOrOne() {
        return quantity == null || quantity < 1 ? 1 : quantity;
    }
}
