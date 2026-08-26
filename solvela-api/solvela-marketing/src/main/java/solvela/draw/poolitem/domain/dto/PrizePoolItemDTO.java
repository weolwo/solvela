package solvela.draw.poolitem.domain.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池奖品项列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class PrizePoolItemDTO {


    private Long id;

    /** 归属活动编码 */
    private String activityCode;

    /** 关联(t_prize_config) */
    private String prizeCode;

    /** 单人限领次数: -1不限, 1表示每人最多中一次 */
    private Integer userMaxCount;

    /** 本次活动总共出几个？-1不限 */
    private Integer totalStock;

    /** 跨奖池累计已出数量 */
    private Integer usedStock;

    /** 活动级白名单：指定用户必中 */
    private String whiteList;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
