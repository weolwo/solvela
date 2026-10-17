package solvela.member.risk.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 自动条目写进 {@code t_security_list} 的唯一入口（网关方案 §6.3「自动条目的写入规则」）。
 *
 * <p>唯一键是（维度, 值, 类型），冲突时：
 * <ul>
 *   <li>有效的<b>自动</b>条目：{@code hit_count + 1}、刷新最近命中与证据，到期取两者中更晚的；</li>
 *   <li>有效的<b>人工</b>条目：<b>不动</b> —— 人工设的长期黑名单不会被自动的 24 小时缩短；</li>
 *   <li>已撤销、或已过期的：复用这一行，重新置为有效、归为自动，撤销三列清空、{@code hit_count} 归 1。
 *       撤销的历史留在 {@code t_security_incident} 与 DataTracer 里。</li>
 * </ul>
 *
 * <h3>时间交给 SQL</h3>
 * 到期传 epoch 秒，由 {@code FROM_UNIXTIME} 按会话时区还原；「现在」用 {@code NOW()} —— Java 这边不做任何时区换算。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityListWriter {

    /** 自动进灰名单时给的分（登录风控 {@code IP_GREYLIST} 按它加分） */
    public static final int AUTO_GREY_SCORE = 30;

    /** 自动条目的操作人 */
    public static final String SYSTEM = "system";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 一条自动条目要写成什么样。
     *
     * @param listType     1-黑, 2-灰
     * @param dimension    1-IP, 2-IP段
     * @param sourceRule   HONEYPOT / CANARY / KEYWORD / RATE_LIMIT / ESCALATE / SEGMENT
     * @param untilEpochSec 到期（epoch 秒），🔴 自动条目必填
     * @param incidentId   证据；没有（由任务推导出来的条目）为 null
     */
    public record Entry(int listType, int dimension, String value, String sourceRule, String reason,
                        long untilEpochSec, Long incidentId) {
    }

    /** 写的结果 */
    public enum Outcome { INSERTED, EXTENDED, REUSED, KEPT_MANUAL, KEPT_EXISTING }

    /** 按上面的规则写一条 */
    public Outcome upsert(Entry e) {
        return upsert(e, false);
    }

    /**
     * @param onlyIfAbsent 已有一条有效的就什么都不做（连坐这种每个周期都会重新推导一遍的，不该每次都 +1）
     */
    public Outcome upsert(Entry e, boolean onlyIfAbsent) {
        try {
            return doUpsert(e, onlyIfAbsent);
        } catch (DuplicateKeyException race) {
            // 读到「没有」之后、插入之前，有人（后台人工、另一个任务）先插了一行：按冲突规则再走一遍
            return doUpsert(e, onlyIfAbsent);
        }
    }

    private Outcome doUpsert(Entry e, boolean onlyIfAbsent) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, source, status, (expire_time IS NOT NULL AND expire_time <= NOW()) AS expired
                  FROM t_security_list
                 WHERE dimension = ? AND list_value = ? AND list_type = ?
                """, e.dimension(), e.value(), e.listType());
        Integer score = e.listType() == 2 ? AUTO_GREY_SCORE : null;
        if (rows.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO t_security_list (list_type, dimension, list_value, score, reason, source, source_rule,
                                                 hit_count, last_hit_time, last_incident_id, status, expire_time, operator)
                    VALUES (?, ?, ?, ?, ?, 2, ?, 1, NOW(), ?, 1, FROM_UNIXTIME(?), ?)
                    """, e.listType(), e.dimension(), e.value(), score, e.reason(), e.sourceRule(), e.incidentId(),
                    e.untilEpochSec(), SYSTEM);
            return Outcome.INSERTED;
        }
        Map<String, Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        boolean valid = ((Number) row.get("status")).intValue() == 1 && ((Number) row.get("expired")).intValue() == 0;
        boolean manual = ((Number) row.get("source")).intValue() == 1;
        if (valid && manual) {
            return Outcome.KEPT_MANUAL;
        }
        if (valid && onlyIfAbsent) {
            return Outcome.KEPT_EXISTING;
        }
        if (valid) {
            jdbcTemplate.update("""
                    UPDATE t_security_list
                       SET hit_count = hit_count + 1, last_hit_time = NOW(), last_incident_id = COALESCE(?, last_incident_id),
                           source_rule = ?, expire_time = GREATEST(expire_time, FROM_UNIXTIME(?))
                     WHERE id = ?
                    """, e.incidentId(), e.sourceRule(), e.untilEpochSec(), id);
            return Outcome.EXTENDED;
        }
        jdbcTemplate.update("""
                UPDATE t_security_list
                   SET status = 1, source = 2, source_rule = ?, reason = ?, score = ?, hit_count = 1, last_hit_time = NOW(),
                       last_incident_id = ?, expire_time = FROM_UNIXTIME(?), operator = ?,
                       revoke_operator = NULL, revoke_reason = NULL, revoke_time = NULL
                 WHERE id = ?
                """, e.sourceRule(), e.reason(), score, e.incidentId(), e.untilEpochSec(), SYSTEM, id);
        return Outcome.REUSED;
    }
}
