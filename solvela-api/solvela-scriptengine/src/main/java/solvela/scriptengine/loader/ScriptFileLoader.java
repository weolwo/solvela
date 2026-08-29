package solvela.scriptengine.loader;

import solvela.enums.EnableStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.Script;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.manager.ScriptManager;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.scriptengine.spi.ScriptScene;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动期把 {@code resources/scripts/} 下的脚本文件加载进来。
 *
 * <p>五件事，顺序不能变：
 * <ol>
 *   <li><b>扫描 + 解析文件头</b> —— 格式不对、场景不存在、目录放错，直接抛；</li>
 *   <li><b>语法校验</b> —— 调 {@code ScriptEngine.check()}，<b>一个脚本语法错，整个应用起不来</b>；</li>
 *   <li><b>同步进 t_script</b> —— 按 content_hash 判断有没有变，变了才写、version +1；</li>
 *   <li><b>引用完整性校验</b> —— 还有业务对象挂着的脚本，文件不许消失；</li>
 *   <li><b>常驻内存</b> —— 运行期按 script_code 取，不查库。</li>
 * </ol>
 *
 * <p>🔴 <b>第 2 步是这套方案相对「脚本存数据库」的核心收益</b>：坏脚本进不了生产。
 * 存数据库的方案里，语法错误最早也要等运营点保存那一刻才知道，而且那时它已经在库里了。
 *
 * <p>为什么是 {@link SmartInitializingSingleton} 而不是 {@code ApplicationRunner}：
 * 前者在容器完全就绪、但 Web 容器还没开始收流量之前跑完，
 * 校验失败时外部一个请求都还没进来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScriptFileLoader implements SmartInitializingSingleton {

    private static final String LOCATION_PATTERN =
            ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + ScriptFileParser.ROOT + "**/*" + ScriptFileParser.EXTENSION;

    private final ScriptEngine scriptEngine;

    private final ScriptManager scriptManager;

    private final ScriptRefManager scriptRefManager;

    /**
     * script_code -> 可执行体。运行期只读这里，不查库
     */
    private final Map<String, ExecutableScript> cache = new LinkedHashMap<>();

    /**
     * script_code -> 文件元数据，供管理页展示
     */
    private final Map<String, ScriptFile> files = new LinkedHashMap<>();

    @Override
    public void afterSingletonsInstantiated() {
        List<ScriptFile> scriptFiles = scanAndParse();
        checkSyntax(scriptFiles);
        syncToDatabase(scriptFiles);
        checkRefIntegrity(scriptFiles);
        fillCache(scriptFiles);
        logSummary(scriptFiles);
    }

    // ------------------------------------------------------------------
    // 运行期读取入口
    // ------------------------------------------------------------------

    /**
     * 按 script_code 取可执行体。取不到返回 empty。
     */
    public Optional<ExecutableScript> find(String scriptCode) {
        return Optional.ofNullable(cache.get(scriptCode));
    }

    /**
     * 按 script_code 取文件元数据（场景、名称、描述等）
     */
    public Optional<ScriptFile> findFile(String scriptCode) {
        return Optional.ofNullable(files.get(scriptCode));
    }

    public Collection<ScriptFile> allFiles() {
        return files.values();
    }

    // ------------------------------------------------------------------
    // ① 扫描 + 解析
    // ------------------------------------------------------------------

    private List<ScriptFile> scanAndParse() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("扫描脚本目录失败：" + LOCATION_PATTERN, e);
        }

        Map<String, String> seen = new LinkedHashMap<>();
        List<ScriptFile> parsed = new ArrayList<>();
        for (Resource resource : resources) {
            String filePath = describe(resource);
            String content;
            try (var in = resource.getInputStream()) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("读取脚本文件失败：" + filePath, e);
            }

            ScriptFile file = ScriptFileParser.parse(filePath, content);
            // 同 code 冲突要炸：两个 jar 里放了同名脚本时，谁生效完全看 classpath 顺序
            String previous = seen.putIfAbsent(file.scriptCode(), filePath);
            if (previous != null) {
                throw new IllegalStateException(String.format(
                        "脚本编码冲突：[%s] 同时来自 %s 和 %s", file.scriptCode(), previous, filePath));
            }
            parsed.add(file);
        }
        return parsed;
    }

    /**
     * 从 Resource 还原出 classpath 相对路径，报错信息里要能直接定位到文件
     */
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
    // ② 语法校验：坏脚本不许进生产
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
            // 一次性报全部错误，而不是修一个重启一次
            throw new IllegalStateException("以下脚本语法校验失败，应用拒绝启动：\n" + String.join("\n", errors));
        }
    }

    // ------------------------------------------------------------------
    // ③ 同步进 t_script
    // ------------------------------------------------------------------

    private void syncToDatabase(List<ScriptFile> scriptFiles) {
        Map<String, Script> existing = scriptManager.list().stream()
                .collect(Collectors.toMap(Script::getScriptCode, script -> script, (a, b) -> a));

        int inserted = 0;
        int updated = 0;
        for (ScriptFile file : scriptFiles) {
            Script current = existing.get(file.scriptCode());
            if (current == null) {
                scriptManager.save(toEntity(file, new Script(), 1));
                inserted++;
            } else if (!file.contentHash().equals(current.getContentHash())) {
                // 内容变了才写，且 version +1 —— 让「这个脚本改过几次」在库里看得出来
                Script next = toEntity(file, current, current.getVersion() + 1);
                scriptManager.updateById(next);
                updated++;
            }
        }

        // 文件里已经删掉的脚本：不物理删（可能还有 ref 指着它），停用并告警
        List<String> codes = scriptFiles.stream().map(ScriptFile::scriptCode).toList();
        List<Script> orphans = existing.values().stream()
                .filter(script -> !codes.contains(script.getScriptCode()))
                .filter(script -> script.getStatus() == EnableStatusEnum.ENABLED)
                .toList();
        if (!orphans.isEmpty()) {
            orphans.forEach(script -> script.setStatus(EnableStatusEnum.DISABLED));
            scriptManager.updateBatchById(orphans);
            log.warn("[ScriptEngine] 以下脚本在文件里已不存在，已自动停用（未物理删除，可能仍有引用）：{}",
                    orphans.stream().map(Script::getScriptCode).toList());
        }

        if (inserted > 0 || updated > 0) {
            log.info("[ScriptEngine] t_script 同步完成：新增 {} 条，更新 {} 条", inserted, updated);
        }
    }

    // ------------------------------------------------------------------
    // ④ 引用完整性：挂着的脚本，文件不许消失
    // ------------------------------------------------------------------

    /**
     * 校验 {@code t_script_ref} 里每一条启用中的引用，指向的脚本文件都还在。
     *
     * <p><b>为什么是启动失败而不是打条 WARN：</b>一条悬空引用的含义是
     * 「某个奖池/活动/任务模板挂着一个已经不存在的脚本」——
     * 它不会立刻出事，而是等到某天真有用户触发那个奖池时才抛异常。
     * 这套系统别的地方都是「能在启动期发现就绝不留到运行期」，这里没有理由破例。
     *
     * <p>报错会列出<b>全部</b>悬空引用与处理方式，而不是只报第一条。
     */
    private void checkRefIntegrity(List<ScriptFile> scriptFiles) {
        Set<String> alive = scriptFiles.stream().map(ScriptFile::scriptCode).collect(Collectors.toSet());

        List<ScriptRef> dangling = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getStatus, 1)
                .list().stream()
                .filter(ref -> !alive.contains(ref.getScriptCode()))
                .toList();

        if (dangling.isEmpty()) {
            return;
        }
        String detail = dangling.stream()
                .map(ref -> String.format("  %s [%s] 挂着 [%s]，但该脚本文件已不存在",
                        ScriptRefPoint.of(ref.getRefType(), ref.getRefSlot())
                                .map(ScriptRefPoint::getTitle).orElse(ref.getRefType() + "/" + ref.getRefSlot()),
                        ref.getRefId(), ref.getScriptCode()))
                .collect(Collectors.joining(System.lineSeparator()));
        throw new IllegalStateException("""
                存在悬空的脚本引用，应用拒绝启动（否则这些业务对象会在真被触发时才报错）：
                %s
                
                处理方式二选一：把脚本文件加回 resources/scripts/，或在「脚本管理」页摘除这些挂载。"""
                .formatted(detail));
    }

    private Script toEntity(ScriptFile file, Script target, int version) {
        ScriptScene scene = file.scene();
        target.setScriptCode(file.scriptCode());
        target.setScriptName(file.name());
        target.setDomain(file.domain().name());
        target.setScene(scene.name());
        target.setFilePath(file.filePath());
        target.setContent(file.content());
        target.setContentHash(file.contentHash());
        target.setVersion(version);
        // params_schema 不人工维护，直接由场景契约生成 —— 两处定义必然漂移，一处生成不会
        target.setParamsSchema(JsonUtils.toJson(scene.getParams().stream()
                .map(param -> Map.of(
                        "name", param.name(),
                        "type", param.type().getSimpleName(),
                        "required", param.required(),
                        "description", param.description()))
                .toList()));
        target.setReturnType(scene.getReturnType().getSimpleName());
        target.setDescription(file.description());
        target.setStatus(EnableStatusEnum.ENABLED);
        return target;
    }

    // ------------------------------------------------------------------
    // ④ 常驻内存
    // ------------------------------------------------------------------

    private void fillCache(List<ScriptFile> scriptFiles) {
        cache.clear();
        files.clear();
        for (ScriptFile file : scriptFiles) {
            // trusted：来自项目内 git 管理的文件，引擎可以放心缓存编译产物
            cache.put(file.scriptCode(), ExecutableScript.trusted(file.scriptCode(), file.content()));
            files.put(file.scriptCode(), file);
        }
    }

    private void logSummary(List<ScriptFile> scriptFiles) {
        if (scriptFiles.isEmpty()) {
            log.info("[ScriptEngine] 未发现任何脚本文件（{}）", LOCATION_PATTERN);
            return;
        }
        Map<ScriptScene, Long> bySceneCount = scriptFiles.stream()
                .collect(Collectors.groupingBy(ScriptFile::scene, Collectors.counting()));
        String detail = bySceneCount.entrySet().stream()
                .map(entry -> String.format("  %-16s %-12s %d 个", entry.getKey().name(),
                        entry.getKey().getTitle(), entry.getValue()))
                .collect(Collectors.joining("\n"));
        log.info("[ScriptEngine] 脚本文件加载完成，共 {} 个：\n{}", scriptFiles.size(), detail);
    }
}
