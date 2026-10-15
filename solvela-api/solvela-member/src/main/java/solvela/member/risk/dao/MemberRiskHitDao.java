package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import solvela.member.MemberRiskHit;
import solvela.member.risk.domain.dto.MemberRiskHitDTO;

import java.time.LocalDateTime;

import java.util.List;

/**
 * 风控规则命中明细（按月分区）
 *
 * @Date 2026-10-15
 */
@Mapper
public interface MemberRiskHitDao extends BaseMapper<MemberRiskHit> {

    /**
     * 一次写完这次尝试的全部命中。
     *
     * <h3>为什么要批量</h3>
     * 一次登录通常 0 到 3 条命中，但被攻击时一条尝试可能命中七八条规则 ——
     * 逐条 insert 就是七八次往返，而这些往返全落在登录接口的响应时间上。
     *
     * <h3>这里用 {@code #{h.decision}} 而不是 {@code #{h.decision.value}}</h3>
     * 与 {@code MemberRiskEventDao.insertEvent} 刻意不同：{@code decision} 对打分规则<b>是 null</b>，
     * 而 {@code .value} 这种写法一旦遇到 null 中间对象就会炸。枚举映射靠全局处理器
     * （{@code MybatisPlusConfig} 把默认处理器换成了认 {@code getValue()} 的 {@code MybatisEnumTypeHandler}），
     * 它对 null 是安全的。
     */
    @Insert("""
            <script>
            INSERT INTO t_member_risk_hit
              (event_id, rule_code, mode, decision, score, detail, error, create_time)
            VALUES
            <foreach collection="hits" item="h" separator=",">
              (#{h.eventId}, #{h.ruleCode}, #{h.mode}, #{h.decision}, #{h.score},
               #{h.detail}, #{h.error}, #{h.createTime})
            </foreach>
            </script>
            """)
    int insertHits(@Param("hits") List<MemberRiskHit> hits);

    /**
     * 取一次尝试命中的全部规则，顺带把规则名也带出来。
     *
     * <h3>为什么要 join 规则表</h3>
     * 命中明细只存编码（规则改名、改档位都不该动历史记录），而编码不是给人看的。
     * {@code LEFT JOIN}：规则被下线删掉之后，历史命中<b>照样要能看</b>，只是没有名字。
     *
     * <p>🔴 {@code create_time} 不能省：两张表都按月分区，少了它两边都不裁剪分区。
     */
    @Select("""
            SELECT h.id, h.rule_code, r.rule_name, h.mode, h.decision, h.score, h.detail,
                   h.error, h.create_time
              FROM t_member_risk_hit h
              LEFT JOIN t_member_risk_rule r ON r.rule_code = h.rule_code
             WHERE h.event_id = #{eventId} AND h.create_time = #{createTime}
             ORDER BY h.id
            """)
    List<MemberRiskHitDTO> selectByEvent(@Param("eventId") Long eventId,
                                         @Param("createTime") LocalDateTime createTime);
}
