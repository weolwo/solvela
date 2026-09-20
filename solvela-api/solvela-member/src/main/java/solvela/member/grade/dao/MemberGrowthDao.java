package solvela.member.grade.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import solvela.member.MemberGrowth;
import solvela.member.grade.domain.dto.GrowthCheckRow;
import solvela.member.grade.domain.dto.MemberGrowthDTO;
import solvela.member.grade.domain.query.MemberGrowthQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员成长值 Dao。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Mapper
public interface MemberGrowthDao extends BaseMapper<MemberGrowth> {

    /**
     * 原子累加成长值。
     *
     * <h3>🔴 为什么是 SQL 里 {@code value = value + ?}，不是读出来加完再写回</h3>
     * 同一个会员可能<b>并发入账</b>（一次活动里同时中了积分和现金、任务同时达标）。
     * 读-改-写会丢更新：两个线程都读到 100，各加 50，最后写进去的是 150 而不是 200。
     *
     * <p>而丢的这一次<b>在流水里是有的</b> —— 于是「明细加起来对不上总数」，
     * 那正是冗余字段最经典的烂法。这条 SQL 是它唯一的防线。
     *
     * @return 影响行数。0 表示这个会员的行不存在（调用方该先 loadOrInit）
     */
    @Update("""
            UPDATE t_member_growth
               SET current_period_value = current_period_value + #{delta},
                   total_value          = total_value + #{delta},
                   update_time  = NOW()
             WHERE member_id = #{memberId}
            """)
    int addValue(@Param("memberId") Long memberId, @Param("delta") long delta);

    /** 数据库时钟。周期边界判定不能用应用时钟 —— 两边有偏差时跨周期的那一刻会算错 */
    @Select("SELECT NOW()")
    java.time.LocalDateTime selectDbNow();

    /**
     * 条件更新等级：<b>只有当前等级仍然是 {@code oldGrade} 时才改</b>。
     *
     * <h3>🔴 为什么是一条带条件的 UPDATE，不是先查后改</h3>
     * 同一个会员可能并发入账，两条线程都读到「当前 V1、该升 V2」，
     * 先查后改会让<b>两条都改成功</b> —— 于是留痕表里出现两条
     * 「V1 → V2」，客服看到的是「他升了两次级」。
     *
     * <p>把判断压进 WHERE 之后，第二条的影响行数是 0，调用方据此跳过留痕。
     * 形状与 {@code MemberDeliveryService} 那条「id + memberId + status 一起进 WHERE」一致。
     *
     * <p>⚠️ 这条 SQL <b>不碰 period_value</b>。等级是派生状态，改等级永远不该
     * 反过来改成长值 —— 人工把某人调到白金，不代表他真的攒够了那些成长值，
     * 期末结算照样要按他实际的成长值重新判。
     *
     * @return 影响行数。0 表示等级已被别人改过，本次判断作废
     */
    @Update("""
            UPDATE t_member_growth
               SET current_grade = #{newGrade},
                   grade_since   = NOW(),
                   update_time   = NOW()
             WHERE member_id     = #{memberId}
               AND current_grade = #{oldGrade}
            """)
    int updateGrade(@Param("memberId") Long memberId,
                    @Param("oldGrade") int oldGrade,
                    @Param("newGrade") int newGrade);

    /**
     * 只取周期累计值。
     *
     * <h3>🔴 判级必须读这个，不能拿累加前的快照加 delta</h3>
     * {@link #addValue} 是原子自增，正因为并发下「快照 + delta」是错的才有它。
     * 判级时再用那个错值，等于把刚防住的丢更新又请回来 ——
     * 两笔并发入账各自以为自己只加到 100，而实际已经 200，
     * 于是<b>该升的级没升</b>，且不报错。
     */
    @Select("SELECT current_period_value FROM t_member_growth WHERE member_id = #{memberId}")
    Long selectCurrentPeriodValue(@Param("memberId") Long memberId);

    /** 管理端分页。账号与昵称走子查询，理由同 {@code MemberLoginLogMapper} */
    List<MemberGrowthDTO> queryPage(Page<?> page, @Param("queryForm") MemberGrowthQuery queryForm);

