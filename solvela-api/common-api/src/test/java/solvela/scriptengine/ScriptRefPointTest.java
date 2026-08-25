package solvela.scriptengine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.scriptengine.spi.ScriptScene;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挂载点契约的行为固化测试。
 *
 * <p>钉住的核心是：<b>每个挂载点都声明了它期望的场景</b>。
 * 有了这条，「把任务判定脚本挂到奖池准入上」就能在写入 t_script_ref 的那一刻被拒绝，
 * 而不是等某天真有人来抽奖时才炸。
 */
public class ScriptRefPointTest {

    @Test
    @DisplayName("(ref_type, ref_slot) 组合唯一 —— 它是 uk_script_ref_point 唯一索引的代码侧对应物")
    void ref_type_and_slot_combination_is_unique() {
        Set<String> seen = new HashSet<>();
        for (ScriptRefPoint point : ScriptRefPoint.values()) {
            String key = point.getRefType() + "#" + point.getRefSlot();
            assertTrue(seen.add(key), "挂载点 (ref_type, ref_slot) 重复: " + key);
        }
    }

    @Test
    @DisplayName("每个挂载点都声明了期望场景与中文名")
    void every_point_declares_scene_and_title() {
        for (ScriptRefPoint point : ScriptRefPoint.values()) {
            assertNotNull(point.getExpectedScene(), point + " 没声明期望场景");
            assertTrue(point.getTitle().contains(" - "), point + " 的中文名应形如「奖池 - 准入判定」");
        }
    }

    @Test
    @DisplayName("三个挂载点分别对上三个真实切入点与各自的场景")
    void three_points_map_to_the_three_real_anchors() {
        assertEquals(ScriptScene.TASK_RULE, ScriptRefPoint.TASK_TEMPLATE_RULE.getExpectedScene());
        assertEquals(ScriptScene.POOL_ENTRY, ScriptRefPoint.PRIZE_POOL_ENTRY.getExpectedScene());
        assertEquals(ScriptScene.ACTIVITY_RULE, ScriptRefPoint.ACTIVITY_ENTRY.getExpectedScene());

        assertEquals("TASK_TEMPLATE", ScriptRefPoint.TASK_TEMPLATE_RULE.getRefType());
        assertEquals("PRIZE_POOL", ScriptRefPoint.PRIZE_POOL_ENTRY.getRefType());
        assertEquals("ACTIVITY", ScriptRefPoint.ACTIVITY_ENTRY.getRefType());
    }

    @Test
    @DisplayName("能按 (ref_type, ref_slot) 反查回挂载点 —— 从库里读出来的行要还原成枚举")
    void can_look_up_by_type_and_slot() {
        for (ScriptRefPoint point : ScriptRefPoint.values()) {
            assertEquals(point, ScriptRefPoint.of(point.getRefType(), point.getRefSlot()).orElseThrow());
            assertEquals(point, ScriptRefPoint.of(point.name()).orElseThrow());
        }
        assertTrue(ScriptRefPoint.of("NOT_A_TYPE", "NOT_A_SLOT").isEmpty());
        assertTrue(ScriptRefPoint.of("NOT_A_POINT").isEmpty());
    }
}
