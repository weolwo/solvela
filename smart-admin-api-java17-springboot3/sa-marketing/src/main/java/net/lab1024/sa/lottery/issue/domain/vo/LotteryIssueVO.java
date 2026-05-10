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

    @Schema(description = "开始偏移量")
    private Integer startOffset;

    @Schema(description = "已售/已派发数量")
    private Integer soldCount;

    @Schema(description = "开始售卖")
    private LocalDateTime sellStartTime;

    @Schema(description = "结束时间")
    private LocalDateTime sellEndTime;

    @Schema(description = "开奖时间")
    private LocalDateTime openTime;

    @Schema(description = "是否可重复开奖：0否，1是")
    private Integer canRepeat;

    @Schema(description = "开奖号码")
    private String winningNumber;

    @Schema(description = "状态: 0-待开奖, 1-部分开奖, 2-已开奖")
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
