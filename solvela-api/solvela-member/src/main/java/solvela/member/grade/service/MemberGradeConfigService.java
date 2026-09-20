package solvela.member.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.MemberGrowth;
import solvela.member.MemberGrade;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGradeDao;

import java.util.List;

/**
 * 等级配置的管理端写入口。
 *
 * <h3>为什么等级是配置而不是枚举</h3>
 * 「加一档」是这类体系最常见的运营动作。写成 Java 枚举的话每加一档要发版，
 * 而运营那边加档的节奏是按季度的。
 *
 * <h3>🔴 改配置<b>不会</b>回溯已有会员的等级</h3>
 * 调高门槛之后，已经在高等级上的人<b>不会当场掉下来</b>：
 * <ul>
 *   <li>升级只在成长值入账时判，且{@code MemberGrowthService} 那条路<b>只升不降</b>；</li>
 *   <li>降级只在期末结算里发生（阶段 4）。</li>
 * </ul>
 * 这是刻意的。门槛一改就把一批人当场降级，是会上新闻的那种事故 ——
 * 让它在下一个考核周期末自然生效，运营还有时间反悔。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGradeConfigService {

    /** 新会员的落点。这一档的存在性和门槛都是硬约束 */
    private static final int BASE_GRADE = 0;

    private final MemberGradeDao memberGradeDao;
    private final MemberGrowthDao memberGrowthDao;

    /** 全部等级，按门槛升序。管理端列表与下拉共用 */
    public List<MemberGrade> listAll() {
        return memberGradeDao.selectList(new LambdaQueryWrapper<MemberGrade>()
                .orderByAsc(MemberGrade::getGradeCode));
    }

    /**
     * 新增或修改一档。
     *
     * @param operator 操作人，落审计列
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(MemberGrade form, String operator) {
        checkBasics(form);

        MemberGrade existing = findByGradeCode(form.getGradeCode());
        if (existing != null && !existing.getId().equals(form.getId())) {
            throw new BusinessException("等级 " + form.getGradeCode() + " 已经存在，不能重复添加");
        }
        checkThresholdMonotonic(form);

        if (form.getId() == null) {
            form.setCreateBy(operator);
            memberGradeDao.insert(form);
        } else {
            form.setUpdateBy(operator);
            memberGradeDao.updateById(form);
        }
        log.info("【会员等级】配置已保存：grade={}, name={}, threshold={}, 操作人={}",
                form.getGradeCode(), form.getGradeName(), form.getThreshold(), operator);
    }

    /**
     * 启用 / 停用一档。
     *
     * <p>🔴 {@code grade=0} 不允许停用：它是新会员的落点，也是判级函数的兜底。
     * 停掉之后 {@code MemberGradeResolver} 会对每一次判级打一条告警，
     * 而所有没攒够成长值的人会落进一个「没有名字的等级」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, EnableStatusEnum status, String operator) {
        MemberGrade grade = memberGradeDao.selectById(id);
        if (grade == null) {
            throw new BusinessException("等级配置不存在");
        }
        if (grade.getGradeCode() != null && grade.getGradeCode() == BASE_GRADE
                && status == EnableStatusEnum.DISABLED) {
            throw new BusinessException("最低档（等级 0）是新会员的落点，不能停用");
        }

        if (status == EnableStatusEnum.DISABLED) {
            /*
             * 停用一个中间档不拦，但必须说出影响面：这些人的 grade 列不会变，
             * 他们会一直停在一个「配置里已经没有」的等级上，直到期末结算
             * 按新配置重判 —— 那时才会掉下来，而那已经是一个月以后的事了。
             */
            long affected = memberGrowthDao.selectCount(new LambdaQueryWrapper<MemberGrowth>()
                    .eq(MemberGrowth::getCurrentGrade, grade.getGradeCode()));
            if (affected > 0) {
                log.warn("【会员等级】停用等级 {}（{}），当前有 {} 名会员停在这一档 —— "
                                + "他们会在本周期结束时按新配置重判。操作人={}",
                        grade.getGradeCode(), grade.getGradeName(), affected, operator);
            }
        }

        MemberGrade update = new MemberGrade();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateBy(operator);
        memberGradeDao.updateById(update);
        log.info("【会员等级】配置 {} 改为 {}，操作人={}", grade.getGradeName(), status.getDesc(), operator);
    }

    private void checkBasics(MemberGrade form) {
        if (form.getGradeCode() == null || form.getGradeCode() < 0) {
            throw new BusinessException("等级不能为空，且不能小于 0");
        }
        if (form.getThreshold() == null || form.getThreshold() < 0) {
            throw new BusinessException("成长值门槛不能为空，且不能为负");
        }
        if (form.getGradeCode() == BASE_GRADE && form.getThreshold() != 0L) {
            // 0 档门槛不为 0，等于给新会员设了一道他还没开始就没过的线
            throw new BusinessException("最低档（等级 0）的门槛必须是 0");
        }
    }

    /**
     * 🔴 门槛必须随等级<b>严格递增</b>。
     *
     * <p>{@code MemberGradeResolver.gradeOf} 取的是「门槛不超过成长值的最高那一档」。
     * 一旦出现「等级 3 的门槛比等级 2 低」，一个成长值刚过等级 2 的人会被直接判成等级 3 ——
     * 不报错，不异常，只是<b>所有人都白升了一级</b>。
     *
     * <p>这种配置错误在页面上看不出来（两行数字而已），只能在这里拦住。
     */
    private void checkThresholdMonotonic(MemberGrade form) {
        for (MemberGrade other : listAll()) {
            if (other.getId().equals(form.getId()) || other.getGradeCode() == null || other.getThreshold() == null) {
                continue;
            }
            boolean higherLevelNotHigherThreshold =
                    form.getGradeCode() > other.getGradeCode() && form.getThreshold() <= other.getThreshold();
            boolean lowerLevelNotLowerThreshold =
                    form.getGradeCode() < other.getGradeCode() && form.getThreshold() >= other.getThreshold();
            if (higherLevelNotHigherThreshold || lowerLevelNotLowerThreshold) {
                throw new BusinessException(String.format(
                        "门槛必须随等级递增：等级 %d 的门槛 %d 与等级 %d 的门槛 %d 冲突",
                        form.getGradeCode(), form.getThreshold(), other.getGradeCode(), other.getThreshold()));
            }
        }
    }

    private MemberGrade findByGradeCode(Integer grade) {
        return memberGradeDao.selectOne(new LambdaQueryWrapper<MemberGrade>()
                .eq(MemberGrade::getGradeCode, grade)
                .last("LIMIT 1"));
    }
}
