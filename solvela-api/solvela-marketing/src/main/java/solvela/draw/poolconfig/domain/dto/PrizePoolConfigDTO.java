package solvela.draw.poolconfig.domain.dto;

import solvela.enums.PrizePoolStatusEnum;
import solvela.enums.DrawModeEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池配置列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class PrizePoolConfigDTO {


    private Long id;

    /** 活动编码 */
    private String activityCode;

    /** 奖池唯一编码 (如: VIP_POOL) */
    private String poolCode;

    /** 奖池名称 */
    private String poolName;

    /** 重置周期，天，周，月，活动期间 */

    /** 抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio) */

    /** 0关闭，1开启 */
    private PrizePoolStatusEnum status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
