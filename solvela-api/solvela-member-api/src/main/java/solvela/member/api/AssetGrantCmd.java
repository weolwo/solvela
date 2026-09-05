package solvela.member.api;

import java.math.BigDecimal;

/**
 * 发放指令。
 *
 * <p>字段是三条通道的并集，<b>按 {@link #assetType} 取用</b>：
 * <ul>
 *   <li>{@code PHYSICAL} —— 用 receiver 三要素，不看 assetRef / amount</li>
 *   <li>{@code COUPON}   —— 用 assetRef（券模编码）+ assetName，不看 receiver / amount</li>
 *   <li>{@code BALANCE}  —— 用 amount（<b>单份</b>面额，实发 amount × quantity），不看其余</li>
 * </ul>
 *
 * <p>之所以是一个并集 record 而不是三个子类型：跨进程之后它要序列化成 JSON，
 * sealed interface + 多态反序列化要在两边各配一套 {@code @JsonSubTypes}，
 * 而收益只是编译期少几个 null —— 那几个 null 由实现侧的前置校验挡住，
 * 拒绝原因还能带回给调用方（见 {@link AssetGrantReason}）。
 *
 * @param assetType  资产类型，取值对齐 {@code PrizeTypeEnum}
 * @param assetRef   资产引用：COUPON 存券模编码，BALANCE 存面额来源标识，PHYSICAL 留空
 * @param assetName  展示名。券名会直接显示给用户，<b>取不到时不要拿备注顶替</b> ——
 *                   {@code CouponAssetHandler} 里那段红字记的就是这个事故
 * @param quantity   发几份。null 视为 1
 * @param amount     BALANCE 的单份面额
 * @param sourceType 来源类型，落到履约单的 {@code source_type}。商城传 {@code MALL}
 * @param bizRefId   来源单号，落到履约单的 {@code source_biz_id}。商城传订单号
 * @param bizType    资产流水上的业务类型（仅 BALANCE 用得到）
 */
public record AssetGrantCmd(
        Long memberId,
        String assetType,
        String assetRef,
        String assetName,
        Integer quantity,
        BigDecimal amount,
        String sourceType,
        String bizRefId,
        String bizType,
        /* 🔴 明文。只在服务端内部流转，不要写进面向用户的日志 */
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String remark) {

    /** 份数。null / 非正数一律按 1 —— 发放宁可少发也不能因为一个脏值发出 0 或负数份 */
    public int quantityOrOne() {
        return quantity == null || quantity <= 0 ? 1 : quantity;
    }
}
