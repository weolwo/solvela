package solvela.lottery.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import solvela.exception.BusinessException;
import solvela.lottery.LotteryConfig;
import solvela.lottery.LotteryIssue;
import solvela.lottery.issue.dao.LotteryIssueDao;
import solvela.lottery.record.dao.LotteryRecordDao;
import solvela.lottery.runtime.domain.TicketObtainDTO;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 落一张票。
 *
 * <p>🔴 已发数 +1 影响 0 行（号码已发完，或开奖已经开始）时必须抛出去，让事务回滚这张票。
 * 以前不看返回值，票照样落库：「DB 兜底不让已发数超过上限」并不成立，开奖开始后进来的票也能落进来。
 */
@ExtendWith(MockitoExtension.class)
class TicketPersistServiceTest {

    @Mock
    private LotteryRecordDao lotteryRecordDao;
    @Mock
    private LotteryIssueDao lotteryIssueDao;

    @InjectMocks
    private TicketPersistService service;

    @Test
    @DisplayName("🔴 已发数 +1 影响 0 行：抛业务异常，交给事务回滚")
    void 闸门没抢到就抛() {
        when(lotteryIssueDao.selectDbNow()).thenReturn(LocalDateTime.now());
        when(lotteryIssueDao.increaseSoldCount(7L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> persist());
    }

    @Test
    @DisplayName("已发数 +1 成功：返回号码")
    void 正常落票() {
        when(lotteryIssueDao.selectDbNow()).thenReturn(LocalDateTime.now());
        when(lotteryIssueDao.increaseSoldCount(7L)).thenReturn(1);

        assertEquals("123456", persist().ticketNumber());
    }

    private TicketObtainDTO persist() {
        LotteryConfig config = new LotteryConfig();
        config.setLotteryCode("ZZLOT");
        LotteryIssue issue = new LotteryIssue();
        issue.setId(7L);
        issue.setIssueNo("2026001");
        return service.persist(config, issue, 940001L, "zz-test", 5L, "123456", "sign");
    }
}
