package solvela.member.api;

/**
 * 我的实物履约单，按「用户关心的那件事」数出来的几个数。给「我的」页用。
 *
 * <p>分组口径由资产域给，不让端上按 status 推 —— 与 {@link MemberDeliveryView#needAddress} 同一条规矩：
 * 状态机改一次，各端各推一份的那个端就会开始数错。
 *
 * @param total       全部（含已签收、已取消）
 * @param needAddress 待填收货信息：待发货且还没填地址。<b>要用户动手</b>，不动手就收不到东西
 * @param pending     待发货：地址已填、等仓库发
 * @param shipped     已发货：在路上
 */
public record DeliverySummaryView(long total, long needAddress, long pending, long shipped) {
}
