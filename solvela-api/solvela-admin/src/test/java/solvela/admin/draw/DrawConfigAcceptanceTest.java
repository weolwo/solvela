package solvela.admin.draw;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.draw.DrawConfig;
import solvela.draw.PrizePoolConfig;
import solvela.draw.drawconfig.manager.DrawConfigManager;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽奖配置的验收：<b>编码冻结</b>与<b>一个活动一套</b>。
 *
 * <h3>为什么盯这两条</h3>
 * 它们都是「坏了也不报错」的那一类：
 * <ul>
 *   <li><b>编码被改掉</b> —— 脚本挂载表指向的还是旧编码，活动照常上线，
 *       一抽才报「没挂脚本」，而运营明明在脚本页看到挂着；</li>
 *   <li><b>一个活动两套抽奖</b> —— 运行期按活动查配置会拿到不确定的那一条，
 *       重置周期时对时错。</li>
 * </ul>
 * 第二条由唯一键兜底，这里验的是<b>报错是不是人话</b>；第一条只有代码保证，必须有测试盯着。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DrawConfigAcceptanceTest {

    /** 刻意用真实业务不会出现的编码 */
    private static final String ACTIVITY_CODE = "__DRAW_CFG_ACPT__";

    private static final String POOL_CODE = "__DRAW_CFG_POOL__";

    @Autowired
    private DrawConfigService drawConfigService;

    @Autowired
    private DrawConfigManager drawConfigManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM t_prize_pool_config WHERE pool_code = ?", POOL_CODE);
        jdbcTemplate.update("DELETE FROM t_draw_config WHERE activity_code = ?", ACTIVITY_CODE);
    }

    @Test
    @DisplayName("工作台首次保存：用前端给的编码建出配置")
    void 首次保存用前端给的编码() {
        String code = drawConfigService.saveFromWorkbench(
                ACTIVITY_CODE, "WACPT00001", "验收抽奖", "WEEK",
                DrawModeEnum.PROBABILITY, EnableStatusEnum.ENABLED, "acceptance-test");

        assertEquals("WACPT00001", code);
        DrawConfig saved = drawConfigService.getByActivityCode(ACTIVITY_CODE);
        assertNotNull(saved);
        assertEquals("验收抽奖", saved.getDrawName());
        assertEquals("WEEK", saved.getResetPeriod());
    }

    @Test
    @DisplayName("🔴 再次保存时传别的编码，编码不变 —— 它是脚本挂载的引用键")
    void 编码创建后冻结() {
        String first = drawConfigService.saveFromWorkbench(
                ACTIVITY_CODE, "WACPT00001", "验收抽奖", "DAY",
                DrawModeEnum.PROBABILITY, EnableStatusEnum.ENABLED, "acceptance-test");

        String second = drawConfigService.saveFromWorkbench(
                ACTIVITY_CODE, "WACPT99999", "改了名字", "MONTH",
                DrawModeEnum.PROBABILITY, EnableStatusEnum.ENABLED, "acceptance-test");

        assertEquals(first, second,
                "编码被改掉了。脚本挂载表指向的还是旧编码，活动照常上线、一抽才报「没挂脚本」");
        DrawConfig saved = drawConfigService.getByActivityCode(ACTIVITY_CODE);
        assertEquals("WACPT00001", saved.getDrawCode());
        // 其它字段该改的照改，别把「冻结编码」做成「整条不许动」
        assertEquals("改了名字", saved.getDrawName());
        assertEquals("MONTH", saved.getResetPeriod());
    }

    @Test
    @DisplayName("编码没给或格式非法时自己生成，不让非法值落库")
    void 编码非法时自动生成() {
        String code = drawConfigService.saveFromWorkbench(
                ACTIVITY_CODE, "不是合法编码", "验收抽奖", "DAY",
                DrawModeEnum.PROBABILITY, EnableStatusEnum.ENABLED, "acceptance-test");

        assertTrue(code.matches("^[A-Z0-9]{10}$"), "生成的编码不合规范：" + code);
    }

    @Test
    @DisplayName("一个活动只能一套抽奖，重复新增报的是人话")
    void 一个活动只能一套() {
        drawConfigService.saveFromWorkbench(
                ACTIVITY_CODE, "WACPT00001", "验收抽奖", "DAY",
                DrawModeEnum.PROBABILITY, EnableStatusEnum.ENABLED, "acceptance-test");

        DrawConfig another = new DrawConfig();
        another.setActivityCode(ACTIVITY_CODE);
        another.setDrawCode("WACPT00002");
        another.setDrawName("第二套");
        another.setResetPeriod("DAY");

        BusinessException e = assertThrows(BusinessException.class,
                () -> drawConfigService.add(another, "acceptance-test"));
        assertTrue(e.getMessage().contains("一个活动只能有一套"), "实际: " + e.getMessage());
    }

    @Test
    @DisplayName("🔴 底下还有奖池时不许删 —— 奖池的重置周期要从这里读")
    void 有奖池时不许删() {
        drawConfigService.saveFromWorkbench(
                ACTIVITY_CODE, "WACPT00001", "验收抽奖", "DAY",
                DrawModeEnum.PROBABILITY, EnableStatusEnum.ENABLED, "acceptance-test");
        jdbcTemplate.update("""
                INSERT INTO t_prize_pool_config (activity_code, draw_code, pool_code, pool_name)
                VALUES (?, 'WACPT00001', ?, '验收奖池')
                """, ACTIVITY_CODE, POOL_CODE);

        Long id = drawConfigManager.lambdaQuery()
                .eq(DrawConfig::getActivityCode, ACTIVITY_CODE).one().getId();

        BusinessException e = assertThrows(BusinessException.class, () -> drawConfigService.delete(id));
        assertTrue(e.getMessage().contains("奖池"), "实际: " + e.getMessage());

        // 删掉奖池之后就该放行 —— 拦截是有条件的，不是永久锁死
        jdbcTemplate.update("DELETE FROM t_prize_pool_config WHERE pool_code = ?", POOL_CODE);
        drawConfigService.delete(id);
        assertEquals(0, drawConfigManager.count(Wrappers.<DrawConfig>lambdaQuery()
                .eq(DrawConfig::getActivityCode, ACTIVITY_CODE)));
    }
}
