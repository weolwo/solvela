package solvela.member.grade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import solvela.event.BizActionCodes;
import solvela.member.MemberGrowth;
import solvela.member.MemberGrowthLog;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGrowthLogDao;
import solvela.member.grade.service.MemberGrowthService;
import solvela.member.grade.service.MemberGradeChangeService;
import solvela.member.grade.service.MemberGradeResolver;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 入账之后的即时升级（阶段 2）。
 *
 * <h3>🔴 这里守的两条都会静默出错</h3>
 * ① <b>该升的要当场升</b> —— 升级晚了用户不会报错，只会觉得"这个体系没反应"；
 * ② <b>入账永远不能降级</b> —— 运营调高一次门槛，如果这里照算出来的值改，
 *    下一次入账就会把所有存量用户静默降级。没有人点过「降级」，也不会有任何报错。
 *
 * @Date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGrowthServiceUpgradeTest {

    private static final Long MEMBER_ID = 1001L;

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
        when(memberGrowthDao.selectDbNow()).thenReturn(LocalDateTime.of(2026, 9, 18, 12, 0));
    }

    /** 累加前 / 累加后两次 selectById 的返回值。前者给 loadOrInit，后者给判级 */
    private void givenGrowth(int grade, long valueBefore, long valueAfter) {
        when(memberGrowthDao.selectById(MEMBER_ID))
                .thenReturn(growth(grade, valueBefore), growth(grade, valueAfter));
    }

    private static MemberGrowth growth(int grade, long periodValue) {
        MemberGrowth row = new MemberGrowth();
        row.setMemberId(MEMBER_ID);
        row.setCurrentGrade(grade);
        row.setCurrentPeriodValue(periodValue);
        row.setTotalValue(periodValue);
        row.setPeriodStart(LocalDateTime.of(2026, 1, 1, 0, 0));
        row.setPeriodEnd(LocalDateTime.of(2027, 1, 1, 0, 0));
        return row;
    }

    @Test
    @DisplayName("跨过门槛当场升级，跨几档升几档")
    void 跨门槛当场升级() {
        givenGrowth(0, 900L, 25000L);
        when(memberGradeResolver.gradeOf(25000L)).thenReturn(3);

        assertTrue(service.accrue(MEMBER_ID, 24100L, BizActionCodes.SCORE_EARNED,
                "PROPOSAL_REWARD", "P-1", "发奖"));

        // 0 → 3 是一次变更，不是三次
        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(0), eq(3),
                eq(GradeChangeType.UPGRADE), eq(25000L), isNull(), isNull());
    }

    @Test
    @DisplayName("没跨门槛：一行都不该写")
    void 没跨门槛不动() {
        givenGrowth(1, 1000L, 1100L);
        when(memberGradeResolver.gradeOf(1100L)).thenReturn(1);

        service.accrue(MEMBER_ID, 100L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-2", "发奖");

        verify(memberGradeChangeService, never())
                .change(anyLong(), anyInt(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("🔴 算出来比当前低也【绝不】降级 —— 运营调高门槛不该静默降一批人")
    void 入账永不降级() {
        /*
         * 三种正当来源都会走到这里：运营调高了门槛、人工把人调到了他没攒够的等级、
         * 保级缓冲期内等级取的是 protect_target。
         * 三种情况下「什么都不做」都是对的；照算出来的值改，才是事故。
         */
        givenGrowth(3, 20000L, 20100L);
        when(memberGradeResolver.gradeOf(20100L)).thenReturn(1);

        service.accrue(MEMBER_ID, 100L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-3", "发奖");

        verify(memberGradeChangeService, never())
                .change(anyLong(), anyInt(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("🔴 重复投递：撞唯一键就整笔返回，连判级都不该走到")
    void 重复投递不判级() {
        givenGrowth(0, 100L, 100L);
        doThrowOnInsert();

        assertFalse(service.accrue(MEMBER_ID, 100L, BizActionCodes.SCORE_EARNED,
                "PROPOSAL_REWARD", "P-4", "发奖"));

        // 成长值没加，等级自然也不该动 —— 对账 job 每轮都会重推，这里松一次就是每轮多升一次
        verify(memberGrowthDao, never()).addValue(anyLong(), anyLong());
        verify(memberGradeChangeService, never())
                .change(anyLong(), anyInt(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("判级读的是累加【之后】的库里值，不是入账前的快照加 delta")
    void 判级用重新读的值() {
        /*
         * 并发入账时「快照 + delta」是错的 —— 那正是 addValue 用原子自增的理由。
         * 这里快照是 900，本次加 100，但库里实际已经被另一笔推到了 5000：
         * 按快照算只有 1000（升不到金卡），按库里算是 5000（该升）。
         */
        givenGrowth(1, 900L, 5000L);
        when(memberGradeResolver.gradeOf(5000L)).thenReturn(2);
        when(memberGradeResolver.gradeOf(1000L)).thenReturn(1);

        service.accrue(MEMBER_ID, 100L, BizActionCodes.SCORE_EARNED, "PROPOSAL_REWARD", "P-5", "发奖");

        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(1), eq(2),
                eq(GradeChangeType.UPGRADE), eq(5000L), isNull(), isNull());
    }

    private void doThrowOnInsert() {
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_t_mbr_gr_log_src"))
                .when(memberGrowthLogDao).insert(any(MemberGrowthLog.class));
    }
}
