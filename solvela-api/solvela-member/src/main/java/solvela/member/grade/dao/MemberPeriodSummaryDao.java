package solvela.member.grade.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.member.MemberPeriodSummary;

/**
 * 周期结算快照 Dao。
 *
 * <p>⚠️ <b>只插不改</b>。快照被 UPDATE 过一次，它作为「当时到底是多少」的凭证就没了 ——
 * 与 {@code MemberGradeLogDao} 同一条规矩，所以这里没有也不该有 update 方法。
 *
 * <p>重跑靠 {@code uk(member_id, period_no)} 撞键挡住，不靠先查后写。
 *
 * @author alaric
 * @date 2026-09-20
 */
@Mapper
public interface MemberPeriodSummaryDao extends BaseMapper<MemberPeriodSummary> {
}
