package net.lab1024.sa.prize.prizelog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖励记录表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */

@Data
public class PrizeLogVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "优惠配置ID")
    private Long promotionConfigId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖品级别")
    private Integer prizeLevel;

    @Schema(description = "奖品名称")
    private String prizeName;

    @Schema(description = "奖励类型：SCORE, BALANCE, COUPON, PHYSICAL")
    private String prizeType;

    @Schema(description = "奖励体值(积分数/券ID)")
    private String prizeValue;

    @Schema(description = "异常原因")
    private String failReason;

    @Schema(description = "执行状态：0-等待, 1-成功, 2-失败")
    private Integer status;

    @Schema(description = "外部单号")
    private String externalBizNo;

    @Schema(description = "异常原因")
    private String remark;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
