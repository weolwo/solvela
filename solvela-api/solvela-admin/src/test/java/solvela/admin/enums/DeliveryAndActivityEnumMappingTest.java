package solvela.admin.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.activity.ActivityConfig;
import solvela.activity.dao.ActivityConfigDao;
import solvela.enums.ActivityStatusEnum;
import solvela.enums.DeliveryStatusEnum;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履约单与活动状态枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>这一批有个别处没有的形态：{@code t_physical_delivery.status} 的合法取值里<b>有负数</b>
 * （-1 已取消）。负值走的是同一套 TypeHandler，但它长期不在枚举里，
 * 也正是「四个状态桶加起来比总数少 1」那个老问题的来源，值得单独钉一下。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DeliveryAndActivityEnumMappingTest {

    @Autowired
    private PhysicalDeliveryDao physicalDeliveryDao;

    @Autowired
    private ActivityConfigDao activityConfigDao;

    /** 直接读列，用来和 TypeHandler 装配出来的枚举对账 */
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("履约单：负值状态 -1 也能正确装配成枚举")
    void 履约单装配() {
        List<PhysicalDelivery> list = physicalDeliveryDao.selectList(null);
        assertFalse(list.isEmpty(), "t_physical_delivery 没有数据，这条用例失去意义");
        for (PhysicalDelivery e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null —— 负值取值多半没落进枚举");
        }
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == DeliveryStatusEnum.CANCELLED),
                "库里那条 status=-1 没有被识别成 CANCELLED");
    }

    @Test
    @DisplayName("履约单：按负值枚举过滤，条件要按 value 下推")
    void 按取消状态过滤() {
        List<PhysicalDelivery> cancelled = physicalDeliveryDao.selectList(
                new LambdaQueryWrapper<PhysicalDelivery>()
                        .eq(PhysicalDelivery::getStatus, DeliveryStatusEnum.CANCELLED));
        assertFalse(cancelled.isEmpty(), "按 CANCELLED 过滤一条都没查到 —— -1 多半没被正确写进 SQL");
        for (PhysicalDelivery e : cancelled) {
            assertEquals(DeliveryStatusEnum.CANCELLED, e.getStatus());
        }
    }

    @Test
    @DisplayName("活动：三档状态都能装配，且「未开始」与「下线」没有被混为一谈")
    void 活动装配() {
        List<ActivityConfig> list = activityConfigDao.selectList(null);
        assertFalse(list.isEmpty(), "t_activity_config 没有数据，这条用例失去意义");
        for (ActivityConfig e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }

        long notStart = list.stream().filter(e -> e.getStatus() == ActivityStatusEnum.NOT_START).count();
        long online = list.stream().filter(e -> e.getStatus() == ActivityStatusEnum.ONLINE).count();
        assertTrue(notStart > 0, "一条「未开始」都没有");
        assertTrue(online > 0, "一条「上线」都没有");

        /*
         * 「取值口径有没有反」直接按 status 列比对，<b>不靠两档的数量多少</b>。
         *
         * 🔴 2026-09-18 订正：这里原本断言「未开始的条数多于上线的条数」，
         *    注释写着「库里是 0×7 / 1×1」—— 那是某一天联调库的快照。
         *    每跑一次造数脚本、每上线一个活动，这个比例都会变；今天它是 6:6，
         *    于是测试红了，而<b>枚举装配本身一点问题都没有</b>。
         *    这种「断言环境数据长什么样」的用例迟早会红，而且红的时候指不出真问题。
         */
        for (ActivityConfig e : list) {
            Integer raw = rawStatusOf(e.getActivityCode());
            assertNotNull(raw, "查不到 " + e.getActivityCode() + " 的原始 status");
            assertEquals(raw, e.getStatus().getValue(),
                    "活动 " + e.getActivityCode() + " 的 status 列是 " + raw
                            + "，却装配成了 " + e.getStatus() + "(" + e.getStatus().getValue() + ") —— 取值口径反了");
        }
    }

    /** 绕开枚举直接读 status 列：装配对不对，只能拿没经过 TypeHandler 的原值来比 */
    private Integer rawStatusOf(String activityCode) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM t_activity_config WHERE activity_code = ?", Integer.class, activityCode);
    }

    @Test
    @DisplayName("活动：按枚举分状态查询的总数要等于总量")
    void 按状态过滤活动() {
        List<ActivityConfig> all = activityConfigDao.selectList(null);
        assertFalse(all.isEmpty());

        int sum = 0;
        for (ActivityStatusEnum status : ActivityStatusEnum.values()) {
            List<ActivityConfig> hit = activityConfigDao.selectList(
                    new LambdaQueryWrapper<ActivityConfig>().eq(ActivityConfig::getStatus, status));
            for (ActivityConfig e : hit) {
                assertEquals(status, e.getStatus(), "按 " + status + " 查询却查出了 " + e.getStatus());
            }
            sum += hit.size();
        }
        assertEquals(all.size(), sum, "分状态查询的总数与总量对不上，说明有行的 status 落在枚举之外");
    }
}
