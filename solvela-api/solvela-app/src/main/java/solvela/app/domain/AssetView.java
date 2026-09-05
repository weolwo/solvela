package solvela.app.domain;

/**
 * C 端看到的一种资产。
 *
 * <h3>label 与 currency 是<b>展示决策</b>，所以在网关拼，不在 ledger 域里</h3>
 * 「SCORE 叫积分还是星币」「按金额展示（带千分位与两位小数）还是按次数展示（整数）」——
 * 这两件事换个端就可能换个答案。放进域里，第二个端想改叫法就得改域、就得发版。
 *
 * @param assetType 资产类型编码，由后端给。<b>前端不硬编码这个枚举</b> ——
 *                  新增一种资产时前端不用改。
 * @param label     给用户看的名字。认不出的编码回显编码本身，见 AssetService。
 * @param amount    金额，十进制字符串。🔴 前端不许 {@code Number()} 之后 toFixed，
 *                  超过 2^53-1 会静默丢精度，而余额正是最不该丢精度的数。
 * @param currency  是否按金额展示。为 false 表示这是个「次数」类资产，整数展示。
 * @param frozen    钱包被冻结。前端据此在这一项上标注，而不是把它藏起来 ——
 *                  藏起来用户会以为资产没了。
 */
public record AssetView(
        String assetType,
        String label,
        String amount,
        boolean currency,
        boolean frozen) {
}
