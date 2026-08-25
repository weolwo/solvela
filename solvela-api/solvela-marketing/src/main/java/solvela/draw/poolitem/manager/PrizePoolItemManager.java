package solvela.draw.poolitem.manager;

import solvela.draw.poolitem.dao.PrizePoolItemDao;
import solvela.draw.poolitem.domain.entity.PrizePoolItem;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 奖池奖项库  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizePoolItemManager extends ServiceImpl<PrizePoolItemDao, PrizePoolItem> {


}
