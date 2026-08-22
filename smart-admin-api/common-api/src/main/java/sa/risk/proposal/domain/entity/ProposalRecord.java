package sa.risk.proposal.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 提案表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */

@Data
@TableName("t_proposal_record")
public class ProposalRecord {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 提案单号，服务端生成，对外唯一标识
     */
    private String tradeNo;

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
     * SCORE/BALANCE/COUPON/PHYSICAL
     */
    private String assetType;

    /**
     * 资产引用：券模/SKU，值类资产为空
     */
    private String assetRef;

    /**
     * 资产展示名（券名/商品名），由营销侧传入（v3.45.0 新增）。
     *
     * <p>加这一列是为了在<b>不反查营销域</b>的前提下让账务侧拿到人话名称。
     * 依赖方向是「营销 -> 账务」单向的，账务域不能回头查 t_prize_log；
     * 而在补上这条搬运通道之前，{@code CouponAssetHandler} 退而用了 {@code remark}，
     * 那是「提案生成成功」这种状态说明 —— 实测发出去的券全叫这个名字。
     */
    private String assetName;

    /**
     * 发放金额/积分数
     */
    private BigDecimal amount;

    /**
     * 发放数量，扣 used_quota 用
     */
    private Integer quantity;

    /**
     * 来源：TASK, DRAW, MANUAL
     */
    private String sourceType;

    /**
     * 来源单号
     */
    private String sourceBizId;

    /**
     * 优惠配置ID
     */
    private Long promotionConfigId;

    /**
     * 0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截
     */
    private Integer status;

    /**
     * 执行失败/风控拦截原因，或调用方传入的场景说明
     */
    private String remark;

    /**
     * 风控拦截分类（对齐 {@code RiskBlockCode}）：仅 status=80 时有值。
     *
     * <p>与 {@link #remark} <b>并存且用途相反</b>：这一列取值封闭、给漏斗聚类用；
     * remark 是给用户/客服看的话术，会改、也早晚会带上具体数值。
     * 只留文案会统计不了，只留编码会查不了客诉 —— 与 {@code t_task_record_flow.discard_code} 同一模式。
     */
    private String riskCode;

    /**
     * 一审人
     */
    private String firstReviewer;

    /**
     * 一审时间
     */
    private LocalDateTime firstReviewTime;

    /**
     * 二审人
     */
    private String secondReviewer;

    /**
     * 二审时间
     */
    private LocalDateTime secondReviewTime;

    /**
     * 审核意见/驳回理由
     */
    private String reviewComment;

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
