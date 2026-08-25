package solvela.task.taskevent.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务事件注册表 列表 VO
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Data
public class TaskEventVO {

    @Schema(description = "id")
    private Long id;

    @Schema(description = "事件编码")
    private String eventCode;

    @Schema(description = "展示名")
    private String eventName;

    @Schema(description = "计量来源：NONE(计次) 或 payload 里的字段名(计额)")
    private String metricSource;

    @Schema(description = "该事件会带哪些字段（JSON 原文）")
    private String payloadSchema;

    @Schema(description = "上游是否必须带幂等单号：1-必须, 0-可按事件日兜底")
    private Integer bizIdRequired;

    @Schema(description = "是否高频事件（预留，本期未实现路由优化）")
    private Integer isHighFrequency;

    @Schema(description = "是否记录被丢弃事件的流水")
    private Integer discardLogFlag;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
