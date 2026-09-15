package solvela.biz.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.coupon.CouponTemplate;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.exception.BusinessException;
import solvela.ledger.coupon.template.dao.CouponTemplateDao;
import solvela.ledger.coupon.template.dao.CouponWriteOffDao;
import solvela.ledger.coupon.template.service.CouponTemplateService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券模板的<b>真库</b>验证：建一版、读回来、再建一版、停用。
 *
 * <h3>为什么真库而不是 mock</h3>
 * {@code NotificationMapperBindingTest} 只回答「这条语句存不存在」，
 * <b>不回答 SQL 写得对不对</b> —— 列名拼错、条件写反，那个守卫一样过。
 * 而券模板的 Dao 全是手写 XML（复合主键，MyBatis-Plus 的
 * {@code selectById} 系列对本实体生成不出来），正是最容易拼错列名的那种。
 *
 * <h3>🔴 {@code @Transactional}：本测试必须一行都不留</h3>
 * {@code t_coupon_template} 是<b>种子表</b>，在 {@code data-baseline.sql} 里。
 * 测试写进去的行，下一次 {@code DumpSeedData} 会把它们一起导出去 ——
 * 于是一条测试数据变成了<b>所有新环境的初始数据</b>。
 *
 * <p>所以这里靠 Spring Test 的默认回滚：方法跑完事务回滚，一行不留。
 * 这也是它和 {@code NotificationLiveTest} 的区别 —— 那边写的是
 * {@code t_member_notification}（业务表，不进基线），留着无所谓。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@SpringBootTest
@Transactional
class CouponTemplateLiveTest {

    /** 测试用的券编码。带前缀是为了万一回滚没生效时一眼能认出来 */
    private static final String TEST_CODE = "ZZTESTCOUPON";

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private CouponTemplateDao couponTemplateDao;

    @Autowired
    private CouponWriteOffDao couponWriteOffDao;

    @Test
    @DisplayName("🔴 建一版、读回来、再建一版、停用 —— 每个字段都真的落到了库里")
    void 券模板完整生命周期() {
        // ---------- 建 v1 ----------
        int v1 = couponTemplateService.save(fixedTemplate());
        assertEquals(1, v1, "第一版应当是 v1（库里没有这个编码）");

        CouponTemplate latest = couponTemplateService.getLatestEnabled(TEST_CODE);
        assertNotNull(latest, "刚存进去就查不回来 —— selectLatestEnabled 的 SQL 有问题");

        // 逐字段核对：这是 mapper 守卫覆盖不到的部分（列名拼错它一样过）
        assertEquals(1, latest.getVersion());
        assertEquals("单元测试用券", latest.getCouponName());
        assertEquals(CouponDiscountTypeEnum.FIXED, latest.getDiscountType());
        assertEquals(0, new BigDecimal("20.00").compareTo(latest.getDiscountValue()));
        assertEquals(0, new BigDecimal("100.00").compareTo(latest.getMinAmount()));
        assertEquals(CouponDeductTargetEnum.CASH, latest.getDeductTarget());
        assertEquals(CouponScopeTypeEnum.ALL, latest.getScopeType());
        assertEquals(30, latest.getValidDays());
        assertEquals(1, latest.getStatus());

        // ---------- 建 v2：版本号由服务端算，不是页面传的 ----------
        CouponTemplate next = fixedTemplate();
        next.setCouponName("单元测试用券-改过");
        next.setDiscountValue(new BigDecimal("30.00"));
        assertEquals(2, couponTemplateService.save(next), "第二次保存应当是 v2");

        List<CouponTemplate> versions = couponTemplateService.listVersions(TEST_CODE);
        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getVersion(), "历史版本列表应当新的在前");

        // 🔴 这条是整个不可变设计的意义所在：加了 v2，v1 必须原样还在。
        //    v1 若被改写，用户手里按 v1 发出去的券就贬值了
        CouponTemplate stillV1 = couponTemplateDao.selectByCodeAndVersion(TEST_CODE, 1);
        assertNotNull(stillV1);
        assertEquals("单元测试用券", stillV1.getCouponName());
        assertEquals(0, new BigDecimal("20.00").compareTo(stillV1.getDiscountValue()));

        assertEquals(2, couponTemplateDao.selectMaxVersion(TEST_CODE));
        assertEquals(2, couponTemplateService.getLatestEnabled(TEST_CODE).getVersion(),
                "最新启用版应当跟到 v2");

