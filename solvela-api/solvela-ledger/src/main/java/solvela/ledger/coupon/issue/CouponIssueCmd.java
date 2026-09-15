package solvela.ledger.coupon.issue;

/**
 * 发一张券要知道的全部信息。
 *
 * <p>刻意做成一个入参对象而不是六个参数：这六个里有四个都是 {@code String}，
 * 位置写反了编译器一句话都不会说，而结果是一张券的来源单号变成了券名。
 *
 * @param couponCode   券模编码。发券侧靠它去 {@code t_coupon_template} 找规则
 * @param memberId     发给谁
 * @param memberName   会员账号<b>展示快照</b>，不是关联键（见 {@code MemberCoupon.memberName}）
 * @param sourceType   来源：{@code PROPOSAL}（发奖）/ {@code MALL}（兑换）/ 将来的人工发券
 * @param sourceBizId  来源单号。客服拿着一张券要回答「这是哪来的」，靠的就是它
 * @param fallbackName 兜底券名：<b>只在券模板不存在时</b>才会用到。
 *                     传调用方手上那个展示名（提案的 assetName / 商品名），
 *                     它比券编码好看，但比模板名不权威 —— 见
 *                     {@link CouponIssueService} 对券名优先级的说明
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
public record CouponIssueCmd(String couponCode,
                             Long memberId,
                             String memberName,
                             String sourceType,
                             String sourceBizId,
                             String fallbackName) {
}
