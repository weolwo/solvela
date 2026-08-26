package solvela.draw.drawlog.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 抽奖记录列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class DrawPrizeLogDTO {


    private Long id;

    /** 请求ID */
    private String traceId;

    /** 活动编码 */
    private String activityCode;

    /** 奖池编码 */
    private String poolCode;

    /** 会员号 */
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    private String memberName;

    /** 奖项ID */
    private Long prizeItemId;

    /** 奖品code */
    private String prizeCode;

    /** 状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

}
