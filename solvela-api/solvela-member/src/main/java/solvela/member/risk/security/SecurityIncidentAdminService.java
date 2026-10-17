package solvela.member.risk.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaStringUtil;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台「安全事件」页（网关方案 §6.2、§6.4）：按 IP / 设备 / 会员 / 类型 / 时间查 {@code t_security_incident}。
 *
 * <h3>必须带时间范围</h3>
 * 表按月分区；不带时间就扫所有分区。不传时兜最近 7 天，一次最多 31 天。
 *
 * <h3>🔴 {@code matchedSample}、{@code userAgent} 是攻击者可控的内容</h3>
 * 原样返回，<b>前端只按纯文本渲染</b>（{@code {{ }}}，绝不 {@code v-html}）：一次探测请求就能在运营打开页面时执行脚本。
 *
 * @Date 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class SecurityIncidentAdminService {

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    private static final Duration MAX_WINDOW = Duration.ofDays(31);

    private final JdbcTemplate jdbcTemplate;

    /** 查询条件。都可空；时间范围不给时兜最近 7 天 */
    public record Query(String clientIp, String deviceId, Long memberId, Integer incidentType, LocalDateTime from,
                        LocalDateTime to, long pageNum, long pageSize) {
    }

    /** 一行事件 */
    public record Row(long id, int incidentType, String ruleCode, int action, int suppressReason, Integer penaltyLevel,
                      String clientIp, String deviceId, Long memberId, String httpMethod, String requestPath,
                      Integer matchedField, String matchedParam, String matchedSample, String userAgent,
                      LocalDateTime penaltyUntil, String traceId, LocalDateTime createTime) {
    }

    public PageResult<Row> queryPage(Query q) {
        LocalDateTime to = q.to() == null ? LocalDateTime.now() : q.to();
        LocalDateTime from = q.from() == null ? to.minus(DEFAULT_WINDOW) : q.from();
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("一次最多查 " + MAX_WINDOW.toDays() + " 天 —— 这张表按月分区，更长的区间请分几次查");
        }
        StringBuilder where = new StringBuilder(" WHERE create_time >= ? AND create_time < ?");
        List<Object> args = new ArrayList<>(List.of(from, to));
        if (SolvelaStringUtil.isNotBlank(q.clientIp())) {
            where.append(" AND client_ip = ?");
            args.add(q.clientIp().trim());
        }
        if (SolvelaStringUtil.isNotBlank(q.deviceId())) {
            where.append(" AND device_id = ?");
            args.add(q.deviceId().trim());
        }
        if (q.memberId() != null) {
            where.append(" AND member_id = ?");
            args.add(q.memberId());
        }
        if (q.incidentType() != null) {
            where.append(" AND incident_type = ?");
            args.add(q.incidentType());
        }
        long pageNum = Math.max(1, q.pageNum());
        long pageSize = Math.min(100, Math.max(1, q.pageSize()));
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_security_incident" + where, Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((pageNum - 1) * pageSize);
        pageArgs.add(pageSize);
        List<Row> rows = jdbcTemplate.query("""
                SELECT id, incident_type, rule_code, action, suppress_reason, penalty_level, client_ip, device_id,
                       member_id, http_method, request_path, matched_field, matched_param, matched_sample, user_agent,
                       penalty_until, trace_id, create_time
                  FROM t_security_incident""" + where + " ORDER BY create_time DESC, id DESC LIMIT ?, ?",
                (rs, i) -> new Row(rs.getLong("id"), rs.getInt("incident_type"), rs.getString("rule_code"),
                        rs.getInt("action"), rs.getInt("suppress_reason"), (Integer) rs.getObject("penalty_level", Integer.class),
                        rs.getString("client_ip"), rs.getString("device_id"), (Long) rs.getObject("member_id", Long.class),
                        rs.getString("http_method"), rs.getString("request_path"),
                        (Integer) rs.getObject("matched_field", Integer.class), rs.getString("matched_param"),
                        rs.getString("matched_sample"), rs.getString("user_agent"),
                        rs.getObject("penalty_until", LocalDateTime.class), rs.getString("trace_id"),
                        rs.getObject("create_time", LocalDateTime.class)),
                pageArgs.toArray());
        long t = total == null ? 0 : total;
        return new PageResult<>(pageNum, pageSize, t, (t + pageSize - 1) / pageSize, rows);
    }
}
