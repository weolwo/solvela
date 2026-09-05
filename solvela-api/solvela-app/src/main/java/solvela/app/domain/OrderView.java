package solvela.app.domain;

import java.util.List;

/**
 * 一条兑换记录（C 端形状）。
 *
 * @param cost       对价的完整说法，如「45,000 积分 + ¥299.00」。
 *                   <b>拼好再下发</b> —— 积分是整数、现金是小数，
 *                   让端上自己拼这两半，三个页面就会拼出三种样子
 * @param specs      规格，如 {@code ["颜色：曜石黑", "尺码：L"]}。无规格商品是空列表
 * @param statusText 给用户看的状态
 * @param hint       状态之外还要说的那一句（「等待发货」「已退回积分」）。
 *                   没有就为 null，端上不画那一行
 */
public record OrderView(
        String orderNo,
        String commodityName,
        String coverUrl,
        List<String> specs,
        Integer quantity,
        String cost,
        String statusText,
        String status,
        String hint,
        String createTime) {
}
