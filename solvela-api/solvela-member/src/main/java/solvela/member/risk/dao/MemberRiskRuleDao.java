package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.member.MemberRiskRule;

/**
 * 风控规则（只读：规则由后台维护，会员域只加载）
 *
 * @Date 2026-10-15
 */
@Mapper
public interface MemberRiskRuleDao extends BaseMapper<MemberRiskRule> {
}
