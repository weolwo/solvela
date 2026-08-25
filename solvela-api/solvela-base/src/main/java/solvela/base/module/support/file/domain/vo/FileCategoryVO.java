package solvela.base.module.support.file.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件分类（带统计）。给素材库的分类卡片用。
 *
 * @Date 2026-08-10
 */
@Data
public class FileCategoryVO {

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类编码，代码引用它而非ID")
    private String categoryCode;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "标签")
    private String categoryTag;

    @Schema(description = "排序")
    private Integer sort;

    /**
     * 该分类下的文件数。
     *
     * <p><b>用一次 GROUP BY 统计出来，不是每张卡片查一次</b> —— 分类卡片页会一次性展示
     * 十几到几十张卡，逐个 count 就是 N+1，而这个页面是运营每天进的。
     */
    @Schema(description = "该分类下的文件数")
    private Long fileCount;

    @Schema(description = "是否内置分类（内置的不允许删除、不允许改编码）")
    private Boolean systemFlag;
}
