package solvela.lottery.record.domain.dto;

import solvela.enums.LotteryDispatchStatusEnum;
import solvela.enums.TicketStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票投注记录列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class LotteryRecordDTO {


    private Long id;

    /** 彩票编码 */
    private String lotteryCode;

    /** 归属期号 */
    private String issueNo;

    /** FPE算号基数 */
    private Integer sequenceNo;

    /** 彩票号码 */
    private String ticketNumber;

    /** 会员号 */
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    private String memberName;

    /** 领取时间 */
    private LocalDateTime obtainTime;

    /** 中奖状态: 0-未开奖, 1-未中奖, 已开奖 */
    private TicketStatusEnum winStatus;

    /** 奖励等级 */
    private Integer prizeLevel;

    /** 中奖奖品编码：核销时快照，防规则变更后漂移 */
    private String prizeCode;

    /** 派发状态：0-待派发/无需派发, 1-已投递, 2-投递失败 */
    private LotteryDispatchStatusEnum dispatchStatus;

    /** 签名 */
    private String securitySign;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
