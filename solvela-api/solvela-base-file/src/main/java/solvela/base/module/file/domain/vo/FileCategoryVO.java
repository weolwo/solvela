package solvela.base.module.file.domain.vo;

import lombok.Data;

/**
 * 文件分类（带统计）。给素材库的分类卡片用。
 *
 * @Date 2026-08-10
 */
@Data
public class FileCategoryVO {

    /** 分类ID */
    private Long categoryId;

    /** 分类编码，代码引用它而非ID */
    private String categoryCode;

    /** 分类名称 */
    private String categoryName;

    /** 标签 */
    private String categoryTag;

    /** 排序 */
    private Integer sort;

    /**
     * 该分类下的文件数。
     *
     * <p><b>用一次 GROUP BY 统计出来，不是每张卡片查一次</b> —— 分类卡片页会一次性展示
     * 十几到几十张卡，逐个 count 就是 N+1，而这个页面是运营每天进的。
     */
    private Long fileCount;

    /** 是否内置分类（内置的不允许删除、不允许改编码） */
    private Boolean systemFlag;

    /** 该分类的文件是否免登录可读。运营在分类表单里勾 */
    private Boolean publicFlag;
}
