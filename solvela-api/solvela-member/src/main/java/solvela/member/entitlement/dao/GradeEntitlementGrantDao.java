package solvela.member.entitlement.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import solvela.member.GradeEntitlementGrant;
import solvela.member.entitlement.domain.EntitlementCandidate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权益发放记录 Dao。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Mapper
public interface GradeEntitlementGrantDao extends BaseMapper<GradeEntitlementGrant> {

    /**
     * 扫「该拿这份权益」的会员，按会员号游标分页。
     *
     * <h3>⚠️ 从 t_member 出发 LEFT JOIN 成长值，不是反过来</h3>
     * 反过来（从 t_member_growth 出发）会漏掉<b>从没攒过成长值的会员</b> ——
     * 他们没有成长值行，但确实是 0 级的真实会员。{@code minGrade = 0} 的权益
     * （比如「所有人都有的月度券」）会整批漏掉这些人，而且不报错。
     *
     * <h3>🔴 闰年生日：平年顺延到 2 月 28</h3>
     * {@code includeFeb29} 为 true 时（平年的 2 月 28 日），额外把 2 月 29 出生的人捞进来。
     * 不做这件事的话，那批人<b>四年里有三年收不到生日礼</b> ——
     * 而这件事不报错、没有日志、没有人会发现。
     *
     * @param birthMonth   生日月；为 null 表示不按生日过滤（月度券走这条）
     * @param includeFeb29 是否把 2 月 29 出生的人也算进今天
     * @param afterMemberId 游标，传 0 从头开始
     */
    List<EntitlementCandidate> selectCandidates(@Param("minGrade") int minGrade,
                                                @Param("birthMonth") Integer birthMonth,
                                                @Param("birthDay") Integer birthDay,
                                                @Param("includeFeb29") boolean includeFeb29,
                                                @Param("afterMemberId") long afterMemberId,
                                                @Param("limit") int limit);

    /**
     * 把到期还没领的置为已过期。
     *
     * <p>条件里带 {@code status = PENDING}：已领取的绝不能被这句碰到 ——
     * 一条 UPDATE 写漏这个条件，会把历史上所有已领记录改成「已过期」，
     * 而那是不可逆的（领取时间还在，但状态说它过期了，对不上）。
     */
    int expirePending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 领取：条件 UPDATE 抢占。
     *
     * <p>🔴 用<b>条件更新</b>而不是「先查状态再改」：两个请求同时点领取，
     * 先查后改会双双通过 —— 而那是发两份。影响 0 行就是没抢到，调用方据此拒绝。
     */
    int tryClaim(@Param("id") Long id, @Param("memberId") Long memberId,
                 @Param("now") LocalDateTime now);

    /** 把发放结果写回（成功与失败都写，失败的留原因便于排查） */
    int markResult(@Param("id") Long id, @Param("result") String result);
}
