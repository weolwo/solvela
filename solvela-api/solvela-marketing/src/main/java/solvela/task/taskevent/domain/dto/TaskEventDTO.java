package solvela.task.taskevent.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务事件列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */
@Data
public class TaskEventDTO {

    private Long id;

    /** 事件编码 */
    private String eventCode;

    /** 展示名 */
    private String eventName;

    /** 计量来源：NONE(计次) 或 payload 里的字段名(计额) */
    private String metricSource;

    /** 该事件会带哪些字段（JSON 原文） */
    private String payloadSchema;

    /** 上游是否必须带幂等单号：1-必须, 0-可按事件日兜底 */
    private Integer bizIdRequired;

    /** 是否高频事件（预留，本期未实现路由优化） */
    private Integer isHighFrequency;

    /** 是否记录被丢弃事件的流水 */
    private Integer discardLogFlag;

    /** 备注 */
    private String remark;

    /** 状态：0-停用, 1-启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
