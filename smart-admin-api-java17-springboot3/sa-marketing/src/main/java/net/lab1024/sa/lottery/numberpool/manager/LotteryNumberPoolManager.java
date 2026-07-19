package net.lab1024.sa.lottery.numberpool.manager;

import net.lab1024.sa.lottery.numberpool.dao.LotteryNumberPoolDao;
import net.lab1024.sa.lottery.numberpool.domain.entity.LotteryNumberPool;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 彩票号码池  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryNumberPoolManager extends ServiceImpl<LotteryNumberPoolDao, LotteryNumberPool> {


}
