package solvela.mall.commodity.domain.query;

import solvela.enums.MallPayTypeEnum;
import solvela.enums.MallCommodityStatusEnum;
import solvela.base.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城商品分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallCommodityQuery extends PageParam {

    /** 商品编码：10位大写字母+数字，全局唯一，创建后不可改 */
    private String commodityCode;

    /** 分类id */
    private Long categoryId;

    /** 状态：0-下架, 1-上架, 2-草稿 */
    private MallCommodityStatusEnum status;

    /** 商品类型：PHYSICAL-实物, COUPON-优惠券, BALANCE-现金/红包 */
    private String commodityType;

    /** 模糊匹配。列表页那个输入框是「搜索」，精确等于的话运营得把商品名一字不差地打出来 */
    private String commodityName;

    /** 支付方式：1-纯积分, 2-积分+现金 */
    private MallPayTypeEnum payType;

}
