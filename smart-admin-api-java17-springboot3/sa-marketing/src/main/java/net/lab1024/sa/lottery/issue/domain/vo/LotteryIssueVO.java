package net.lab1024.sa.lottery.issue.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 期号配置 列表VO
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
public class LotteryIssueVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "期号")
    private String issueNo;

    @Schema(description = "已售/已派发数量")
    private Integer soldCount;

    @Schema(description = "售卖开始时间")
    private LocalDateTime saleStartTime;

    @Schema(description = "售卖结束时间")
    private LocalDateTime saleEndTime;

    @Schema(description = "开奖时间")
    private LocalDateTime settleTime;

    @Schema(description = "开奖号码")
    private String winningNumber;

    @Schema(description = "状态: 0-待开奖, 1-售卖中, 2-已开奖")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
