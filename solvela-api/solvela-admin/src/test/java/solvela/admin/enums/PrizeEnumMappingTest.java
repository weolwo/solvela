package solvela.admin.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class PrizeEnumMappingTest {

    @Autowired
    private PrizeConfigDao prizeConfigDao;

    @Autowired
    private PrizeLogDao prizeLogDao;

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

        // 库里 approve_status 是 0×1230 / 1×66 / 3×1，status 是 0×67 / 1×1130 / 2×100。
        // 「无需审批」必须是最多的：绝大多数奖品配的是自动免审。
        long notRequired = list.stream()
                .filter(e -> e.getApproveStatus() == PrizeApproveStatusEnum.NOT_REQUIRED).count();
        long pending = list.stream()
                .filter(e -> e.getApproveStatus() == PrizeApproveStatusEnum.PENDING).count();
        assertTrue(notRequired > pending,
                "「无需审批」(" + notRequired + ") 居然不比「待审批」(" + pending + ") 多 —— 0/1 口径多半反了");

        // 发放成功应当是最多的执行状态
        long success = list.stream().filter(e -> e.getStatus() == PrizeDispatchStatusEnum.SUCCESS).count();
        long fail = list.stream().filter(e -> e.getStatus() == PrizeDispatchStatusEnum.FAIL).count();
        assertTrue(success > fail, "发放成功(" + success + ") 不比失败(" + fail + ") 多，口径多半反了");
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
