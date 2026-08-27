package solvela.mall.order.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城订单列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class MallOrderDTO {


    private Long id;

    /** 订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键 */
    private String orderNo;

    /** 会员号：关联键 */
    private Long memberId;

    /** 下单时的会员账号【展示快照，非关联键，不要用于查询】 */
    private String memberName;

    /** 商品id */
    private Long commodityId;

    /** 商品编码（跨环境稳定的那个） */
    private String commodityCode;

    /** SKUid */
    private Long skuId;

    /** SKU编码 */
    private String skuCode;

    /** 商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它 */
    private String commodityType;

    /** 资产引用快照：券模编码等 */
    private String assetRef;

    /** 商品名称快照 */
    private String commodityName;

    /** 封面图快照 file_id */
    private Long coverFileId;

    /** 规格快照 */
    private String skuAttrs;

    /** 兑换件数 */
    private Integer quantity;

    /** 单件积分单价快照 */
    private Integer pointsPrice;

    /** 单件现金单价快照 */
    private BigDecimal cashPrice;

    /** 实付积分合计 */
    private Integer payPoints;

    /** 实付现金合计 */
    private BigDecimal payCash;

    /** 收货地址id(软引用t_mall_address)，仅PHYSICAL有值。收件信息快照在t_physical_delivery，不在本表 */
    private Long addressId;

    /** 状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败 */
    private Integer status;

    /** 待支付超时时间：到点由 job 取消并释放锁定库存。纯积分订单为空 */
    private LocalDateTime expireTime;

    /** 支付/扣分完成时间 */
    private LocalDateTime payTime;

    /** 履约完成时间 */
    private LocalDateTime finishTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次 */
    private String sourceType;

    /** 来源单号：FLASH_SALE 时存场次编码，NORMAL 为空 */
    private String sourceBizId;

    /** 履约单引用：发货单id / 券id */
    private String fulfillRefId;

    /** 履约失败原因（status=60 时有值） */
    private String failReason;

    /** 用户备注 / 运营备注 */
    private String remark;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
