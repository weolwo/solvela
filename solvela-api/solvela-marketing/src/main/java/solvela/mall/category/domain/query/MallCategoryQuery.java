package solvela.mall.category.domain.query;

import solvela.enums.EnableStatusEnum;
import solvela.base.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城分类分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallCategoryQuery extends PageParam {

    /** 分类名称：如 数码3C / 虚拟权益 */
    private String categoryName;

    /** 状态：0-禁用, 1-启用 */
    private EnableStatusEnum status;

    /** 创建人 */
    private String createBy;

}
