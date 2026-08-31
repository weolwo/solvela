package solvela.admin.script;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.scriptengine.Script;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.domain.ScriptSaveCommand;
import solvela.scriptengine.domain.dto.ScriptDTO;
import solvela.scriptengine.domain.dto.ScriptRefDTO;
import solvela.scriptengine.manager.ScriptManager;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.runtime.ScriptRuntime;
import solvela.scriptengine.service.ScriptEditService;
import solvela.scriptengine.service.ScriptQueryService;
import solvela.scriptengine.service.ScriptRefService;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.scriptengine.spi.ScriptScene;
import solvela.scriptengine.store.ScriptStore;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脚本存储链路的端到端验收：<b>后台保存 → 激活 → 挂载 → 取出来执行</b>。
 *
 * <p>这条链路横跨数据库、缓存、引擎三层，任何一层的单测都证明不了它整体是通的，
 * 所以放在 {@code @SpringBootTest} 里对着真实库跑。
 *
 * <h3>这套用例守的是版本化最容易做错的三件事</h3>
 * <ol>
 *   <li><b>保存不等于生效</b> —— 存了新版本，线上还得跑旧的；</li>
 *   <li><b>激活即回滚</b> —— 指回旧版本和发布是同一个动作；</li>
 *   <li><b>至多一个激活版本</b> —— 而且这条<b>由唯一键保证</b>，不靠代码自觉。</li>
 * </ol>
 *
 * <p>用的脚本编码与挂载对象都带 {@code __ACCEPTANCE__} 前缀，测完即删，不碰任何真实业务数据。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ScriptRuntimeAcceptanceTest {

    /** 刻意用真实业务里不会出现的编码，避免误挂到线上奖池 */
    private static final String FAKE_POOL_CODE = "__SCRIPT_ACCEPTANCE_TEST_POOL__";

    private static final String POOL_SCRIPT = "__acceptance__/pool_entry";

    private static final String TASK_SCRIPT = "__acceptance__/task_rule";

    /** poolCode 是 VIP_POOL 才准入 */
    private static final String POOL_V1 = "poolCode == \"VIP_POOL\" && memberId > 0";

    /** 换成谁都准入，用来验证「激活哪一版，跑的就是哪一版」 */
    private static final String POOL_V2 = "memberId > 0";

    @Autowired
    private ScriptRuntime scriptRuntime;

    @Autowired
    private ScriptRefService scriptRefService;

    @Autowired
    private ScriptEditService scriptEditService;

    @Autowired
    private ScriptQueryService scriptQueryService;

    @Autowired
    private ScriptStore scriptStore;

    @Autowired
    private ScriptManager scriptManager;

    @Autowired
    private ScriptRefManager scriptRefManager;

    @AfterEach
    void cleanUp() {
        scriptRefService.unbind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE);
        scriptManager.remove(Wrappers.<Script>lambdaQuery()
                .in(Script::getScriptCode, List.of(POOL_SCRIPT, TASK_SCRIPT)));
        scriptStore.evictAll();
    }

    // =====================================================================
    // 保存与激活
    // =====================================================================

    @Test
    @DisplayName("首版保存后自动激活 —— 此时它还没被挂到任何地方，激活不改变任何线上行为")
    void first_version_is_activated_on_save() {
        Long id = savePool(POOL_V1, "首版");

        ScriptDTO saved = scriptQueryService.detail(id);
        assertEquals(1, saved.getVersion());
        assertTrue(saved.getActive(), "首版应该自动激活，否则「存完为什么用不了」");
        assertTrue(scriptStore.findActive(POOL_SCRIPT).isPresent());
    }

    @Test
    @DisplayName("🔴 保存第二版不会改变线上行为 —— 存了不等于发了")
    void saving_a_second_version_does_not_change_runtime() {
        savePool(POOL_V1, "首版");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        savePool(POOL_V2, "第二版：放开限制");

        // v2 已入库，但线上跑的还是 v1：NORMAL_POOL 依旧被拒
        Optional<Boolean> result = scriptRuntime.evaluate(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, poolContext("NORMAL_POOL"), Boolean.class);
        assertTrue(result.isPresent());
        assertFalse(result.get(), "保存新版本就改变了线上行为，「先存一下待会儿再看」会直接改掉生产逻辑");
    }

    @Test
    @DisplayName("🌟 激活第二版后运行期立刻跟着变，再激活回第一版就是回滚")
    void activate_switches_runtime_and_rollback_is_the_same_action() {
        Long v1 = savePool(POOL_V1, "首版");
        Long v2 = savePool(POOL_V2, "第二版：放开限制");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        scriptEditService.activate(v2, "acceptance-test");
        assertTrue(evaluatePool("NORMAL_POOL"), "激活 v2 之后 NORMAL_POOL 也该准入");

        // 回滚：把 active 指回 v1，和发布是同一个方法
        scriptEditService.activate(v1, "acceptance-test");
        assertFalse(evaluatePool("NORMAL_POOL"), "回滚到 v1 之后 NORMAL_POOL 应该重新被拒");
    }

    @Test
    @DisplayName("🔴 同一脚本至多一个激活版本，且这条由唯一键保证")
    void at_most_one_active_version() {
        savePool(POOL_V1, "首版");
        Long v2 = savePool(POOL_V2, "第二版");
        scriptEditService.activate(v2, "acceptance-test");

        long activeCount = scriptManager.lambdaQuery()
                .eq(Script::getScriptCode, POOL_SCRIPT)
                .eq(Script::getActiveFlag, true)
                .count();
        assertEquals(1, activeCount, "出现两个激活版本，说明「取消旧激活」那步被 null 策略吃掉了");

        long total = scriptManager.lambdaQuery().eq(Script::getScriptCode, POOL_SCRIPT).count();
        assertEquals(2, total, "历史版本不该被删，回滚要靠它");
    }

    @Test
    @DisplayName("语法不通过的脚本存不进库 —— 坏脚本一步都进不来")
    void broken_syntax_is_rejected_before_insert() {
        assertThrows(Exception.class, () -> savePool("poolCode == ", "语法坏的"));

        assertEquals(0, scriptManager.lambdaQuery().eq(Script::getScriptCode, POOL_SCRIPT).count(),
                "语法都没过就落库了");
    }

    @Test
    @DisplayName("内容一字不差时不产生新版本，并提示直接激活那一版")
    void identical_content_does_not_create_a_version() {
        savePool(POOL_V1, "首版");

        BusinessException e = assertThrows(BusinessException.class, () -> savePool(POOL_V1, "手滑又存了一次"));
        assertTrue(e.getMessage().contains("v1"), "实际: " + e.getMessage());
        assertEquals(1, scriptManager.lambdaQuery().eq(Script::getScriptCode, POOL_SCRIPT).count());
    }

    @Test
    @DisplayName("🔴 同一编码不许换场景 —— 换了就是把已有挂载全部悄悄作废")
    void scene_cannot_change_within_one_script_code() {
        savePool(POOL_V1, "首版");

        BusinessException e = assertThrows(BusinessException.class, () -> scriptEditService.save(
                new ScriptSaveCommand(POOL_SCRIPT, "换个场景", ScriptScene.TASK_RULE.name(),
                        null, "true", "偷换契约", "acceptance-test")));

        assertTrue(e.getMessage().contains("POOL_ENTRY") && e.getMessage().contains("TASK_RULE"),
                "实际: " + e.getMessage());
    }

    // =====================================================================
    // 挂载与执行
    // =====================================================================

    @Test
    @DisplayName("没挂脚本时返回 empty —— 这是正常情况，不是错误")
    void unbound_point_returns_empty() {
        Optional<Boolean> result = scriptRuntime.evaluate(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, poolContext(FAKE_POOL_CODE), Boolean.class);

        assertTrue(result.isEmpty(), "没挂脚本却返回了值，「忘了挂」和「脚本拒绝」就分不清了");
    }

    @Test
    @DisplayName("🌟 完整链路：保存 → 激活 → 挂载 → 按场景契约执行")
    void save_activate_bind_then_evaluate_end_to_end() {
        savePool(POOL_V1, "首版");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        assertTrue(evaluatePool("VIP_POOL"), "poolCode=VIP_POOL 应该判定为准入");
        assertFalse(evaluatePool("NORMAL_POOL"), "poolCode 不是 VIP_POOL 应该判定为不准入");
    }

    @Test
    @DisplayName("🔴 挂载时校验场景：把任务判定脚本挂到奖池准入上，写入那一刻就被拒绝")
    void binding_a_script_of_the_wrong_scene_is_rejected() {
        scriptEditService.save(new ScriptSaveCommand(TASK_SCRIPT, "连续签到", ScriptScene.TASK_RULE.name(),
                null, "currentMetric >= 7", "首版", "acceptance-test"));

        BusinessException e = assertThrows(BusinessException.class, () -> scriptRefService.bind(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, TASK_SCRIPT, "acceptance-test"));

        assertTrue(e.getMessage().contains("场景不匹配"), "实际: " + e.getMessage());
        assertTrue(e.getMessage().contains("POOL_ENTRY") && e.getMessage().contains("TASK_RULE"));
    }

    @Test
    @DisplayName("挂载没有激活版本的脚本被拒绝，并说清该去激活")
    void binding_a_script_without_an_active_version_is_rejected() {
        BusinessException e = assertThrows(BusinessException.class, () -> scriptRefService.bind(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, "__acceptance__/never_saved", "acceptance-test"));

        assertTrue(e.getMessage().contains("激活"), "实际: " + e.getMessage());
    }

    @Test
    @DisplayName("🌟 引用表的存在理由：改这个脚本会影响哪些业务对象")
    void ref_table_answers_who_is_affected() {
        savePool(POOL_V1, "首版");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        List<ScriptRefDTO> refs = scriptRefService.findRefsOfScript(POOL_SCRIPT);
        ScriptRefDTO mine = refs.stream()
                .filter(ref -> FAKE_POOL_CODE.equals(ref.getRefId()))
                .findFirst().orElseThrow(() -> new AssertionError("挂上去了却查不到引用"));

        assertEquals("PRIZE_POOL_ENTRY", mine.getRefPoint());
        assertEquals("奖池 - 准入判定", mine.getRefPointTitle());
        assertEquals("VIP 奖池准入", mine.getScriptName(), "引用列表要能显示脚本名，不能只有 code");
        assertEquals(1, mine.getScriptVersion(), "要能看出这条挂载此刻跑的是哪一版");
        assertEquals("", mine.getRefKey(), "单值槽位的 key 必须是空串 —— null 会让唯一键失效");
    }

    @Test
    @DisplayName("同一挂载点重复挂等于替换，不会产生两行")
    void rebinding_replaces_instead_of_duplicating() {
        savePool(POOL_V1, "首版");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        long count = scriptRefService.findRefsOfScript(POOL_SCRIPT).stream()
                .filter(ref -> FAKE_POOL_CODE.equals(ref.getRefId()))
                .count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("🔴 单值槽位不接受挂载键 —— 想绕开「一个槽位一个脚本」时当场被拒")
    void single_valued_slot_refuses_a_key() {
        savePool(POOL_V1, "首版");

        assertThrows(IllegalArgumentException.class, () -> scriptRefService.bind(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, "MEMBER_LOGIN", POOL_SCRIPT, "acceptance-test"));
    }

    @Test
    @DisplayName("挂了脚本但上下文不满足场景契约时，报的是缺哪个变量")
    void context_violating_scene_contract_is_reported() {
        savePool(POOL_V1, "首版");
        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        EngineContext incomplete = EngineContext.create().bind("memberId", 8848L);
        BusinessException e = assertThrows(BusinessException.class, () -> scriptRuntime.evaluate(
                ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, incomplete, Boolean.class));

        assertTrue(e.getMessage().contains("activityCode") || e.getMessage().contains("poolCode"),
                "实际: " + e.getMessage());
    }

    // =====================================================================
    // 管理页依赖的查询
    // =====================================================================

    @Test
    @DisplayName("脚本列表每个编码一行，取激活版本，带中文名与引用数")
    void script_list_carries_display_fields() {
        savePool(POOL_V1, "首版");
        savePool(POOL_V2, "第二版");

        List<ScriptDTO> mine = scriptQueryService.listActive().stream()
                .filter(vo -> POOL_SCRIPT.equals(vo.getScriptCode()))
                .toList();

        assertEquals(1, mine.size(), "两个版本在列表页应该只占一行");
        ScriptDTO pool = mine.getFirst();
        assertEquals(1, pool.getVersion(), "列表取的应该是激活的那一版");
        assertEquals("VIP 奖池准入", pool.getScriptName());
        assertEquals("抽奖域", pool.getDomainTitle(), "中文名从枚举取，不存库");
        assertEquals("奖池准入判定", pool.getSceneTitle());
        assertEquals("Boolean", pool.getReturnType());
        assertEquals(0, pool.getRefCount(), "没挂到任何对象上时引用数应为 0");
        assertNull(pool.getContent(), "列表不该带脚本内容");
    }

    @Test
    @DisplayName("版本列表新的在前，带内容，能看出哪一版在跑")
    void version_list_shows_which_one_is_live() {
        savePool(POOL_V1, "首版");
        savePool(POOL_V2, "第二版");

        List<ScriptDTO> versions = scriptQueryService.listVersions(POOL_SCRIPT);

        assertEquals(2, versions.size());
        assertEquals(2, versions.getFirst().getVersion(), "新的在前");
        assertFalse(versions.getFirst().getActive(), "v2 只是存了，没激活");
        assertTrue(versions.getLast().getActive(), "在跑的是 v1");
        assertNotNull(versions.getFirst().getContent(), "版本列表要带内容，回滚前得对比");
        assertEquals("第二版", versions.getFirst().getChangeLog());
    }

    @Test
    @DisplayName("详情的引用数随挂载实时变化")
    void detail_carries_live_ref_count() {
        Long id = savePool(POOL_V1, "首版");
        assertEquals(0, scriptQueryService.detail(id).getRefCount());

        scriptRefService.bind(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, POOL_SCRIPT, "acceptance-test");

        assertEquals(1, scriptQueryService.detail(id).getRefCount());
    }

    // =====================================================================
    // 悬空挂载
    // =====================================================================

    @Test
    @DisplayName("🔴 悬空挂载不再阻塞启动，但一被触发就报错，且点名是哪个对象挂了哪个脚本")
    void dangling_ref_fails_loudly_at_runtime_not_at_startup() {
        // 直接插一条指向「没有激活版本」的引用，模拟脚本被停用但挂载没摘
        ScriptRef dangling = new ScriptRef();
        dangling.setScriptCode("__acceptance__/no_active_version");
        dangling.setRefType(ScriptRefPoint.PRIZE_POOL_ENTRY.getRefType());
        dangling.setRefId(FAKE_POOL_CODE);
        dangling.setRefSlot(ScriptRefPoint.PRIZE_POOL_ENTRY.getRefSlot());
        dangling.setRefKey(ScriptRefPoint.SINGLE_KEY);
        dangling.setStatus(EnableStatusEnum.ENABLED);
        scriptRefManager.save(dangling);

        try {
            // 🔴 启动期只告警不抛：修复它的工具就在后台，让 admin 起不来等于把修复工具一起锁死
            assertDoesNotThrow(() -> scriptRuntime.findBoundScriptCode(
                    ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE));

            BusinessException e = assertThrows(BusinessException.class, () -> scriptRuntime.evaluate(
                    ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE, poolContext("VIP_POOL"), Boolean.class));

            assertTrue(e.getMessage().contains("__acceptance__/no_active_version"), "要点名是哪个脚本");
            assertTrue(e.getMessage().contains(FAKE_POOL_CODE), "要点名是哪个业务对象");
        } finally {
            scriptRefManager.removeById(dangling.getId());
        }
    }

    // =====================================================================

    private Long savePool(String content, String changeLog) {
        return scriptEditService.save(new ScriptSaveCommand(
                POOL_SCRIPT, "VIP 奖池准入", ScriptScene.POOL_ENTRY.name(),
                "验收用", content, changeLog, "acceptance-test"));
    }

    private boolean evaluatePool(String poolCode) {
        return scriptRuntime.evaluate(ScriptRefPoint.PRIZE_POOL_ENTRY, FAKE_POOL_CODE,
                        poolContext(poolCode), Boolean.class)
                .orElseThrow(() -> new AssertionError("挂了脚本却没返回值"));
    }

    private EngineContext poolContext(String poolCode) {
        return EngineContext.create()
                .bind("memberId", 8848L)
                .bind("activityCode", "ACT_ACCEPTANCE")
                .bind("poolCode", poolCode);
    }
}
