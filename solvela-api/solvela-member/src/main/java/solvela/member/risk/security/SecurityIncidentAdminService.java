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
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        /*
         * 🔴 没给的那一端交给 SQL 的 NOW()，不在 Java 里取「现在」：create_time 是秒精度，写入时毫秒被四舍五入，
         * 刚落库的那一行可能比 Java 此刻的时钟还「晚」半秒 —— 拿 Java 的 now 当上界，它就查不到。
         * 没给结束时间就不设上界。
         */
        if (q.from() == null && q.to() == null) {
            where.append(" AND create_time >= NOW() - INTERVAL ").append(DEFAULT_WINDOW.toDays()).append(" DAY");
        } else {
            LocalDateTime from = q.from() != null ? q.from() : q.to().minus(DEFAULT_WINDOW);
            if (q.to() != null && from.isAfter(q.to())) {
                throw new IllegalArgumentException("开始时间不能晚于结束时间");
            }
            if (Duration.between(from, q.to() != null ? q.to() : LocalDateTime.now()).compareTo(MAX_WINDOW) > 0) {
                throw new IllegalArgumentException("一次最多查 " + MAX_WINDOW.toDays() + " 天 —— 这张表按月分区，更长的区间请分几次查");
            }
            where.append(" AND create_time >= ?");
            args.add(from);
            if (q.to() != null) {
                where.append(" AND create_time < ?");
                args.add(q.to());
            }
        }
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
