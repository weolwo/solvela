package solvela.admin.module.mall.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-兑换订单 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */

@Data
public class MallOrderVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键")
    private String orderNo;

    @Schema(description = "会员号：关联键")
    private Long memberId;

    @Schema(description = "下单时的会员账号【展示快照，非关联键，不要用于查询】")
    private String memberName;

    @Schema(description = "商品id")
    private Long commodityId;

    @Schema(description = "商品编码（跨环境稳定的那个）")
    private String commodityCode;

    @Schema(description = "SKUid")
    private Long skuId;

    @Schema(description = "SKU编码")
    private String skuCode;

    @Schema(description = "商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它")
    private String commodityType;

    @Schema(description = "资产引用快照：券模编码等")
    private String assetRef;

    @Schema(description = "商品名称快照")
    private String commodityName;

    @Schema(description = "封面图快照 file_id")
    private Long coverFileId;

    @Schema(description = "规格快照")
    private String skuAttrs;

    @Schema(description = "兑换件数")
    private Integer quantity;

    @Schema(description = "单件积分单价快照")
    private Integer pointsPrice;

    @Schema(description = "单件现金单价快照")
    private BigDecimal cashPrice;

    @Schema(description = "实付积分合计")
    private Integer payPoints;

    @Schema(description = "实付现金合计")
    private BigDecimal payCash;

    @Schema(description = "收货地址id(软引用t_mall_address)，仅PHYSICAL有值。收件信息快照在t_physical_delivery，不在本表")
    private Long addressId;

    @Schema(description = "状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败")
    private Integer status;

    @Schema(description = "待支付超时时间：到点由 job 取消并释放锁定库存。纯积分订单为空")
    private LocalDateTime expireTime;

    @Schema(description = "支付/扣分完成时间")
    private LocalDateTime payTime;

    @Schema(description = "履约完成时间")
    private LocalDateTime finishTime;

    @Schema(description = "取消时间")
    private LocalDateTime cancelTime;

    @Schema(description = "订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次")
    private String sourceType;

    @Schema(description = "来源单号：FLASH_SALE 时存场次编码，NORMAL 为空")
    private String sourceBizId;

    @Schema(description = "履约单引用：发货单id / 券id")
    private String fulfillRefId;

    @Schema(description = "履约失败原因（status=60 时有值）")
    private String failReason;

    @Schema(description = "用户备注 / 运营备注")
    private String remark;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
