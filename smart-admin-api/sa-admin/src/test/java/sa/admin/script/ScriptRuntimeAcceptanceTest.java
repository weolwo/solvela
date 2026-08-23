package sa.admin.script;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import sa.base.common.exception.BusinessException;
import sa.scriptengine.domain.vo.ScriptRefVO;
import sa.scriptengine.domain.vo.ScriptVO;
import sa.scriptengine.loader.ScriptFileLoader;
import sa.scriptengine.runtime.ScriptRuntime;
import sa.scriptengine.service.ScriptQueryService;
import sa.scriptengine.service.ScriptRefService;
import sa.scriptengine.spi.EngineContext;
import sa.scriptengine.spi.ScriptRefPoint;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本存储链路的端到端验收：<b>文件 → t_script → 挂载 → 取出来执行</b>。
 *
 * <p>这条链路横跨文件系统、数据库、引擎三层，任何一层的单测都证明不了它整体是通的，
 * 所以放在 {@code @SpringBootTest} 里对着真实库跑。
 *
 * <p>用的是文件里已有的样例脚本 {@code draw/vip_pool_entry}，
 * 挂载对象用一个不存在的假奖池编码，测完即删，不污染任何真实业务数据。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ScriptRuntimeAcceptanceTest {

    /** 刻意用一个真实业务里不会出现的编码，避免误挂到线上奖池 */
    private static final String FAKE_POOL_CODE = "__SCRIPT_ACCEPTANCE_TEST_POOL__";

    private static final String POOL_SCRIPT = "draw/vip_pool_entry";

    private static final String TASK_SCRIPT = "task/streak_sign_7d";

    @Autowired
    private ScriptRuntime scriptRuntime;

    @Autowired
    private ScriptRefService scriptRefService;

    @Autowired
    private ScriptFileLoader scriptFileLoader;

    @Autowired
    private ScriptQueryService scriptQueryService;

    @AfterEach
    void cleanUp() {
        scriptRefService.unbind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE);
    }

    // =====================================================================

    @Test
    @DisplayName("启动期文件已加载进内存，运行期按 script_code 取得到")
    void scripts_are_loaded_from_files_at_startup() {
        assertTrue(scriptFileLoader.find(POOL_SCRIPT).isPresent(), "样例脚本没被加载进来");
        assertEquals(POOL_SCRIPT, scriptFileLoader.find(POOL_SCRIPT).orElseThrow().name());
        assertTrue(scriptFileLoader.findFile(TASK_SCRIPT).isPresent());
    }

    @Test
    @DisplayName("没挂脚本时返回 empty —— 这是正常情况，不是错误")
    void unbound_point_returns_empty() {
        Optional<Boolean> result = scriptRuntime.evaluate(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, poolContext(FAKE_POOL_CODE), Boolean.class);

        assertTrue(result.isEmpty(), "没挂脚本却返回了值，「忘了挂」和「脚本拒绝」就分不清了");
    }

    @Test
    @DisplayName("🌟 完整链路：挂载 → 取出 → 按场景契约执行")
    void bind_then_evaluate_end_to_end() {
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        // 脚本内容是 poolCode == "VIP_POOL" && memberId > 0
        Optional<Boolean> hit = scriptRuntime.evaluate(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, poolContext("VIP_POOL"), Boolean.class);
        assertTrue(hit.isPresent());
        assertTrue(hit.get(), "poolCode=VIP_POOL 应该判定为准入");

        Optional<Boolean> miss = scriptRuntime.evaluate(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, poolContext("NORMAL_POOL"), Boolean.class);
        assertTrue(miss.isPresent());
        assertFalse(miss.get(), "poolCode 不是 VIP_POOL 应该判定为不准入");
    }

    @Test
    @DisplayName("🔴 挂载时校验场景：把任务判定脚本挂到奖池准入上，写入那一刻就被拒绝")
    void binding_a_script_of_the_wrong_scene_is_rejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptRefService.bind(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, TASK_SCRIPT, "acceptance-test"));

        assertTrue(e.getMessage().contains("场景不匹配"), "实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("POOL_ENTRY") && e.getMessage().contains("TASK_RULE"));
    }

    @Test
    @DisplayName("挂载不存在的脚本被拒绝，并提示脚本要先作为文件提交")
    void binding_a_nonexistent_script_is_rejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptRefService.bind(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, "draw/does_not_exist", "acceptance-test"));

        assertTrue(e.getMessage().contains("resources/scripts/"), "实际: " + e.getMessage());
    }

    @Test
    @DisplayName("🌟 引用表的存在理由：改这个脚本会影响哪些业务对象")
    void ref_table_answers_who_is_affected() {
        assertTrue(scriptRefService.findRefsOfScript(POOL_SCRIPT).stream()
                .noneMatch(ref -> FAKE_POOL_CODE.equals(ref.getRefId())), "前置状态不干净");

        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        List<ScriptRefVO> refs = scriptRefService.findRefsOfScript(POOL_SCRIPT);
        ScriptRefVO mine = refs.stream()
                .filter(ref -> FAKE_POOL_CODE.equals(ref.getRefId()))
                .findFirst().orElseThrow(() -> new AssertionError("挂上去了却查不到引用"));

        assertEquals("PRIZE_POOL_ENTRY", mine.getRefPoint());
        assertEquals("奖池 - 准入判定", mine.getRefPointTitle());
        assertEquals("VIP 奖池准入", mine.getScriptName(), "引用列表要能显示脚本名，不能只有 code");
    }

    @Test
    @DisplayName("同一挂载点重复挂等于替换，不会产生两行")
    void rebinding_replaces_instead_of_duplicating() {
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        long count = scriptRefService.findRefsOfScript(POOL_SCRIPT).stream()
                .filter(ref -> FAKE_POOL_CODE.equals(ref.getRefId()))
                .count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("挂了脚本但上下文不满足场景契约时，报的是缺哪个变量")
    void context_violating_scene_contract_is_reported() {
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        EngineContext incomplete = EngineContext.create().bind("memberId", 8848L);
        BusinessException e = assertThrows(BusinessException.class, () -> scriptRuntime.evaluate(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, incomplete, Boolean.class));

        assertTrue(e.getMessage().contains("activityCode") || e.getMessage().contains("poolCode"),
                "实际: " + e.getMessage());
    }

    // =====================================================================
    // 管理页依赖的查询：列表与详情
    // =====================================================================

    @Test
    @DisplayName("脚本列表带得出域/场景中文名与引用数 —— 管理页的表格全靠这些字段")
    void script_list_carries_display_fields() {
        ScriptVO pool = scriptQueryService.listAll().stream()
                .filter(vo -> POOL_SCRIPT.equals(vo.getScriptCode()))
                .findFirst().orElseThrow(() -> new AssertionError("样例脚本没进 t_script"));

        assertEquals("VIP 奖池准入", pool.getScriptName());
        assertEquals("抽奖域", pool.getDomainTitle(), "中文名从枚举取，不存库");
        assertEquals("奖池准入判定", pool.getSceneTitle());
        assertEquals("Boolean", pool.getReturnType());
        assertEquals(0, pool.getRefCount(), "没挂到任何对象上时引用数应为 0");
        // 列表不带内容：脚本可能很长，列表页不需要
        assertNull(pool.getContent(), "列表不该带脚本内容");
    }

    @Test
    @DisplayName("脚本详情带内容，且引用数随挂载实时变化")
    void script_detail_carries_content_and_live_ref_count() {
        ScriptVO before = scriptQueryService.detail(POOL_SCRIPT);
        assertNotNull(before.getContent(), "详情必须带内容，抽屉里要展示");
        assertTrue(before.getContent().contains("@scene"), "带的应该是含文件头的原文");
        int baseline = before.getRefCount();

        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        assertEquals(baseline + 1, scriptQueryService.detail(POOL_SCRIPT).getRefCount());
    }

    private EngineContext poolContext(String poolCode) {
        return EngineContext.create()
                .bind("memberId", 8848L)
                .bind("activityCode", "ACT_ACCEPTANCE")
                .bind("poolCode", poolCode);
    }
}
