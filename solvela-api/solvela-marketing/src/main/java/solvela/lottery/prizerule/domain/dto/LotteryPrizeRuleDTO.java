package solvela.lottery.prizerule.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票奖励规则列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class LotteryPrizeRuleDTO {


    private Long id;

    /** 彩票编码 */
    private String lotteryCode;

    /** 奖品奖级 */
    private Integer prizeLevel;

    /** 匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配 */
    private String matchRule;

    /** 匹配长度 */
    private Integer matchLength;

    /** 奖品编码 */
    private String prizeCode;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
