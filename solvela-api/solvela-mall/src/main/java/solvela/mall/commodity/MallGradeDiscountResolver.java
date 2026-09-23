package solvela.mall.commodity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.mall.MallGradePrice;
import solvela.mall.commodity.dao.MallGradePriceDao;
import solvela.member.MemberGrade;
import solvela.member.grade.service.MemberGradeResolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「这个人在这几件商品上是什么价」—— 把等级配置 + 单品覆盖价翻译成 {@link GradeDiscount}。
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
    private final MallGradePriceDao mallGradePriceDao;

    /**
     * 这个人在<b>这几件商品</b>上的等级价。<b>会查等级，一次请求只该调一次。</b>
     *
     * <p>⚠️ 商品列表页<b>不要</b>每件商品调一遍 —— 等级和折扣率在同一次请求里不会变，
     * 而一页 20 件就是 40 次查询。列表页的用法见 {@code MallClientFacade.GradeLens}：
     * 请求级算一次，之后走 {@link #of(int, List, Collection)}。
     *
     * <p>🔴 {@code commodityIds} 决定<b>要不要查覆盖价、查哪些</b>。
     * 不传（空集合）就一行都不查 —— 覆盖价是按商品配的，全表拉回来在商品多了以后
     * 就是一次没有上限的查询，而它<b>不会报错，只会慢</b>。
     */
    public GradeDiscount forMember(Long memberId, Collection<Long> commodityIds) {
        return of(mallGradeGate.gradeOf(memberId), memberGradeResolver.enabledGrades(), commodityIds);
    }

    /**
     * 等级与等级配置都已经在手上时用这个，不再查等级。
     *
     * <p>⚠️ {@code grades} 里找不到这一档时<b>仍然要查覆盖价</b>，只是不打折 ——
     * 运营把某一档停用之后，挂在那一档上的人不再享受普惠折扣，
     * 但给他们单独配的特价是另一回事，不该跟着一起失效。
     *
     * <p>等级号照样带上：他确实还在那一档，订单快照要记的是事实。
     */
    public GradeDiscount of(int gradeCode, List<MemberGrade> grades, Collection<Long> commodityIds) {
        if (gradeCode <= 0) {
            return GradeDiscount.NONE;
        }
        int percent = FULL;
        if (grades != null) {
            for (MemberGrade grade : grades) {
                if (grade.getGradeCode() != null && grade.getGradeCode() == gradeCode) {
                    percent = clamp(grade);
                    break;
                }
            }
        }
        Map<GradeDiscount.Key, Integer> overrides = loadOverrides(gradeCode, commodityIds);
        if (percent == FULL && overrides.isEmpty()) {
            return GradeDiscount.NONE;
        }
        return new GradeDiscount(gradeCode, percent, overrides);
    }

    /**
     * 这一档在这几件商品上的覆盖价。
     *
     * <p>⚠️ 一次 {@code IN} 查完，不在商品上循环 —— 循环的写法不报错，
     * 只是列表页每多一件商品就多一次查询，而那种慢到被发现时通常已经在生产上了。
     */
    private Map<GradeDiscount.Key, Integer> loadOverrides(int gradeCode, Collection<Long> commodityIds) {
        if (commodityIds == null || commodityIds.isEmpty()) {
            return Map.of();
        }
        List<MallGradePrice> rows = mallGradePriceDao.selectList(
                new LambdaQueryWrapper<MallGradePrice>()
                        .eq(MallGradePrice::getGradeCode, gradeCode)
                        .in(MallGradePrice::getCommodityId, commodityIds));
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<GradeDiscount.Key, Integer> map = new HashMap<>(rows.size() * 2);
        for (MallGradePrice row : rows) {
            if (row.getCommodityId() == null || row.getPointsPrice() == null) {
                continue;
            }
            long skuId = row.getSkuId() == null ? 0L : row.getSkuId();
            map.put(new GradeDiscount.Key(row.getCommodityId(), skuId), row.getPointsPrice());
        }
        return map;
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
