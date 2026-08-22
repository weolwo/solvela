package sa.ledger.logistic.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import sa.base.common.crypto.PiiTypeHandler;

/**
 * 发货物流表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */

/**
 * 🔴 {@code autoResultMap = true} 不能删：收件三列挂了 {@link PiiTypeHandler}，
 * 而 MyBatis-Plus <b>只在写的时候用 typeHandler，读的时候要靠 autoResultMap 才会用</b>。
 * 少了它的表现是「存进去是密文，查出来还是密文」，且不报任何错。
 */
@Data
@TableName(value = "t_physical_delivery", autoResultMap = true)
public class PhysicalDelivery {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号：关联键（v3.71.0 换键）。查询、join、对账一律用它。
     */
    private Long memberId;

    /**
     * 会员账号 —— <b>展示快照，不是关联键</b>。
     *
     * <p>记的是「写这条记录当时那个账号」，会员改名之后<b>刻意不跟着变</b>：
     * 单据要回答的是「当时是谁」，这和 {@code t_mall_order} 里存商品名快照是同一个模式。
     *
     * <p>🔴 <b>不要拿它做查询条件</b>：这一列身上已经没有任何索引（v3.71.0 换到 member_id 了），
     * 写 {@code WHERE member_name = ?} 就是全表扫；建索引更不行 —— 关联键会就此悄悄退回
     * member_name，改名断链的问题原样复活。按账号找人先经 {@code MemberService} 换成会员号。
     */
    private String memberName;

    /**
     * 来源单号 —— <b>只认单号，不认上游是什么业务</b>。
     *
     * <p>原先这一列叫 {@code proposal_id bigint}，写死了「履约单必然来自发奖提案」。
     * 商城兑换实物压根没有提案 ID，那种形态下这张表<b>根本插不进去</b>
     * （唯一键含 proposal_id 且 NOT NULL），实物商品也就落不了发货单。
     *
     * <p>泛化之后语义反而更正：本表本来就有 {@code source_type}，
     * 与 {@code t_proposal_record} / {@code t_member_coupon} / {@code t_mall_order}
     * 那几张表「(source_type, source_biz_id) 定位上游」的口径完全一致。
     *
     * <p>取值：{@code PROPOSAL} 存提案 ID，{@code MALL} 存商城订单号。
     */
    private String sourceBizId;

    /**
     * 来源类型：PROPOSAL-发奖提案 / MALL-商城兑换。与 sourceBizId 一起构成唯一键
     */
    private String sourceType;

    /**
     * 收件人姓名【<b>密文落库</b>，见 {@link PiiTypeHandler}】。
     * 中奖时未知，由用户后续补填 —— 所以可空，不是忘了加约束。
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String receiverName;

    /**
     * 收件人电话【<b>密文落库</b>】
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String receiverPhone;

    /**
     * 收件详细地址【<b>密文落库</b>】
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String receiverAddress;

    /**
     * 物流公司
     */
    private String logisticsCompany;

    /**
     * 物流单号
     */
    private String logisticsNo;

    /**
     * 状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回
     */
    private Integer status;

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
