package solvela.mall.commodity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.member.MemberGrade;
import solvela.member.grade.service.MemberGradeResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 「这个人享受几折」。
 *
 * <h3>🔴 这里守的是<b>夹取值</b>，而每一条错法的表现都是「钱」</h3>
 * {@code points_discount} 是运营手填的一列 tinyint，而算价那边
 * （{@link GradeDiscount} 的注释）明确写了「取值已经夹过，不再兜底」——
 * 也就是说这个类漏夹一种情况，那个值就会<b>直接乘进价格里</b>。
 *
 * <ul>
 *   <li>{@code 0} 漏夹 → 这一档全场白送；</li>
 *   <li>{@code 120} 漏夹 → 这一档比别人贵两成，而页面上写着「会员优惠」；</li>
 *   <li>{@code null} 当 0 → 同第一条。</li>
 * </ul>
 * 三种都不抛异常、不留日志（除了这里主动打的那条 WARN）。
 *
 * @Date 2026-09-23
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallGradeDiscountResolverTest {

    @Mock
    private MallGradeGate mallGradeGate;

    @Mock
    private MemberGradeResolver memberGradeResolver;

    @InjectMocks
    private MallGradeDiscountResolver resolver;

    private static MemberGrade grade(int code, Integer discount) {
        MemberGrade g = new MemberGrade();
        g.setGradeCode(code);
        g.setGradeName("等级" + code);
        g.setPointsDiscount(discount);
        return g;
    }

    private static final List<MemberGrade> GRADES = List.of(
            grade(0, 100), grade(1, 98), grade(3, 90));

    @Test
    @DisplayName("按等级取到对应的折扣率，并把等级号一起带上")
    void 取到折扣() {
        GradeDiscount d = resolver.of(3, GRADES);
        assertEquals(90, d.percent());
        assertEquals(3, d.gradeCode(), "等级号要带上 —— 订单快照用它，算价不用");
    }

    @Test
    @DisplayName("🔴 折扣率为 0：不打折 + 告警，绝不当成白送")
    void 零不是白送() {
        /*
         * 0 的字面意思是这一档全场免费。它几乎一定是想填 100 少按了个键，
         * 而后果不可逆 —— 分已经扣了、货已经发了。表单那层拦了，
         * 这里拦的是【直接改库】那条路，而那条路没有任何别的守卫。
         */
        assertFalse(resolver.of(3, List.of(grade(3, 0))).applies());
    }

    @Test
    @DisplayName("🔴 折扣率大于 100：不打折 —— 加价卖不是这套机制该有的能力")
    void 超过一百不加价() {
        assertEquals(100, resolver.of(3, List.of(grade(3, 120))).percent());
    }

    @Test
    @DisplayName("折扣率为空 = 这一档没配 = 不打折")
    void 空是不打折() {
        assertFalse(resolver.of(3, List.of(grade(3, null))).applies());
    }

    @Test
    @DisplayName("🔴 等级 0 与未登录都不打折")
    void 零级不打折() {
        assertEquals(GradeDiscount.NONE, resolver.of(0, GRADES));
        assertEquals(GradeDiscount.NONE, resolver.of(-1, GRADES));
    }

    @Test
    @DisplayName("⚠️ 配置里找不到这一档：不打折，但等级号仍然是 0 不是编造的")
    void 找不到这一档() {
        /*
         * 运营停用了等级 2 之后，挂在 2 上的人查不到折扣率。
         * 这时返回 NONE（不打折），不是猜一个邻近档的折扣 ——
         * 猜的话「停用一档」会变成「这一档的人悄悄换了个折扣」。
         */
        assertEquals(GradeDiscount.NONE, resolver.of(2, GRADES));
    }

    @Test
    @DisplayName("forMember：等级与折扣配置各查一次，不在商品上循环")
    void 按会员取() {
        when(mallGradeGate.gradeOf(any())).thenReturn(3);
        when(memberGradeResolver.enabledGrades()).thenReturn(GRADES);

        assertEquals(90, resolver.forMember(100L).percent());
    }
}
