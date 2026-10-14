package solvela.member.risk;

import java.time.LocalDateTime;

/**
 * 指向一行决策流水。<b>id 和时间必须一起带</b>。
 *
 * <h3>🔴 为什么不能只传 id</h3>
 * {@code t_member_risk_event} 按月分区，主键是 {@code (id, create_time)}。
 * 只拿 id 去查，分区裁剪不生效（裁剪看的是 WHERE 里有没有分区列，与索引无关），
 * 每个分区都要各探一次索引。所以凭票里存的是这一对，二次验证那一行靠它填 {@code ref_event_id}。
 *
 * @Date 2026-10-15
 */
public record RiskEventRef(Long eventId, LocalDateTime createTime) {
}
