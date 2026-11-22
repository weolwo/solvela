package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.LotteryDispatchStatusEnum;
import solvela.enums.TicketStatusEnum;
import solvela.lottery.LotteryRecord;
import solvela.lottery.record.dao.LotteryRecordDao;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 彩票记录的开奖、派发状态在真库上走一遍：出票 → 条件 UPDATE → 读回实体。
 *
 * <p>{@code LotteryRecordStatusSqlTest} 只渲染 SQL、不连库；这里验的是 {@code ${...}} 在 Spring 装配的
 * MyBatis-Plus 配置下能求值并真的命中行，两列状态去掉 setter 之后仍被装配进来，
 * 以及标投递失败不再覆盖已投递的记录。用的是测试专用的彩票编码与期号，结束时回滚 ——
 * 不碰任何真实期次的发号游标与密钥（交接文档 §4 的不可逆约束与这里无关）。
 */
@SpringBootTest
@Transactional
class LotteryRecordStatusFlowTest {

    private static final String LOTTERY = "ZZTESTLOT";

    @Autowired
    private LotteryRecordDao lotteryRecordDao;

    @Test
    @DisplayName("🔴 开奖：精确匹配的票认领为中奖，其余判未中奖；同一张票不会被认领两次")
    void 开奖() {
        String issue = "T" + System.nanoTime() % 100000000;
        LotteryRecord hit = insert(issue, 1, "12345");
        LotteryRecord miss = insert(issue, 2, "54321");
        assertEquals(TicketStatusEnum.WAIT, lotteryRecordDao.selectById(hit.getId()).getWinStatus(), "出票后读回的状态不对，或没装配上");

        assertEquals(1, lotteryRecordDao.claimByRule(LOTTERY, issue, "EXACT", null, "12345", 1, "ZZPRIZE", 1000));
        assertEquals(0, lotteryRecordDao.claimByRule(LOTTERY, issue, "EXACT", null, "12345", 2, "ZZPRIZE2", 1000),
                "已中奖的票不该被低奖级再认领一次");
        assertEquals(1, lotteryRecordDao.markNoWin(LOTTERY, issue, 1000));

        assertEquals(TicketStatusEnum.SUCCESS_MATCH, lotteryRecordDao.selectById(hit.getId()).getWinStatus());
        assertEquals(TicketStatusEnum.FAILURE_MATCH, lotteryRecordDao.selectById(miss.getId()).getWinStatus());
        assertEquals(List.of(hit.getId()), lotteryRecordDao.selectPendingDispatch(LOTTERY, issue, 1000)
                .stream().map(LotteryRecord::getId).toList());
    }

    @Test
    @DisplayName("🔴 派发：已投递的不会被标成失败，没中奖的也不会")
    void 派发() {
        String issue = "T" + System.nanoTime() % 100000000;
        LotteryRecord dispatched = insert(issue, 1, "11111");
        LotteryRecord failed = insert(issue, 2, "22222");
        LotteryRecord lose = insert(issue, 3, "33333");
        lotteryRecordDao.claimByRule(LOTTERY, issue, "EXACT", null, "11111", 1, "ZZPRIZE", 1000);
        lotteryRecordDao.claimByRule(LOTTERY, issue, "EXACT", null, "22222", 1, "ZZPRIZE", 1000);
        lotteryRecordDao.markNoWin(LOTTERY, issue, 1000);

        assertEquals(1, lotteryRecordDao.markDispatched(List.of(dispatched.getId())));
        assertEquals(0, lotteryRecordDao.markDispatchFailed(dispatched.getId()), "已投递的不该被标成失败");
        assertEquals(0, lotteryRecordDao.markDispatchFailed(lose.getId()), "没中奖的不该被标成投递失败");
        assertEquals(1, lotteryRecordDao.markDispatchFailed(failed.getId()));

        assertEquals(LotteryDispatchStatusEnum.DISPATCHED, lotteryRecordDao.selectById(dispatched.getId()).getDispatchStatus());
        assertEquals(LotteryDispatchStatusEnum.FAILED, lotteryRecordDao.selectById(failed.getId()).getDispatchStatus());
        assertEquals(LotteryDispatchStatusEnum.WAITING, lotteryRecordDao.selectById(lose.getId()).getDispatchStatus());
    }

    private LotteryRecord insert(String issue, int seq, String number) {
        LotteryRecord record = new LotteryRecord();
        record.setLotteryCode(LOTTERY);
        record.setIssueNo(issue);
        record.setSequenceNo(seq);
        record.setTicketNumber(number);
        record.setMemberId(950001L);
        record.setMemberName("zz-test");
        record.setObtainTime(LocalDateTime.now());
        record.setPrizeLevel(99);
        record.setSecuritySign("zz-test-sign");
        record.openTicket();
        lotteryRecordDao.insert(record);
        return record;
    }
}
