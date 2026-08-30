package solvela.risk.promotiongroup.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;
import solvela.enums.EnableStatusEnum;

/**
 * 优惠配置分组分页查询的领域参数。
 *
 * <p>刻意没有 {@code @Schema} 与校验注解 —— 接口文档和参数校验是端的职责，
 * 分层理由见 {@code PromotionConfigQuery}。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PromotionGroupQuery extends PageParam {

    /** 分组名称，模糊匹配 */
    private String groupName;

    /** 分组编码 */
    private String groupCode;

    /** 状态：0-停用, 1-启用 */
    private EnableStatusEnum status;
}
