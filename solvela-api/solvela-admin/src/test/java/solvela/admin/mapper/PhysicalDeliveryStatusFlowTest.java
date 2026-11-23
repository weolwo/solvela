package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.sonicexcel.SolvelaExcelUtil;
import solvela.enums.AssetSourceEnum;
import solvela.enums.DeliveryStatusEnum;
import solvela.exception.BusinessException;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.ledger.logistic.domain.excel.PhysicalDeliveryShipImportRow;
import solvela.ledger.logistic.service.PhysicalDeliveryService;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履约单状态在真库上走一遍：落待发货 → 条件 UPDATE / 回填导入 → 读回实体。
 *
 * <p>{@code PhysicalDeliveryStatusSqlTest} 只渲染 SQL、不连库；这里验的是 {@code ${...}} 在 Spring 装配的
 * MyBatis-Plus 配置下能求值并真的命中行，去掉 setter 之后状态仍被装配进来，
 * 以及回填导入真的按状态机校验：有一行迁移不合法整批退回。数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class PhysicalDeliveryStatusFlowTest {

    @Autowired
    private PhysicalDeliveryDao physicalDeliveryDao;
    @Autowired
    private PhysicalDeliveryService physicalDeliveryService;

    @Test
    @DisplayName("🔴 闸门：待发货 → 已发货 → 已签收；终态之后退回、作废、再发货都影响 0 行")
    void 闸门走一遍() {
        PhysicalDelivery d = insert();
        assertEquals(DeliveryStatusEnum.PENDING, statusOf(d), "落库后读回的状态不对，或没装配上");

        assertEquals(1, physicalDeliveryDao.markShipped(d.getId(), "顺丰", "SF001"));
        assertEquals(0, physicalDeliveryDao.markShipped(d.getId(), "顺丰", "SF002"), "已发货的单又被发了一次货");
        assertEquals(0, physicalDeliveryDao.discardById(d.getId()), "已发货的单被作废了");
        assertEquals(1, physicalDeliveryDao.markSigned(d.getId(), null, null));
        assertEquals(0, physicalDeliveryDao.markReturned(d.getId(), null, null), "已签收的单被改成了退回");

        PhysicalDelivery after = physicalDeliveryDao.selectById(d.getId());
        assertAll(
                () -> assertEquals(DeliveryStatusEnum.SIGNED, after.getStatus()),
                () -> assertEquals("顺丰", after.getLogisticsCompany(), "签收时传 null 不该清掉物流公司"),
                () -> assertEquals("SF001", after.getLogisticsNo()));
    }

    @Test
    @DisplayName("待发货可以直接签收；作废只认待发货")
    void 直接签收与作废() {
        PhysicalDelivery signed = insert();
        assertEquals(1, physicalDeliveryDao.markSigned(signed.getId(), "京东", "JD001"));
        assertEquals(DeliveryStatusEnum.SIGNED, statusOf(signed));

        PhysicalDelivery cancelled = insert();
        assertEquals(1, physicalDeliveryDao.discardById(cancelled.getId()));
        assertEquals(0, physicalDeliveryDao.markShipped(cancelled.getId(), null, null), "已取消的单又被发货了");
        assertEquals(DeliveryStatusEnum.CANCELLED, statusOf(cancelled));
    }

    @Test
    @DisplayName("🔴 回填导入：有一行把已签收改回已发货，整批退回，合法的那行也不落")
    void 回填非法迁移整批退回() {
        PhysicalDelivery signed = insert();
        assertEquals(1, physicalDeliveryDao.markSigned(signed.getId(), null, null));
        PhysicalDelivery pending = insert();

        BusinessException error = assertThrows(BusinessException.class, () -> importShip(
                row(pending, "顺丰", "SF100", DeliveryStatusEnum.DELIVERED),
                row(signed, null, null, DeliveryStatusEnum.DELIVERED)));

        assertTrue(error.getMessage().contains("状态不能从「已签收」改成「已发货」"), error.getMessage());
        assertEquals(DeliveryStatusEnum.PENDING, statusOf(pending), "校验没过，合法的那行也不该落库");
        assertEquals(DeliveryStatusEnum.SIGNED, statusOf(signed));
    }

    @Test
    @DisplayName("回填导入：发货、直接签收、同状态只补单号各走各的路")
    void 回填合法批次() {
        PhysicalDelivery toShip = insert();
        PhysicalDelivery toSign = insert();
        PhysicalDelivery shipped = insert();
        assertEquals(1, physicalDeliveryDao.markShipped(shipped.getId(), "圆通", null));

        String result = importShip(
                row(toShip, "顺丰", "SF200", DeliveryStatusEnum.DELIVERED),
                row(toSign, "京东", "JD200", DeliveryStatusEnum.SIGNED),
                row(shipped, null, "YT200", DeliveryStatusEnum.DELIVERED));

        assertEquals("成功回填 3 条", result);
        PhysicalDelivery afterShipped = physicalDeliveryDao.selectById(shipped.getId());
        assertAll(
                () -> assertEquals(DeliveryStatusEnum.DELIVERED, statusOf(toShip)),
                () -> assertEquals(DeliveryStatusEnum.SIGNED, statusOf(toSign)),
                () -> assertEquals(DeliveryStatusEnum.DELIVERED, afterShipped.getStatus()),
                () -> assertEquals("圆通", afterShipped.getLogisticsCompany(), "没填的物流公司不该被清掉"),
                () -> assertEquals("YT200", afterShipped.getLogisticsNo()));
    }

    private String importShip(PhysicalDeliveryShipImportRow... rows) {
        byte[] file = SolvelaExcelUtil.toBytes("回填", PhysicalDeliveryShipImportRow.class, List.of(rows));
        return physicalDeliveryService.importShip(new ByteArrayInputStream(file));
    }

    private static PhysicalDeliveryShipImportRow row(PhysicalDelivery d, String company, String no, DeliveryStatusEnum status) {
        return new PhysicalDeliveryShipImportRow(d.getSourceBizId(), d.getSourceType().name(), company, no, status.getValue());
    }

    private DeliveryStatusEnum statusOf(PhysicalDelivery d) {
        return physicalDeliveryDao.selectById(d.getId()).getStatus();
    }

    private PhysicalDelivery insert() {
        PhysicalDelivery d = new PhysicalDelivery();
        d.setMemberId(940001L);
        d.setMemberName("zz-test");
        d.setSourceType(AssetSourceEnum.MANUAL);
        d.setSourceBizId("ZZD" + System.nanoTime());
        d.setPrizeName("状态机验收");
        d.awaitShipment();
        physicalDeliveryDao.insert(d);
        return d;
    }
}
