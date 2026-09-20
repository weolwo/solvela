package solvela.member.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.MemberGrowth;
import solvela.member.MemberGrowthLog;
import solvela.member.MemberGrade;
import solvela.member.grade.GradeChangeType;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGrowthLogDao;
import solvela.member.grade.dao.MemberGradeDao;
import solvela.member.grade.dao.MemberGradeLogDao;
import solvela.member.grade.domain.dto.MemberGrowthDTO;
import solvela.member.grade.domain.dto.MemberGrowthLogDTO;
import solvela.member.grade.domain.dto.MemberGradeLogDTO;
import solvela.member.grade.domain.query.MemberGrowthLogQuery;
import solvela.member.grade.domain.query.MemberGrowthQuery;
import solvela.member.grade.domain.query.MemberGradeLogQuery;
import solvela.member.service.MemberService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员等级的管理端能力：查现状、查流水、查留痕、人工调级。
 *
 * <h3>🔴 它在 member 域，不在 admin 模块</h3>
 * 管理端只是<b>第一个</b>调用方。客服工单系统、将来的会员 360 视图都要问同样的问题，
 * 而「这个会员现在什么等级、还差多少升级」的答案必须只有一份。
 * 把它写在 admin 的 Controller 里，第二个调用方出现时就会出现第二份算法。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGradeAdminService {

    private final MemberGrowthDao memberGrowthDao;
    private final MemberGrowthLogDao memberGrowthLogDao;
    private final MemberGradeLogDao memberGradeLogDao;
    private final MemberGradeDao memberGradeDao;
    private final MemberGrowthService memberGrowthService;
    private final MemberGradeChangeService memberGradeChangeService;
    private final MemberService memberService;

    // ==================== 查询 ====================

    /** 会员成长值分页。等级名、距下一级差多少都在这里拼 */
    public PageResult<MemberGrowthDTO> queryGrowthPage(MemberGrowthQuery query) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(query);
        List<MemberGrowthDTO> list = memberGrowthDao.queryPage(page, query);
        enrich(list);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 单个会员的成长值现状。
     *
     * <p>⚠️ 从没拿到过成长值的会员<b>没有行</b>，这里返回 {@code null}，
     * 由调用端显示「尚无成长值记录」。<b>不要在这里顺手建一行</b> ——
     * 「查一下」不该产生写入，否则运营翻一遍列表就凭空多出几千行。
     */
    public MemberGrowthDTO getGrowth(Long memberId) {
        MemberGrowthQuery query = new MemberGrowthQuery();
        query.setMemberId(memberId);
        query.setPageNum(1L);
        query.setPageSize(1L);
        List<MemberGrowthDTO> list = memberGrowthDao.queryPage(SolvelaPageUtil.convert2PageQuery(query), query);
        if (list.isEmpty()) {
            return null;
        }
        enrich(list);
        return list.get(0);
    }

    /** 成长值流水分页。{@code memberId} 必填 —— 全表扫这张表没有业务场景 */
    public PageResult<MemberGrowthLogDTO> queryGrowthLogPage(MemberGrowthLogQuery query) {
        if (query.getMemberId() == null) {
            throw new BusinessException("请先选定会员再查成长值流水");
        }
        LambdaQueryWrapper<MemberGrowthLog> wrapper = new LambdaQueryWrapper<MemberGrowthLog>()
                .eq(MemberGrowthLog::getMemberId, query.getMemberId())
                .eq(StringUtils.isNotBlank(query.getSource()), MemberGrowthLog::getSource, query.getSource())
                .eq(StringUtils.isNotBlank(query.getBizType()), MemberGrowthLog::getBizType, query.getBizType())
                .eq(StringUtils.isNotBlank(query.getPeriodTag()), MemberGrowthLog::getPeriodTag, query.getPeriodTag())
                .ge(query.getCreateTimeBegin() != null, MemberGrowthLog::getCreateTime,
                        query.getCreateTimeBegin() == null ? null : query.getCreateTimeBegin().atStartOfDay())
                // 🔴 「< 次日零点」而不是「<= 当天」：LocalDate 绑进 datetime 列是 00:00:00，
                //    用 <= 的话「查今天」结果恒为 0，且不报错
                .lt(query.getCreateTimeEnd() != null, MemberGrowthLog::getCreateTime,
                        query.getCreateTimeEnd() == null ? null : query.getCreateTimeEnd().plusDays(1).atStartOfDay())
                .orderByDesc(MemberGrowthLog::getId);

        Page<MemberGrowthLog> page = memberGrowthLogDao.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<MemberGrowthLogDTO> list = page.getRecords().stream()
                .map(row -> SolvelaBeanUtil.copy(row, MemberGrowthLogDTO.class))
                .toList();
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /** 等级变更留痕分页。会员可以不填 —— 「最近谁被人工调级了」是真实的审计场景 */
    public PageResult<MemberGradeLogDTO> queryGradeLogPage(MemberGradeLogQuery query) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(query);
        List<MemberGradeLogDTO> list = memberGradeLogDao.queryPage(page, query);
        Map<Integer, String> names = gradeNames();
        for (MemberGradeLogDTO row : list) {
            row.setOldGradeName(names.get(row.getOldGrade()));
            row.setNewGradeName(names.get(row.getNewGrade()));
        }
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    // ==================== 人工调级 ====================

    /**
     * 人工把一个会员调到指定等级。
     *
     * <h3>🔴 它<b>不改成长值</b>，所以调完等级和 f(成长值) 会对不上</h3>
     * 这不是 bug，是这个功能的定义：人工调级是一次<b>补偿</b>（活动出了问题、
     * 客诉安抚），不是「送他这些成长值」。所以：
     * <ul>
     *   <li>往上调：期末结算会按他<b>真实的</b>成长值重判，把他放回该在的档 ——
     *       也就是说人工升级默认<b>只在本周期有效</b>；</li>
     *   <li>往下调：入账那条路只升不降，所以他会停在这里，直到自己攒够再升上去。</li>
     * </ul>
     * 想让它永久生效，得配套调成长值 —— 那是另一个功能（且要有额度和审批），
     * 这一版不做。运营需要的话，先把它当"本周期补偿"用。
     *
     * <h3>原因必填</h3>
     * 人工改数据而没有理由，半年后就是一条谁也解释不了的记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void adjustGrade(Long memberId, Integer newGrade, String reason, String operator) {
        if (memberId == null || newGrade == null) {
            throw new BusinessException("会员与目标等级都不能为空");
        }
        if (StringUtils.isBlank(reason)) {
            throw new BusinessException("人工调级必须填写原因");
        }
        // 会员号指向一个不存在的人，是最该在入口就拦住的那类静默错误
        memberService.requireExists(memberId);

        MemberGrade target = memberGradeDao.selectOne(new LambdaQueryWrapper<MemberGrade>()
                .eq(MemberGrade::getGradeCode, newGrade)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException("等级 " + newGrade + " 不存在");
        }
        if (target.getStatus() != EnableStatusEnum.ENABLED) {
            // 调到一个停用的档，等于把人放到一个用户端显示不出来的等级上
            throw new BusinessException("等级「" + target.getGradeName() + "」已停用，不能调到这一档");
        }

        /*
         * 🔴 用 loadOrInit 而不是 selectById：从没拿过成长值的会员没有行，
         *    而"给一个新会员补个等级"恰恰是客服最常见的诉求。
         *    建行的同时周期从此刻起算 —— 与第一次入账的处理完全一致。
         */
        MemberGrowth growth = memberGrowthService.loadOrInit(memberId);
        int from = growth.getCurrentGrade() == null ? 0 : growth.getCurrentGrade();
        if (from == newGrade) {
            throw new BusinessException("该会员已经是「" + target.getGradeName() + "」，无需调整");
        }

        boolean changed = memberGradeChangeService.change(memberId, from, newGrade,
                GradeChangeType.MANUAL, nullToZero(growth.getCurrentPeriodValue()), reason, operator);
        if (!changed) {
            // 影响 0 行 = 页面上看到的等级已经不是库里的了，硬改会把别人的变更覆盖掉
            throw new BusinessException("该会员的等级刚刚被改动过，请刷新后重试");
        }
        log.info("【会员等级】人工调级 {} → {}，memberId={}, 原因={}, 操作人={}",
                from, newGrade, memberId, reason, operator);
    }

    // ==================== 内部 ====================

    /**
     * 把等级名、下一级、还差多少补进 DTO。
     *
     * <p>🔴「下一级」按<b>成长值</b>算，不按当前 grade 算。人工调级之后两者会不一致，
     * 那时运营想知道的是「他真实攒到了哪一步」，不是「他挂着的牌子的下一档」。
     */
    private void enrich(List<MemberGrowthDTO> list) {
        if (list.isEmpty()) {
            return;
        }
        List<MemberGrade> grades = enabledGradesAsc();
        Map<Integer, String> names = grades.stream().collect(Collectors.toMap(
                MemberGrade::getGradeCode, MemberGrade::getGradeName, (a, b) -> a));
        LocalDateTime now = memberGrowthDao.selectDbNow();

        for (MemberGrowthDTO row : list) {
            row.setGradeName(names.get(row.getCurrentGrade()));
            row.setInProtect(row.getProtectUntil() != null && row.getProtectUntil().isAfter(now));

            long value = nullToZero(row.getCurrentPeriodValue());
            grades.stream()
                    .filter(grade -> grade.getThreshold() != null && grade.getThreshold() > value)
                    .findFirst()
                    .ifPresent(next -> {
                        row.setNextGrade(next.getGradeCode());
                        row.setNextGradeName(next.getGradeName());
                        row.setNextGap(next.getThreshold() - value);
                    });
        }
    }

    /** 等级号 → 等级名。<b>含停用的档</b>：留痕里的历史等级可能已经被停用，也得显示出名字 */
    private Map<Integer, String> gradeNames() {
        return memberGradeDao.selectList(new LambdaQueryWrapper<MemberGrade>())
                .stream()
                .filter(grade -> grade.getGradeCode() != null)
                .collect(Collectors.toMap(MemberGrade::getGradeCode, MemberGrade::getGradeName, (a, b) -> a));
    }

    private List<MemberGrade> enabledGradesAsc() {
        return memberGradeDao.selectList(new LambdaQueryWrapper<MemberGrade>()
                .eq(MemberGrade::getStatus, EnableStatusEnum.ENABLED)
                .orderByAsc(MemberGrade::getThreshold));
    }

    private static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
