package solvela.marketing.api;

import solvela.enums.MallOrderStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 一条兑换记录。
 *
 * <h3>🔴 全部取订单上的快照，不 join 商品表</h3>
 * 商品名、图、价格、规格都是<b>下单当时</b>那一份。运营下周把「T恤」改名、
 * 把 5000 分调成 8000 分，历史记录必须还长原来的样子 ——
 * 靠 join 拿名字和价格，改一次价历史全乱。
 * 订单表当初把这些列都冗余了一份，就是为了这一页。
 *
 * @param coverUrl   封面图 URL，可能为 null（没配图或文件已删）。
 *                   <b>是 URL 不是 file_id</b> —— C 端没有按 id 换 URL 的接口
 * @param skuAttrs   规格快照，如 {@code {"颜色":"曜石黑","尺码":"L"}}。无规格商品是空 map
 * @param payPoints  实付积分。<b>是整单的</b>，不是单价
 * @param payCash    实付现金。payType=1 时为 0
 * @param status     状态枚举。翻成给用户看的话是网关的事
 * @param failReason 履约失败原因。🔴 <b>这句是给运营看的</b>（「券商品未配置券模编码」
 *                   这类），不能直接甩给用户 —— 网关要换成一句他能理解、
 *                   而且知道该找谁的话
 */
public record MallOrderView(
        String orderNo,
        Long commodityId,
        String commodityName,
        String commodityType,
        String coverUrl,
        Map<String, String> skuAttrs,
        Integer quantity,
        Integer payPoints,
        BigDecimal payCash,
        MallOrderStatusEnum status,
        String failReason,
        LocalDateTime createTime) {
}
