package solvela.member.risk.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import solvela.member.RateLimitRule;

/**
 * 网关限流规则
 *
 * @Date 2026-10-17
 */
@Mapper
public interface RateLimitRuleDao extends BaseMapper<RateLimitRule> {
}
