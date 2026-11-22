package solvela.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.biz.server.BizApplication;
import solvela.enums.ExternalOrderStatusEnum;
import solvela.external.dao.ExternalOrderDao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部单状态机在真库上走一遍：落单 → 条件 UPDATE 迁移 → 读回实体。
 *
 * <p>{@code ExternalOrderStatusSqlTest} 只渲染 SQL、不连库；这里验的是它渲染不到的两件事：
 * <ul>
 *   <li>状态值引用枚举的 {@code ${...}} 在 Spring 装配出来的 MyBatis-Plus 配置下也能求值，并且真的命中行；</li>
 *   <li>{@link ExternalOrder#getStatus()} 没有 setter 之后，读库时状态仍被装配进来（装配不上时是静默的 null）。</li>
 * </ul>
 *
 * <p>放在 app-biz：它是外部单唯一的运行进程。数据由用例自己造，类上的 {@code @Transactional} 让它在用例结束时回滚。
 */
@SpringBootTest(classes = BizApplication.class)
@Transactional
class ExternalOrderStatusFlowTest {

    @Autowired
    private ExternalOrderDao externalOrderDao;

    @Test
    @DisplayName("🔴 待支付 → 待充值 → 充值中 → 成功，每一步读回的状态都对，成功的单进打点反查")
    void 支付后充值成功() {
        String orderNo = insert(LocalDateTime.now().plusMinutes(15));
        assertEquals(ExternalOrderStatusEnum.UNPAID, statusOf(orderNo), "落单后读回的状态不对，或没装配上");

        assertEquals(1, externalOrderDao.markPaid(orderNo), "待支付 → 待充值 的闸门没命中");
        assertEquals(1, externalOrderDao.markExecuting(orderNo));
        assertEquals(0, externalOrderDao.markExecuting(orderNo), "同一单不该被抢到两次执行权");
        assertEquals(ExternalOrderStatusEnum.EXECUTING, statusOf(orderNo));

        assertEquals(1, externalOrderDao.markSuccess(orderNo, "REF-1"));
        ExternalOrder done = externalOrderDao.getByOrderNo(orderNo);
        assertAll(
                () -> assertEquals(ExternalOrderStatusEnum.SUCCESS, done.getStatus()),
                () -> assertEquals("REF-1", done.getExternalRefNo()),
                () -> assertNotNull(done.getFinishTime()));

        LocalDateTime at = done.getFinishTime();
        assertTrue(externalOrderDao.selectSucceededBetween(at.minusMinutes(1), at.plusMinutes(1), 1000).stream()
                .anyMatch(o -> orderNo.equals(o.getOrderNo())), "充值成功的单不在打点反查里");
    }

    @Test
    @DisplayName("充值中 → 失败")
    void 充值失败() {
        String orderNo = insert(LocalDateTime.now().plusMinutes(15));
        externalOrderDao.markPaid(orderNo);
        externalOrderDao.markExecuting(orderNo);
        assertEquals(1, externalOrderDao.markFailed(orderNo, "测试失败"));
        assertEquals(ExternalOrderStatusEnum.FAILED, statusOf(orderNo));
    }

    @Test
    @DisplayName("超时扫描捞得到，取消后支付闸门抢不到")
    void 待支付单超时取消() {
        String orderNo = insert(LocalDateTime.now().minusMinutes(1));
        assertTrue(externalOrderDao.selectExpiredUnpaid(LocalDateTime.now(), 1000).stream()
                .anyMatch(o -> orderNo.equals(o.getOrderNo())), "超时扫描没捞到这一单");

        assertEquals(1, externalOrderDao.markCancelled(orderNo, "测试超时"));
        assertEquals(0, externalOrderDao.markPaid(orderNo), "已取消的单不该再被支付");
        assertEquals(ExternalOrderStatusEnum.CANCELLED, statusOf(orderNo));
    }

    /* ---------------- 工具 ---------------- */

    private ExternalOrderStatusEnum statusOf(String orderNo) {
        return externalOrderDao.getByOrderNo(orderNo).getStatus();
    }

    private String insert(LocalDateTime expireTime) {
        String orderNo = "T" + System.nanoTime();
        ExternalOrder order = new ExternalOrder();
        order.setOrderNo(orderNo);
        order.setMemberId(900001L);
        order.setSceneCode("MOBILE_RECHARGE");
        order.setTargetAccount("13800000000");
        order.setTargetMasked("138****0000");
        order.setOriginalAmount(new BigDecimal("50.00"));
        order.setPayAmount(new BigDecimal("50.00"));
        order.awaitPayment(expireTime);
        externalOrderDao.insert(order);
        return orderNo;
    }
}
