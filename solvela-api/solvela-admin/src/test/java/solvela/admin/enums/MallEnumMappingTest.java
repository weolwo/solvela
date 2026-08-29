package solvela.admin.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.CouponStatusEnum;
import solvela.enums.EnableStatusEnum;
import solvela.enums.MallCommodityStatusEnum;
import solvela.enums.MallOrderStatusEnum;
import solvela.enums.MallPayTypeEnum;
import solvela.enums.WalletStatusEnum;
import solvela.ledger.MemberCoupon;
import solvela.ledger.MemberWallet;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.wallet.dao.MemberWalletDao;
import solvela.mall.MallCategory;
import solvela.mall.MallCommodity;
import solvela.mall.MallOrder;
import solvela.mall.MallSku;
import solvela.mall.category.dao.MallCategoryDao;
import solvela.mall.commodity.dao.MallCommodityDao;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.sku.dao.MallSkuDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商城 + 券/钱包这一批枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>覆盖 7 列：{@code t_mall_sku.sku_status}、{@code t_mall_category.status}、
 * {@code t_mall_commodity.status}、{@code t_mall_commodity.pay_type}、
 * {@code t_mall_order.status}、{@code t_member_coupon.status}、{@code t_member_wallet.status}。
 *
 * <p>⚠️ <b>{@code t_mall_order} 是零行的</b>（订单模块还没开工），所以
 * {@link MallOrderStatusEnum} 在这里只验得到「按枚举过滤 SQL 不炸」，
 * 验不到任何一行真实数据的装配 —— 这一列目前只有编译期和 DDL 注释两重保障。
 *
 * <p>「分状态计数之和 == 总量」这条断言是这一批的主力：它能抓到
 * 「库里有落在枚举之外的取值」，也能抓到「0/1 方向写反」——
 * 方向反了的话某个状态会一行都查不到，而另一个会多出来，和还是对的，
 * 所以另外单独断言了各状态的数据形状。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class MallEnumMappingTest {

    @Autowired
    private MallSkuDao mallSkuDao;

    @Autowired
    private MallCategoryDao mallCategoryDao;

    @Autowired
    private MallCommodityDao mallCommodityDao;

    @Autowired
    private MallOrderDao mallOrderDao;

    @Autowired
    private MemberCouponDao memberCouponDao;

    @Autowired
    private MemberWalletDao memberWalletDao;

    @Test
    @DisplayName("SKU 状态：全部能装配，且分状态计数之和等于总量")
    void SKU状态() {
        List<MallSku> list = mallSkuDao.selectList(null);
        assertFalse(list.isEmpty(), "t_mall_sku 没有数据，这条用例失去意义");
        for (MallSku e : list) {
            assertNotNull(e.getSkuStatus(), "skuStatus 装配成了 null");
        }
        // 库里以启用为主。这条同时挡住 0/1 写反 —— 反了的话 ENABLED 会一行都查不到
        assertTrue(list.stream().anyMatch(e -> e.getSkuStatus() == EnableStatusEnum.ENABLED),
                "一个启用的 SKU 都没有，多半是 0/1 方向反了");

        assertEquals(list.size(), sumByStatus(EnableStatusEnum.values(),
                        s -> mallSkuDao.selectCount(new LambdaQueryWrapper<MallSku>().eq(MallSku::getSkuStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 sku_status 落在枚举之外");
    }

    @Test
    @DisplayName("分类状态：全部能装配，且分状态计数之和等于总量")
    void 分类状态() {
        List<MallCategory> list = mallCategoryDao.selectList(null);
        assertFalse(list.isEmpty(), "t_mall_category 没有数据，这条用例失去意义");
        for (MallCategory e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == EnableStatusEnum.ENABLED),
                "一个启用的分类都没有，多半是 0/1 方向反了");

        assertEquals(list.size(), sumByStatus(EnableStatusEnum.values(),
                        s -> mallCategoryDao.selectCount(new LambdaQueryWrapper<MallCategory>().eq(MallCategory::getStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 status 落在枚举之外");
    }

    @Test
    @DisplayName("商品状态与支付方式：两列都能装配，计数之和都等于总量")
    void 商品状态与支付方式() {
        List<MallCommodity> list = mallCommodityDao.selectList(null);
        assertFalse(list.isEmpty(), "t_mall_commodity 没有数据，这条用例失去意义");
        for (MallCommodity e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
            assertNotNull(e.getPayType(), "payType 装配成了 null");
        }
        // 商品状态是 0-下架/1-上架/2-草稿，三值不对称，方向写反了这条就红
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == MallCommodityStatusEnum.ON),
                "一个上架商品都没有，多半是取值对错了位");

        assertEquals(list.size(), sumByStatus(MallCommodityStatusEnum.values(),
                        s -> mallCommodityDao.selectCount(new LambdaQueryWrapper<MallCommodity>().eq(MallCommodity::getStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 status 落在枚举之外");

        assertEquals(list.size(), sumByStatus(MallPayTypeEnum.values(),
                        t -> mallCommodityDao.selectCount(new LambdaQueryWrapper<MallCommodity>().eq(MallCommodity::getPayType, t))),
                "分支付方式计数之和与总量对不上，说明有行的 pay_type 落在枚举之外");
    }

    @Test
    @DisplayName("订单状态：零行，只能验到查询链路不炸")
    void 订单状态查询不炸() {
        // t_mall_order 当前零行（订单模块未实现）。断言写成「查得动、且每一行都能装配」，
        // 而不是断言某个具体取值 —— 等有数据了这条才开始有意义，现在不会假绿。
        List<MallOrder> list = mallOrderDao.selectList(null);
        for (MallOrder e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        for (MallOrderStatusEnum status : MallOrderStatusEnum.values()) {
            assertNotNull(mallOrderDao.selectCount(
                            new LambdaQueryWrapper<MallOrder>().eq(MallOrder::getStatus, status)),
                    "按 " + status + " 过滤时 SQL 出错");
        }
    }

    @Test
    @DisplayName("券状态：全部能装配，且分状态计数之和等于总量")
    void 券状态() {
        List<MemberCoupon> list = memberCouponDao.selectList(null);
        for (MemberCoupon e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        Long total = memberCouponDao.selectCount(new LambdaQueryWrapper<>());
        assertNotNull(total);
        assertEquals(total.longValue(), sumByStatus(CouponStatusEnum.values(),
                        s -> memberCouponDao.selectCount(new LambdaQueryWrapper<MemberCoupon>().eq(MemberCoupon::getStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 status 落在枚举之外");
    }

    @Test
    @DisplayName("钱包状态：全部能装配，且 checkAvailable 认的就是库里那个「正常」")
    void 钱包状态() {
        List<MemberWallet> list = memberWalletDao.selectList(null);
        assertFalse(list.isEmpty(), "t_member_wallet 没有数据，这条用例失去意义");
        for (MemberWallet e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        // 钱包冻结是 0、正常是 1，和 t_member.status（冻结=2）不是一套字典。
        // 方向反了的话 checkAvailable() 会把所有正常钱包判成冻结，用户全线扣不了款。
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == WalletStatusEnum.NORMAL),
                "一个正常钱包都没有，多半是 0/1 方向反了");
        list.stream()
                .filter(e -> e.getStatus() == WalletStatusEnum.NORMAL)
                .findFirst()
                .ifPresent(MemberWallet::checkAvailable);

        assertEquals(list.size(), sumByStatus(WalletStatusEnum.values(),
                        s -> memberWalletDao.selectCount(new LambdaQueryWrapper<MemberWallet>().eq(MemberWallet::getStatus, s))),
                "分状态计数之和与总量对不上，说明有行的 status 落在枚举之外");
    }

    private <E> long sumByStatus(E[] values, java.util.function.Function<E, Long> counter) {
        long sum = 0;
        for (E v : values) {
            Long n = counter.apply(v);
            assertNotNull(n, "按 " + v + " 过滤时 SQL 出错");
            sum += n;
        }
        return sum;
    }
}
