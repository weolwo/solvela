package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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

    /**
     * 给「某台设备上那一次登录」打标（方案 §8.1 的「这不是我」）。
     *
     * <h3>为什么是一条带 ORDER BY / LIMIT 的 UPDATE</h3>
     * 「先查出 id 再更新」要两趟，而且两趟之间那一行可能已经被分区维护任务 DROP 掉了。
     * MySQL 的 {@code UPDATE ... ORDER BY ... LIMIT 1} 一条语句就够，也不需要事务。
     *
     * <h3>🔴 时间范围不能省</h3>
     * 它既是「只标那一次登录，不要连带标上同一台设备的其它几次」的判据，
     * <b>也是分区裁剪的唯一依据</b>（表按月分区）。省掉它会变成全表扫。
     *
     * <h3>只标成功进去的那几行</h3>
     * {@code outcome} 限定在「登录成功」和「二次验证通过」：被拦下的那些本来就没进去，
     * 标成「确认被盗」会让「漏过率」这个数凭空变大。
     *
     * @param fromEpochSecond 时间窗下界（Unix 秒）
     * @param toEpochSecond   时间窗上界
     * @param atEpochSecond   会话的登录时刻，用来在窗口里挑<b>最接近</b>的那一行
     * @return 影响行数；0 表示没找到对应的流水（老会话没有设备号、或者流水已过期被删）
     */
    @Update("""
            UPDATE t_member_risk_event
               SET label = #{label}, label_source = #{labelSource}, label_time = NOW()
             WHERE member_id = #{memberId}
               AND device_id = #{deviceId}
               AND outcome IN (1, 11)
               AND create_time >= FROM_UNIXTIME(#{fromEpochSecond})
               AND create_time <= FROM_UNIXTIME(#{toEpochSecond})
             ORDER BY ABS(TIMESTAMPDIFF(SECOND, create_time, FROM_UNIXTIME(#{atEpochSecond})))
             LIMIT 1
            """)
    int labelNearestLogin(@Param("memberId") Long memberId,
                          @Param("deviceId") String deviceId,
                          @Param("fromEpochSecond") long fromEpochSecond,
                          @Param("toEpochSecond") long toEpochSecond,
                          @Param("atEpochSecond") long atEpochSecond,
                          @Param("label") int label,
                          @Param("labelSource") int labelSource);
}
