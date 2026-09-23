package solvela.member.entitlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.util.SolvelaCodeUtil;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.GradeEntitlement;
import solvela.member.GradeEntitlementGrant;
import solvela.member.entitlement.EntitlementGrantStatus;
import solvela.member.entitlement.EntitlementType;
import solvela.member.entitlement.dao.GradeEntitlementDao;
import solvela.member.entitlement.dao.GradeEntitlementGrantDao;

import java.util.List;

/**
 * 权益配置的管理端写入口。
 *
 * <h3>🔴 这里配的东西<b>会真的花钱</b></h3>
 * 与 {@code GradePrivilegeService}（纯展示文案）不是一回事：
 * 在这里加一条「每月给所有白金发一张 20 元券」，下一个 job 周期就会真的发出去。
 * 所以它的权限点是单独的 {@code memberEntitlement:config}，
 * <b>不跟改展示文案共用</b> —— 能改一句文案和能承诺一笔预算，是两种授权。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeEntitlementAdminService {

    private final GradeEntitlementDao gradeEntitlementDao;
    private final GradeEntitlementGrantDao gradeEntitlementGrantDao;

    /** 全部权益配置（含停用），新的在前 */
    public List<GradeEntitlement> listAll() {
        return gradeEntitlementDao.selectList(new LambdaQueryWrapper<GradeEntitlement>()
                .orderByAsc(GradeEntitlement::getMinGrade)
                .orderByDesc(GradeEntitlement::getId));
    }

    /**
     * 新增或修改一条。
     *
     * @param operator 操作人，落审计列
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(GradeEntitlement form, String operator) {
        checkBasics(form);

        if (form.getId() == null) {
            // 编码由服务端生成，前端不传 —— 它是这条配置的身份，让前端填就会撞
            form.setEntitlementCode(SolvelaCodeUtil.generateUniqueBizCode(
                    code -> gradeEntitlementDao.exists(new LambdaQueryWrapper<GradeEntitlement>()
                            .eq(GradeEntitlement::getEntitlementCode, code))));
            form.setCreateBy(operator);
            gradeEntitlementDao.insert(form);
        } else {
            GradeEntitlement existing = gradeEntitlementDao.selectById(form.getId());
            if (existing == null) {
                throw new BusinessException("权益配置不存在，可能已被删除");
            }
            /*
             * 🔴 类型与编码建成后不可改。
             *
             * 改类型 = 改周期键的口径（yyyy ↔ yyyyMM），而周期键是幂等键的一半 ——
             * 已经发过的记录用的是旧口径，改完之后新口径算出来的键与它们不冲突，
             * 于是【同一个周期会再发一次】。想换周期就新建一条，把旧的停用。
             */
            form.setEntitlementType(existing.getEntitlementType());
            form.setEntitlementCode(existing.getEntitlementCode());
            form.setUpdateBy(operator);
            gradeEntitlementDao.updateById(form);
        }
        log.info("【权益配置】已保存：{}（{}），类型={}, 最低等级={}, 发放={} × {}, 操作人={}",
                form.getEntitlementCode(), form.getEntitlementName(), form.getEntitlementType(),
                form.getMinGrade(), form.getAssetName(), form.getQuantity(), operator);
    }

    /**
     * 启用 / 停用。
     *
     * <p>⚠️ 停用<b>不影响已经生成的待领取记录</b> —— 那些是已经承诺给用户的东西，
     * 收回来会让「我明明看见有一张券」变成客诉。停用只是不再生成新的。
     * 真要作废已发的，那是另一个动作，应该单独做并且留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, EnableStatusEnum status, String operator) {
        GradeEntitlement existing = gradeEntitlementDao.selectById(id);
        if (existing == null) {
            throw new BusinessException("权益配置不存在");
        }
        if (status == EnableStatusEnum.DISABLED) {
            long pending = gradeEntitlementGrantDao.selectCount(
                    new LambdaQueryWrapper<GradeEntitlementGrant>()
                            .eq(GradeEntitlementGrant::getEntitlementId, id)
                            .eq(GradeEntitlementGrant::getStatus, EntitlementGrantStatus.PENDING));
            if (pending > 0) {
                // 说清影响面：这些人还能领，停用只是不再生成新的
                log.warn("【权益配置】停用 {}（{}），当前还有 {} 条待领取 —— 它们仍然可以领取，"
                                + "停用只是不再生成新的。操作人={}",
                        existing.getEntitlementCode(), existing.getEntitlementName(), pending, operator);
            }
        }

        GradeEntitlement update = new GradeEntitlement();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateBy(operator);
        gradeEntitlementDao.updateById(update);
        log.info("【权益配置】{} 改为 {}，操作人={}", existing.getEntitlementName(), status.getDesc(), operator);
    }

    private void checkBasics(GradeEntitlement form) {
        if (!EntitlementType.isKnown(form.getEntitlementType())) {
            /*
             * 类型只能是代码认识的那两个。放进一个不认识的值，job 会跳过它并打 ERROR ——
             * 也就是说这条配置【看起来配好了，实际一条都不会发】，而没有人会去看那行日志。
             */
            throw new BusinessException("权益类型只能是 BIRTHDAY（生日礼）或 MONTHLY（月度券）"
                    + " —— 加新周期要写代码，不是在这里填一个新值");
        }
        if (form.getMinGrade() == null || form.getMinGrade() < 0) {
            throw new BusinessException("最低等级不能为空，且不能为负");
        }
        if (form.getAssetName() == null || form.getAssetName().isBlank()) {
            // 券名会直接显示给用户，取不到时不要拿备注顶替（CouponAssetHandler 那段红字记的就是这个事故）
            throw new BusinessException("请填写展示名 —— 它会直接显示给用户");
        }
        if (form.getQuantity() == null || form.getQuantity() <= 0) {
            throw new BusinessException("发放份数必须大于 0");
        }
        if (form.getClaimDays() == null || form.getClaimDays() <= 0) {
            /*
             * 🔴 不允许 0 或负数。0 天意味着生成当天就过期 ——
             * 而 job 里过期是在生成之后跑的，用户会看到一份「刚出现就没了」的权益。
             */
            throw new BusinessException("可领取天数必须大于 0，否则生成当天就会过期");
        }
    }
}
