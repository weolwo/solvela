package net.lab1024.sa.prize.prizelog.manager;

import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.prize.prizelog.dao.PrizeLogDao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 奖励记录表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizeLogManager extends ServiceImpl<PrizeLogDao, PrizeLog> {


}
