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
import solvela.member.MemberGrade;
import solvela.member.MemberGrowth;
import solvela.member.MemberPeriodSummary;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberPeriodSummaryDao;
import solvela.member.grade.service.MemberGradeChangeService;
import solvela.member.grade.service.MemberGradeResolver;
import solvela.member.grade.service.MemberGradeSettleService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 期末结算 —— <b>降级唯一会发生的地方</b>。
 *
 * <h3>🔴 这里每一条错了都很贵</h3>
 * 降级是这套系统里最能生投诉的动作，而它一年只对一个人发生一次 ——
 * 也就是说<b>线上跑错了，要等一年才有第二次机会验证修复</b>。
 * 所以状态机的四条分支必须在这里全部钉死。
 *
 * @Date 2026-09-20
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGradeSettleServiceTest {

    private static final Long MEMBER_ID = 1001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 9, 20, 3, 0);
    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 9, 18, 10, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2027, 9, 18, 10, 0);

    @Mock
    private MemberGrowthDao memberGrowthDao;
    @Mock
    private MemberPeriodSummaryDao memberPeriodSummaryDao;
    @Mock
    private MemberGradeResolver memberGradeResolver;
    @Mock
    private MemberGradeChangeService memberGradeChangeService;

    private MemberGradeSettleService service;

    /** 0 / 1000 / 5000 / 20000 / 60000 —— 与种子一致 */
    private static List<MemberGrade> ladder() {
        return List.of(grade(0, "普通会员", 0L), grade(1, "银卡会员", 1000L),
                grade(2, "金卡会员", 5000L), grade(3, "白金会员", 20000L),
                grade(4, "钻石会员", 60000L));
    }

    private static MemberGrade grade(int code, String name, long threshold) {
        MemberGrade g = new MemberGrade();
        g.setGradeCode(code);
        g.setGradeName(name);
        g.setThreshold(threshold);
        return g;
    }

    private static MemberGrowth growth(int currentGrade, long value, Integer protectGrade,
                                       LocalDateTime protectUntil) {
        MemberGrowth g = new MemberGrowth();
        g.setMemberId(MEMBER_ID);
        g.setCurrentGrade(currentGrade);
        g.setCurrentPeriodValue(value);
        g.setTotalValue(value);
        g.setPeriodStart(PERIOD_START);
        g.setPeriodEnd(PERIOD_END);
        g.setProtectGrade(protectGrade);
        g.setProtectUntil(protectUntil);
        return g;
    }

    @BeforeEach
    void setUp() {
        service = new MemberGradeSettleService(memberGrowthDao, memberPeriodSummaryDao,
                memberGradeResolver, memberGradeChangeService, new GrowthProperties());
        when(memberGradeResolver.enabledGrades()).thenReturn(ladder());
        when(memberGradeResolver.gradeOf(anyLong(), any())).thenAnswer(inv -> {
            long v = inv.getArgument(0);
            int matched = 0;
            for (MemberGrade g : ladder()) {
                if (v >= g.getThreshold()) {
                    matched = g.getGradeCode();
                }
            }
            return matched;
        });
        when(memberGrowthDao.closePeriod(anyLong(), any(), any(), any())).thenReturn(1);
        when(memberGrowthDao.startProtect(anyLong(), any(), any(), anyInt())).thenReturn(1);
    }

    private MemberPeriodSummary capturedSummary() {
        ArgumentCaptor<MemberPeriodSummary> captor = ArgumentCaptor.forClass(MemberPeriodSummary.class);
        verify(memberPeriodSummaryDao).insert(captor.capture());
        return captor.getValue();
    }

    // ==================== 周期到点 ====================

    @Test
    @DisplayName("攒够了：保持原级，结清并推进周期，【不】写等级留痕")
    void 达标保持() {
        // 金卡门槛 5000，他攒了 8000
        String result = service.settle(growth(2, 8000L, null, null), NOW);

        assertEquals(GradeSettleResult.KEEP, result);
        verify(memberGrowthDao).closePeriod(eq(MEMBER_ID), eq(PERIOD_END), eq(NOW), any());
        // 留痕回答「等级为什么变了」，这里什么都没变；结局由周期快照回答
        verify(memberGradeChangeService, never())
                .change(anyLong(), anyInt(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("🔴 没攒够【不直接降级】，先给缓冲期 —— 且此时不结清、不清零、不推进周期")
    void 不达标先进缓冲() {
        /*
         * 直接降级的话，用户是在「已经掉了」之后才知道；
         * 给缓冲期 + 双倍成长值，他是在「还够得着」的时候知道的。
         * 后者才可能把人拉回来 —— 这正是做等级体系要换的东西。
         */
        String result = service.settle(growth(2, 3000L, null, null), NOW);

        assertEquals(GradeSettleResult.PROTECT_START, result);
        verify(memberGrowthDao).startProtect(eq(MEMBER_ID), eq(PERIOD_END),
                eq(NOW.plusMonths(3)), eq(2));
        // 这个周期还没有结局
        verify(memberPeriodSummaryDao, never()).insert(any(MemberPeriodSummary.class));
        verify(memberGrowthDao, never()).closePeriod(anyLong(), any(), any(), any());
        verify(memberGradeChangeService, never())
                .change(anyLong(), anyInt(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("0 级的人永远进不了缓冲期 —— 没有比 0 更低的档")
    void 零级不进缓冲() {
        String result = service.settle(growth(0, 0L, null, null), NOW);

        assertEquals(GradeSettleResult.KEEP, result);
        verify(memberGrowthDao, never()).startProtect(anyLong(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("期末才发现该升级：补升并留痕（正常走不到，运营调低门槛才会）")
    void 期末补升() {
        String result = service.settle(growth(1, 8000L, null, null), NOW);

        assertEquals(GradeSettleResult.UPGRADE, result);
        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(1), eq(2),
                eq(GradeChangeType.UPGRADE), eq(8000L), anyString(), any());
    }

    // ==================== 缓冲期满 ====================

    @Test
    @DisplayName("🔴 缓冲期内补够了：等级保住，而且【要】写留痕 —— 那是用户该看见的好消息")
    void 保级成功() {
        MemberGrowth g = growth(3, 21000L, 3, NOW.minusDays(1));

        String result = service.settle(g, NOW);

        assertEquals(GradeSettleResult.PROTECT_KEPT, result);
        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(3), eq(3),
                eq(GradeChangeType.KEEP), eq(21000L), anyString(), any());
        assertEquals(3, capturedSummary().getSettledGrade());
    }

    @Test
    @DisplayName("缓冲期满还是不够：按成长值降级，留痕类型是 DOWNGRADE")
    void 保级失败降级() {
        // 白金门槛 20000，缓冲期结束只有 6000 → 掉到金卡(2)
        MemberGrowth g = growth(3, 6000L, 3, NOW.minusDays(1));

        String result = service.settle(g, NOW);

        assertEquals(GradeSettleResult.PROTECT_FAILED, result);
        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(3), eq(2),
                eq(GradeChangeType.DOWNGRADE), eq(6000L), anyString(), any());
        assertEquals(2, capturedSummary().getSettledGrade());
        assertEquals(3, capturedSummary().getGradeBefore());
    }

    @Test
    @DisplayName("降级按【纯映射】，不是「只降一级」—— 规则是纯的，客服只有一句话要解释")
    void 一次掉多级() {
        // 钻石(4) 一年只攒了 900 → 直接掉到 0 级，不是掉到 3
        MemberGrowth g = growth(4, 900L, 4, NOW.minusDays(1));

        service.settle(g, NOW);

        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(4), eq(0),
                eq(GradeChangeType.DOWNGRADE), eq(900L), anyString(), any());
    }

    // ==================== 快照 ====================

    @Test
    @DisplayName("🔴 快照记的是【清零前】的成长值，且门槛是【当时】的快照")
    void 快照内容() {
        service.settle(growth(2, 8000L, null, null), NOW);

        MemberPeriodSummary s = capturedSummary();
        assertEquals(8000L, s.getFinalGrowthValue(), "清零前的那个数，这张表的全部理由");
        assertEquals("20260918", s.getPeriodNo(), "必须与 growth_log.period_tag 同口径，对账靠它 JOIN");
        assertEquals(PERIOD_START, s.getPeriodStart());
        assertEquals(PERIOD_END, s.getPeriodEnd());
        assertEquals(3, s.getNextGrade(), "8000 的下一档是白金(3)");
        // 运营改过门槛之后拿今天的配置回算会得出另一个答案，而客诉问的是「我当时差多少」
        assertEquals(20000L, s.getNextThreshold());
        assertEquals(NOW, s.getSettledAt());
    }

    @Test
    @DisplayName("已是最高档：下一档与门槛都留空，不要编一个出来")
    void 最高档没有下一档() {
        service.settle(growth(4, 99999L, null, null), NOW);

        MemberPeriodSummary s = capturedSummary();
        assertNull(s.getNextGrade());
        assertNull(s.getNextThreshold());
    }

    // ==================== 并发 ====================

    @Test
    @DisplayName("进缓冲时被并发抢先：返回 null 跳过，不写任何东西")
    void 并发进缓冲跳过() {
        when(memberGrowthDao.startProtect(anyLong(), any(), any(), anyInt())).thenReturn(0);

        assertNull(service.settle(growth(2, 3000L, null, null), NOW));
        verify(memberPeriodSummaryDao, never()).insert(any(MemberPeriodSummary.class));
    }

    @Test
    @DisplayName("🔴 结清时被并发抢先：整笔抛出去回滚，不能留下「快照写了、周期没推进」")
    void 并发结清抛异常() {
        /*
         * 留下半截状态的后果：下一轮再结算一次，撞 uk(member_id, period_no) 之后
         * 这个人就彻底卡住了 —— 每轮都被捞出来、每轮都失败。
         */
        when(memberGrowthDao.closePeriod(anyLong(), any(), any(), any())).thenReturn(0);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.settle(growth(2, 8000L, null, null), NOW));
        assertTrue(e.getMessage().contains("并发结算"), e.getMessage());
    }
}
