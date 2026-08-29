package solvela.task;

import solvela.enums.TaskFlowTypeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务事件流水表 实体类。
 *
 * <p>一张表解决三个问题（方案 §4.5）：
 * ① 事件幂等（uk_t_tsk_flw_evt 挡住上游/MQ 重复投递）；
 * ② 客诉自证（被丢弃的事件也留痕，能回答「用户下了 99 元的单为什么没进度」）；
 * ③ STREAK 日内幂等（STREAK 的 period_key 恒为 NONE，唯一索引不再承担日内防重）。
 *
 * <p>⚠️ 时间字段上<b>不要</b>加 {@code @TableField(fill = ...)}（铁律 9）：
 * 只要注解存在，MyBatis-Plus 生成 INSERT 时就会带上该字段且跳过判空，
 * null 会被显式写入并覆盖 DDL 的 DEFAULT CURRENT_TIMESTAMP —— 实测曾导致整列为 NULL。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Data
@TableName("t_task_record_flow")
public class TaskRecordFlow {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号：关联键（v3.71.0 换键）。查询、join、对账一律用它。
     */
    private Long memberId;

    /**
     * 会员账号 —— <b>展示快照，不是关联键</b>。
     *
     * <p>记的是「写这条记录当时那个账号」，会员改名之后<b>刻意不跟着变</b>：
     * 单据要回答的是「当时是谁」，这和 {@code t_mall_order} 里存商品名快照是同一个模式。
     *
     * <p>🔴 <b>不要拿它做查询条件</b>：这一列身上已经没有任何索引（v3.71.0 换到 member_id 了），
     * 写 {@code WHERE member_name = ?} 就是全表扫；建索引更不行 —— 关联键会就此悄悄退回
     * member_name，改名断链的问题原样复活。按账号找人先经 {@code MemberService} 换成会员号。
     */
    private String memberName;

    /**
     * 任务配置ID
     */
    private Long taskConfigId;

    /**
     * 任务记录ID：被丢弃的事件可能还没建记录，故可空
     */
    private Long recordId;

    /**
     * 事件编码：DAILY_SIGN / ORDER_PAID ...
     */
    private String eventCode;

    /**
     * 幂等键：上游单号；无天然单号的事件用 D+yyyyMMdd(事件日) 兜底
     */
    private String eventBizId;

    /**
     * 1-进度推进(已生效), 2-事件丢弃(未生效)
     */
    private TaskFlowTypeEnum flowType;

    /**
     * 本次增量
     */
    private BigDecimal deltaMetric;

    /**
     * 推进后进度值，便于按时间轴复盘
     */
    private BigDecimal afterMetric;

    /**
     * 丢弃原因分类（对齐 {@code TaskDiscardCode}）：flow_type=2 时必填。
     *
     * <p>与 {@link #discardReason} <b>并存且用途相反</b>：
     * 这一列取值封闭、给大屏聚类用；{@code discardReason} 是带具体数值的自由文本、给人排查用。
     * 只留文本会统计不了，只留码会查不了客诉。
     */
    private String discardCode;

    /**
     * 丢弃原因：flow_type=2 时必填
     */
    private String discardReason;

    /**
     * 事件原文快照，供客诉复盘
     */
    private String eventPayload;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
