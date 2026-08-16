package sa.lottery.prizerule.manager;

import sa.lottery.prizerule.dao.LotteryPrizeRuleDao;
import sa.lottery.prizerule.domain.entity.LotteryPrizeRule;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 彩票奖励配置  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryPrizeRuleManager extends ServiceImpl<LotteryPrizeRuleDao, LotteryPrizeRule> {


}
