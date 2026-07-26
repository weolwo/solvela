package net.lab1024.sa.lottery.record.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户号码记录 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */

@Data
public class LotteryRecordAddForm {

    @Schema(description = "租户id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户id 不能为空")
    private String tenantId;

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "归属期号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "归属期号 不能为空")
    private String issueNo;

    @Schema(description = "FPE算号基数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "FPE算号基数 不能为空")
    private Integer sequenceNo;

    @Schema(description = "彩票号码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票号码 不能为空")
    private String ticketNumber;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "领取时间")
    private LocalDateTime obtainTime;

    @Schema(description = "中奖状态: 0-未开奖, 1-未中奖, 已开奖", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "中奖状态: 0-未开奖, 1-未中奖, 已开奖 不能为空")
    private Integer winStatus;

    @Schema(description = "奖励等级")
    private Integer prizeLevel;

    @Schema(description = "签名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "签名 不能为空")
    private String securitySign;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}