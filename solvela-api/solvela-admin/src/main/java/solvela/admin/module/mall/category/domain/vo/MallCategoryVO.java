package solvela.admin.module.mall.category.domain.vo;

import solvela.enums.EnableStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-商品分类 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */

@Data
public class MallCategoryVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "父级id：0-顶级分类。业务上限死两级")
    private Long parentId;

    @Schema(description = "分类名称：如 数码3C / 虚拟权益")
    private String categoryName;

    @Schema(description = "分类图标 file_id（C端宫格导航用）")
    private Long iconFileId;

    @Schema(description = "排序：从小到大")
    private Integer sort;

    @Schema(description = "状态：0-禁用, 1-启用")
    private EnableStatusEnum status;

    /**
     * 该分类下的商品数。列表里直接显示出来，运营点删除之前就知道会不会被拦 ——
     * 不显示的话，删除守卫的拒绝提示对他来说是突然冒出来的。
     */
    @Schema(description = "该分类下的商品数")
    private Integer commodityCount;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
