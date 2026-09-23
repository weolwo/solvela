package solvela.mall.commodity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.mall.MallGradePrice;
import solvela.mall.commodity.dao.MallGradePriceDao;
import solvela.member.MemberGrade;
import solvela.member.grade.service.MemberGradeResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Mock
    private MallGradePriceDao mallGradePriceDao;

    /**
     * ⚠️ {@code LambdaQueryWrapper} 要靠 MyBatis-Plus 的 TableInfo 缓存把方法引用翻成列名，
     * 而那份缓存平时是<b>启动时扫实体</b>填上的 —— 纯 Mockito 用例里没有那一步，
     * 于是 {@code new LambdaQueryWrapper<MallGradePrice>()} 直接抛
     * 「can not find lambda cache for this entity」。
     *
     * <p>这里补上这一步，而不是把生产代码改成写列名字符串的 {@code QueryWrapper}：
     * 写字符串的话，哪天列名改了<b>编译照样过</b>，只是查不出东西来。
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), MallGradePrice.class);
    }

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

    /** 商品 id，覆盖价按它查 */
    private static final List<Long> CMD = List.of(7L);

    private static MallGradePrice override(long commodityId, long skuId, int price) {
        MallGradePrice p = new MallGradePrice();
        p.setCommodityId(commodityId);
        p.setSkuId(skuId);
        p.setGradeCode(3);
        p.setPointsPrice(price);
        return p;
    }

    private void givenOverrides(MallGradePrice... rows) {
        when(mallGradePriceDao.selectList(any())).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("按等级取到对应的折扣率，并把等级号一起带上")
    void 取到折扣() {
        GradeDiscount d = resolver.of(3, GRADES, List.of());
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
        assertFalse(resolver.of(3, List.of(grade(3, 0)), List.of()).applies());
    }

    @Test
    @DisplayName("🔴 折扣率大于 100：不打折 —— 加价卖不是这套机制该有的能力")
    void 超过一百不加价() {
        assertEquals(100, resolver.of(3, List.of(grade(3, 120)), List.of()).percent());
    }

    @Test
    @DisplayName("折扣率为空 = 这一档没配 = 不打折")
    void 空是不打折() {
        assertFalse(resolver.of(3, List.of(grade(3, null)), List.of()).applies());
    }

    @Test
    @DisplayName("🔴 等级 0 与未登录都不打折")
    void 零级不打折() {
        assertEquals(GradeDiscount.NONE, resolver.of(0, GRADES, List.of()));
        assertEquals(GradeDiscount.NONE, resolver.of(-1, GRADES, List.of()));
    }

    @Test
    @DisplayName("⚠️ 配置里找不到这一档：不打折，但等级号仍然是 0 不是编造的")
    void 找不到这一档() {
        /*
         * 运营停用了等级 2 之后，挂在 2 上的人查不到折扣率。
         * 这时返回 NONE（不打折），不是猜一个邻近档的折扣 ——
         * 猜的话「停用一档」会变成「这一档的人悄悄换了个折扣」。
         */
        assertEquals(GradeDiscount.NONE, resolver.of(2, GRADES, List.of()));
    }

    // ------------------------------------------------------------ 单品覆盖价

    @Test
    @DisplayName("覆盖价按 (商品, 规格) 装进 map；sku_id=0 是整个商品那一行")
    void 装配覆盖价() {
        givenOverrides(override(7L, 0L, 888), override(7L, 70L, 666));

        GradeDiscount d = resolver.of(3, GRADES, CMD);

        assertEquals(888, d.overrideOf(7L, 0L));
        assertEquals(666, d.overrideOf(7L, 70L));
        assertEquals(888, d.overrideOf(7L, 71L), "没配规格覆盖价的规格落回商品那一行");
        assertNull(d.overrideOf(8L, 0L), "别的商品没有覆盖价");
    }

    @Test
    @DisplayName("🔴 不传商品 id 就一行都不查 —— 全表拉回来不报错，只会越来越慢")
    void 不传商品不查覆盖价() {
        resolver.of(3, GRADES, List.of());

        verify(mallGradePriceDao, never()).selectList(any());
    }

    @Test
    @DisplayName("🔴 等级停用了仍然查覆盖价 —— 普惠折扣没了，单独配的特价是另一回事")
    void 停用的档仍有覆盖价() {
        /*
         * 等级 2 不在 GRADES 里（被运营停用了）。这时不打折是对的，
         * 但把给他们配的特价一起吞掉不是 —— 那是两次独立的运营决定。
         */
        givenOverrides(override(7L, 0L, 888));

        GradeDiscount d = resolver.of(2, GRADES, CMD);

        assertEquals(100, d.percent(), "停用的档不享受普惠折扣");
        assertEquals(888, d.overrideOf(7L, 0L));
        assertEquals(2, d.gradeCode(), "等级号照样带上：订单快照记的是事实");
    }

    @Test
    @DisplayName("既没折扣也没覆盖价时回 NONE，省掉下游一路判空")
    void 都没有时回NONE() {
        givenOverrides();

        assertEquals(GradeDiscount.NONE, resolver.of(2, GRADES, CMD));
    }

    @Test
    @DisplayName("forMember：等级与折扣配置各查一次，不在商品上循环")
    void 按会员取() {
        when(mallGradeGate.gradeOf(any())).thenReturn(3);
        when(memberGradeResolver.enabledGrades()).thenReturn(GRADES);

        assertEquals(90, resolver.forMember(100L, List.of()).percent());
    }
}
