package solvela.mall.category.domain.dto;

import solvela.enums.EnableStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城分类列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class MallCategoryDTO {


    private Long id;

    /** 父级id：0-顶级分类。业务上限死两级 */
    private Long parentId;

    /** 分类名称：如 数码3C / 虚拟权益 */
    private String categoryName;

    /** 分类图标 file_id（C端宫格导航用） */
    private Long iconFileId;

    /** 排序：从小到大 */
    private Integer sort;

    /** 状态：0-禁用, 1-启用 */
    private EnableStatusEnum status;

    /**
     * 该分类下的商品数。列表里直接显示出来，运营点删除之前就知道会不会被拦 ——
     * 不显示的话，删除守卫的拒绝提示对他来说是突然冒出来的。
     */
    private Integer commodityCount;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
