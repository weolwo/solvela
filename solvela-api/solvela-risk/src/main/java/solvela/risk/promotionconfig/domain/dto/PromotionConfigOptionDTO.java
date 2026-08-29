package solvela.risk.promotionconfig.domain.dto;

import solvela.enums.ReviewLevelEnum;
import java.math.BigDecimal;

/**
 * 优惠配置下拉选项VO：供奖品配置表单按资产类型级联选择
 * <p>
 * 前端一次性拉全量，按 prizeType 分组缓存在本地，选完奖品类型直接过滤，
 * 避免每切一次类型就打一次接口。带上预算水位是为了让运营在下拉里就能看出这个池子还有没有额度。
 *
 * @param id          优惠配置ID，即 t_prize_config.promotion_config_id
 * @param promoName   配置名称
 * @param prizeType   资产类型：SCORE / BALANCE / COUPON / PHYSICAL，下拉级联就按它过滤
 * @param totalAmount 总预算，-1 为不限
 * @param usedAmount  已用预算
 * @param totalQuota  总数量，-1 为不限
 * @param usedQuota   已用数量
 * @param reviewLevel 审核层级：0-无需审核, 1-单层, 2-双层
 * @Author alaric
 * @Date 2026-07-26
 */
public record PromotionConfigOptionDTO(Long id,
                                      String promoName,
                                      String prizeType,
                                      BigDecimal totalAmount,
                                      BigDecimal usedAmount,
                                      Integer totalQuota,
                                      Integer usedQuota,
                                      ReviewLevelEnum reviewLevel) {
}
