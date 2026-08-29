package solvela.admin.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.draw.DrawPrizeLog;
import solvela.draw.PrizePoolConfig;
import solvela.draw.drawlog.dao.DrawPrizeLogDao;
import solvela.draw.poolconfig.dao.PrizePoolConfigDao;
import solvela.enums.DrawModeEnum;
import solvela.enums.DrawResultEnum;
import solvela.enums.PrizePoolStatusEnum;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽奖模块三个状态列枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>覆盖 {@code t_prize_pool_config.draw_mode / status} 与 {@code t_draw_prize_log.status}。
 * 其中抽奖流水有 500 行、跨「已中奖 / 库存不足」两个取值，是这一批里数据最厚的一张表。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DrawEnumMappingTest {

    @Autowired
    private PrizePoolConfigDao prizePoolConfigDao;

    @Autowired
    private DrawPrizeLogDao drawPrizeLogDao;

    @Test
    @DisplayName("奖池配置：draw_mode 与 status 都能从 int 列装配")
    void 奖池配置装配() {
        List<PrizePoolConfig> list = prizePoolConfigDao.selectList(null);
        assertFalse(list.isEmpty(), "t_prize_pool_config 没有数据，这条用例失去意义");
        for (PrizePoolConfig e : list) {
            assertNotNull(e.getDrawMode(), "drawMode 装配成了 null");
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
    }

    @Test
    @DisplayName("抽奖流水：status 能装配，且「未中奖」与「库存不足」没有被混成一个值")
    void 抽奖流水装配() {
        List<DrawPrizeLog> list = drawPrizeLogDao.selectList(null);
        assertFalse(list.isEmpty(), "t_draw_prize_log 没有数据，这条用例失去意义");
        for (DrawPrizeLog e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }

        long hit = list.stream().filter(e -> e.getStatus() == DrawResultEnum.HIT).count();
        long noStock = list.stream().filter(e -> e.getStatus() == DrawResultEnum.NO_STOCK).count();

        // 库里是 1×315 / 2×185。两个值都必须出现 ——
        // 「中了但没库存」是运营的补货信号，一旦被并进「未中奖」就再也看不见了。
        assertTrue(hit > 0, "一条中奖记录都没有");
        assertTrue(noStock > 0, "一条「库存不足」都没有 —— 这个信号如果丢了，奖池配少了也没人知道");
        assertTrue(hit > noStock, "中奖(" + hit + ") 居然不比库存不足(" + noStock + ") 多，取值口径多半反了");
    }

    @Test
    @DisplayName("按枚举过滤奖池：条件要按 value 下推到 SQL")
    void 按状态过滤奖池() {
        List<PrizePoolConfig> all = prizePoolConfigDao.selectList(null);
        assertFalse(all.isEmpty());

        int sum = 0;
        for (PrizePoolStatusEnum status : PrizePoolStatusEnum.values()) {
            List<PrizePoolConfig> hit = prizePoolConfigDao.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PrizePoolConfig>()
                            .eq(PrizePoolConfig::getStatus, status));
            for (PrizePoolConfig e : hit) {
                assertTrue(status == e.getStatus(), "按 " + status + " 查询却查出了 " + e.getStatus());
            }
            sum += hit.size();
        }
        assertTrue(sum == all.size(),
                "分状态查询的总数(" + sum + ")与总量(" + all.size() + ")对不上");
    }

    @Test
    @DisplayName("draw_mode 的两种算法都能被正确识别")
    void 抽奖算法装配() {
        List<PrizePoolConfig> list = prizePoolConfigDao.selectList(null);
        assertFalse(list.isEmpty());
        for (PrizePoolConfig e : list) {
            assertTrue(e.getDrawMode() == DrawModeEnum.PROBABILITY || e.getDrawMode() == DrawModeEnum.STOCK_RATIO,
                    "drawMode 落在枚举之外：" + e.getDrawMode());
        }
    }
}
