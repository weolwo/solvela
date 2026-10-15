package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import solvela.member.MemberRiskEvent;
import solvela.member.risk.domain.dto.MemberRiskEventDTO;
import solvela.member.risk.domain.query.MemberRiskEventQuery;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 后台分页查。
     *
     * <h3>🔴 {@code create_time} 的条件一个都不能少</h3>
     * 表按月分区，而分区裁剪只看 WHERE 里有没有分区列。少了它，每个分区都要各探一次索引 ——
     * 所以 Service 在没传时间时会兜一个默认区间，而不是把条件去掉。
     *
     * <h3>{@code identity_hash} 要 HEX / UNHEX</h3>
     * 列是 {@code binary(32)}，而 Java 侧一律用 hex（{@code PiiHasher} 的约定）。
     * 查询条件漏了 {@code UNHEX} 的表现是「按手机号查永远 0 条」，而 SQL 看着很正常。
     */
    List<MemberRiskEventDTO> queryPage(Page<?> page, @Param("query") MemberRiskEventQuery query);

    /**
     * 取一行详情。
     *
     * <p>🔴 必须同时带 id 和 create_time：只按 id 查不裁剪分区（见 {@link #queryPage}）。
     */
    @Select("""
            SELECT id, event_type, ref_event_id, member_id, HEX(identity_hash) AS identity_hash_hex,
                   login_type, device_id, device_trust, ip_familiar, dormant, client_ip, ip_region,
                   captcha_passed, pre_score, post_score, shadow_score, decision, shadow_decision,
                   verify_channel, outcome, label, label_source, label_time, cost_ms, trace_id, create_time
              FROM t_member_risk_event
             WHERE id = #{id} AND create_time = #{createTime}
            """)
    MemberRiskEventDTO selectOneAt(@Param("id") Long id, @Param("createTime") LocalDateTime createTime);
}
