package solvela.draw.drawlog.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 抽奖记录 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */

@Data
public class DrawPrizeLogAddForm {

    @Schema(description = "请求ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请求ID 不能为空")
    private String traceId;

    @Schema(description = "活动编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "活动编码 不能为空")
    private String activityCode;

    @Schema(description = "奖池编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖池编码 不能为空")
    private String poolCode;

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    @Schema(description = "会员号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号 不能为空")
    private Long memberId;

    @Schema(description = "奖项ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "奖项ID 不能为空")
    private Long prizeItemId;

    @Schema(description = "奖品code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "奖品code 不能为空")
    private String prizeCode;

    @Schema(description = "状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}