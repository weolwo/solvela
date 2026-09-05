package solvela.mall.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.mall.MallAddress;
import solvela.mall.MallOrder;
import solvela.mall.address.service.MallAddressService;
import solvela.mall.order.dao.MallOrderDao;
import solvela.member.api.AssetGrantApi;
import solvela.member.api.AssetGrantCmd;
import solvela.member.api.AssetGrantReason;
import solvela.member.api.AssetGrantResult;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商城履约：状态机的后半段。
 *
 * <h3>三条守则，每一条对应一种真实的坏结果</h3>
 * ① <b>抢不到「待履约 → 履约中」就什么都不做。</b>
 *    t_member_coupon 上没有唯一键，重复发券在库那一层拦不住 —— 这次 CAS 是唯一的闸门。
 * ② <b>拒绝标 60，异常回滚。</b>把可重试的故障标成「履约失败」，
 *    用户的东西就永远发不出来了；把不可重试的拒绝当成故障，就是无限重试一个死单。
 * ③ <b>履约失败不退积分。</b>东西还欠着用户，不是没买。
 *
 * @Date 2026-09-05
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MallFulfillServiceTest {

    private static final String ORDER_NO = "M20260905120000123ABCDEF";

    @Mock
    private MallOrderDao mallOrderDao;
    @Mock
    private MallAddressService mallAddressService;
    @Mock
    private AssetGrantApi assetGrantApi;

    @InjectMocks
    private MallFulfillService service;

    @Test
    @DisplayName("🔴 抢不到「待履约 → 履约中」就直接退出，一次发放都不发起")
    void 抢不到闸门就不发放() {
        // 0 行 = 别人已经在做，或者这单压根不在待履约状态
        when(mallOrderDao.markFulfilling(ORDER_NO)).thenReturn(0);

        service.fulfill(ORDER_NO);

        assertAll(
                /*
                 * 这就是重复发券的唯一防线。少了它，同一单被投递两次
                 * 就是实打实发两份券出去 —— t_member_coupon 上没有唯一键接得住。
                 */
                () -> verify(assetGrantApi, never()).grant(any()),
                () -> verify(mallOrderDao, never()).getByOrderNo(anyString()));
    }

    @Test
    @DisplayName("实物：地址从地址簿读出来拼成一条，随发放指令带过去")
    void 实物履约带上地址快照() {
        givenOrder(physicalOrder());
        MallAddress address = new MallAddress();
        address.setReceiverName("张三");
        address.setReceiverPhone("13800000000");
        address.setProvince("上海市");
        address.setCity("上海市");
        address.setDistrict("黄浦区");
        address.setDetailAddress("XX路1号");
        when(mallAddressService.getOwned(anyLong(), anyLong())).thenReturn(address);
        when(assetGrantApi.grant(any())).thenReturn(AssetGrantResult.ofAccepted("777"));

        service.fulfill(ORDER_NO);

        ArgumentCaptor<AssetGrantCmd> captor = ArgumentCaptor.forClass(AssetGrantCmd.class);
        verify(assetGrantApi).grant(captor.capture());
        AssetGrantCmd cmd = captor.getValue();
        assertAll(
                () -> assertEquals("PHYSICAL", cmd.assetType()),
                () -> assertEquals("MALL", cmd.sourceType()),
                // 来源单号就是订单号，运营在发货台上按它找回这一单
                () -> assertEquals(ORDER_NO, cmd.bizRefId()),
                () -> assertEquals("张三", cmd.receiverName()),
                () -> assertEquals("上海市上海市黄浦区XX路1号", cmd.receiverAddress()),
                () -> verify(mallOrderDao).markFinished(ORDER_NO, "777"));
    }

    @Test
    @DisplayName("🔴 地址在下单到履约之间被删了 → 收件信息传 null，交给发放侧拒绝")
    void 地址被删就传空并被拒() {
        givenOrder(physicalOrder());
        // 软引用，DDL 刻意不加外键 —— 用户随时能删掉这条地址
        when(mallAddressService.getOwned(anyLong(), anyLong())).thenReturn(null);
        when(assetGrantApi.grant(any()))
                .thenReturn(AssetGrantResult.ofReject(AssetGrantReason.RECEIVER_REQUIRED));

        service.fulfill(ORDER_NO);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(mallOrderDao).markFailed(anyString(), reason.capture());
        assertAll(
                // 失败原因是给运营看的：要能直接告诉他该去做什么
                () -> assertTrue(reason.getValue().contains("收货地址"), reason.getValue()),
                // 🔴 不退积分：东西还欠着用户，不是没买
                () -> verify(mallOrderDao, never()).markFinished(anyString(), anyString()));
    }

    @Test
    @DisplayName("券：份数带过去，发放成功后回填履约单引用")
    void 券履约带上份数() {
        MallOrder order = physicalOrder();
        order.setCommodityType("COUPON");
        order.setAssetRef("SUMMER2026");
        order.setQuantity(3);
        order.setAddressId(null);
        givenOrder(order);
        when(assetGrantApi.grant(any())).thenReturn(AssetGrantResult.ofAccepted("100"));

        service.fulfill(ORDER_NO);

        ArgumentCaptor<AssetGrantCmd> captor = ArgumentCaptor.forClass(AssetGrantCmd.class);
        verify(assetGrantApi).grant(captor.capture());
        assertAll(
                () -> assertEquals("SUMMER2026", captor.getValue().assetRef()),
                () -> assertEquals(3, captor.getValue().quantityOrOne()),
                // 券商品不需要地址，不该白查一次地址簿
                () -> verify(mallAddressService, never()).getOwned(any(), any()),
                () -> verify(mallOrderDao).markFinished(ORDER_NO, "100"));
    }

    @Test
    @DisplayName("🔴 发放抛异常 → 让它上抛回滚，不标失败（单子退回待履约等重发）")
    void 故障要回滚而不是标失败() {
        givenOrder(physicalOrder());
        when(mallAddressService.getOwned(anyLong(), anyLong())).thenReturn(new MallAddress());
        doThrow(new IllegalStateException("数据库抖了一下")).when(assetGrantApi).grant(any());

        assertThrows(IllegalStateException.class, () -> service.fulfill(ORDER_NO));

        /*
         * 标成 60-履约失败的话，这单就死在那里了 —— 而它只是撞上一次抖动。
         * 上抛让 REQUIRES_NEW 那个事务整体回滚，status 退回 10，下一轮还能接手。
         */
        verify(mallOrderDao, never()).markFailed(anyString(), anyString());
        verify(mallOrderDao, never()).markFinished(anyString(), anyString());
    }

    @Test
    @DisplayName("🔴 BALANCE 商品直接标失败，且一次都不去调发放")
    void 现金商品暂不履约() {
        MallOrder order = physicalOrder();
        order.setCommodityType("BALANCE");
        order.setAddressId(null);
        givenOrder(order);

        service.fulfill(ORDER_NO);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(mallOrderDao).markFailed(anyString(), reason.capture());
        assertAll(
                /*
                 * 商品表至今没有「兑到手面额」这一列。拿 original_price 顶替能跑，
                 * 但那等于让前端的划线展示价决定真实发多少钱 —— 改一次文案就是一次资损。
                 */
                () -> assertTrue(reason.getValue().contains("面额"), reason.getValue()),
                () -> verify(assetGrantApi, never()).grant(any()));
    }

    @Test
    @DisplayName("失败原因不超过 fail_reason 的 varchar(255)")
    void 失败原因不超长() {
        givenOrder(physicalOrder());
        when(mallAddressService.getOwned(anyLong(), anyLong())).thenReturn(new MallAddress());
        for (AssetGrantReason reason : AssetGrantReason.values()) {
            when(assetGrantApi.grant(any())).thenReturn(AssetGrantResult.ofReject(reason));
            service.fulfill(ORDER_NO);
        }
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mallOrderDao, org.mockito.Mockito.atLeastOnce()).markFailed(anyString(), captor.capture());
        // 超长在 MySQL 严格模式下不是截断，是整条 UPDATE 被拒 —— 单子会卡在「履约中」
        captor.getAllValues().forEach(text ->
                assertTrue(text.length() <= 255, "失败原因超长: " + text));
    }

    /* ---------------- 造数 ---------------- */

    private void givenOrder(MallOrder order) {
        when(mallOrderDao.markFulfilling(ORDER_NO)).thenReturn(1);
        when(mallOrderDao.getByOrderNo(ORDER_NO)).thenReturn(order);
    }

    private static MallOrder physicalOrder() {
        MallOrder order = new MallOrder();
        order.setOrderNo(ORDER_NO);
        order.setMemberId(1001L);
        order.setCommodityCode("TSHIRT0001");
        order.setCommodityType("PHYSICAL");
        order.setCommodityName("限量T恤");
        order.setQuantity(1);
        order.setAddressId(9L);
        order.setCashPrice(BigDecimal.ZERO);
        return order;
    }
}
