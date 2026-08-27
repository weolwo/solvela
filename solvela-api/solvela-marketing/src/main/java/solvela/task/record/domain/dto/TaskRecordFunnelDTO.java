package solvela.task.record.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 任务记录漏斗：把一堆进度行压成「任务接出去了没有、达标了没有、奖发出去没有」。
 *
 * <p>原先的记录页是 19 列裸字段、零聚合，翻十页也答不出最基本的问题：
 * 达标率多少、哪个任务没人做得完、为什么用户做了事进度却不涨。而这些流水里全都有。
 *
 * <h3>本页独有的两个数字</h3>
 * <ol>
 *   <li><b>已过有效期却仍是「进行中」</b>：全工程<b>没有任何地方</b>把 status 改成 3-已过期
 *       （{@code idx_t_tsk_rec_expire(status, valid_end_time)} 就是给那个扫描建的，但扫描没写）。
 *       这些记录不会自己收口，用户端会一直看到一个永远完不成的任务，而没人查就发现不了；</li>
 *   <li><b>事件丢弃分类</b>：{@code discard_code} 这一列建出来就是给聚类用的
 *       （见 {@code TaskDiscardCode} 的类注释），但至今没有任何地方读它。
 *       「用户下了 99 元的单为什么没进度」——答案本来就躺在这里，只是从来没被算过。
 *       其中 AUDIENCE_UNKNOWN / CONFIG_INVALID / POOL_REJECTED <b>不是</b>正常业务拦截，
 *       哪怕只有几条也该有人去看。</li>
 * </ol>
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class TaskRecordFunnelDTO {

    /** 接取总数（筛选范围内） */
    private Long totalCount;

    /** 参与人数（去重会员数） */
    private Long memberCount;

    /** 人均接取任务数 */
    private BigDecimal recordPerMember;

    /** 进行中：status=0 */
    private Long runningCount;

    /**
     * 停在「已完成」的记录数。{@code markCompleted} 与 {@code markDispatched} 是紧邻的两条 SQL，
     * 正常不会停在中间 —— 停住了就说明发奖那一步断了，用户达标了却没拿到奖。
     */
    private Long completedCount;

    /** 已发奖：status=2 */
    private Long dispatchedCount;

    /** 已过期：status=3 */
    private Long expiredCount;

    /** 达标率 = (已完成 + 已发奖) / 接取总数 */
    private BigDecimal reachRate;

    /**
     * 已过有效期却仍是「进行中」。没有过期扫描任务，这些记录永远不会自己收口。
     */
    private Long staleRunningCount;

    // ---------------- 事件丢弃（进度不涨的原因就在这里） ----------------

    /** 被丢弃的事件总数：t_task_record_flow.flow_type=2 */
    private Long discardTotalCount;

    /** 其中需要人介入的丢弃数：AUDIENCE_UNKNOWN / CONFIG_INVALID / POOL_REJECTED */
    private Long discardAttentionCount;

    /** 事件丢弃分类分布，按次数降序 */
    private List<DiscardStatDTO> discardList;

    /** 任务维度分布，按接取量降序（TOP 20） */
    private List<TaskStatDTO> taskList;

    /** 数据一致性体检告警 */
    private List<String> issueList;

    /**
     * 一个任务的接取与达标情况
     */
    @Data
    public static class TaskStatDTO {

        /** 任务配置ID */
        private Long taskConfigId;

        /** 任务名称，配置已删除时为 null */
        private String taskName;

        /** 任务分组 */
        private String taskGroup;

        /** 接取数 */
        private Long recordCount;

        /** 参与人数 */
        private Long memberCount;

        /** 达标数：status IN (1, 2) */
        private Long reachedCount;

        /** 本任务的达标率 = 达标数 / 接取数 */
        private BigDecimal reachRate;

        /** 已过有效期仍在进行中的条数 */
        private Long staleRunningCount;
    }

    /**
     * 一类丢弃原因的次数
     */
    @Data
    public static class DiscardStatDTO {

        /** 丢弃分类编码，对齐 TaskDiscardCode；写入侧没写时为 null */
        private String discardCode;

        /** 分类说明 */
        private String discardDesc;

        /** 次数 */
        private Long discardCount;

        /** 占全部丢弃的比例 */
        private BigDecimal discardShare;

        /**
         * 是否需要人介入。正常业务规则拦截（人群不符、次数用尽…）量再大也不用管，
         * 这一类哪怕只有几条都该去查。判据收在 {@code TaskDiscardCode.needsAttention()} 一处。
         */
        private Boolean needsAttention;
    }
}
