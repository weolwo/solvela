package sa.lottery.config.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票配置 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */

@Data
public class LotteryConfigVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "彩票名称")
    private String lotteryName;

    @Schema(description = "字符集：0-9, A-Z")
    private String numberCharset;

    @Schema(description = "号码长度")
    private Integer numberLength;

    @Schema(description = "号池总数 (如: 1,000,000)")
    private Integer totalCount;

    @Schema(description = "状态：0-下线, 1-上线")
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
