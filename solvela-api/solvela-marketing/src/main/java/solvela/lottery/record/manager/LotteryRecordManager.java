package solvela.lottery.record.manager;

import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.LotteryRecord;

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
