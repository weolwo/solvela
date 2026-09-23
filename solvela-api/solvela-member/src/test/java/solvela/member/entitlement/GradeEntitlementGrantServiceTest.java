package solvela.member.entitlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import solvela.enums.EnableStatusEnum;
import solvela.member.GradeEntitlement;
import solvela.member.GradeEntitlementGrant;
import solvela.member.entitlement.dao.GradeEntitlementDao;
import solvela.member.entitlement.dao.GradeEntitlementGrantDao;
import solvela.member.entitlement.domain.EntitlementCandidate;
import solvela.member.entitlement.service.GradeEntitlementGrantService;
import solvela.member.entitlement.service.GradeEntitlementGrantService.GenerateResult;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生成「待领取」的权益。
 *
 * <h3>这里守的四条，错了都不报错</h3>
 * <ol>
 *   <li><b>幂等靠唯一键</b> —— 撞键算「已给过」，不是失败。job 每天扫全量正是靠它；</li>
 *   <li><b>闰年生日顺延</b> —— 不做的话 2 月 29 出生的人四年里有三年收不到，而且没人会发现；</li>
 *   <li><b>游标按扫到的推</b> —— 按「生成了几条」推的话，一批全是已有记录时扫描原地打转；</li>
 *   <li><b>未知类型只报警不猜</b> —— 猜成月度会让一年一次变成一年十二次。</li>
 * </ol>
 *
 * @Date 2026-09-22
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradeEntitlementGrantServiceTest {

    @Mock
    private GradeEntitlementDao gradeEntitlementDao;

    @Mock
    private GradeEntitlementGrantDao gradeEntitlementGrantDao;

    @InjectMocks
    private GradeEntitlementGrantService service;

    private static GradeEntitlement entitlement(String type, int minGrade) {
        GradeEntitlement e = new GradeEntitlement();
        e.setId(1L);
        e.setEntitlementCode("ENT0000001");
        e.setEntitlementName("白金生日礼");
        e.setEntitlementType(type);
        e.setMinGrade(minGrade);
        e.setAssetType("COUPON");
        e.setAssetRef("CPN001");
        e.setAssetName("生日 20 元券");
        e.setQuantity(1);
        e.setClaimDays(30);
        e.setStatus(EnableStatusEnum.ENABLED);
        return e;
    }

    private static EntitlementCandidate candidate(long memberId, int grade) {
        EntitlementCandidate c = new EntitlementCandidate();
        c.setMemberId(memberId);
        c.setGradeCode(grade);
        return c;
    }

    private void givenCandidates(List<EntitlementCandidate> first) {
        when(gradeEntitlementGrantDao.selectCandidates(anyInt(), any(), any(), anyBoolean(), anyLong(), anyInt()))
                .thenReturn(first, List.of());
    }

    @BeforeEach
    void setUp() {
        when(gradeEntitlementGrantDao.insert(any(GradeEntitlementGrant.class))).thenReturn(1);
    }

    @Test
    @DisplayName("生成待领取：周期键、等级快照、发放单号都要落上")
    void 生成待领取() {
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of(entitlement(EntitlementType.MONTHLY, 3)));
        givenCandidates(List.of(candidate(100L, 4)));

        GenerateResult result = service.generate(LocalDate.of(2026, 9, 22), null);

        assertEquals(1, result.getCreated());
        ArgumentCaptor<GradeEntitlementGrant> saved = ArgumentCaptor.forClass(GradeEntitlementGrant.class);
        verify(gradeEntitlementGrantDao).insert(saved.capture());
        GradeEntitlementGrant g = saved.getValue();

        assertEquals("202609", g.getPeriodKey(), "月度券的周期键是 yyyyMM");
        assertEquals(4, g.getGradeCode(), "等级是生成时的快照，不是配置里的 minGrade");
        assertEquals(EntitlementGrantStatus.PENDING, g.getStatus());
        assertEquals("ENT0000001", g.getEntitlementCode(), "编码要快照，配置改名后历史记录仍是当时那个");
        assertTrue(g.getGrantBizId() != null && !g.getGrantBizId().isBlank(),
                "发放单号必须在生成时就定下来 —— 领取时现生成的话，一次超时重试就换号，资产侧的防重失效");
        assertEquals(LocalDate.of(2026, 10, 22).atStartOfDay(), g.getExpireTime(),
                "claimDays=30，从当天零点起算");
    }

    @Test
    @DisplayName("🔴 撞唯一键算「这个周期已给过」，不是失败 —— job 每天扫全量正是靠它")
    void 撞键算已给过() {
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of(entitlement(EntitlementType.MONTHLY, 0)));
        givenCandidates(List.of(candidate(100L, 0)));
        when(gradeEntitlementGrantDao.insert(any(GradeEntitlementGrant.class)))
                .thenThrow(new DuplicateKeyException("uk_t_gd_ent_grant"));

        GenerateResult result = service.generate(LocalDate.of(2026, 9, 22), null);

        assertEquals(0, result.getCreated());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getScanned(), "扫到了就要算进 scanned，否则「都给过了」和「没扫到人」长得一样");
    }

    @Test
    @DisplayName("🔴 平年的 2 月 28：把 2 月 29 出生的人也算作今天过生日")
    void 闰年生日平年顺延() {
        /*
         * 不做这件事的话，2 月 29 出生的人【四年里有三年收不到生日礼】——
         * 不报错、没日志、没有任何人会发现，直到某个用户来问。
         */
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of(entitlement(EntitlementType.BIRTHDAY, 0)));
        givenCandidates(List.of(candidate(100L, 0)));

        service.generate(LocalDate.of(2026, 2, 28), null);

        verify(gradeEntitlementGrantDao).selectCandidates(
                anyInt(), eq(2), eq(28), eq(true), anyLong(), anyInt());
    }

    @Test
    @DisplayName("闰年的 2 月 28 不顺延 —— 那一年真有 2 月 29，顺延就成了发两次")
    void 闰年当天不顺延() {
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of(entitlement(EntitlementType.BIRTHDAY, 0)));
        givenCandidates(List.of(candidate(100L, 0)));

        // 2028 是闰年
        service.generate(LocalDate.of(2028, 2, 28), null);

        verify(gradeEntitlementGrantDao).selectCandidates(
                anyInt(), eq(2), eq(28), eq(false), anyLong(), anyInt());
    }

    @Test
    @DisplayName("月度券不按生日过滤：生日月/日传 null")
    void 月度券不过滤生日() {
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of(entitlement(EntitlementType.MONTHLY, 2)));
        givenCandidates(List.of(candidate(100L, 2)));

        service.generate(LocalDate.of(2026, 9, 22), null);

        verify(gradeEntitlementGrantDao).selectCandidates(
                eq(2), eq(null), eq(null), eq(false), anyLong(), anyInt());
    }

    @Test
    @DisplayName("🔴 未知类型只报警不猜 —— 猜成月度会让一年一次变成一年十二次")
    void 未知类型不生成() {
        GradeEntitlement bad = entitlement("WEEKLY", 0);
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of(bad));

        GenerateResult result = service.generate(LocalDate.of(2026, 9, 22), null);

        assertEquals(0, result.getCreated());
        assertEquals(0, result.getScanned());
        // 连扫都不该扫 —— 扫了就意味着我们已经替它选了一种周期
        verify(gradeEntitlementGrantDao, never()).selectCandidates(
                anyInt(), any(), any(), anyBoolean(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("🔴 游标按【扫到的】最大会员号推，一批全是已有记录也要往前走")
    void 游标按扫到的推() {
        /*
         * 按「生成了几条」推游标的话，一批人全都已经有记录时就拿不到新游标，
         * 下一批还从同一个位置开始 —— 扫描原地打转，后面的人永远扫不到。
         */
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of(entitlement(EntitlementType.MONTHLY, 0)));
        when(gradeEntitlementGrantDao.insert(any(GradeEntitlementGrant.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        /*
         * ⚠️ 第一批必须是【满的】，否则服务判定「这是最后一批」直接收工，
         *    根本不会有第二次调用 —— 那样这条用例验的就不是游标，而是提前收工。
         *    批大小是服务内部的常量，这里按它构造 500 条。
         */
        List<EntitlementCandidate> fullBatch = new java.util.ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            fullBatch.add(candidate(i, 0));
        }
        when(gradeEntitlementGrantDao.selectCandidates(anyInt(), any(), any(), anyBoolean(), eq(0L), anyInt()))
                .thenReturn(fullBatch);
        when(gradeEntitlementGrantDao.selectCandidates(anyInt(), any(), any(), anyBoolean(), eq(500L), anyInt()))
                .thenReturn(List.of());

        GenerateResult result = service.generate(LocalDate.of(2026, 9, 22), null);

        assertEquals(500, result.getScanned());
        assertEquals(500, result.getSkipped(), "这一批全是已有记录（撞键），一条都没新建");
        // 第二批必须从 500 之后接着要 —— 没有这次调用就说明游标是按「生成了几条」推的，而那是 0
        verify(gradeEntitlementGrantDao).selectCandidates(
                anyInt(), any(), any(), anyBoolean(), eq(500L), anyInt());
    }

    @Test
    @DisplayName("没有启用中的配置：什么都不做，也不报错")
    void 没有配置时空转() {
        when(gradeEntitlementDao.selectList(any())).thenReturn(List.of());

        GenerateResult result = service.generate(LocalDate.of(2026, 9, 22), null);

        assertEquals(0, result.getScanned());
        assertTrue(result.summary().contains("扫描 0 人次"), result.summary());
    }
}
