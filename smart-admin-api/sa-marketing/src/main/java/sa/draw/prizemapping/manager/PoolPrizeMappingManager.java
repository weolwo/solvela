package sa.draw.prizemapping.manager;

import sa.draw.prizemapping.domain.entity.PoolPrizeMapping;
import sa.draw.prizemapping.dao.PoolPrizeMappingDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 奖池奖项映射  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PoolPrizeMappingManager extends ServiceImpl<PoolPrizeMappingDao, PoolPrizeMapping> {


}
