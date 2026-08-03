package net.lab1024.sa.stat.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 事件健康度：推进 / 丢弃，丢弃按 discard_code 聚类
 *
 * <p>⚠️ <b>重复投递在这张表里看不见</b>：幂等命中走的是撞唯一索引 → 直接 return，<b>不写流水行</b>
 * （{@code TaskRecordAdvanceService}）。要监控重复上报量得单独埋计数器，别指望从流水里统计。
 *
 * <p>🔴 <b>必须按 discard_code 聚类，不能按 discard_reason</b>：后者是自由文本且刻意带了具体数值
 * （「单笔金额 99 未达门槛 100」），设计目标是给人读（客诉自证），直接 GROUP BY 会炸成几百个值。
 *
 * @Author weolwo
 * @Date 2026-08-03
 */
@Data
public class EventHealthVO {

    @Schema(description = "统计天数")
    private Integer days;

    @Schema(description = "有效推进条数 flow_type=1")
    private Integer advanceCount;

    @Schema(description = "丢弃条数 flow_type=2")
    private Integer discardCount;

    @Schema(description = "需要人介入的丢弃条数：上游漏传属性 / 配置坏了 / 线程池拒绝")
    private Integer attentionCount;

    @Schema(description = "丢弃原因聚类，按条数降序")
    private List<DiscardItem> discardList;

    @Data
    @Schema(description = "一类丢弃原因")
    public static class DiscardItem {

        @Schema(description = "分类码")
        private String discardCode;

        @Schema(description = "人话说明，取自 TaskDiscardCode 枚举，不在前端散写")
        private String discardDesc;

        @Schema(description = "条数")
        private Integer count;

        @Schema(description = "是否需要人介入：正常业务规则量再大也不用管，这三类哪怕几条都该看")
        private Boolean needsAttention;
    }
}
