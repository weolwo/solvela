package net.lab1024.sa.lottery.record.manager;

import net.lab1024.sa.lottery.record.dao.LotteryRecordDao;
import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 用户号码记录  Manager
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryRecordManager extends ServiceImpl<LotteryRecordDao, LotteryRecord> {


}
