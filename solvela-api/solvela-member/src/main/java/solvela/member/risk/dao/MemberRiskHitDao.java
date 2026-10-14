package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.member.MemberRiskHit;

/**
 * 风控规则命中明细（按月分区）
 *
 * @Date 2026-10-15
 */
@Mapper
public interface MemberRiskHitDao extends BaseMapper<MemberRiskHit> {
}
