package solvela.prize.prizelog.domain.dto;


import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeApproveStatusEnum;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 奖品发放记录列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * 完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class PrizeLogDTO {


    private Long id;

    /** 会员号 */
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    private String memberName;

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

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