        // ---------- 停用 v2：v1 不受影响，且不是删除 ----------
        couponTemplateService.disable(TEST_CODE, 2);
        assertEquals(1, couponTemplateService.getLatestEnabled(TEST_CODE).getVersion(),
                "停了 v2，最新启用版应当退回 v1");
        assertEquals(2, couponTemplateService.listVersions(TEST_CODE).size(),
                "停用不是删除 —— 历史版本必须还查得到");
        assertEquals(0, couponTemplateDao.selectByCodeAndVersion(TEST_CODE, 2).getStatus());

        // ---------- 列表页：每个编码只出现一次，且是最新启用版 ----------
        List<CouponTemplate> list = couponTemplateService.listLatest();
        long mine = list.stream().filter(t -> TEST_CODE.equals(t.getCouponCode())).count();
        assertEquals(1, mine, "listLatest 里同一个编码只能出现一次");
        assertFalse(list.isEmpty(), "库里有 5 条种子模板，列表不该是空的");
    }

    @Test
    @DisplayName("🔴 百分比券不封顶 = 一单就能亏穿，所以保存这一步就要拦住")
    void 百分比券必须封顶() {
        CouponTemplate percent = fixedTemplate();
        percent.setDiscountType(CouponDiscountTypeEnum.PERCENT);
        percent.setDiscountValue(new BigDecimal("20"));
        percent.setMaxDiscount(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> couponTemplateService.save(percent));
        assertTrue(ex.getMessage().contains("最高抵扣"));

        // 折扣率 100 是免单，不该用券表达
        percent.setMaxDiscount(new BigDecimal("50"));
        percent.setDiscountValue(new BigDecimal("100"));
        assertThrows(BusinessException.class, () -> couponTemplateService.save(percent));
    }

    @Test
    @DisplayName("有效期两种填法必须二选一 —— 都不填是永不过期的负债，都填则规则含糊")
    void 有效期二选一() {
        CouponTemplate none = fixedTemplate();
        none.setValidDays(null);
        none.setValidEndTime(null);
        assertThrows(BusinessException.class, () -> couponTemplateService.save(none));

        CouponTemplate both = fixedTemplate();
        both.setValidEndTime(LocalDateTime.now().plusDays(10));
        assertThrows(BusinessException.class, () -> couponTemplateService.save(both));
    }

    @Test
    @DisplayName("非全场券必须给范围明细，否则那张券谁也用不了、而且不报错")
    void 限定范围必须给明细() {
        CouponTemplate scoped = fixedTemplate();
        scoped.setScopeType(CouponScopeTypeEnum.CATEGORY);
        scoped.setScopeRefs(null);
        assertThrows(BusinessException.class, () -> couponTemplateService.save(scoped));
    }

    /**
     * 核销流水表还没有任何写入方（那是阶段 3），这里只验它的查询 SQL 跑得通。
     *
     * <p>⚠️ 这些断言是「空结果」，不是「正确结果」—— 真正的核销语义要等
     * 三阶段核销接口做出来之后才谈得上验。
     */
    @Test
    @DisplayName("核销流水的查询 SQL 跑得通（阶段 3 之前还没有写入方）")
    void 核销流水查询可用() {
        assertTrue(couponWriteOffDao.selectByCoupon(-1L).isEmpty());
        assertTrue(couponWriteOffDao.selectByBiz("MALL_ORDER", "NOT_EXIST").isEmpty());
        // 没有任何核销时返回 null，调用方按 0 处理 —— 这正是 Dao 注释里承诺的行为
        assertNull(couponWriteOffDao.sumConfirmedAmount(
                LocalDateTime.now().minusYears(50), LocalDateTime.now().minusYears(49)));
    }

    private static CouponTemplate fixedTemplate() {
        CouponTemplate template = new CouponTemplate();
        template.setCouponCode(TEST_CODE);
        template.setCouponName("单元测试用券");
        template.setDiscountType(CouponDiscountTypeEnum.FIXED);
        template.setDiscountValue(new BigDecimal("20.00"));
        template.setMinAmount(new BigDecimal("100.00"));
        template.setDeductTarget(CouponDeductTargetEnum.CASH);
        template.setScopeType(CouponScopeTypeEnum.ALL);
        template.setValidDays(30);
        template.setCreateBy("unit-test");
        return template;
    }
}
