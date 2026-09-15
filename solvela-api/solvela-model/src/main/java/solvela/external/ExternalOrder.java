package solvela.external;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.crypto.PiiTypeHandler;
import solvela.enums.ExternalOrderStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 外部场景消费单：用户在某个<b>外部场景</b>消费了一笔钱，用券抵扣。
 *
 * <p>充话费是第一个接它的场景，但表和类都<b>不叫充话费</b> ——
 * 下一个场景（视频会员、加油卡…）不该需要再建一套一模一样的东西。
 *
 * <h3>🔴 {@code autoResultMap = true} 不能删</h3>
 * {@link #targetAccount} 挂了 {@link PiiTypeHandler}，而 MyBatis-Plus
 * <b>只在写的时候用 typeHandler，读的时候要靠 autoResultMap 才会用</b>。
 * 删了它写进去是密文、读回来还是密文，而且不报错 —— 页面上显示一串乱码。
 * 与 {@code PhysicalDelivery} 踩的是同一个坑。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Data
@TableName(value = "t_external_order", autoResultMap = true)
public class ExternalOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号。服务端生成，同时是<b>锁券的 bizRefId</b> 与幂等键 */
    private String orderNo;

    /** 会员号：关联键 */
    private Long memberId;

    /**
     * 会员账号 —— <b>展示快照，不是关联键</b>。
     * 记的是下单当时那个账号，改名之后刻意不跟着变。
     */
    private String memberName;

    /** 场景码，如 {@code MOBILE_RECHARGE}。券的 {@code scope_refs} 按它匹配 */
    private String sceneCode;

    /**
     * 充值目标（手机号 / 账号）【<b>密文落库</b>】。
     *
     * <p>与 {@code t_physical_delivery.receiver_phone} 同一套 {@link PiiTypeHandler}、
     * 同一把密钥。
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String targetAccount;

    /**
     * 打码后的目标，如 {@code 138****8888}。
     *
     * <p>⚠️ 冗余一列是为了<b>列表页不用解密</b> —— 一页 20 单就是 20 次解密，
     * 而列表本来也只需要看个打码值。明文<b>不存第二份</b>。
     */
    private String targetMasked;

    /** 抵扣前应付（用户选的面额） */
    private BigDecimal originalAmount;

    /** 用掉的会员券 id。软引用，不加外键；null = 没用券 */
    private Long couponId;

    /**
     * 券抵扣了多少。
     *
     * <p>⚠️ 冗余，<b>权威在 {@code t_coupon_write_off}</b>。
     * 存在这里是为了订单详情零 join 显示「原价 100，券减 10，实付 90」。
     */
    private BigDecimal couponDiscount;

    /** 实付 = 抵扣前 - 券抵扣 */
    private BigDecimal payAmount;

    private ExternalOrderStatusEnum status;

    /** 待支付超时时间，到点由 {@code externalOrderExpire} 取消并放回券 */
    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime finishTime;

    /** 外部流水号（运营商返回的）。<b>对账靠它</b> */
    private String externalRefNo;

    private String failReason;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
