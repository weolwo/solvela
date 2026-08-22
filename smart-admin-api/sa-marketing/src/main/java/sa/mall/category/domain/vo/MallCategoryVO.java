package sa.mall.category.domain.vo;

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
