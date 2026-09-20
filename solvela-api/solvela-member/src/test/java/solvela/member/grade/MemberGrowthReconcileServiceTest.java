package solvela.member.grade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.domain.dto.GrowthCheckRow;
import solvela.member.grade.service.MemberGrowthReconcileService;
import solvela.member.grade.service.MemberGrowthReconcileService.ReconcileResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成长值对账。
 *
 * <h3>🔴 这个 job 存在的意义，全在「它别自己制造问题」上</h3>
 * 它扫的是全体会员、改的是判级依据。三件事必须钉死：
 * <ol>
 *   <li><b>默认不改</b> —— 自动修会让引起漂移的 bug 被静默抹平，没人知道它存在；</li>
 *   <li><b>改的时候要带并发判据</b> —— 从读到写之间用户可能又入账了，
 *       强写会把那笔覆盖掉，<b>对账任务自己造出一笔差异，而且往少了写</b>；</li>
 *   <li><b>游标按扫到的推，不按异常行推</b> —— 一批全对也得往前走，否则原地打转。</li>
 * </ol>
 *
 * @Date 2026-09-21
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGrowthReconcileServiceTest {

    @Mock
    private MemberGrowthDao memberGrowthDao;

    @InjectMocks
    private MemberGrowthReconcileService service;

    private static GrowthCheckRow row(long memberId, long recorded, long logSum) {
        GrowthCheckRow r = new GrowthCheckRow();
        r.setMemberId(memberId);
        r.setPeriodTag("20260918");
        r.setRecorded(recorded);
        r.setLogSum(logSum);
        return r;
    }

    private void givenBatch(List<GrowthCheckRow> rows) {
        // 第一批给 rows，之后给空 —— 单批返回数小于 batchSize 时服务自己会停
        when(memberGrowthDao.selectForReconcile(any(), anyLong(), anyInt())).thenReturn(rows, List.of());
    }

    @BeforeEach
    void setUp() {
        when(memberGrowthDao.countOrphanGrowthLog()).thenReturn(0L);
        when(memberGrowthDao.correctPeriodValue(anyLong(), anyLong(), anyLong())).thenReturn(1);
    }

    @Test
    @DisplayName("全对：报扫了几个人，一笔异常都没有")
    void 全部对得上() {
        givenBatch(List.of(row(1L, 100L, 100L), row(2L, 0L, 0L)));

        ReconcileResult r = service.reconcile(LocalDateTime.now(), 10, 5, false, null);

        assertEquals(2, r.getScanned());
        assertEquals(0, r.getDrifted());
    }

    @Test
    @DisplayName("🔴 一个人都没扫到也要如实报 —— 「一切正常」和「压根没扫」不能长得一样")
    void 空扫也要报数() {
        givenBatch(List.of());

        ReconcileResult r = service.reconcile(LocalDateTime.now(), 10, 5, false, null);

        // 游标写错、时间窗写错、库连错，症状都是这个。scanned=0 才是该报警的那种
        assertEquals(0, r.getScanned());
        assertTrue(r.summary().contains("扫描 0 人"), r.summary());
    }

    @Test
    @DisplayName("🔴 默认只报不改：发现对不上，一行都不许写")
    void 默认不自动修() {
        givenBatch(List.of(row(1L, 300L, 100L)));

        ReconcileResult r = service.reconcile(LocalDateTime.now(), 10, 5, false, null);

        assertEquals(1, r.getDrifted());
        assertEquals(0, r.getFixed());
        // 自动修会让引起漂移的那个 bug 每半小时被悄悄抹平一次
        verify(memberGrowthDao, never()).correctPeriodValue(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("autoFix 打开：把主表校正成流水求和（流水是真相）")
    void 显式校正() {
        givenBatch(List.of(row(1L, 300L, 100L)));

        ReconcileResult r = service.reconcile(LocalDateTime.now(), 10, 5, true, null);

        assertEquals(1, r.getFixed());
        // 往流水那个数改，不是往主表那个数改
        verify(memberGrowthDao).correctPeriodValue(eq(1L), eq(300L), eq(100L));
    }

    @Test
    @DisplayName("🔴 校正时值已被并发改动：跳过，绝不强写")
    void 并发改动时跳过() {
        /*
         * 影响 0 行 = 从读到写之间这个人又入账了，手里的 logSum 已经过期。
         * 强写会把那笔新入账覆盖掉 —— 对账任务自己造出一笔差异，而且是往少了写，
         * 用户凭空掉成长值。这比不对账更糟。
         */
        givenBatch(List.of(row(1L, 300L, 100L)));
        when(memberGrowthDao.correctPeriodValue(anyLong(), anyLong(), anyLong())).thenReturn(0);

        ReconcileResult r = service.reconcile(LocalDateTime.now(), 10, 5, true, null);

        assertEquals(1, r.getDrifted());
        assertEquals(0, r.getFixed());
        assertEquals(1, r.getSkipped());
    }

    @Test
    @DisplayName("🔴 孤儿流水单独报：有流水没主表行 = 有人绕过累加器写库，比值不对严重得多")
    void 孤儿流水单独报() {
        // 对账连比都没法比 —— 主表那一侧压根不存在
        when(memberGrowthDao.countOrphanGrowthLog()).thenReturn(7L);
        givenBatch(List.of(row(1L, 100L, 100L)));

        ReconcileResult r = service.reconcile(LocalDateTime.now(), 10, 5, false, null);

        assertEquals(7L, r.getOrphanLogMembers());
        assertTrue(r.summary().contains("孤儿流水会员 7"), r.summary());
    }

    @Test
    @DisplayName("🔴 游标按【扫到的】最大 id 推，不按异常行 —— 一批全对也得往前走")
    void 游标按扫到的推() {
        /*
         * 按异常行推游标的话，一批里没有异常就拿不到新游标，
         * 下一批还从同一个位置开始 —— 扫描原地打转，永远到不了后面的人。
         */
        when(memberGrowthDao.selectForReconcile(any(), eq(0L), anyInt()))
                .thenReturn(List.of(row(10L, 1L, 1L), row(20L, 1L, 1L)));
        when(memberGrowthDao.selectForReconcile(any(), eq(20L), anyInt()))
                .thenReturn(List.of(row(30L, 500L, 100L)));

        ReconcileResult r = service.reconcile(LocalDateTime.now(), 2, 5, false, null);

        assertEquals(3, r.getScanned(), "第二批必须从 20 之后接着扫");
        assertEquals(1, r.getDrifted());
    }

    @Test
    @DisplayName("lookbackHours=0 走全量：changedSince 传 null")
    void 全量扫() {
        givenBatch(List.of(row(1L, 1L, 1L)));

        service.reconcile(null, 10, 5, false, null);

        verify(memberGrowthDao).selectForReconcile(eq(null), eq(0L), eq(10));
    }
}
