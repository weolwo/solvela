package net.lab1024.sa.prize.prizeconfig.manager;

import net.lab1024.sa.prize.prizeconfig.domain.entity.PrizeConfig;
import net.lab1024.sa.prize.prizeconfig.dao.PrizeConfigDao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 奖品配置表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizeConfigManager extends ServiceImpl<PrizeConfigDao, PrizeConfig> {


}
