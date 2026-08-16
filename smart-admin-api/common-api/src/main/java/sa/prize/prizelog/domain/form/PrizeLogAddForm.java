package sa.prize.prizelog.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖励记录表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */

@Data
public class PrizeLogAddForm {

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "会员名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员名 不能为空")
    private String memberName;

    @Schema(description = "奖品编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品编码 不能为空")
    private String prizeCode;

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "奖品级别")
    private Integer prizeLevel;

    @Schema(description = "奖品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品名称 不能为空")
    private String prizeName;

    @Schema(description = "奖励类型：SCORE, BALANCE, COUPON, PHYSICAL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖励类型：SCORE, BALANCE, COUPON, PHYSICAL 不能为空")
    private String prizeType;

    @Schema(description = "奖励体值(积分数/券ID)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖励体值(积分数/券ID) 不能为空")
    private String prizeValue;

    @Schema(description = "异常原因")
    private String failReason;

    @Schema(description = "审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回 不能为空")
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

}
