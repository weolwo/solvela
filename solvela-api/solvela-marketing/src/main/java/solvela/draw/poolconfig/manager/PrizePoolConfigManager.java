package solvela.draw.poolconfig.manager;

import solvela.draw.poolconfig.dao.PrizePoolConfigDao;
import solvela.draw.PrizePoolConfig;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 奖池配置  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizePoolConfigManager extends ServiceImpl<PrizePoolConfigDao, PrizePoolConfig> {


}
