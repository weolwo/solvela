package solvela.admin.module.risk.promotiongroup.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 优惠配置分组 列表 VO
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
public class PromotionGroupVO {

    @Schema(description = "分组ID")
    private Long id;

    @Schema(description = "分组编码")
    private String groupCode;

    @Schema(description = "分组名称")
    private String groupName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：0-停用, 1-启用。停用时组内配置全部停用")
    private EnableStatusEnum status;

    @Schema(description = "组内已配置的资产类型数（含停用）")
    private Integer typeCount;

    @Schema(description = "组内启用中的资产类型数")
    private Integer enabledTypeCount;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
