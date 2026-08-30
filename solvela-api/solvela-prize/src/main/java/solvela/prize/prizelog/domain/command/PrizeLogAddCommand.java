package solvela.prize.prizelog.domain.command;


import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeApproveStatusEnum;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 新增奖品发放记录的<b>领域命令</b>。与管理端的 {@code PrizeLogAddCommand} 形状一致，但职责不同：
 *
 * <ul>
 *   <li>Form 是 HTTP 请求体：{@code @Schema} 描述接口文档、{@code @NotNull} 等校验
 *       前端传没传、传得对不对 —— 这些都跟着某个端的页面走；</li>
 *   <li>Command 是领域入参：service 对它做的是<b>业务不变量</b>校验
 *       （编码是否重复、状态能否流转、关联配置是否匹配），与谁调用无关。</li>
 * </ul>
 *
 * <p>合成一个的代价：C 端将来若要写入，得构造一个带管理端校验规则的表单；
 * 而共享层也会一直依赖 springdoc 与 jakarta.validation 这些 HTTP 层的概念。
 *
 * <p>分层说明见 {@code MemberWalletQuery}。
 */

@Data
public class PrizeLogAddCommand {

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    private Long memberId;

    /** 奖品编码 */
    private String prizeCode;

    /** 活动编码 */
    private String activityCode;

    /** 奖品级别 */
    private Integer prizeLevel;

    /** 奖品名称 */
    private String prizeName;

    /** 奖励类型：SCORE, BALANCE, COUPON, PHYSICAL, MARKER */
    private String prizeType;

    /** 奖励体值(积分数/券ID) */
    private String prizeValue;

    /** 异常原因 */
    private String failReason;

    /** 审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回 */
    private PrizeApproveStatusEnum approveStatus;

    /** 审批人 */
    private String approveBy;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 过期时间 */
    private LocalDateTime validUntil;

    /** 执行状态：0-等待, 1-成功, 2-失败 */
    private PrizeDispatchStatusEnum status;

    /** 外部单号 */
    private String externalBizNo;

    /** 异常原因 */
    private String remark;

}
