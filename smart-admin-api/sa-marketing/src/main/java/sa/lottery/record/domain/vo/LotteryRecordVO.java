package sa.lottery.record.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户号码记录 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */

@Data
public class LotteryRecordVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "归属期号")
    private String issueNo;

    @Schema(description = "FPE算号基数")
    private Integer sequenceNo;

    @Schema(description = "彩票号码")
    private String ticketNumber;

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    @Schema(description = "会员账号（下单当时的快照）")
    private String memberName;

    @Schema(description = "领取时间")
    private LocalDateTime obtainTime;

    @Schema(description = "中奖状态: 0-未开奖, 1-未中奖, 已开奖")
    private Integer winStatus;

    @Schema(description = "奖励等级")
    private Integer prizeLevel;

    @Schema(description = "中奖奖品编码：核销时快照，防规则变更后漂移")
    private String prizeCode;

    @Schema(description = "派发状态：0-待派发/无需派发, 1-已投递, 2-投递失败")
    private Integer dispatchStatus;

    @Schema(description = "签名")
    private String securitySign;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
