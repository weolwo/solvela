package sa.prize.prizelog.domain.vo;

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

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    @Schema(description = "会员账号（下单当时的快照）")
    private String memberName;

    @Schema(description = "奖品编码")
    private String prizeCode;

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

    @Schema(description = "审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回")
    private Integer approveStatus;

    @Schema(description = "审批人")
    private String approveBy;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "过期时间")
    private LocalDateTime validUntil;

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
