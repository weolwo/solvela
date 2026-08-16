package sa.draw.poolitem.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池奖项库 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */

@Data
public class PrizePoolItemVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "归属活动编码")
    private String activityCode;

    @Schema(description = "关联(t_prize_config)")
    private String prizeCode;

    @Schema(description = "单人限领次数: -1不限, 1表示每人最多中一次")
    private Integer userMaxCount;

    @Schema(description = "本次活动总共出几个？-1不限")
    private Integer totalStock;

    @Schema(description = "跨奖池累计已出数量")
    private Integer usedStock;

    @Schema(description = "活动级白名单：指定用户必中")
    private String whiteList;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
