package solvela.admin.module.ledger.coupontemplate.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一个「会发券但没有券模板」的配置点。
 *
 * @param couponCode 券模编码 —— 奖品的 {@code prize_code} 或商品的 {@code asset_ref}
 * @param sourceType 谁会发它：{@code PRIZE}（奖品配置）/ {@code MALL}（商城商品）
 * @param sourceCode 那个配置本身的编码，运营拿它去对应的页面里找
 * @param sourceName 那个配置的名字，给人看的
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Schema(description = "会发券但没有券模板的配置点")
public record CouponTemplateGapVO(String couponCode,
                                  String sourceType,
                                  String sourceCode,
                                  String sourceName) {
}
