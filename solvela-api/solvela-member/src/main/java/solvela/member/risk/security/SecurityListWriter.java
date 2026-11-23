package solvela.member.risk.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.enums.SecurityListTypeEnum;

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
 *       撤销的历史留在 {@code t_security_event} 与 DataTracer 里。</li>
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
     * @param listType     黑 / 灰（自动条目不会是白）
     * @param dimension    IP / IP 段 / 会员
     * @param sourceRule   HONEYPOT / CANARY / KEYWORD / RATE_LIMIT / ESCALATE / SEGMENT
     * @param untilEpochSec 到期（epoch 秒），🔴 自动条目必填
     * @param eventId   证据；没有（由任务推导出来的条目）为 null
     */
    public record Entry(SecurityListTypeEnum listType, SecurityListDimensionEnum dimension, String value,
                        String sourceRule, String reason, long untilEpochSec, Long eventId) {
    }

    /** 写的结果 */
    public enum Outcome {
        INSERTED, EXTENDED, REUSED, KEPT_MANUAL, KEPT_EXISTING;

        /** 名单有没有变 —— 变了的话调用方要重载名单缓存、重发网关快照 */
        public boolean changed() {
            return this != KEPT_MANUAL && this != KEPT_EXISTING;
        }
    }

    /** {@code t_security_list.source}：1-人工, 2-自动 */
    public static final int SOURCE_MANUAL = 1;

    /** 按编码找维度（库里读出来的那一列）；不认识的返回 null */
    public static SecurityListDimensionEnum dimension(int code) {
        for (SecurityListDimensionEnum d : SecurityListDimensionEnum.values()) {
            if (d.equalsValue(code)) {
                return d;
            }
        }
        return null;
    }

    /** 按上面的规则写一条 */
    public Outcome upsert(Entry e) {
        return upsert(e, false);
    }

    /**
     * @param onlyIfAbsent 已有一条有效的就什么都不做（连坐这种每个周期都会重新推导一遍的，不该每次都 +1）
     */
    public Outcome upsert(Entry e, boolean onlyIfAbsent) {
        Outcome outcome;
        try {
            outcome = doUpsert(e, onlyIfAbsent);
        } catch (DuplicateKeyException race) {
            // 读到「没有」之后、插入之前，有人（后台人工、另一个任务）先插了一行：按冲突规则再走一遍
            return retry(e, onlyIfAbsent);
        }
        // 读到之后、写之前那一行被人改了（人工重新加上、被撤销、另一个任务先复用了）：条件 UPDATE 影响 0 行，同样再走一遍
        return outcome != null ? outcome : retry(e, onlyIfAbsent);
    }

    private Outcome retry(Entry e, boolean onlyIfAbsent) {
        Outcome outcome = doUpsert(e, onlyIfAbsent);
        if (outcome == null) {
            // 连着两次读完就被改，不该发生；抛出去让调用方看见，而不是假装写成了
            throw new IllegalStateException("安全名单条目连续两次被并发改动，未写入: "
                    + e.dimension() + " " + e.value() + " " + e.listType());
        }
        return outcome;
    }

    private Outcome doUpsert(Entry e, boolean onlyIfAbsent) {
        Existing existing = find(e);
        if (existing == null) {
            insert(e);
            return Outcome.INSERTED;
        }
        if (existing.valid() && existing.manual()) {
            return Outcome.KEPT_MANUAL;
        }
        if (existing.valid() && onlyIfAbsent) {
            return Outcome.KEPT_EXISTING;
        }
        if (existing.valid()) {
            return extend(existing.id(), e) == 0 ? null : Outcome.EXTENDED;
        }
        return reuse(existing.id(), e) == 0 ? null : Outcome.REUSED;
    }

    /** 唯一键上已有的那一行。{@code valid} = 有效且没过期 */
    private record Existing(long id, boolean valid, boolean manual) {
    }

    private Existing find(Entry e) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, source, status, (expire_time IS NOT NULL AND expire_time <= NOW()) AS expired
                  FROM t_security_list
                 WHERE dimension = ? AND list_value = ? AND list_type = ?
                """, e.dimension().getValue(), e.value(), e.listType().getValue());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        boolean valid = SecurityListStatusEnum.VALID.equalsValue(((Number) row.get("status")).intValue())
                && ((Number) row.get("expired")).intValue() == 0;
        return new Existing(((Number) row.get("id")).longValue(), valid,
                ((Number) row.get("source")).intValue() == SOURCE_MANUAL);
    }

    /*
     * 🔴 last_event_id 与 last_incident_id 两列写同一个值（改名前只有后者）。
     * TEMP(安全事件改名-收口): 发版间隙里旧版后台只认 last_incident_id，两列都写，新旧版本读到的才是同一个证据。
     * 移除条件：旧版后台全部下线、收口脚本删掉 last_incident_id 列之前，先把这里的那一列去掉（三处 SQL 都要删）。
     */
    private void insert(Entry e) {
        jdbcTemplate.update("""
                INSERT INTO t_security_list (list_type, dimension, list_value, score, reason, source, source_rule,
                                             hit_count, last_hit_time, last_event_id, last_incident_id, status, expire_time,
                                             operator)
                VALUES (?, ?, ?, ?, ?, 2, ?, 1, NOW(), ?, ?, ?, FROM_UNIXTIME(?), ?)
                """, e.listType().getValue(), e.dimension().getValue(), e.value(), score(e), e.reason(), e.sourceRule(),
                e.eventId(), e.eventId(), SecurityListStatusEnum.VALID.getValue(), e.untilEpochSec(), SYSTEM);
    }

    /**
     * 有效的自动条目：命中次数 +1、刷新证据，到期取两者中更晚的。
     *
     * <p>🔴 WHERE 里再判一次「仍是有效的自动条目」：读完之后它可能被撤销、或被人工改成了长期条目，
     * 那时不该再动它。返回 0 由 {@link #upsert} 重新读一遍再决定。
     */
    private int extend(long id, Entry e) {
        return jdbcTemplate.update("""
                UPDATE t_security_list
                   SET hit_count = hit_count + 1, last_hit_time = NOW(),
                       last_event_id = COALESCE(?, last_event_id), last_incident_id = COALESCE(?, last_incident_id),
                       source_rule = ?, expire_time = GREATEST(expire_time, FROM_UNIXTIME(?))
                 WHERE id = ? AND status = ? AND source = 2
                   AND (expire_time IS NULL OR expire_time > NOW())
                """, e.eventId(), e.eventId(), e.sourceRule(), e.untilEpochSec(), id, SecurityListStatusEnum.VALID.getValue());
    }

    /**
     * 已撤销或已过期的：复用这一行，重新置为有效、归为自动（已撤销 → 有效，见 {@link SecurityListStatusEnum#canTransitTo}）。
     *
     * <p>🔴 WHERE 里再判一次「仍不是有效的」：读完之后若被人工重新加成了有效条目，这里影响 0 行，
     * 不会把人工条目改成自动、把长期封禁缩成 24 小时。返回 0 由 {@link #upsert} 重新读一遍再决定。
     */
    private int reuse(long id, Entry e) {
        return jdbcTemplate.update("""
                UPDATE t_security_list
                   SET status = ?, source = 2, source_rule = ?, reason = ?, score = ?, hit_count = 1, last_hit_time = NOW(),
                       last_event_id = ?, last_incident_id = ?, expire_time = FROM_UNIXTIME(?), operator = ?,
                       revoke_operator = NULL, revoke_reason = NULL, revoke_time = NULL
                 WHERE id = ?
                   AND NOT (status = ? AND (expire_time IS NULL OR expire_time > NOW()))
                """, SecurityListStatusEnum.VALID.getValue(), e.sourceRule(), e.reason(), score(e), e.eventId(), e.eventId(),
                e.untilEpochSec(), SYSTEM, id, SecurityListStatusEnum.VALID.getValue());
    }

    private static Integer score(Entry e) {
        return e.listType() == SecurityListTypeEnum.GREY ? AUTO_GREY_SCORE : null;
    }
}
