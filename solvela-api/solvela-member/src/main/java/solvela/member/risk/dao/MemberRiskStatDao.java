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
     * 每条规则「切到生效会怎样」的那三个数（方案 §8.2 那段手写 SQL 的落地）。
     *
     * <h3>🔴 为什么要做进接口，而不是让人手写 SQL</h3>
     * 它是 P2「逐条切生效」唯一的依据。而对着一张按月分区的表手写 join，
     * 只会在出了问题之后才有人去做 —— 到那时该切的早就该切了。
     *
     * <p>⚠️ {@code wouldChange} 是<b>按尝试</b>算的，不是按规则算的：
     * 一次尝试里可能有好几条规则命中，它们的 {@code decision <> shadow_decision} 是同一个值。
     * 所以把几条规则的这个数加起来<b>没有意义</b>（会重复计），只能一条一条看。
     */
    @Select("""
            SELECT h.rule_code                               AS ruleCode,
                   h.mode                                    AS mode,
                   COUNT(*)                                  AS hits,
                   SUM(e.outcome = 1)                        AS loginSucceeded,
                   SUM(e.label = 2)                          AS confirmedTakeover,
                   SUM(e.decision <> e.shadow_decision)      AS wouldChange,
                   SUM(h.error = 1)                          AS errorCount
              FROM t_member_risk_hit h
              JOIN t_member_risk_event e
                ON e.id = h.event_id AND e.create_time = h.create_time
             WHERE h.create_time >= #{from} AND h.create_time < #{to}
             GROUP BY ruleCode, mode
             ORDER BY hits DESC
            """)
    List<MemberRiskStatDTO.RuleVerdict> ruleVerdicts(@Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    /**
     * IP 熟悉度分布。<b>关掉全局滑块的前置条件之一</b>（§9）。
     *
     * <p>🔴 要求非空：查无此人时算不出熟悉度，混进来会把「陌生」那一档撑大 ——
     * 而撞库恰恰大量落在查无此人上，于是这个数会系统性地低估真实用户的熟悉度，
     * 让人以为「关掉全局滑块没几个人受益」。
     */
    @Select("""
            SELECT ip_familiar AS ipFamiliar, COUNT(*) AS count
              FROM t_member_risk_event
             WHERE event_type = 1 AND ip_familiar IS NOT NULL
               AND create_time >= #{from} AND create_time < #{to}
             GROUP BY ipFamiliar
             ORDER BY ipFamiliar
            """)
    List<MemberRiskStatDTO.FamiliarityBucket> familiarity(@Param("from") LocalDateTime from,
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
