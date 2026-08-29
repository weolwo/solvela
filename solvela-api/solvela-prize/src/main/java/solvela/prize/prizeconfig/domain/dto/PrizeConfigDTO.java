package solvela.prize.prizeconfig.domain.dto;


import solvela.enums.EnableStatusEnum;
import solvela.enums.ApproveModeEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 奖品配置列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * 完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class PrizeConfigDTO {


    private Long id;

    /** 活动编码 */
    private String activityCode;

    /** 优惠配置ID */
    private Long promotionConfigId;

    /** 资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM */
    private String prizeType;

    /** 奖品名称 */
    private String prizeName;

    /** 奖品编码 */
    private String prizeCode;

    /** 奖品级别 */
    private Integer prizeLevel;

    /** 奖励价值 */
    private BigDecimal prizeValue;

    /** 审批模式：0-自动免审, 1-人工审批 */
    private ApproveModeEnum approveMode;

    /** 排序权重 */
    private Integer sortWeight;

    /** 扩展信息：如奖品图片URL、跳转链接等 */
    private String ext;

    /** 状态：0-停用, 1-启用 */
    private EnableStatusEnum status;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
