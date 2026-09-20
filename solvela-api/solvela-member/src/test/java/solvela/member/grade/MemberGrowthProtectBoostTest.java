package solvela.member.grade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.event.BizActionCodes;
import solvela.member.MemberGrowth;
import solvela.member.MemberGrowthLog;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGrowthLogDao;
import solvela.member.grade.service.MemberGradeChangeService;
import solvela.member.grade.service.MemberGradeResolver;
import solvela.member.grade.service.MemberGrowthService;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 保级缓冲期的加速窗口。
 *
 * <h3>🔴 倍率只作用在成长值上，绝不回头多发积分</h3>
 * 积分是负债，翻倍等于凭空多发钱。这里要给的只是一个<b>够得着的目标</b>。
 *
 * <h3>🔴 加速所得只归上一周期 —— 靠「期满清零」实现，不靠 period_tag</h3>
 * 缓冲期是当前周期的<b>延长</b>：周期不推进，累计值不清零，
 * 所以 {@code period_tag} 仍是当前周期。期满结算时一并清零，
 * 于是加速攒的<b>一点都带不进新周期</b>。
 *
 * <p>2026-09-20 订正过一次：阶段 1 写的是「缓冲期 tag 减一个周期」，
 * 那基于「进缓冲就推进周期」的假设，阶段 4 把口径定反了 ——
 * 照旧写法会 tag 到上上个周期，而那期早已结算完，对账时会出现
 * 「有流水找不到对应的周期快照」。
 *
 * @Date 2026-09-20
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGrowthProtectBoostTest {

    private static final Long MEMBER_ID = 1001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 10, 1, 12, 0);
    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 9, 18, 10, 0);

    @Mock
    private MemberGrowthDao memberGrowthDao;
    @Mock
    private MemberGrowthLogDao memberGrowthLogDao;
    @Mock
    private MemberGradeResolver memberGradeResolver;
    @Mock
    private MemberGradeChangeService memberGradeChangeService;

    private MemberGrowthService service;

    @BeforeEach
    void setUp() {
        service = new MemberGrowthService(memberGrowthDao, memberGrowthLogDao,
                memberGradeResolver, new GrowthProperties(), memberGradeChangeService);
        when(memberGrowthDao.addValue(anyLong(), anyLong())).thenReturn(1);
        when(memberGradeResolver.gradeOf(anyLong())).thenReturn(3);
    }

    private void givenMember(LocalDateTime protectUntil) {
        MemberGrowth row = new MemberGrowth();
        row.setMemberId(MEMBER_ID);
        row.setCurrentGrade(3);
        row.setCurrentPeriodValue(100L);
        row.setPeriodStart(PERIOD_START);
        row.setPeriodEnd(LocalDateTime.of(2027, 9, 18, 10, 0));
        row.setProtectUntil(protectUntil);
        row.setProtectGrade(protectUntil == null ? null : 3);
        when(memberGrowthDao.selectById(MEMBER_ID)).thenReturn(row);
        when(memberGrowthDao.selectDbNow()).thenReturn(NOW);
    }

    private MemberGrowthLog capturedLog() {
        ArgumentCaptor<MemberGrowthLog> captor = ArgumentCaptor.forClass(MemberGrowthLog.class);
        verify(memberGrowthLogDao).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("常态：倍率 1，delta 等于基数")
    void 常态不加速() {
        givenMember(null);

        service.accrue(MEMBER_ID, 500L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-1", "发奖");

        MemberGrowthLog row = capturedLog();
        assertEquals(1, row.getMultiplier());
        assertEquals(500L, row.getDelta());
        assertEquals(500L, row.getBaseValue());
    }

    @Test
    @DisplayName("🔴 缓冲期内：倍率 2，基数照实记 —— 客诉要能说清「为什么是 1000 不是 500」")
    void 缓冲期加速() {
        givenMember(NOW.plusDays(30));

        service.accrue(MEMBER_ID, 500L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-2", "发奖");

        MemberGrowthLog row = capturedLog();
        assertEquals(2, row.getMultiplier());
        assertEquals(1000L, row.getDelta(), "翻倍后的值");
        assertEquals(500L, row.getBaseValue(), "倍率之前的基数也要留，否则说不清这 1000 怎么来的");
        // 真正加进去的是翻倍后的值
        verify(memberGrowthDao).addValue(MEMBER_ID, 1000L);
    }

    @Test
    @DisplayName("缓冲期已过：倍率回到 1 —— 判据是 protect_until 与数据库时钟比，不是「有没有这个字段」")
    void 缓冲期已过不加速() {
        givenMember(NOW.minusDays(1));

        service.accrue(MEMBER_ID, 500L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-3", "发奖");

        assertEquals(1, capturedLog().getMultiplier());
    }

    @Test
    @DisplayName("🔴 缓冲期内 period_tag 仍是【当前周期】—— 周期没推进，tag 就不该动")
    void 缓冲期的流水归当前周期() {
        givenMember(NOW.plusDays(30));

        service.accrue(MEMBER_ID, 500L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-4", "发奖");

        // tag 到上上个周期的话，那期早已结算完，对账会出现「流水找不到对应快照」
        assertEquals("20260918", capturedLog().getPeriodTag());
    }

    @Test
    @DisplayName("倍率不设上限：一笔大额入账就能冲回去 —— 够得着才有人去够")
    void 倍率不封顶() {
        givenMember(NOW.plusDays(30));

        service.accrue(MEMBER_ID, 50_000L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-5", "发奖");

        assertEquals(100_000L, capturedLog().getDelta());
        verify(memberGrowthDao).addValue(MEMBER_ID, 100_000L);
        // 套利不是靠封顶堵的，是靠「期满一并清零、带不进新周期」堵的 —— 累加器这一层不该碰周期
        verify(memberGrowthDao, org.mockito.Mockito.never())
                .closePeriod(anyLong(), any(), any(), any());
    }
}
