package solvela.lottery.config.manager;

import solvela.lottery.LotteryConfig;
import solvela.lottery.config.dao.LotteryConfigDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 彩票配置  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryConfigManager extends ServiceImpl<LotteryConfigDao, LotteryConfig> {


}
