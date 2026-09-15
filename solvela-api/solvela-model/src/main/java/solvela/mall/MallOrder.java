package solvela.mall;

import solvela.enums.MallOrderStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商城-兑换订单 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */

@Data
@TableName("t_mall_order")
public class MallOrder {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键
     */
    private String orderNo;

    /**
     * 会员号：关联键
     */
    private Long memberId;

    /**
     * 下单时的会员账号【展示快照，非关联键，不要用于查询】
     */
    private String memberName;

    /**
     * 商品id
     */
    private Long commodityId;

    /**
     * 商品编码（跨环境稳定的那个）
     */
    private String commodityCode;

    /**
     * SKUid
     */
    private Long skuId;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它
     */
    private String commodityType;

    /**
     * 资产引用快照：券模编码等
     */
    private String assetRef;

    /**
     * 商品名称快照
     */
    private String commodityName;

    /**
     * 封面图快照 file_id
     */
    private Long coverFileId;

    /**
     * 规格快照
     */
    private String skuAttrs;

    /**
     * 兑换件数
     */
    private Integer quantity;

    /**
     * 单件积分单价快照
     */
    private Integer pointsPrice;

    /**
     * 单件现金单价快照
     */
    private BigDecimal cashPrice;

    /**
     * 实付积分合计
     */
    private Integer payPoints;

    /**
     * 实付现金合计
     */
    private BigDecimal payCash;

    /**
     * 用掉的会员券 id。软引用，不加外键；{@code null} = 这一单没用券。
     *
     * <p>券行永远不会被物理删除（账务流水只增不改），所以悬空的风险本来就不存在，
     * 加外键换来的只是一次跨表锁。与 {@link #addressId} 同一个做法。
     */
    private Long couponId;

    /**
     * 券抵扣了多少（阶段 4 只抵积分）。
     *
     * <p>⚠️ <b>权威在 {@code t_coupon_write_off}</b>，这里是冗余 ——
     * 订单详情要显示「原价 5000 分，券减 1000 分，实付 4000 分」，
     * 为这一行去 join 一张流水表，等于把每次看订单都变成一次对账。
     *
     * <p>🔴 三个数之间有恒等式，体检 SQL 可以直接按它找出对不上的单：
     * {@code points_price × quantity - coupon_discount = pay_points}。
     * 所以<b>不另加一列存原价</b> —— 多一列就多一处会不一致的地方。
     */
    private BigDecimal couponDiscount;

    /**
     * 收货地址id(软引用t_mall_address)，仅PHYSICAL有值。收件信息快照在t_physical_delivery，不在本表
     */
    private Long addressId;

    /**
     * 状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败
     */
    private MallOrderStatusEnum status;

    /**
     * 待支付超时时间：到点由 job 取消并释放锁定库存。纯积分订单为空
     */
    private LocalDateTime expireTime;

    /**
     * 支付/扣分完成时间
     */
    private LocalDateTime payTime;

    /**
     * 履约完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 取消时间
     */
    private LocalDateTime cancelTime;

    /**
     * 订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次
     */
    private String sourceType;

    /**
     * 来源单号：FLASH_SALE 时存场次编码，NORMAL 为空
     */
    private String sourceBizId;

    /**
     * 履约单引用：发货单id / 券id
     */
    private String fulfillRefId;

    /**
     * 履约失败原因（status=60 时有值）
     */
    private String failReason;

    /**
     * 用户备注 / 运营备注
     */
    private String remark;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
