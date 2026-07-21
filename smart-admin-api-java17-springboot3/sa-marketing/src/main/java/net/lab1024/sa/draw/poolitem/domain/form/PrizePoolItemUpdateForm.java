package net.lab1024.sa.draw.poolitem.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池奖项库 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */

@Data
public class PrizePoolItemUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "关联(t_prize_config)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "关联(t_prize_config) 不能为空")
    private String prizeCode;

    @Schema(description = "单人限领次数: -1不限, 1表示每人最多中一次", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "单人限领次数: -1不限, 1表示每人最多中一次 不能为空")
    private Integer userMaxCount;

    @Schema(description = "本次活动总共出几个？-1不限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "本次活动总共出几个？-1不限 不能为空")
    private Integer totalStock;

    @Schema(description = "跨奖池累计已出数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "跨奖池累计已出数量 不能为空")
    private Integer usedStock;

    @Schema(description = "活动级白名单：指定用户必中")
    private String whiteList;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}