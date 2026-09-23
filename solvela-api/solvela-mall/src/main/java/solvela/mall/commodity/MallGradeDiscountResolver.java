package solvela.mall.commodity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.member.MemberGrade;
import solvela.member.grade.service.MemberGradeResolver;

import java.util.List;

/**
 * 「这个人享受几折」—— 把等级配置翻译成 {@link GradeDiscount}。
 *
 * <h3>🔴 折扣率的取值在这里夹一次，且只在这里夹</h3>
 * {@code points_discount} 是运营手填的一列 tinyint。夹的是三件事：
 * <ul>
 *   <li>{@code null} → 不打折。「没配」不是「白送」</li>
 *   <li>{@code <= 0} → 不打折 + <b>打 WARN</b>。0 的字面意思是白送全场，
 *       而它几乎一定是笔误（想填 100 少按了个键）。表单那一层已经拦了，
 *       这里是拦「绕过表单直接改库」那条路</li>
 *   <li>{@code > 100} → 不打折。加价卖不是这套机制该有的能力</li>
 * </ul>
 * 夹在这一处的理由和 {@link MallPricing} 一样：{@link GradeDiscount} 的
 * 注释里写了「取值已经夹过」，算价那边才敢不再兜底。
 *
 * @author alaric
 * @date 2026-09-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MallGradeDiscountResolver {

    /** 不打折 */
    private static final int FULL = 100;

    private final MallGradeGate mallGradeGate;
    private final MemberGradeResolver memberGradeResolver;

    /**
     * 这个人的折扣。<b>会查等级，一次请求只该调一次。</b>
     *
     * <p>⚠️ 商品列表页<b>不要</b>每件商品调一遍 —— 答案在同一次请求里不会变，
     * 而一页 20 件就是 40 次查询。列表页的用法见 {@code MallClientFacade.GradeLens}：
     * 请求级算一次，之后走 {@link #of(int, List)}。
     */
    public GradeDiscount forMember(Long memberId) {
        return of(mallGradeGate.gradeOf(memberId), memberGradeResolver.enabledGrades());
    }

    /**
     * 等级与等级配置都已经在手上时用这个，不再查库。
     *
     * <p>⚠️ {@code grades} 里找不到这一档时返回 {@link GradeDiscount#NONE} ——
     * 运营把某一档停用之后，挂在那一档上的人不再享受折扣，但<b>仍然按他的等级号落订单</b>。
     * 不是按 0 级算：他确实还在那一档，订单快照要记的是事实。
     */
    public GradeDiscount of(int gradeCode, List<MemberGrade> grades) {
        if (gradeCode <= 0 || grades == null) {
            return GradeDiscount.NONE;
        }
        for (MemberGrade grade : grades) {
            if (grade.getGradeCode() != null && grade.getGradeCode() == gradeCode) {
                return new GradeDiscount(gradeCode, clamp(grade));
            }
        }
        return GradeDiscount.NONE;
    }

    private int clamp(MemberGrade grade) {
        Integer percent = grade.getPointsDiscount();
        if (percent == null || percent == FULL) {
            return FULL;
        }
        if (percent <= 0 || percent > FULL) {
            log.warn("【商城等级价】等级 {}（{}）的积分折扣率是 {}，超出 1-100，按不打折处理。"
                            + "0 的字面意思是这一档全场白送 —— 这一行多半是直接改库改错的",
                    grade.getGradeCode(), grade.getGradeName(), percent);
            return FULL;
        }
        return percent;
    }
}
