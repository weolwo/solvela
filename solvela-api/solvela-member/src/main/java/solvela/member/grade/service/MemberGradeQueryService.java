package solvela.member.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.enums.EnableStatusEnum;
import solvela.member.GradePrivilege;
import solvela.member.MemberGrade;
import solvela.member.MemberGrowth;
import solvela.member.MemberGrowthLog;
import solvela.member.api.GradeGrowthLogView;
import solvela.member.api.GradeLadderView;
import solvela.member.api.GradePrivilegeView;
import solvela.member.api.MemberGradeView;
import solvela.member.grade.GrowthProperties;
import solvela.member.grade.dao.GradePrivilegeDao;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGrowthLogDao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * C 端等级页的读服务。
 *
 * <h3>🔴 没参与过的会员要有一个体面的默认态，不能返回 null</h3>
 * 库里 4000+ 会员大多数没有 {@code t_member_growth} 行 —— 那不是异常，
 * 是「他还没开始」。返回 null 会让等级页白屏；这里给一个 0 级、0 成长值、
 * 阶梯照常展示的视图，用户看到的是「我在普通会员，离银卡还差 1000」——
 * 那正是这个页面该对新人说的话。
 *
 * <h3>⚠️ 这里<b>只读不写</b></h3>
 * 尤其不要顺手 {@code loadOrInit}：「打开一次等级页」不该产生写入，
 * 否则 4000 个会员点一遍，库里就凭空多出 4000 行，
 * 而「这个人参与过没有」也就再也看不出来了。
 *
 * @author alaric
 * @date 2026-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGradeQueryService {

    /** 成长值明细的条数上限。C 端传什么数都不该把库拖垮 */
    private static final int MAX_LOG_LIMIT = 50;

    private final MemberGrowthDao memberGrowthDao;
    private final MemberGrowthLogDao memberGrowthLogDao;
    private final MemberGradeResolver memberGradeResolver;
    private final GradePrivilegeDao gradePrivilegeDao;
    private final GrowthProperties growthProperties;

    public MemberGradeView myGrade(Long memberId) {
        List<MemberGrade> grades = memberGradeResolver.enabledGrades();
        MemberGrowth growth = memberId == null ? null : memberGrowthDao.selectById(memberId);

        long value = growth == null ? 0L : nullToZero(growth.getCurrentPeriodValue());
        long total = growth == null ? 0L : nullToZero(growth.getTotalValue());
        int gradeCode = growth == null || growth.getCurrentGrade() == null ? 0 : growth.getCurrentGrade();

        LocalDateTime now = memberGrowthDao.selectDbNow();
        boolean inProtect = growth != null && growth.getProtectUntil() != null
                && growth.getProtectUntil().isAfter(now);

        MemberGrade next = grades.stream()
                .filter(g -> g.getThreshold() != null && g.getThreshold() > value)
                .findFirst()
                .orElse(null);

        /*
         * 🔴 缓冲期里「还差多少」问的是另一个数：不是「离下一档」，是「离保住」。
         *    这两个在缓冲期内是反的 —— 他要够的是自己脚下那一档，不是上面那一档。
         *    把这两个混成一个字段，页面上就会出现「差 19800 升白金」，
         *    而他真正需要知道的是「差 19800 保住白金」。措辞完全不同。
         */
        Long protectGap = null;
        if (inProtect) {
            long needed = thresholdOf(grades, growth.getProtectGrade());
            protectGap = Math.max(0L, needed - value);
        }

        Map<Integer, List<GradePrivilegeView>> privileges = privilegesByGrade();
        int currentCode = gradeCode;
        List<GradeLadderView> ladder = grades.stream()
                .map(g -> new GradeLadderView(
                        g.getGradeCode(),
                        g.getGradeName(),
                        g.getThreshold(),
                        g.getThreshold() != null && value >= g.getThreshold(),
                        // ⚠️ current 与 reached 不是一回事：缓冲期内他【在】白金，但成长值【够不着】
                        g.getGradeCode() != null && g.getGradeCode() == currentCode,
                        privileges.getOrDefault(g.getGradeCode(), List.of())))
                .toList();

        return new MemberGradeView(
                gradeCode,
                nameOf(grades, gradeCode),
                growth == null ? null : growth.getGradeSince(),
                value,
                total,
                growth == null ? null : growth.getPeriodEnd(),
                next == null ? null : next.getGradeCode(),
                next == null ? null : next.getGradeName(),
                next == null ? null : next.getThreshold(),
                next == null ? null : next.getThreshold() - value,
                inProtect,
                inProtect ? growth.getProtectUntil() : null,
                inProtect ? growth.getProtectGrade() : null,
                protectGap,
                inProtect ? growthProperties.getProtectMultiplier() : 1,
                ladder);
    }

    /**
     * 成长值明细。
     *
     * <p>⚠️ 不分页，只给最近 N 条。等级页是个「看一眼」的场景，
     * 真要翻历史是另一个入口的事 —— 现在把分页做进来，
     * 换来的是一个没人翻到第二页的分页器。
     */
    public List<GradeGrowthLogView> myGrowthLog(Long memberId, int limit) {
        if (memberId == null) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, MAX_LOG_LIMIT);
        return memberGrowthLogDao.selectList(new LambdaQueryWrapper<MemberGrowthLog>()
                        .eq(MemberGrowthLog::getMemberId, memberId)
                        .orderByDesc(MemberGrowthLog::getId)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(row -> new GradeGrowthLogView(row.getDelta(), row.getBaseValue(),
                        row.getMultiplier(), row.getRemark(), row.getCreateTime()))
                .toList();
    }

    // ==================== 内部 ====================

    private Map<Integer, List<GradePrivilegeView>> privilegesByGrade() {
        return gradePrivilegeDao.selectList(new LambdaQueryWrapper<GradePrivilege>()
                        .eq(GradePrivilege::getStatus, EnableStatusEnum.ENABLED)
                        .orderByDesc(GradePrivilege::getSort))
                .stream()
                .filter(p -> p.getGradeCode() != null)
                .collect(Collectors.groupingBy(GradePrivilege::getGradeCode,
                        Collectors.mapping(p -> new GradePrivilegeView(
                                p.getPrivilegeCode(), p.getPrivilegeName(),
                                p.getDescription(), p.getIconFileId(), p.getActionUrl()),
                                Collectors.toList())));
    }

    private static String nameOf(List<MemberGrade> grades, int gradeCode) {
        return grades.stream()
                .filter(g -> g.getGradeCode() != null && g.getGradeCode() == gradeCode)
                .map(MemberGrade::getGradeName)
                .findFirst()
                // 配置被停用过的档也可能挂着人，别让页面上出现空白
                .orElse("等级 " + gradeCode);
    }

    private static long thresholdOf(List<MemberGrade> grades, Integer gradeCode) {
        if (gradeCode == null) {
            return 0L;
        }
        return grades.stream()
                .filter(g -> gradeCode.equals(g.getGradeCode()) && g.getThreshold() != null)
                .mapToLong(MemberGrade::getThreshold)
                .findFirst()
                .orElse(0L);
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
