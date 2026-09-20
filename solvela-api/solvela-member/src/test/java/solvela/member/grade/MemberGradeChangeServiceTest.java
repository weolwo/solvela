package solvela.member.grade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.member.MemberGradeLog;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGradeLogDao;
import solvela.member.grade.service.MemberGradeChangeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 等级变更的唯一写入口。
 *
 * <h3>🔴 这里守的是「留痕和等级必须同生共死」</h3>
 * 两者分开写的话，漏掉留痕这件事<b>不会报错、不影响任何功能</b> ——
 * 只会在半年后某个用户问「我为什么掉级了」时，变成一句「查不到」。
 *
 * @Date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGradeChangeServiceTest {

    private static final Long MEMBER_ID = 1001L;

    @Mock
    private MemberGrowthDao memberGrowthDao;

    @Mock
    private MemberGradeLogDao memberGradeLogDao;

    @InjectMocks
    private MemberGradeChangeService service;

    @Test
    @DisplayName("升级：等级改了，留痕带上变更那一刻的成长值快照")
    void 升级留痕() {
        when(memberGrowthDao.updateGrade(MEMBER_ID, 1, 3)).thenReturn(1);

        boolean changed = service.change(MEMBER_ID, 1, 3,
                GradeChangeType.UPGRADE, 25000L, null, null);

        assertTrue(changed);
        ArgumentCaptor<MemberGradeLog> captor = ArgumentCaptor.forClass(MemberGradeLog.class);
        verify(memberGradeLogDao).insert(captor.capture());
        MemberGradeLog row = captor.getValue();
        assertEquals(1, row.getOldGrade());
        assertEquals(3, row.getNewGrade(), "跨两档也是一条留痕，不是两条");
        assertEquals(GradeChangeType.UPGRADE, row.getChangeType());
        assertEquals(25000L, row.getPeriodValue(), "快照必须是调用方传进来的那个数");
        assertNull(row.getOperator(), "系统变更不记操作人");
    }

    @Test
    @DisplayName("🔴 条件更新影响 0 行 = 等级已被并发改过，不能再补一条留痕")
    void 并发撞车不留痕() {
        /*
         * 两笔入账同时到达、都算出「该升到 V2」。先到的那条已经改成了 V2，
         * 后到的 WHERE level = V1 落空。此时再写一条留痕，
         * 客服看到的就是「他升了两次级」—— 而等级只变过一次。
         */
        when(memberGrowthDao.updateGrade(anyLong(), anyInt(), anyInt())).thenReturn(0);

        boolean changed = service.change(MEMBER_ID, 1, 2,
                GradeChangeType.UPGRADE, 1200L, null, null);

        assertFalse(changed);
        verify(memberGradeLogDao, never()).insert(any(MemberGradeLog.class));
    }

    @Test
    @DisplayName("🔴 等级没变就不该动库：连 UPDATE 都不发")
    void 等级没变不写库() {
        boolean changed = service.change(MEMBER_ID, 2, 2,
                GradeChangeType.MANUAL, 8000L, "重复提交", "huke");

        assertFalse(changed);
        verify(memberGrowthDao, never()).updateGrade(anyLong(), anyInt(), anyInt());
        verify(memberGradeLogDao, never()).insert(any(MemberGradeLog.class));
    }

    @Test
    @DisplayName("保级是唯一「等级没变也要留痕」的情况 —— 用户得看见「你保住了」")
    void 保级照样留痕() {
        when(memberGrowthDao.updateGrade(MEMBER_ID, 3, 3)).thenReturn(1);

        boolean changed = service.change(MEMBER_ID, 3, 3,
                GradeChangeType.KEEP, 19000L, "缓冲期内达标", null);

        assertTrue(changed);
        verify(memberGradeLogDao).insert(any(MemberGradeLog.class));
    }

    @Test
    @DisplayName("人工调级：原因和操作人都要落进留痕")
    void 人工调级留操作人() {
        when(memberGrowthDao.updateGrade(MEMBER_ID, 0, 2)).thenReturn(1);

        service.change(MEMBER_ID, 0, 2, GradeChangeType.MANUAL, 0L, "活动故障补偿", "huke");

        ArgumentCaptor<MemberGradeLog> captor = ArgumentCaptor.forClass(MemberGradeLog.class);
        verify(memberGradeLogDao).insert(captor.capture());
        assertEquals("活动故障补偿", captor.getValue().getReason());
        assertEquals("huke", captor.getValue().getOperator());
        assertEquals(GradeChangeType.MANUAL, captor.getValue().getChangeType());
    }

    @Test
    @DisplayName("🔴 改等级不能顺手改成长值 —— 等级是派生状态，反向写回就成了刷等级的口子")
    void 不碰成长值() {
        when(memberGrowthDao.updateGrade(MEMBER_ID, 0, 4)).thenReturn(1);

        service.change(MEMBER_ID, 0, 4, GradeChangeType.MANUAL, 0L, "补偿", "huke");

        // 人工把某人调到钻石，不代表他真的攒够了那些成长值；
        // 期末结算照样要按他实际的成长值重新判
        verify(memberGrowthDao, never()).addValue(anyLong(), anyLong());
        verify(memberGrowthDao).updateGrade(eq(MEMBER_ID), eq(0), eq(4));
    }
}