    /**
     * 捞到期待结算的会员。<b>两种到期各一支</b>：
     * <ul>
     *   <li>周期到点、且<b>不在</b>缓冲期 —— 该判一次；</li>
     *   <li>缓冲期满 —— 该下终局。</li>
     * </ul>
     *
     * <p>⚠️ 进入缓冲之后 {@code period_end} 依然是过去时间（缓冲期不推进周期），
     * 所以第一支必须带 {@code protect_until IS NULL}，否则缓冲期内每一轮都会被反复捞出来。
     *
     * <p>🔴 按 member_id 升序 + limit 分页，不用 offset：上一批结算完周期就推进了、
     * 不会再被捞到，用 offset 反而会跳过刚被别的节点改动的行 ——
     * 形状与 {@code MallOrderExpireJob} 一致。
     */
    @Select("""
            SELECT * FROM t_member_growth
             WHERE (protect_until IS NULL     AND period_end   < #{now})
                OR (protect_until IS NOT NULL AND protect_until < #{now})
             ORDER BY member_id
             LIMIT #{limit}
            """)
    List<MemberGrowth> selectDueForSettle(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 进入保级缓冲期。<b>周期与累计值一概不动</b>。
     *
     * <p>{@code AND protect_until IS NULL} 是并发判据：两个调度节点同时捞到同一个人时，
     * 后到的影响 0 行，调用方据此整笔回滚。
     *
     * @return 影响行数。0 表示已经被别人处理过
     */
    @Update("""
            UPDATE t_member_growth
               SET protect_until = #{protectUntil},
                   protect_grade = #{protectGrade},
                   update_time   = NOW()
             WHERE member_id     = #{memberId}
               AND period_end    = #{expectedPeriodEnd}
               AND protect_until IS NULL
            """)
    int startProtect(@Param("memberId") Long memberId,
                     @Param("expectedPeriodEnd") LocalDateTime expectedPeriodEnd,
                     @Param("protectUntil") LocalDateTime protectUntil,
                     @Param("protectGrade") int protectGrade);

    /**
     * 结清一个周期：累计值清零、周期推进、缓冲状态清空。
     *
     * <h3>🔴 它不改 current_grade</h3>
     * 等级变更一律走 {@code MemberGradeChangeService.change()} 那个唯一写入口 ——
     * 在这里顺手改一下，等级就有了第二个写路径，而那正是「总有一处会忘记留痕」的开始。
     *
     * <p>⚠️ {@code total_value} 也不动：它是终身累计，不跟着周期清零。
     *
     * @return 影响行数。0 表示这一期已经被别人结过了
     */
    @Update("""
            UPDATE t_member_growth
               SET period_start         = #{periodStart},
                   period_end           = #{periodEnd},
                   current_period_value = 0,
                   protect_until        = NULL,
                   protect_grade        = NULL,
                   update_time          = NOW()
             WHERE member_id  = #{memberId}
               AND period_end = #{expectedPeriodEnd}
            """)
    int closePeriod(@Param("memberId") Long memberId,
                    @Param("expectedPeriodEnd") LocalDateTime expectedPeriodEnd,
                    @Param("periodStart") LocalDateTime periodStart,
                    @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * 对账用：扫一批会员，把「主表记的值」和「流水求和」并排取出来。
     *
     * <h3>🔴 返回的是整批，不是只有对不上的</h3>
     * 游标要靠「本批扫到的最大 member_id」往前推。只返回异常行的话，
     * 一批全对就拿不到游标，扫描会原地打转。判断对不对交给调用方。
     *
     * <h3>为什么用相关子查询而不是 JOIN 一个聚合派生表</h3>
     * {@code (SELECT ... GROUP BY member_id, period_tag)} 那种写法会把
     * <b>整张流水表</b>聚合一遍，而流水表是会长到千万行的那一张。
     * 相关子查询走 {@code idx_t_mbr_gr_log_period (member_id, period_tag)}，
     * 每行一次索引区间扫 —— 代价随「本批多少人」走，不随「流水多大」走。
     *
     * @param changedSince 只看这个时刻之后有过变动的人。传 {@code null} 表示全量扫
     * @param afterMemberId 游标：只取 member_id 大于它的。首批传 0
     */
    @Select("""
            <script>
            SELECT g.member_id                                     AS memberId,
                   DATE_FORMAT(g.period_start, '%Y%m%d')           AS periodTag,
                   g.current_period_value                          AS recorded,
                   COALESCE((SELECT SUM(l.delta)
                               FROM t_member_growth_log l
                              WHERE l.member_id  = g.member_id
                                AND l.period_tag = DATE_FORMAT(g.period_start, '%Y%m%d')), 0) AS logSum
              FROM t_member_growth g
             WHERE g.member_id > #{afterMemberId}
            <if test="changedSince != null">
               AND g.update_time >= #{changedSince}
            </if>
             ORDER BY g.member_id
             LIMIT #{limit}
            </script>
            """)
    List<GrowthCheckRow> selectForReconcile(@Param("changedSince") LocalDateTime changedSince,
                                            @Param("afterMemberId") long afterMemberId,
                                            @Param("limit") int limit);

    /**
     * 有流水、却没有主表行的会员数。
     *
     * <p>🔴 正常情况下恒为 0：{@code accrue} 是先 {@code loadOrInit} 建主表行、
     * 再插流水的，两步在同一个事务里。这个数不为 0 意味着有人绕过了累加器直接写流水 ——
     * 那比「值对不上」严重得多，因为对账<b>连比都没法比</b>。
     */
    @Select("""
            SELECT COUNT(DISTINCT l.member_id)
              FROM t_member_growth_log l
             WHERE NOT EXISTS (SELECT 1 FROM t_member_growth g WHERE g.member_id = l.member_id)
            """)
    long countOrphanGrowthLog();

    /**
     * 把主表的值校正成流水求和。
     *
     * <p>🔴 {@code AND current_period_value = #{recorded}} 是<b>必须的</b>：
     * 从「读出来发现对不上」到「写回去」之间，用户完全可能又入账了一笔。
     * 不带这个条件就会把那一笔<b>覆盖掉</b> —— 对账任务自己制造出一笔差异，
     * 而且是往少了写，用户凭空掉成长值。
     *
     * @return 影响行数。0 表示期间有人动过，本次放弃，下一轮再对
     */
    @Update("""
            UPDATE t_member_growth
               SET current_period_value = #{logSum},
                   update_time          = NOW()
             WHERE member_id            = #{memberId}
               AND current_period_value = #{recorded}
            """)
    int correctPeriodValue(@Param("memberId") Long memberId,
                           @Param("recorded") long recorded,
                           @Param("logSum") long logSum);
}
