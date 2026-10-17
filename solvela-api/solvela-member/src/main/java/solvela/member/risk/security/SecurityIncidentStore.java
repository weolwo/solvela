package solvela.member.risk.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import solvela.member.device.DeviceDispositionService;
import solvela.member.risk.config.RiskListCache;
import solvela.security.IpKeys;
import solvela.security.SecurityIncidentEvent;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;

/**
 * 一条安全事件落库：写 {@code t_security_incident}，按投递方给的处置更新名单，必要时把设备推进观察档（网关方案 §6.3、§6.4）。
 *
 * <h3>🔴 事件里的字符串都是攻击者可控的</h3>
 * 投递方截断过，这里照样再截一次（列宽是硬上限）；后台只能按纯文本展示 {@code matched_sample} 与 {@code user_agent}。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityIncidentStore {

    private final JdbcTemplate jdbcTemplate;

    private final SecurityListWriter listWriter;

    private final RiskListCache listCache;

    private final DeviceDispositionService dispositionService;

    /** @return 名单有没有变 —— 变了的话调用方要重发网关快照 */
    public boolean store(SecurityIncidentEvent e) {
        long incidentId = insert(e);
        boolean changed = false;
        if (e.action() != SecurityIncidentEvent.ACTION_RECORD && e.listUntilEpochSec() != null) {
            changed = updateList(e, incidentId);
        }
        if (e.observeDevice() && e.deviceId() != null) {
            dispositionService.observe(e.deviceId(), SecurityIncidentEvent.truncate(
                    "网关限流 " + e.ruleCode() + " 设备维度罚到第 " + e.penaltyLevel() + " 档", 128));
        }
        return changed;
    }

    private boolean updateList(SecurityIncidentEvent e, long incidentId) {
        String value = IpKeys.listValue(e.clientIp());
        if (value == null) {
            return false;
        }
        boolean black = e.action() == SecurityIncidentEvent.ACTION_BLACK;
        if (black && listCache.matchIp(e.clientIp()).white()) {
            log.error("【告警】【安全名单】白名单 IP 被自动拉黑（处罚照常，白名单只豁免限流）, 来源: {} {}, IP: {}, 事件: {}",
                    sourceRule(e.incidentType()), e.ruleCode(), e.clientIp(), incidentId);
        }
        SecurityListWriter.Outcome outcome = listWriter.upsert(new SecurityListWriter.Entry(
                black ? 1 : 2, IpKeys.listDimension(value), value, sourceRule(e.incidentType()), reason(e),
                e.listUntilEpochSec(), incidentId));
        log.info("【安全名单】自动{}: {}, 来源: {} {}, 结果: {}", black ? "拉黑" : "拉灰", value,
                sourceRule(e.incidentType()), e.ruleCode(), outcome);
        return outcome != SecurityListWriter.Outcome.KEPT_MANUAL && outcome != SecurityListWriter.Outcome.KEPT_EXISTING;
    }

    private long insert(SecurityIncidentEvent e) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO t_security_incident (incident_type, rule_code, action, suppress_reason, penalty_level,
                        client_ip, device_id, member_id, identity_hash, http_method, request_path, matched_field,
                        matched_param, matched_sample, user_agent, penalty_until, trace_id, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, UNHEX(?), ?, ?, ?, ?, ?, ?, FROM_UNIXTIME(?), ?, FROM_UNIXTIME(? / 1000))
                    """, Statement.RETURN_GENERATED_KEYS);
            int i = 0;
            ps.setInt(++i, e.incidentType());
            ps.setString(++i, cut(e.ruleCode(), 32));
            ps.setInt(++i, e.action());
            ps.setInt(++i, e.suppressReason());
            setInt(ps, ++i, e.penaltyLevel());
            ps.setString(++i, cut(e.clientIp(), 39));
            ps.setString(++i, cut(e.deviceId(), 32));
            setLong(ps, ++i, e.memberId());
            ps.setString(++i, cut(e.identityHashHex(), 64));
            ps.setString(++i, cut(e.httpMethod(), 8));
            ps.setString(++i, cut(e.requestPath(), 256));
            setInt(ps, ++i, e.matchedField());
            ps.setString(++i, cut(e.matchedParam(), 32));
            ps.setString(++i, cut(e.matchedSample(), 128));
            ps.setString(++i, cut(e.userAgent(), 256));
            setLong(ps, ++i, e.penaltyUntilEpochSec());
            ps.setString(++i, cut(e.traceId(), 64));
            ps.setLong(++i, e.occurredAtEpochMilli());
            return ps;
        }, key);
        Number id = key.getKey();
        return id == null ? 0 : id.longValue();
    }

    static String sourceRule(int incidentType) {
        return switch (incidentType) {
            case SecurityIncidentEvent.HONEYPOT -> "HONEYPOT";
            case SecurityIncidentEvent.CANARY -> "CANARY";
            case SecurityIncidentEvent.KEYWORD -> "KEYWORD";
            default -> "RATE_LIMIT";
        };
    }

    /** 给下一个看到这一行的人的人话 */
    private static String reason(SecurityIncidentEvent e) {
        String text = switch (e.incidentType()) {
            case SecurityIncidentEvent.HONEYPOT -> "碰了蜜罐路径 " + e.ruleCode();
            case SecurityIncidentEvent.CANARY -> "用蜜罐账号登录（用户名单可能已泄露）";
            case SecurityIncidentEvent.KEYWORD -> "请求里带探测特征 " + e.ruleCode();
            default -> "限流 " + e.ruleCode() + " 罚到第 " + e.penaltyLevel() + " 档";
        };
        return SecurityIncidentEvent.truncate(text + "（自动）", 128);
    }

    private static String cut(String s, int max) {
        return SecurityIncidentEvent.truncate(s, max);
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws java.sql.SQLException {
        if (v == null) {
            ps.setNull(i, Types.INTEGER);
        } else {
            ps.setInt(i, v);
        }
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws java.sql.SQLException {
        if (v == null) {
            ps.setNull(i, Types.BIGINT);
        } else {
            ps.setLong(i, v);
        }
    }
}
