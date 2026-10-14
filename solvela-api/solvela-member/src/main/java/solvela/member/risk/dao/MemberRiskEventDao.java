package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import solvela.member.MemberRiskEvent;

/**
 * 风控决策流水（按月分区）。🔴 按 id 定位时一定要同时带 create_time。
 *
 * @Date 2026-10-15
 */
@Mapper
public interface MemberRiskEventDao extends BaseMapper<MemberRiskEvent> {

    /**
     * 写一行流水。不用通用 insert：{@code identity_hash} 是 {@code binary(32)}，要在 SQL 里 {@code UNHEX}
     * （见 {@code PiiHasher} 类注释：二进制不进 Java 类型系统）。
     *
     * <p>枚举一律传 {@code .value}：注解 SQL 里不依赖全局的枚举类型处理器配置，
     * 少一个「换了配置就静默写成枚举名」的可能。
     */
    @Insert("""
            INSERT INTO t_member_risk_event (
              event_type, ref_event_id, member_id, identity_hash, login_type, device_id, device_trust,
              ip_familiar, dormant, client_ip, ip_region, captcha_passed, pre_score, post_score, shadow_score,
              decision, shadow_decision, verify_channel, outcome, cost_ms, trace_id, create_time
            ) VALUES (
              #{e.eventType.value}, #{e.refEventId}, #{e.memberId}, UNHEX(#{e.identityHashHex}), #{e.loginType},
              #{e.deviceId}, #{e.deviceTrust}, #{e.ipFamiliar}, #{e.dormant}, #{e.clientIp}, #{e.ipRegion},
              #{e.captchaPassed}, #{e.preScore}, #{e.postScore}, #{e.shadowScore},
              #{e.decision.value}, #{e.shadowDecision.value}, #{e.verifyChannel}, #{e.outcome.value},
              #{e.costMs}, #{e.traceId}, #{e.createTime}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "e.id", keyColumn = "id")
    int insertEvent(@Param("e") MemberRiskEvent event);
}
