package solvela.marketing.api;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 一条<b>已经结清</b>的业务单，供任务域反查补推。
 *
 * <p>它是 {@code solvela.event.BizActionEvent} 的"事后版本"：字段刻意一一对应，
 * 因为补推出去的东西<b>必须和当初漏掉的那一条长得一模一样</b> ——
 * 尤其是 {@link #bizId} 和 {@link #occurredAt}：
 * <ul>
 *   <li>{@code bizId} 对不上，唯一键就挡不住重复，同一笔单会被算两次；</li>
 *   <li>{@code occurredAt} 传成"现在"而不是当初结清的时间，跨零点的那一笔
 *       会被归进第二天的周期 —— 用户昨天的进度凭空少一次、今天凭空多一次。</li>
 * </ul>
 *
 * @param bizId      业务单号，<b>幂等键</b>。必须与打点时用的那个字符串完全一致
 * @param memberId   会员号
 * @param occurredAt <b>当初结清的时间</b>，不是现在
 * @param payload    事件原文，与打点时同构。计额型任务靠它取数
 *                   （{@code t_task_event.metric_source} 指定取哪个字段）
 * @author alaric
 * @date 2026-09-17
 */
public record BizActionRecord(
        String bizId,
        Long memberId,
        LocalDateTime occurredAt,
        Map<String, Object> payload) {
}
