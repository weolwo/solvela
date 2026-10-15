package solvela.member.risk.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import solvela.member.risk.domain.dto.MemberRiskStatDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板的聚合查询。<b>每一条都带时间范围，一条都不能省。</b>
 *
 * <h3>🔴 为什么单独一个 Dao，不塞进 {@code MemberRiskEventDao}</h3>
 * 那个是「一次尝试的读写」，这里是「一段时间的聚合」—— 两者的索引、代价、调用频率
 * 都不是一个量级。混在一起之后，很容易有人顺手在一个点查接口旁边加一条全表聚合，
 * 而评审时看不出来。
 *
 * <h3>🔴 时间范围是分区裁剪的唯一依据</h3>
 * 两张表按月分区。少了 {@code create_time} 条件，每个分区都要各扫一遍 ——
 * 而看板天然是「最近 N 天」，所以这件事不难办，难办的是忘了它。
 * Service 那边会强制兜一个默认窗口。
 *
 * @Date 2026-10-16
 */
@Mapper
public interface MemberRiskStatDao {

    /** 窗口内的登录尝试总数。给其余几个数当分母 */
    @Select("""
            SELECT COUNT(*) FROM t_member_risk_event
             WHERE event_type = 1 AND create_time >= #{from} AND create_time < #{to}
            """)
    long countAttempts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 每天 × 结论。只数登录（event_type=1）—— 二次验证那条是同一次尝试的后半段，会重复计 */
    @Select("""
            SELECT DATE(create_time) AS day, decision, COUNT(*) AS count
              FROM t_member_risk_event
             WHERE event_type = 1 AND create_time >= #{from} AND create_time < #{to}
             GROUP BY day, decision
             ORDER BY day, decision
            """)
    List<MemberRiskStatDTO.DecisionDaily> decisionDaily(@Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to);

    /**
     * 处置矩阵各格的尝试数。
     *
     * <p>🔴 三列都要求非空：查无此人时它们是 NULL（账号信息算不出来），
     * 而「矩阵哪一格最常命中」问的是有账号的那些尝试。把 NULL 混进来会多出一格
     * 「全是空」的，而那一格不对应矩阵里的任何位置。
     */
    @Select("""
            SELECT device_trust AS deviceTrust, ip_familiar AS ipFamiliar, dormant, COUNT(*) AS count
              FROM t_member_risk_event
             WHERE event_type = 1 AND create_time >= #{from} AND create_time < #{to}
               AND device_trust IS NOT NULL AND ip_familiar IS NOT NULL AND dormant IS NOT NULL
             GROUP BY deviceTrust, ipFamiliar, dormant
             ORDER BY count DESC
            """)
    List<MemberRiskStatDTO.MatrixCell> matrixCells(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    /** 打扰了多少次：判要 OTP 并且真签了票（outcome = 10 已签凭票） */
    @Select("""
            SELECT COUNT(*) FROM t_member_risk_event
             WHERE event_type = 1 AND outcome = 10
               AND create_time >= #{from} AND create_time < #{to}
            """)
    long countChallenged(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 其中后来验过码的次数。
     *
     * <p>🔴 靠 {@code ref_event_id} 指回签票那一行来数，而不是单独数「验证通过」的条数 ——
     * 后者会把窗口<b>之前</b>签的票算进来，于是通过率可能大于 100%，
     * 而那个数字一旦出现过一次，整块看板就没人信了。
     */
    @Select("""
            SELECT COUNT(*) FROM t_member_risk_event v
             WHERE v.event_type = 2 AND v.outcome = 11
               AND v.create_time >= #{from} AND v.create_time < #{to}
               AND EXISTS (
                 SELECT 1 FROM t_member_risk_event e
                  WHERE e.id = v.ref_event_id
                    AND e.create_time >= #{from} AND e.create_time < #{to}
               )
            """)
    long countVerified(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 按规则 × 档位的命中数，顺带数出「取特征失败」的那几条。
     *
     * <p>{@code error = 1} 不为 0 说明这条规则其实<b>没在工作</b>（特征取不到、超了时间预算），
     * 而它在列表上看着是「生效中」—— 这是看板上最该先看的一个数。
     */
    @Select("""
            SELECT rule_code AS ruleCode, mode, COUNT(*) AS count,
                   SUM(CASE WHEN error = 1 THEN 1 ELSE 0 END) AS errorCount
              FROM t_member_risk_hit
             WHERE create_time >= #{from} AND create_time < #{to}
             GROUP BY ruleCode, mode
             ORDER BY count DESC
            """)
    List<MemberRiskStatDTO.RuleHit> ruleHits(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /**
     * 生效结论与陪跑结论不一致的尝试数。<b>「切生效会多打扰多少人」就是这个数。</b>
     *
     * <p>它是陪跑这件事全部的意义所在（方案 §3.6）：陪跑规则照常执行、照常记命中，
     * 但不影响结论 —— 攒下来的差额就是切过去的代价。
     */
    @Select("""
            SELECT COUNT(*) FROM t_member_risk_event
             WHERE event_type = 1 AND shadow_decision <> decision
               AND create_time >= #{from} AND create_time < #{to}
            """)
    long countShadowDiff(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
