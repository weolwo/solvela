package solvela.activity.domain.dto;


import solvela.enums.ActivityStatusEnum;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 活动配置列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * 完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class ActivityConfigDTO {


    private Long id;

    /** 活动编码 */
    private String activityCode;

    /** 活动名称 */
    private String activityName;

    /** 活动类型 */
    private String activityType;

    /** 状态：0-未开始, 1-上线, 2-下线 */
    private ActivityStatusEnum status;

    /** 活动开始时间 */
    private LocalDateTime startTime;

    /** 活动结束时间 */
    private LocalDateTime endTime;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
