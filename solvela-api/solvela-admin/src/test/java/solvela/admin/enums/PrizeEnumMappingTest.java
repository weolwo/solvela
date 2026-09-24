package solvela.admin.enums;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.ApproveModeEnum;
import solvela.enums.EnableStatusEnum;
import solvela.enums.PrizeApproveStatusEnum;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.prize.PrizeConfig;
import solvela.prize.PrizeLog;
import solvela.prize.prizeconfig.dao.PrizeConfigDao;
import solvela.prize.prizelog.dao.PrizeLogDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 奖品模块四个状态列枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>覆盖 {@code t_prize_config.approve_mode / status} 与
 * {@code t_prize_log.approve_status / status}，都是有真实数据量的列
 * （prize_log 1297 行，approve_status 跨 0/1/3 三个取值）。
 *
 * <p>本测试<b>只读</b>：不造数、不改库。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Transactional
class PrizeEnumMappingTest {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private PrizeConfigDao prizeConfigDao;

    @Autowired
    private PrizeLogDao prizeLogDao;

    /**
     * 🔴 用例自带数据，不再靠「开发库里碰巧有什么」。
     *
     * <p>原先这批断言吃的是压测/验收会员留下的存量行，2026-09-24 那批数据被清掉后
     * 当场变红 —— 而它们在一个<b>全新环境上本来就跑不过</b>，只是一直没人验。
     *
     * <p>类上的 {@code @Transactional} 让这些行只活在用例执行期间，方法结束即回滚。
     * 不回滚的话，这个修复就变成了它要解决的那个问题本身。
     */
    @BeforeEach
    void 造夹具数据() {
        EnumMappingFixture.seedPrizeLog(jdbcTemplate);
    }

    @Test
    @DisplayName("prize_config：approve_mode 与 status 都能从 int 列装配")
    void 奖品配置装配() {
        List<PrizeConfig> list = prizeConfigDao.selectList(null);
        assertFalse(list.isEmpty(), "t_prize_config 没有数据，这条用例失去意义");
        for (PrizeConfig e : list) {
            assertNotNull(e.getApproveMode(), "approveMode 装配成了 null");
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        // 库里 approve_mode 是 0×9 / 1×10，两种模式都要出现，否则等于只验了一半
        assertTrue(list.stream().anyMatch(e -> e.getApproveMode() == ApproveModeEnum.AUTO));
        assertTrue(list.stream().anyMatch(e -> e.getApproveMode() == ApproveModeEnum.MANUAL));
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == EnableStatusEnum.ENABLED));
    }

    @Test
    @DisplayName("prize_log：审批状态与执行状态是两个维度，各自独立装配")
    void 发奖记录装配() {
        List<PrizeLog> list = prizeLogDao.selectList(null);
        assertFalse(list.isEmpty(), "t_prize_log 没有数据，这条用例失去意义");

        for (PrizeLog e : list) {
            assertNotNull(e.getApproveStatus(), "approveStatus 装配成了 null");
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }

        /*
         * 🔴 这里原先断的是「无需审批」比「待审批」多、「发放成功」比「失败」多，
         *    注释写着「库里 approve_status 是 0×1230 / 1×66 / 3×1」。
         *
         *    那是对<b>存量数据分布</b>的断言 —— 枚举改造当时有意义（能发现 0/1 口径反了），
         *    但它吃的是压测会员留下的行。2026-09-24 那批数据清掉之后，
         *    剩下的演示数据是 3 成功 / 8 失败，不等式直接不成立。
         *
         *    靠夹具去凑够数量能让它变绿，但那是在迁就一个已经失去依据的断言。
         *    改成钉住<b>夹具那几行</b>：写进去的数字是多少、读出来就该是哪个枚举。
         *    这比数量比较更精确，而且不受库里还剩什么影响。
         */
        assertEquals(PrizeApproveStatusEnum.NOT_REQUIRED, approveStatusOf(list, "FIXP-1"),
                "approve_status 写 0，读出来必须是「无需审批」—— 0/1 口径反了就会变成「待审批」");
        assertEquals(PrizeApproveStatusEnum.PENDING, approveStatusOf(list, "FIXP-4"),
                "approve_status 写 1，读出来必须是「待审批」");
        assertEquals(PrizeDispatchStatusEnum.SUCCESS, dispatchStatusOf(list, "FIXP-1"),
                "dispatch status 写 1，读出来必须是「成功」");
        assertEquals(PrizeDispatchStatusEnum.FAIL, dispatchStatusOf(list, "FIXP-5"),
                "dispatch status 写 2，读出来必须是「失败」");
    }

    private static PrizeApproveStatusEnum approveStatusOf(List<PrizeLog> list, String prizeCode) {
        return byCode(list, prizeCode).getApproveStatus();
    }

    private static PrizeDispatchStatusEnum dispatchStatusOf(List<PrizeLog> list, String prizeCode) {
        return byCode(list, prizeCode).getStatus();
    }

    /** 夹具行必须真的在结果里 —— 找不到就说明查询没把它带出来，那本身就是问题 */
    private static PrizeLog byCode(List<PrizeLog> list, String prizeCode) {
        return list.stream()
                .filter(e -> prizeCode.equals(e.getPrizeCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("夹具行 " + prizeCode + " 没被查出来"));
    }

    @Test
    @DisplayName("两个维度可以并存：批准了但发放失败，是一条合法记录")
    void 审批与执行是两个维度() {
        List<PrizeLog> list = prizeLogDao.selectList(null);
        assertFalse(list.isEmpty());

        // 只要存在「approve_status 与 status 不同步」的组合，就说明两列确实是独立的两个维度，
        // 而不是同一个状态机被拆成了两列。
        boolean independent = list.stream().anyMatch(e ->
                e.getApproveStatus() == PrizeApproveStatusEnum.NOT_REQUIRED
                        && e.getStatus() != PrizeDispatchStatusEnum.SUCCESS);
        assertTrue(independent,
                "没有找到「无需审批但未发放成功」的记录 —— 若两列永远同步，说明其中一列是冗余的");
    }
}
