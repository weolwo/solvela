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
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.MemberGrowth;
import solvela.member.MemberGrade;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGrowthLogDao;
import solvela.member.grade.dao.MemberGradeDao;
import solvela.member.grade.dao.MemberGradeLogDao;
import solvela.member.grade.service.MemberGrowthService;
import solvela.member.grade.service.MemberGradeAdminService;
import solvela.member.grade.service.MemberGradeChangeService;
import solvela.member.service.MemberService;

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
 * 人工调级。
 *
 * <h3>🔴 这是整个等级体系里唯一一条「人能直接改结果」的路</h3>
 * 所以它的每一道校验都不是形式：原因必填是给审计的，
 * 停用档不能调是防止把人放到用户端显示不出来的等级上，
 * 并发判据是防止把别人刚做的变更悄悄覆盖掉。
 *
 * @Date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGradeAdjustTest {

    private static final Long MEMBER_ID = 1001L;

    @Mock
    private MemberGrowthDao memberGrowthDao;
    @Mock
    private MemberGrowthLogDao memberGrowthLogDao;
    @Mock
    private MemberGradeLogDao memberGradeLogDao;
    @Mock
    private MemberGradeDao memberGradeDao;
    @Mock
    private MemberGrowthService memberGrowthService;
    @Mock
    private MemberGradeChangeService memberGradeChangeService;
    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberGradeAdminService service;

    @BeforeEach
    void setUp() {
        givenTargetLevel(2, "金卡会员", EnableStatusEnum.ENABLED);
        givenCurrentGrade(0, 300L);
        when(memberGradeChangeService.change(anyLong(), anyInt(), anyInt(),
                anyString(), anyLong(), any(), any())).thenReturn(true);
    }

    private void givenTargetLevel(int level, String name, EnableStatusEnum status) {
        MemberGrade row = new MemberGrade();
        row.setId(3L);
        row.setGradeCode(level);
        row.setGradeName(name);
        row.setThreshold(5000L);
        row.setStatus(status);
        when(memberGradeDao.selectOne(any())).thenReturn(row);
    }

    private void givenCurrentGrade(int level, long periodValue) {
        MemberGrowth growth = new MemberGrowth();
        growth.setMemberId(MEMBER_ID);
        growth.setCurrentGrade(level);
        growth.setCurrentPeriodValue(periodValue);
        when(memberGrowthService.loadOrInit(MEMBER_ID)).thenReturn(growth);
    }

    @Test
    @DisplayName("🔴 原因必填 —— 人工改数据而没有理由，半年后就是一条谁也解释不了的记录")
    void 原因必填() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.adjustGrade(MEMBER_ID, 2, "   ", "huke"));
        assertTrue(e.getMessage().contains("原因"), e.getMessage());

        verify(memberGradeChangeService, never())
                .change(anyLong(), anyInt(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("🔴 会员不存在要在入口就拦住，不能等到写库时才发现")
    void 会员必须存在() {
        org.mockito.Mockito.doThrow(new BusinessException("会员不存在"))
                .when(memberService).requireExists(MEMBER_ID);

        assertThrows(BusinessException.class,
                () -> service.adjustGrade(MEMBER_ID, 2, "活动补偿", "huke"));

        verify(memberGradeChangeService, never())
                .change(anyLong(), anyInt(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("不能调到已停用的档 —— 那个等级在用户端根本显示不出来")
    void 停用档不能调() {
        givenTargetLevel(2, "金卡会员", EnableStatusEnum.DISABLED);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.adjustGrade(MEMBER_ID, 2, "活动补偿", "huke"));
        assertTrue(e.getMessage().contains("已停用"), e.getMessage());
    }

    @Test
    @DisplayName("不存在的等级号直接报错")
    void 等级必须存在() {
        when(memberGradeDao.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.adjustGrade(MEMBER_ID, 9, "活动补偿", "huke"));
    }

    @Test
    @DisplayName("已经是这个等级了：明确报错，不要静悄悄地什么都不做")
    void 等级相同报错() {
        givenCurrentGrade(2, 6000L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.adjustGrade(MEMBER_ID, 2, "活动补偿", "huke"));
        assertTrue(e.getMessage().contains("无需调整"), e.getMessage());
    }

    @Test
    @DisplayName("🔴 并发撞车要变成一句运营看得懂的话，不能当成功返回")
    void 并发撞车报错() {
        /*
         * change 返回 false = 页面上看到的等级已经不是库里的了。
         * 这里如果静默成功，运营会以为调过了，而实际等级是另一个值 ——
         * 且留痕里什么都没有。
         */
        when(memberGradeChangeService.change(anyLong(), anyInt(), anyInt(),
                anyString(), anyLong(), any(), any())).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.adjustGrade(MEMBER_ID, 2, "活动补偿", "huke"));
        assertTrue(e.getMessage().contains("刷新"), e.getMessage());
    }

    @Test
    @DisplayName("正常调级：类型是 MANUAL，成长值快照与操作人都带上")
    void 正常调级() {
        service.adjustGrade(MEMBER_ID, 2, "活动故障补偿", "huke");

        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(0), eq(2),
                eq(GradeChangeType.MANUAL), eq(300L), eq("活动故障补偿"), eq("huke"));
    }

    @Test
    @DisplayName("🔴 从没入账过的会员也能调 —— loadOrInit 会按需建行，客服最常见的诉求就是这个")
    void 没有成长值行也能调() {
        service.adjustGrade(MEMBER_ID, 2, "新人补偿", "huke");

        verify(memberGrowthService).loadOrInit(MEMBER_ID);
        // 不是 selectById：那样会对一个从没参与过的会员返回 null，然后报「查不到」
        verify(memberGrowthDao, never()).selectById(anyLong());
        verify(memberGradeChangeService).change(eq(MEMBER_ID), eq(0), eq(2),
                eq(GradeChangeType.MANUAL), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("成长值流水必须指定会员 —— 全表翻这张表没有业务场景")
    void 流水必须带会员() {
        solvela.member.grade.domain.query.MemberGrowthLogQuery query =
                new solvela.member.grade.domain.query.MemberGrowthLogQuery();

        assertThrows(BusinessException.class, () -> service.queryGrowthLogPage(query));
        verify(memberGrowthLogDao, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("人工调级不碰成长值：只改等级，不给他补那些成长值")
    void 不改成长值() {
        service.adjustGrade(MEMBER_ID, 2, "活动补偿", "huke");

        // 期末结算照样要按他真实的成长值重判 —— 所以人工升级默认只在本周期有效
        verify(memberGrowthDao, never()).addValue(anyLong(), anyLong());
        verify(memberGrowthLogDao, never()).insert(any(solvela.member.MemberGrowthLog.class));
        verify(memberGradeLogDao, never()).insert(any(solvela.member.MemberGradeLog.class));
        // 留痕由 MemberGradeChangeService 统一写，不在这里各写一遍
        verify(memberGradeChangeService).change(anyLong(), anyInt(), anyInt(),
                anyString(), anyLong(), anyString(), anyString());
    }
}
