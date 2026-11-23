package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.IssueStatusEnum;
import solvela.enums.LotteryConfigStatusEnum;
import solvela.lottery.LotteryConfig;
import solvela.lottery.LotteryIssue;
import solvela.lottery.config.dao.LotteryConfigDao;
import solvela.lottery.issue.dao.LotteryIssueDao;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 彩票期号与玩法的状态在真库上走一遍：落初始状态 → 条件 UPDATE → 读回实体。
 *
 * <p>{@code LotteryIssueStatusSqlTest} / {@code LotteryConfigStatusSqlTest} 只渲染 SQL、不连库；这里验的是
 * {@code ${...}} 在 Spring 装配的 MyBatis-Plus 配置下能求值并真的命中行，去掉 setter 之后状态仍被装配进来，
 * 以及最要紧的两条：开奖开始之后发不出号、发过号的期删不掉。数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class LotteryIssueStatusFlowTest {

    @Autowired
    private LotteryIssueDao lotteryIssueDao;
    @Autowired
    private LotteryConfigDao lotteryConfigDao;

    @Test
    @DisplayName("🔴 开奖：待开奖 → 核销中（定案号码）→ 已开奖；抢到开奖之后已发数 +1 影响 0 行")
    void 开奖之后发不出号() {
        LotteryConfig config = insertConfig(10);
        LotteryIssue issue = insertIssue(config);
        assertEquals(IssueStatusEnum.WAIT, lotteryIssueDao.selectById(issue.getId()).getStatus(), "落库后读回的状态不对，或没装配上");

        assertEquals(1, lotteryIssueDao.increaseSoldCount(issue.getId()), "待开奖时应能发号");
        assertEquals(1, lotteryIssueDao.startSettle(issue.getId(), "123456"));
        assertEquals(0, lotteryIssueDao.startSettle(issue.getId(), "654321"), "开奖号码被第二次定案了");
        assertEquals(0, lotteryIssueDao.increaseSoldCount(issue.getId()), "开奖开始之后还能发号");
        assertEquals(1, lotteryIssueDao.finishSettle(issue.getId()));
        assertEquals(0, lotteryIssueDao.finishSettle(issue.getId()));

        LotteryIssue after = lotteryIssueDao.selectById(issue.getId());
        assertAll(
                () -> assertEquals(IssueStatusEnum.OPENED, after.getStatus()),
                () -> assertEquals("123456", after.getWinningNumber()),
                () -> assertNotNull(after.getSettleTime()),
                () -> assertEquals(1, after.getSoldCount()));
    }

    @Test
    @DisplayName("🔴 已发数到上限后 +1 影响 0 行；发过号的期删不掉，零发号的空期能删")
    void 上限与删除() {
        LotteryConfig config = insertConfig(1);
        LotteryIssue sold = insertIssue(config);
        assertEquals(1, lotteryIssueDao.increaseSoldCount(sold.getId()));
        assertEquals(0, lotteryIssueDao.increaseSoldCount(sold.getId()), "超过发行上限还能 +1");
        assertEquals(0, lotteryIssueDao.deleteUnsold(sold.getId()), "发过号的期被删了");

        LotteryIssue empty = insertIssue(config);
        assertEquals(1, lotteryIssueDao.deleteUnsold(empty.getId()));
        assertNull(lotteryIssueDao.selectById(empty.getId()));
    }

    @Test
    @DisplayName("玩法上下线：只放行一次，重复点影响 0 行")
    void 上下线() {
        LotteryConfig config = insertConfig(10);
        assertEquals(LotteryConfigStatusEnum.OFFLINE, lotteryConfigDao.selectById(config.getId()).getStatus());

        assertEquals(1, lotteryConfigDao.markOnline(config.getId()));
        assertEquals(0, lotteryConfigDao.markOnline(config.getId()));
        assertEquals(1, lotteryConfigDao.markOffline(config.getId()));
        assertEquals(0, lotteryConfigDao.markOffline(config.getId()));
        assertEquals(LotteryConfigStatusEnum.OFFLINE, lotteryConfigDao.selectById(config.getId()).getStatus());
    }

    private LotteryConfig insertConfig(int totalCount) {
        LotteryConfig config = new LotteryConfig();
        config.setActivityCode("ZZTESTACT");
        config.setLotteryCode("ZZL" + (System.nanoTime() % 1_000_000_000L));
        config.setLotteryName("状态机验收");
        config.setNumberLength(6);
        config.setTotalCount(totalCount);
        config.startOffline();
        lotteryConfigDao.insert(config);
        return config;
    }

    private LotteryIssue insertIssue(LotteryConfig config) {
        LocalDateTime now = LocalDateTime.now();
        LotteryIssue issue = new LotteryIssue();
        issue.setLotteryCode(config.getLotteryCode());
        issue.setIssueNo("ZZ" + (System.nanoTime() % 1_000_000_000L));
        issue.setSaleStartTime(now.minusDays(1));
        issue.setSaleEndTime(now.plusDays(1));
        issue.setSoldCount(0);
        issue.awaitDraw();
        lotteryIssueDao.insert(issue);
        return issue;
    }
}
