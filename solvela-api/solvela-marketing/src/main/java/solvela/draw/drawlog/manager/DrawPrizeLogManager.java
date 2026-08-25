package solvela.draw.drawlog.manager;

import solvela.draw.drawlog.domain.entity.DrawPrizeLog;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 抽奖记录  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class DrawPrizeLogManager extends ServiceImpl<DrawPrizeLogDao, DrawPrizeLog> {


}
