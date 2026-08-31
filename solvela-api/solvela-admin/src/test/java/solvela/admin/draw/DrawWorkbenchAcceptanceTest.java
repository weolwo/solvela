package solvela.admin.draw;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.draw.DrawConfig;
import solvela.draw.PrizePoolConfig;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.draw.poolconfig.domain.command.DrawWorkbenchMappingCommand;
import solvela.draw.poolconfig.domain.command.DrawWorkbenchPoolCommand;
import solvela.draw.poolconfig.domain.command.DrawWorkbenchPoolItemCommand;
import solvela.draw.poolconfig.domain.command.DrawWorkbenchSaveCommand;
import solvela.draw.poolconfig.domain.dto.DrawWorkbenchDTO;
import solvela.draw.poolconfig.manager.PrizePoolConfigManager;
import solvela.draw.poolconfig.service.PrizePoolConfigService;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽奖工作台聚合保存的验收。
 *
 * <h3>为什么值得单独有一套</h3>
 * 工作台是这套后台<b>唯一能一次改动物资、奖池、概率、玩法配置的地方</b>，
 * 而它此前一条测试都没有。前端的概率闭环与上线锁只是 UI 防呆 ——
 * 绕过界面直接打接口时，服务端必须自己拦住，否则一个 curl 就能配出永远抽不中的奖池。
 *
 * <h3>盯的是「坏了也不报错」的那几条</h3>
 * <ul>
 *   <li><b>概率不闭环</b> —— 引擎照跑，只是某些奖永远抽不到，或者总有人抽空；</li>
 *   <li><b>已发库存被前端重置</b> —— 库存账当场归零，超发且不可追溯；</li>
 *   <li><b>奖池没归属到抽奖配置</b> —— 限领永远不重置，且没有任何报错。</li>
 * </ul>
 *
 * <p>造数全部自己建、自己删，编码带 {@code __} 前缀，不碰任何真实业务数据。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DrawWorkbenchAcceptanceTest {

    private static final String ACTIVITY_CODE = "__WB_ACPT__";

    /** 奖池编码要过 SolvelaCodeUtil 的格式校验（10 位大写字母数字），所以不能带下划线 */
    private static final String POOL_A = "WBACPTPOL1";

    private static final String POOL_B = "WBACPTPOL2";

    private static final String PRIZE_1 = "WBACPTPRZ1";

    private static final String PRIZE_2 = "WBACPTPRZ2";

    @Autowired
    private PrizePoolConfigService prizePoolConfigService;

    @Autowired
    private PrizePoolConfigManager prizePoolConfigManager;

    @Autowired
    private DrawConfigService drawConfigService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO t_activity_config (activity_code, activity_name, activity_type, status, start_time, end_time)
                VALUES (?, '工作台验收活动', 'DRAW', 0, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 7 DAY)
                """, ACTIVITY_CODE);
        // 物资必须先存在于资产大库，工作台才认
        insertPrizeConfig(PRIZE_1, "验收奖品一");
        insertPrizeConfig(PRIZE_2, "验收奖品二");
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_pool_prize_mapping WHERE pool_code IN (?, ?)", POOL_A, POOL_B);
        jdbcTemplate.update("DELETE FROM t_prize_pool_config WHERE activity_code = ?", ACTIVITY_CODE);
        jdbcTemplate.update("DELETE FROM t_prize_pool_item WHERE activity_code = ?", ACTIVITY_CODE);
        jdbcTemplate.update("DELETE FROM t_prize_config WHERE activity_code = ?", ACTIVITY_CODE);
        jdbcTemplate.update("DELETE FROM t_draw_config WHERE activity_code = ?", ACTIVITY_CODE);
        jdbcTemplate.update("DELETE FROM t_activity_config WHERE activity_code = ?", ACTIVITY_CODE);
    }

    // =====================================================================
    // 服务端校验：绕过界面也拦得住
    // =====================================================================

    @Test
    @DisplayName("🔴 概率不闭环被拒 —— 前端的闭环校验只是防呆，服务端自己重算")
    void 概率必须等于一百() {
        DrawWorkbenchSaveCommand cmd = baseCommand();
        cmd.getPoolList().getFirst().setPrizeMappingList(List.of(
                mapping(PRIZE_1, "60", false),
                mapping(PRIZE_2, "30", true)));

        BusinessException e = assertThrows(BusinessException.class, () -> prizePoolConfigService.workbenchSave(cmd));

        assertTrue(e.getMessage().contains("必须等于100%"), "实际: " + e.getMessage());
        assertEquals(0, poolCount(), "校验失败却落了库，说明校验在写入之后");
    }

    @Test
    @DisplayName("坑位引用物资列表外的奖品被拒 —— 否则映射会指向一个不存在的奖项")
    void 坑位不能引用列表外的奖品() {
        DrawWorkbenchSaveCommand cmd = baseCommand();
        cmd.getPoolList().getFirst().setPrizeMappingList(List.of(
                mapping(PRIZE_1, "50", false),
                mapping("WBACPTNOPE", "50", true)));

        BusinessException e = assertThrows(BusinessException.class, () -> prizePoolConfigService.workbenchSave(cmd));
        assertTrue(e.getMessage().contains("物资列表外"), "实际: " + e.getMessage());
    }

    @Test
    @DisplayName("一个池最多一个兜底奖项 —— 两个兜底时引擎该命中谁没有定义")
    void 兜底最多一个() {
        DrawWorkbenchSaveCommand cmd = baseCommand();
        cmd.getPoolList().getFirst().setPrizeMappingList(List.of(
                mapping(PRIZE_1, "50", true),
                mapping(PRIZE_2, "50", true)));

        BusinessException e = assertThrows(BusinessException.class, () -> prizePoolConfigService.workbenchSave(cmd));
        assertTrue(e.getMessage().contains("兜底"), "实际: " + e.getMessage());
    }

    @Test
    @DisplayName("奖池编码格式非法被拒 —— 与活动/奖品编码同一套约定")
    void 奖池编码格式校验() {
        DrawWorkbenchSaveCommand cmd = baseCommand();
        cmd.getPoolList().getFirst().setPoolCode("bad-code");

        assertThrows(BusinessException.class, () -> prizePoolConfigService.workbenchSave(cmd));
    }

    // =====================================================================
    // 库存账
    // =====================================================================

    @Test
    @DisplayName("🔴 已发库存不接受前端值 —— 前端传 0 也不许把 used_stock 清零")
    void 已发库存不可被前端重置() {
        prizePoolConfigService.workbenchSave(baseCommand());
        // 模拟已经发出去 7 件
        jdbcTemplate.update("UPDATE t_prize_pool_item SET used_stock = 7 WHERE activity_code = ? AND prize_code = ?",
                ACTIVITY_CODE, PRIZE_1);

        // 再保存一次（前端回显里根本没有 usedStock 这个可编辑字段，但接口挡不住有人手造）
        prizePoolConfigService.workbenchSave(baseCommand());

        Integer used = jdbcTemplate.queryForObject(
                "SELECT used_stock FROM t_prize_pool_item WHERE activity_code = ? AND prize_code = ?",
                Integer.class, ACTIVITY_CODE, PRIZE_1);
        assertEquals(7, used, "已发库存被保存动作清零了 —— 库存账当场作废，而且不会报错");
    }

    // =====================================================================
    // 与抽奖配置的联动（本轮新增）
    // =====================================================================

    @Test
    @DisplayName("🌟 保存时一并建出抽奖配置，新奖池挂到它下面")
    void 保存时建出抽奖配置并归属奖池() {
        DrawWorkbenchSaveCommand cmd = baseCommand();
        cmd.setDrawName("验收抽奖");
        cmd.setResetPeriod("WEEK");
        cmd.setDrawMode(DrawModeEnum.PROBABILITY);
        cmd.setDrawStatus(EnableStatusEnum.ENABLED);

        prizePoolConfigService.workbenchSave(cmd);

        DrawConfig config = drawConfigService.getByActivityCode(ACTIVITY_CODE);
        assertNotNull(config, "工作台保存后仍然没有抽奖配置，抽奖起不来且不报错");
        assertEquals("验收抽奖", config.getDrawName());
        assertEquals("WEEK", config.getResetPeriod());

        PrizePoolConfig pool = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getPoolCode, POOL_A).one();
        assertEquals(config.getDrawCode(), pool.getDrawCode(),
                "奖池没归属到抽奖配置：限领永远不会重置，而且不会有任何报错");
    }

    @Test
    @DisplayName("🔴 二次保存时抽奖编码不变 —— 脚本挂载引用的就是它")
    void 二次保存不改抽奖编码() {
        prizePoolConfigService.workbenchSave(baseCommand());
        String first = drawConfigService.getByActivityCode(ACTIVITY_CODE).getDrawCode();

        DrawWorkbenchSaveCommand second = baseCommand();
        second.setDrawCode("WBHACKED99");
        prizePoolConfigService.workbenchSave(second);

        assertEquals(first, drawConfigService.getByActivityCode(ACTIVITY_CODE).getDrawCode(),
                "编码被改掉了：挂载表还指着旧编码，活动照常上线，一抽才报「没挂脚本」");
    }

    @Test
    @DisplayName("存量奖池 draw_code 为空时，保存会顺手补上")
    void 存量奖池补齐归属() {
        prizePoolConfigService.workbenchSave(baseCommand());
        // 模拟迁移之前建的奖池：归属为空
        jdbcTemplate.update("UPDATE t_prize_pool_config SET draw_code = NULL WHERE pool_code = ?", POOL_A);

        prizePoolConfigService.workbenchSave(baseCommand());

        String drawCode = drawConfigService.getByActivityCode(ACTIVITY_CODE).getDrawCode();
        assertEquals(drawCode, prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getPoolCode, POOL_A).one().getDrawCode());
    }

    // =====================================================================
    // 增量语义
    // =====================================================================

    @Test
    @DisplayName("未提交的存量奖池连同其坑位一并删除 —— 保存是「以本次提交为准」，不是追加")
    void 未提交的奖池会被删掉() {
        DrawWorkbenchSaveCommand twoPools = baseCommand();
        twoPools.getPoolList().add(pool(POOL_B, "验收池二"));
        prizePoolConfigService.workbenchSave(twoPools);
        assertEquals(2, poolCount());

        // 只提交一个池
        prizePoolConfigService.workbenchSave(baseCommand());

        assertEquals(1, poolCount());
        Integer mappings = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_pool_prize_mapping WHERE pool_code = ?", Integer.class, POOL_B);
        assertEquals(0, mappings, "奖池删了但坑位映射还在，下次有人复用这个池编码就会撞上脏数据");
    }

    // =====================================================================
    // 回显
    // =====================================================================

    @Test
    @DisplayName("没配过的活动回显空壳 + 预生成编码，且那个编码不落库")
    void 未配置时回显空壳() {
        DrawWorkbenchDTO detail = prizePoolConfigService.workbenchDetail(ACTIVITY_CODE);

        assertFalse(detail.drawConfigured());
        assertFalse(detail.drawCodeLocked());
        assertTrue(detail.drawCode().matches("^[A-Z0-9]{10}$"), "预生成编码不合规范：" + detail.drawCode());
        assertNull(detail.drawStatus(), "还没配置就给了状态，前端分不出「新建」和「已存在但关闭」");
        assertNull(drawConfigService.getByActivityCode(ACTIVITY_CODE),
                "只是打开工作台就建出了一条配置 —— 每看一次造一条垃圾数据");
    }

    @Test
    @DisplayName("配过之后回显真实配置，且编码标记为冻结")
    void 配置后回显真实值() {
        DrawWorkbenchSaveCommand cmd = baseCommand();
        cmd.setDrawName("验收抽奖");
        cmd.setResetPeriod("MONTH");
        prizePoolConfigService.workbenchSave(cmd);

        DrawWorkbenchDTO detail = prizePoolConfigService.workbenchDetail(ACTIVITY_CODE);

        assertTrue(detail.drawConfigured());
        assertTrue(detail.drawCodeLocked());
        assertEquals("验收抽奖", detail.drawName());
        assertEquals("MONTH", detail.resetPeriod());
        assertEquals(1, detail.poolList().size());
        assertEquals(2, detail.prizeItemList().size());
    }

    // =====================================================================

    private DrawWorkbenchSaveCommand baseCommand() {
        DrawWorkbenchSaveCommand cmd = new DrawWorkbenchSaveCommand();
        cmd.setActivityCode(ACTIVITY_CODE);
        cmd.setPrizeItemList(List.of(item(PRIZE_1), item(PRIZE_2)));
        // 用可变 list：好几条用例要往里加池子
        cmd.setPoolList(new java.util.ArrayList<>(List.of(pool(POOL_A, "验收池一"))));
        return cmd;
    }

    private static DrawWorkbenchPoolItemCommand item(String prizeCode) {
        DrawWorkbenchPoolItemCommand item = new DrawWorkbenchPoolItemCommand();
        item.setPrizeCode(prizeCode);
        item.setTotalStock(100);
        item.setUserMaxCount(1);
        return item;
    }

    private static DrawWorkbenchPoolCommand pool(String poolCode, String poolName) {
        DrawWorkbenchPoolCommand pool = new DrawWorkbenchPoolCommand();
        pool.setPoolCode(poolCode);
        pool.setPoolName(poolName);
        pool.setPrizeMappingList(new java.util.ArrayList<>(List.of(
                mapping(PRIZE_1, "30", false),
                mapping(PRIZE_2, "70", true))));
        return pool;
    }

    private static DrawWorkbenchMappingCommand mapping(String prizeCode, String probability, boolean fallback) {
        DrawWorkbenchMappingCommand mapping = new DrawWorkbenchMappingCommand();
        mapping.setPrizeCode(prizeCode);
        mapping.setProbability(new BigDecimal(probability));
        mapping.setIsFallback(fallback);
        return mapping;
    }

    private void insertPrizeConfig(String prizeCode, String prizeName) {
        jdbcTemplate.update("""
                INSERT INTO t_prize_config
                    (activity_code, promotion_config_id, prize_type, prize_name, prize_code, prize_value)
                VALUES (?, 0, 'SCORE', ?, ?, 1)
                """, ACTIVITY_CODE, prizeName, prizeCode);
    }

    private int poolCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_prize_pool_config WHERE activity_code = ?", Integer.class, ACTIVITY_CODE);
        return count == null ? 0 : count;
    }
}
