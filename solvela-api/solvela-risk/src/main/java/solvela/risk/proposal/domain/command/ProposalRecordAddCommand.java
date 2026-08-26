package solvela.risk.proposal.domain.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 提交风控提案的<b>领域命令</b>。
 *
 * <h3>它从来不是一个 HTTP 表单</h3>
 * 构造它的是 solvela-consumer 里的四个发奖 handler（BalanceHandler / CouponHandler /
 * PhysicalPrizeHandler / ScoreHandler）—— 发奖时在 Java 里 new 出来直接调
 * {@code ProposalRecordService.addProposal}。<b>admin 没有任何接口绑定它</b>，
 * 所以它身上原有的 {@code @Schema} 是纯装饰：既不会出现在任何接口文档里，
 * 又让共享层看起来依赖了 HTTP 层的概念。
 *
 * <p>改名为 Command 只是把名字改对，没有引入任何新类，也没有装配开销。
 * 命名对不上实际用途的代价，是下一个人读到 "Form" 时会去找那个并不存在的接口。
 */

@Data
public class ProposalRecordAddCommand {

    // 单号由提案域自己发（saveProposal 里生成），调用方不该也不能指定 —— 它是本域对外的凭证。
    // 这里若保留 @NotBlank，将来任何 Controller 暴露提案创建接口都会被误挡在门外
    /** 提案单号，服务端生成，调用方无需传入 */
    private String tradeNo;

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    @NotNull(message = "会员号 不能为空")
    private Long memberId;

    /** SCORE/BALANCE/COUPON/PHYSICAL */
    @NotBlank(message = "SCORE/BALANCE/COUPON/PHYSICAL 不能为空")
    private String assetType;

    /** 资产引用：券模/SKU，值类资产为空 */
    private String assetRef;

    /** 资产展示名（券名/商品名）：账务侧发券/发货时直接用，不传则回退用 assetRef */
    private String assetName;

    /** 发放金额/积分数 */
    @NotNull(message = "发放金额/积分数 不能为空")
    private BigDecimal amount;

    /** 发放数量，扣 used_quota 用；不传按 1 计 */
    private Integer quantity;

    /** 来源：TASK(任务), DRAW(抽奖), MANUAL(人工) */
    @NotBlank(message = "来源：TASK(任务), DRAW(抽奖), MANUAL(人工) 不能为空")
    private String sourceType;

    /** 来源单号(task_record_id 或 draw_log_trace_id) */
    @NotBlank(message = "来源单号(task_record_id 或 draw_log_trace_id) 不能为空")
    private String sourceBizId;

    /** 优惠配置ID */
    @NotNull(message = "优惠配置ID 不能为空")
    private Long promotionConfigId;

    /** 状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截 */
    private Integer status;

    /** 执行失败/风控拦截原因 */
    private String remark;

    /** 一审人 */
    private String firstReviewer;

    /** 一审时间 */
    private LocalDateTime firstReviewTime;

    /** 二审人 */
    private String secondReviewer;

    /** 二审时间 */
    private LocalDateTime secondReviewTime;

    /** 审核意见/驳回理由 */
    private String reviewComment;

}