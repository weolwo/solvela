package solvela.task.runtime.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 上游埋点上报任务事件。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Data
public class TaskEventReportCommand {

    /** 事件编码：DAILY_SIGN / ORDER_PAID / GOODS_SHARE ... */
    private String eventCode;

    /**
     * 会员号（关联键）。v3.71.0 之前这里收的是账号 —— 账号可改，
     * 改完这个人的任务进度会从头开始算，而且不报错。
     * 流水上的账号快照由服务端查会员表补，上游不用传。
     */
    private Long memberId;

    /**
     * 幂等键。
     *
     * <p>⚠️ 有天然单号的事件（ORDER_PAID 的订单号）<b>必须传</b>，否则同一笔订单会被累计多次 ——
     * 服务端只能按「事件日」兜底，那对订单类事件意味着「一天只算一笔」，同样是错的。
     *
     * <p>P0 阶段先靠约定；P1 的 {@code t_task_event.biz_id_required} 会把它变成表里的强制契约，
     * 缺失直接拒绝并落丢弃流水（方案 §2.2）。
     */
    private String eventBizId;

    /** 计量值，AMOUNT 类任务用（如订单实付金额） */
    private BigDecimal amount;

    /** 事件实际发生时间。不传则取数据库时钟；迟到的事件应传真实发生时间，否则会归错周期 */
    private LocalDateTime eventTime;

    /**
     * 该会员是不是新会员，由上游告知。
     *
     * <p>🔴 <b>营销域不拥有会员数据</b>（v3.71.0 起有了 {@code t_member}，但营销域只认
     * {@code member_id} 这个关联键，不读会员档案），而且「新会员」的定义本就属于会员域的业务概念
     * （注册 7 天内？首单前？各家不同），不该由任务引擎去猜。
     *
     * <p>⚠️ <b>不传的后果</b>：目标人群配了「新会员」或「老会员」的任务会
     * <b>丢弃该事件并写明原因</b>（在事件流水里可见），而不是默默放行 ——
     * 默默放行等于人群配置静默失效，那正是这次要消灭的东西。
     * 目标人群是「全部会员」的任务不受影响。
     */
    private Boolean isNewMember;

    /** 事件原文，落进流水供客诉复盘 */
    private Map<String, Object> payload;
}
