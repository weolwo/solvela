package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.MallOrderSourceEnum;
import solvela.enums.MallOrderStatusEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.mall.MallOrder;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.order.domain.query.MallOrderQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态机在真库上走一遍：落单 → 条件 UPDATE 迁移 → 读回实体。
 *
 * <p>{@code MallOrderStatusSqlTest} 只渲染 SQL、不连库；这里验的是它渲染不到的两件事：
 * <ul>
 *   <li>状态值引用枚举的 {@code ${...}} 在 Spring 装配出来的 MyBatis-Plus 配置下也能求值，并且真的命中行；</li>
 *   <li>{@link MallOrder#getStatus()} 没有 setter 之后，读库时状态仍被装配进来（装配不上时是静默的 null）。</li>
 * </ul>
 *
 * <p>数据由用例自己造，类上的 {@code @Transactional} 让它在用例结束时回滚，不依赖库里已有的订单。
 */
@SpringBootTest
@Transactional
class MallOrderStatusFlowTest {

    @Autowired
    private MallOrderDao mallOrderDao;

    @Test
    @DisplayName("🔴 纯积分单：待履约 → 履约中 → 已完成，每一步读回的状态都对")
    void 纯积分单走完履约() {
        String orderNo = insert(order -> order.settleOnCreate(LocalDateTime.now()));
        assertEquals(MallOrderStatusEnum.PENDING, statusOf(orderNo), "落单后读回的状态不对，或没装配上");

        assertEquals(1, mallOrderDao.markFulfilling(orderNo), "待履约 → 履约中 的闸门没命中");
        assertEquals(0, mallOrderDao.markFulfilling(orderNo), "同一单不该被抢到两次");
        assertEquals(MallOrderStatusEnum.FULFILLING, statusOf(orderNo));

        assertEquals(1, mallOrderDao.markFinished(orderNo, "777"));
        MallOrder finished = mallOrderDao.getByOrderNo(orderNo);
        assertAll(
                () -> assertEquals(MallOrderStatusEnum.FINISHED, finished.getStatus()),
                () -> assertEquals("777", finished.getFulfillRefId()),
                () -> assertNotNull(finished.getFinishTime()));

        MallOrderQuery query = new MallOrderQuery();
        query.setOrderNo(orderNo);
        assertEquals(1L, mallOrderDao.queryStat(query).getFinishedCount(), "统计 SQL 里的状态引用没对上");
    }

    @Test
    @DisplayName("待支付单：超时扫描捞得到，取消后进不了结清口径，支付闸门也抢不到了")
    void 待支付单超时取消() {
        LocalDateTime expired = LocalDateTime.now().minusMinutes(1);
        String orderNo = insert(order -> order.awaitPayment(expired));
        assertEquals(MallOrderStatusEnum.UNPAID, statusOf(orderNo));

        assertTrue(mallOrderDao.selectExpiredUnpaid(LocalDateTime.now(), 1000).stream()
                .anyMatch(o -> orderNo.equals(o.getOrderNo())), "超时扫描没捞到这一单");

        assertEquals(1, mallOrderDao.markCancelled(orderNo, "测试超时"));
        assertEquals(0, mallOrderDao.markPaid(orderNo), "已取消的单不该再被支付");
        assertEquals(MallOrderStatusEnum.CANCELLED, statusOf(orderNo));
    }

    @Test
    @DisplayName("待支付单支付后进入结清口径")
    void 待支付单支付() {
        String orderNo = insert(order -> order.awaitPayment(LocalDateTime.now().plusMinutes(15)));
        assertEquals(1, mallOrderDao.markPaid(orderNo));
        MallOrder paid = mallOrderDao.getByOrderNo(orderNo);
        assertEquals(MallOrderStatusEnum.PENDING, paid.getStatus());

        LocalDateTime payTime = paid.getPayTime();
        assertTrue(mallOrderDao.selectSettledBetween(payTime.minusMinutes(1), payTime.plusMinutes(1), 1000).stream()
                .anyMatch(o -> orderNo.equals(o.getOrderNo())), "付过款的单不在结清口径里");
    }

    /* ---------------- 工具 ---------------- */

    private MallOrderStatusEnum statusOf(String orderNo) {
        return mallOrderDao.getByOrderNo(orderNo).getStatus();
    }

    private String insert(Consumer<MallOrder> open) {
        String orderNo = "T" + System.nanoTime();
        MallOrder order = new MallOrder();
        order.setOrderNo(orderNo);
        order.setMemberId(900001L);
        order.setCommodityId(1L);
        order.setCommodityCode("TESTCMD001");
        order.setSkuId(1L);
        order.setSkuCode("TESTSKU001");
        order.setCommodityType(PrizeTypeEnum.PHYSICAL);
        order.setCommodityName("状态机验收");
        order.setSkuAttrs("{}");
        order.setQuantity(1);
        order.setPointsPrice(100);
        order.setCashPrice(BigDecimal.ZERO);
        order.setPayPoints(100);
        order.setPayCash(BigDecimal.ZERO);
        order.setSourceType(MallOrderSourceEnum.NORMAL);
        open.accept(order);
        mallOrderDao.insert(order);
        return orderNo;
    }
}
