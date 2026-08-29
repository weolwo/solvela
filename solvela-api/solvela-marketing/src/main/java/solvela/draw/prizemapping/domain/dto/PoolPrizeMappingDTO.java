package solvela.draw.prizemapping.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池奖品映射列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class PoolPrizeMappingDTO {


    private Long id;

    /** 奖池编码 */
    private String poolCode;

    /** 奖项id */
    private Long prizeItemId;

    /** 中奖概率(万分位) */
    private BigDecimal probability;

    /** 是否兜底奖项：1-兜底，每池最多一个 */
    private Boolean isFallback;

    /** 序号 */
    private Integer sortWeight;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
