package net.lab1024.sa.stat.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Top 获奖用户（营销域口径：已发出价值）
 *
 * <p>⚠️ 「一键拉黑」没有现成机制可复用：{@code t_promotion_config} 只有 identify/phone/ip 限次
 * 与互斥规则，没有黑名单字段；抽奖白名单在 {@code t_prize_pool_item.white_list}。
 * 要做拉黑得新建，且属风控域而非统计范畴。
 *
 * @Author weolwo
 * @Date 2026-08-03
 */
@Data
public class TopMemberVO {

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "获奖次数（发奖记录条数）")
    private Integer count;

    @Schema(description = "获得价值：仅 status=1 计入")
    private BigDecimal issuedValue;
}
