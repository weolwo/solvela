package sa.lottery.prizerule.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票奖励配置 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */

@Data
public class LotteryPrizeRuleVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "奖品奖级")
    private Integer prizeLevel;

    @Schema(description = "匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配")
    private String matchRule;

    @Schema(description = "匹配长度")
    private Integer matchLength;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
