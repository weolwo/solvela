package solvela.mall.sku.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

/**
 * 商城SKU分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MallSkuQuery extends PageParam {

    /** 商品名称：模糊匹配 */
    private String commodityName;

    /** 商品编码：精确匹配 */
    private String commodityCode;

    /** SKU编码：精确匹配 */
    private String skuCode;

    /** 分类id */
    private Long categoryId;

    /** SKU状态：0-停用, 1-启用 */
    private Integer skuStatus;

    /** 商品状态：0-下架, 1-上架, 2-草稿 */
    private Integer commodityStatus;

    /**
     * 只看告警：可用库存 ≤ 阈值。
     *
     * <p>这是这个页面的主用法 —— 打开就想知道「今天该补哪些货」，
     * 而不是从几百个规格里自己找。
     */
    private Integer stockBelow;
}
