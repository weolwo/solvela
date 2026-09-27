package solvela.ledger.logistic.service;

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
import solvela.enums.DeliveryStatusEnum;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.member.api.DeliverySummaryView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「我的」页的实物单分组。
 *
 * <h3>钉住的几条</h3>
 * <ul>
 *   <li>🔴 「待填地址」与列表上的 needAddress 是<b>同一个函数</b>判的：待发货且没填地址。
 *       填了地址的待发货单要落进「待发货」，不能两边都数；</li>
 *   <li>已签收、已取消只进总数，不进任何一格；</li>
 *   <li>没登录不查库。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeliverySummaryTest {

    /**
     * summary 用 LambdaQueryWrapper.select 只取两列，那要靠 MyBatis-Plus 的 TableInfo 缓存翻译列名 ——
     * 纯 Mockito 用例里没有启动扫实体那一步，理由同 MallGradeDiscountResolverTest。
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PhysicalDelivery.class);
    }

    @Mock
    private PhysicalDeliveryDao physicalDeliveryDao;

    @InjectMocks
    private MemberDeliveryService service;

    private static PhysicalDelivery row(DeliveryStatusEnum status, String address) {
        PhysicalDelivery d = new PhysicalDelivery();
        d.setStatus(status);
        d.setReceiverAddress(address);
        return d;
    }

    @Test
    @DisplayName("🔴 待填地址 / 待发货 / 已发货各数各的，签收与取消只进总数")
    void 分组() {
        when(physicalDeliveryDao.selectList(any())).thenReturn(List.of(
                row(DeliveryStatusEnum.PENDING, null),
                row(DeliveryStatusEnum.PENDING, " "),
                row(DeliveryStatusEnum.PENDING, "某某路 1 号"),
                row(DeliveryStatusEnum.DELIVERED, "某某路 1 号"),
                row(DeliveryStatusEnum.SIGNED, "某某路 1 号"),
                row(DeliveryStatusEnum.CANCELLED, null)));

        DeliverySummaryView view = service.summary(1001L);

        // 空白地址也算没填 —— 与列表上的 needAddress 同一个 isBlank 判据
        assertEquals(new DeliverySummaryView(6, 2, 1, 1), view);
    }

    @Test
    @DisplayName("没登录不查库，全是 0")
    void 未登录() {
        assertEquals(new DeliverySummaryView(0, 0, 0, 0), service.summary(null));
        verify(physicalDeliveryDao, never()).selectList(any());
    }
}
