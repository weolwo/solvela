package sa.scriptengine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sa.scriptengine.loader.ScriptFile;
import sa.scriptengine.loader.ScriptFileParser;
import sa.scriptengine.spi.ScriptDomain;
import sa.scriptengine.spi.ScriptScene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本文件头解析的行为固化测试。
 *
 * <p>解析与校验是这套加载机制里最容易写错的部分，所以 {@link ScriptFileParser} 被刻意做成
 * 纯静态函数 —— 这些规则必须能被单测直接盯住，而不是只能靠「启动一次应用看看报不报错」验证。
 *
 * <p>每条不合法的用例都断言<b>报错信息里带得出文件名</b>：启动失败时人要能立刻知道去改哪个文件。
 */
public class ScriptFileParserTest {

    private static final String VALID = """
            /**
             * @name  连续签到满 7 天
             * @scene TASK_RULE
             * @desc  连续签到进度达到 7 天即判定达标。
             */
            return currentMetric >= 7;
            """;

    // =====================================================================
    // 正常解析
    // =====================================================================

    @Test
    @DisplayName("解析出 name / scene / desc，域由场景推导")
    void parses_header_tags() {
        ScriptFile file = ScriptFileParser.parse("scripts/task/streak_sign_7d.ql", VALID);

        assertEquals("连续签到满 7 天", file.name());
        assertEquals(ScriptScene.TASK_RULE, file.scene());
        assertEquals("连续签到进度达到 7 天即判定达标。", file.description());
        // 域不在文件头里写，由 scene 推导 —— 能推导的东西不留给人写
        assertEquals(ScriptDomain.TASK, file.domain());
    }

    @Test
    @DisplayName("script_code 由文件路径推导，不在文件头里重复声明")
    void script_code_is_derived_from_path() {
        assertEquals("task/streak_sign_7d",
                ScriptFileParser.toScriptCode("scripts/task/streak_sign_7d.ql"));
        // classpath URL 形态、Windows 反斜杠都要能处理
        assertEquals("draw/vip_pool_entry",
                ScriptFileParser.toScriptCode("file:/D:/app/BOOT-INF/classes/scripts/draw/vip_pool_entry.ql"));
        assertEquals("activity/basic_entry",
                ScriptFileParser.toScriptCode("scripts\\activity\\basic_entry.ql"));
    }

    @Test
    @DisplayName("@desc 支持多行，续行的 * 装饰会被剥掉")
    void desc_supports_multiple_lines() {
        ScriptFile file = ScriptFileParser.parse("scripts/task/multi.ql", """
                /**
                 * @name  多行描述
                 * @scene TASK_RULE
                 * @desc  第一行说明。
                 *        第二行说明。
                 */
                return true;
                """);

        assertEquals("第一行说明。 第二行说明。", file.description());
    }

    @Test
    @DisplayName("内容变一个字，hash 就变 —— 加载器据此判断要不要写库")
    void content_hash_detects_change() {
        String hash = ScriptFileParser.sha256(VALID);
        assertEquals(64, hash.length());
        assertEquals(hash, ScriptFileParser.sha256(VALID));
        assertNotEquals(hash, ScriptFileParser.sha256(VALID + " "));
    }

    // =====================================================================
    // 不合法的形态：每条都必须在启动期炸掉
    // =====================================================================

    @Test
    @DisplayName("没有文件头直接拒绝")
    void missing_header_is_rejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ScriptFileParser.parse("scripts/task/naked.ql", "return true;"));
        assertTrue(e.getMessage().contains("scripts/task/naked.ql"), "报错要带文件名");
        assertTrue(e.getMessage().contains("文件头"));
    }

    @Test
    @DisplayName("缺 @name / @scene / @desc 任何一个都拒绝")
    void missing_required_tag_is_rejected() {
        assertTrue(assertThrows(IllegalStateException.class, () -> ScriptFileParser.parse(
                "scripts/task/x.ql", "/**\n * @scene TASK_RULE\n * @desc d\n */\nreturn true;"))
                .getMessage().contains("@name"));

        assertTrue(assertThrows(IllegalStateException.class, () -> ScriptFileParser.parse(
                "scripts/task/x.ql", "/**\n * @name n\n * @desc d\n */\nreturn true;"))
                .getMessage().contains("@scene"));

        assertTrue(assertThrows(IllegalStateException.class, () -> ScriptFileParser.parse(
                "scripts/task/x.ql", "/**\n * @name n\n * @scene TASK_RULE\n */\nreturn true;"))
                .getMessage().contains("@desc"));
    }

    @Test
    @DisplayName("@scene 写了不存在的场景要拒绝，并提示去看 ScriptScene")
    void unknown_scene_is_rejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ScriptFileParser.parse(
                "scripts/task/x.ql", "/**\n * @name n\n * @scene NOT_A_SCENE\n * @desc d\n */\nreturn true;"));
        assertTrue(e.getMessage().contains("NOT_A_SCENE"));
        assertTrue(e.getMessage().contains("ScriptScene"));
    }

    @Test
    @DisplayName("🔴 目录与场景所属域不一致要拒绝 —— 这条规则的存在就是为了让目录树不撒谎")
    void directory_must_match_scene_domain() {
        // TASK_RULE 属于 task 域，却放在 draw/ 下
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ScriptFileParser.parse("scripts/draw/misplaced.ql", VALID));

        assertTrue(e.getMessage().contains("scripts/task/"), "要指出正确的目录，实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("draw"), "要指出当前放错的目录");
    }

    @Test
    @DisplayName("放在 scripts 根目录下也算目录不匹配")
    void root_level_file_is_rejected() {
        assertThrows(IllegalStateException.class,
                () -> ScriptFileParser.parse("scripts/streak.ql", VALID));
    }

    @Test
    @DisplayName("路径不在 scripts/ 下、或扩展名不对，都拒绝")
    void path_outside_root_or_wrong_extension_is_rejected() {
        assertThrows(IllegalStateException.class,
                () -> ScriptFileParser.toScriptCode("other/task/x.ql"));
        assertThrows(IllegalStateException.class,
                () -> ScriptFileParser.toScriptCode("scripts/task/x.txt"));
    }
}
