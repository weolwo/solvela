package solvela.scriptengine.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.enums.ScriptSourceEnum;
import solvela.scriptengine.Script;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.manager.ScriptManager;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.scriptengine.spi.ScriptScene;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动期扫一眼 {@code resources/scripts/}：<b>只做种子导入与体检，不覆盖任何东西</b>。
 *
 * <h3>它以前是什么，现在是什么</h3>
 * 以前这里是权威：启动时把文件内容覆盖写进 {@code t_script}，运行期从内存里取。
 * 那意味着「谁是权威」由部署时机决定 —— 后台改一行脚本，下次发版就被静默覆盖回去，
 * 而且没有任何告警。
 *
 * <p>现在文件退回<b>开发工作区</b>的位置：开发在那里写、走 git review，
 * 上线靠人复制进后台并激活。运行期只认 {@code t_script} 里 {@code active_flag = 1} 的那一行。
 * 本类只剩三件事：
 * <ol>
 *   <li><b>种子导入</b> —— 只对<b>库里一行都没有</b>的脚本编码插入 v1 并激活。
 *       让全新环境（新同事、新库）不是空的，之后永远是空操作；</li>
 *   <li><b>体检</b> —— 语法错、工作区与线上版本不一致、挂载指向没有激活版本的脚本，
 *       全部<b>记日志</b>；</li>
 *   <li>没有第三件了。</li>
 * </ol>
 *
 * <h3>🔴 为什么这里不再抛异常</h3>
 * 以前语法错和悬空引用都会让应用<b>拒绝启动</b>，那是对的 —— 当时文件就是线上逻辑。
 * 现在不行了：修复这些问题的唯一途径是后台的脚本管理页，而后台就在 admin 进程里。
 * 让它启动失败，就等于<b>把修复它的工具一起锁死</b>，只能改库或回滚发版才能出来。
 *
 * <p>体检结果一律打 ERROR/WARN，靠日志告警发现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptSeedImporter implements SmartInitializingSingleton {

    private static final String LOCATION_PATTERN =
            ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + ScriptFileParser.ROOT + "**/*" + ScriptFileParser.EXTENSION;

    private final ScriptEngine scriptEngine;

    private final ScriptManager scriptManager;

    private final ScriptRefManager scriptRefManager;

    @Override
    public void afterSingletonsInstantiated() {
        List<ScriptFile> scriptFiles = scanAndParse();
        checkSyntax(scriptFiles);
        seed(scriptFiles);
        reportDrift(scriptFiles);
        reportDanglingRefs();
    }

    // ------------------------------------------------------------------
    // 扫描 + 解析
    // ------------------------------------------------------------------

    private List<ScriptFile> scanAndParse() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            log.error("[ScriptEngine] 扫描脚本目录失败：{}", LOCATION_PATTERN, e);
            return List.of();
        }

        Map<String, String> seen = new LinkedHashMap<>();
        List<ScriptFile> parsed = new ArrayList<>();
        for (Resource resource : resources) {
            String filePath = describe(resource);
            String content;
            try (var in = resource.getInputStream()) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("[ScriptEngine] 读取脚本文件失败：{}", filePath, e);
                continue;
            }

            ScriptFile file;
            try {
                file = ScriptFileParser.parse(filePath, content);
            } catch (RuntimeException e) {
                log.error("[ScriptEngine] 脚本文件头解析失败：{} -> {}", filePath, e.getMessage());
                continue;
            }
            String previous = seen.putIfAbsent(file.scriptCode(), filePath);
            if (previous != null) {
                // 同 code 冲突：谁生效完全看 classpath 顺序，是排查噩梦
                log.error("[ScriptEngine] 脚本编码冲突：[{}] 同时来自 {} 和 {}，后者已忽略",
                        file.scriptCode(), previous, filePath);
                continue;
            }
            parsed.add(file);
        }
        return parsed;
    }

    private String describe(Resource resource) {
        try {
            String url = resource.getURL().toString().replace('\\', '/');
            int rootAt = url.lastIndexOf(ScriptFileParser.ROOT);
            return rootAt >= 0 ? url.substring(rootAt) : url;
        } catch (IOException e) {
            return String.valueOf(resource.getFilename());
        }
    }

    // ------------------------------------------------------------------
    // 体检①：语法
    // ------------------------------------------------------------------

    private void checkSyntax(List<ScriptFile> scriptFiles) {
        List<String> errors = new ArrayList<>();
        for (ScriptFile file : scriptFiles) {
            try {
                scriptEngine.check(ExecutableScript.trusted(file.scriptCode(), file.content()));
            } catch (Exception e) {
                errors.add("  " + file.filePath() + " -> " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            log.error("[ScriptEngine] 工作区里有 {} 个脚本文件语法不通过（不影响线上，线上跑的是库里激活的版本）：\n{}",
                    errors.size(), String.join("\n", errors));
        }
    }

    // ------------------------------------------------------------------
    // 种子导入：只补空缺
    // ------------------------------------------------------------------

    /**
     * 🔴 只对「库里一行都没有」的脚本编码插入。已经有版本的一律不碰 ——
     * 哪怕文件比库里新，也不动：那正是以前那套「部署即覆盖」的问题所在。
     */
    private void seed(List<ScriptFile> scriptFiles) {
        if (scriptFiles.isEmpty()) {
            return;
        }
        Set<String> known = scriptManager.lambdaQuery()
                .select(Script::getScriptCode)
                .list().stream()
                .map(Script::getScriptCode)
                .collect(Collectors.toSet());

        List<Script> seeds = scriptFiles.stream()
                .filter(file -> !known.contains(file.scriptCode()))
                .map(this::toEntity)
                .toList();
        if (seeds.isEmpty()) {
            return;
        }
        scriptManager.saveBatch(seeds);
        log.info("[ScriptEngine] 种子导入 {} 个脚本（库里原本没有，已作为 v1 激活）：{}",
                seeds.size(), seeds.stream().map(Script::getScriptCode).toList());
    }

    private Script toEntity(ScriptFile file) {
        ScriptScene scene = file.scene();
        Script target = new Script();
        target.setScriptCode(file.scriptCode());
        target.setScriptName(file.name());
        target.setDomain(file.domain().name());
        target.setScene(scene.name());
        target.setFilePath(file.filePath());
        target.setContent(file.content());
        target.setContentHash(file.contentHash());
        target.setVersion(1);
        target.setActiveFlag(Boolean.TRUE);
        target.setSource(ScriptSourceEnum.FILE);
        target.setParamsSchema(JsonUtils.toJson(scene.getParams().stream()
                .map(param -> Map.of(
                        "name", param.name(),
                        "type", param.type().getSimpleName(),
                        "required", param.required(),
                        "description", param.description()))
                .toList()));
        target.setReturnType(scene.getReturnType().getSimpleName());
        target.setDescription(file.description());
        target.setChangeLog("从项目文件种子导入");
        return target;
    }

    // ------------------------------------------------------------------
    // 体检②：工作区与线上的差异
    // ------------------------------------------------------------------

    /**
     * 文件内容与线上激活版本不一致时打一行日志。
     *
     * <p>这不是错误 —— 开发改了文件还没发布，是完全正常的中间状态。
     * 但它是<b>唯一</b>能看出「有人改了工作区却忘了上线」的地方，值得每次启动说一声。
     */
    private void reportDrift(List<ScriptFile> scriptFiles) {
        if (scriptFiles.isEmpty()) {
            return;
        }
        Map<String, Script> active = scriptManager.lambdaQuery()
                .eq(Script::getActiveFlag, true)
                .list().stream()
                .collect(Collectors.toMap(Script::getScriptCode, script -> script, (a, b) -> a));

        List<String> drifted = scriptFiles.stream()
                .filter(file -> {
                    Script online = active.get(file.scriptCode());
                    return online != null && !file.contentHash().equals(online.getContentHash());
                })
                .map(file -> String.format("  %s（线上 v%d）", file.scriptCode(),
                        active.get(file.scriptCode()).getVersion()))
                .toList();

        if (!drifted.isEmpty()) {
            log.info("[ScriptEngine] 以下脚本的工作区文件与线上激活版本不一致，线上跑的是库里的版本：\n{}",
                    String.join("\n", drifted));
        }
    }

    // ------------------------------------------------------------------
    // 体检③：挂载指向了没有激活版本的脚本
    // ------------------------------------------------------------------

    private void reportDanglingRefs() {
        Set<String> activeCodes = scriptManager.lambdaQuery()
                .select(Script::getScriptCode)
                .eq(Script::getActiveFlag, true)
                .list().stream()
                .map(Script::getScriptCode)
                .collect(Collectors.toSet());

        List<ScriptRef> dangling = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getStatus, 1)
                .list().stream()
                .filter(ref -> !activeCodes.contains(ref.getScriptCode()))
                .toList();

        if (dangling.isEmpty()) {
            return;
        }
        String detail = dangling.stream()
                .map(ref -> String.format("  %s [%s%s] 挂着 [%s]，但该脚本没有激活版本",
                        ScriptRefPoint.of(ref.getRefType(), ref.getRefSlot())
                                .map(ScriptRefPoint::getTitle).orElse(ref.getRefType() + "/" + ref.getRefSlot()),
                        ref.getRefId(),
                        ref.getRefKey() == null || ref.getRefKey().isEmpty() ? "" : " / " + ref.getRefKey(),
                        ref.getScriptCode()))
                .collect(Collectors.joining(System.lineSeparator()));
        // 这些业务对象会在真被触发时抛异常。不阻塞启动，理由见类注释
        log.error("""
                [ScriptEngine] 存在悬空挂载，这些业务对象一旦被触发就会报错：
                {}
                处理方式二选一：到「脚本管理」激活一个版本，或摘除这些挂载。""", detail);
    }
}
