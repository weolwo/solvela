package solvela.scriptengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.exception.BusinessException;
import solvela.scriptengine.Script;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.domain.dto.ScriptDTO;
import solvela.scriptengine.manager.ScriptManager;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptScene;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 脚本的只读查询。
 *
 * <p>{@code t_script} 一行是一个<b>版本</b>，所以这里有两种列表：
 * {@link #listActive()} 是「有哪些脚本」，{@link #listVersions} 是「这个脚本改过几版」。
 * 把两者混成一个列表页会让人看不出哪一版在跑，那正是版本化最要回答的问题。
 */
@Service
@RequiredArgsConstructor
public class ScriptQueryService {

    private final ScriptManager scriptManager;

    private final ScriptRefManager scriptRefManager;

    /**
     * 脚本列表：每个编码<b>一行</b>，取当前激活的那一版。
     *
     * <p>没有任何激活版本的脚本也会出现在这里（版本号为空）—— 它们正是最需要被看见的：
     * 挂载点指着它就会在运行期报错。藏起来等于把问题藏起来。
     */
    public List<ScriptDTO> listActive() {
        Map<String, Long> refCounts = refCounts();
        Map<String, List<Script>> byCode = scriptManager.list().stream()
                .collect(Collectors.groupingBy(Script::getScriptCode));

        return byCode.values().stream()
                .map(versions -> versions.stream()
                        .filter(script -> Boolean.TRUE.equals(script.getActiveFlag()))
                        .findFirst()
                        // 没有激活版本时用最新的那一版占位，至少让名称与场景能显示出来
                        .orElseGet(() -> versions.stream()
                                .max(Comparator.comparing(Script::getVersion))
                                .orElseThrow()))
                .map(script -> toDTO(script, refCounts.getOrDefault(script.getScriptCode(), 0L).intValue(), false))
                .sorted(Comparator.comparing(ScriptDTO::getScriptCode))
                .toList();
    }

    /**
     * 某个脚本的全部版本，新的在前。带内容，供后台对比与回滚选择。
     */
    public List<ScriptDTO> listVersions(String scriptCode) {
        int refCount = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getScriptCode, scriptCode)
                .count().intValue();
        List<Script> versions = scriptManager.lambdaQuery()
                .eq(Script::getScriptCode, scriptCode)
                .orderByDesc(Script::getVersion)
                .list();
        if (versions.isEmpty()) {
            throw new BusinessException("脚本 [" + scriptCode + "] 不存在");
        }
        return versions.stream().map(script -> toDTO(script, refCount, true)).toList();
    }

    /**
     * 单个版本的详情，含内容。
     */
    public ScriptDTO detail(Long id) {
        Script script = Optional.ofNullable(scriptManager.getById(id))
                .orElseThrow(() -> new BusinessException("脚本版本 [" + id + "] 不存在"));
        int refCount = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getScriptCode, script.getScriptCode())
                .count().intValue();
        return toDTO(script, refCount, true);
    }

    private Map<String, Long> refCounts() {
        return scriptRefManager.list().stream()
                .collect(Collectors.groupingBy(ScriptRef::getScriptCode, Collectors.counting()));
    }

    private ScriptDTO toDTO(Script script, int refCount, boolean withContent) {
        ScriptDTO vo = new ScriptDTO();
        vo.setId(script.getId());
        vo.setScriptCode(script.getScriptCode());
        vo.setScriptName(script.getScriptName());
        vo.setDomain(script.getDomain());
        vo.setScene(script.getScene());
        vo.setReturnType(script.getReturnType());
        vo.setDescription(script.getDescription());
        vo.setFilePath(script.getFilePath());
        vo.setSource(script.getSource());
        vo.setChangeLog(script.getChangeLog());
        vo.setVersion(script.getVersion());
        vo.setActive(Boolean.TRUE.equals(script.getActiveFlag()));
        vo.setRefCount(refCount);
        vo.setCreateBy(script.getCreateBy());
        vo.setCreateTime(script.getCreateTime());
        vo.setUpdateTime(script.getUpdateTime());
        // 中文名从枚举取而不是存库：枚举改了名，历史行不用回刷
        enumValue(script.getDomain(), ScriptDomain::valueOf, ScriptDomain::getTitle).ifPresent(vo::setDomainTitle);
        enumValue(script.getScene(), ScriptScene::valueOf, ScriptScene::getTitle).ifPresent(vo::setSceneTitle);
        if (withContent) {
            vo.setContent(script.getContent());
        }
        return vo;
    }

    /**
     * 库里存的枚举名可能已经在代码里删掉了（比如场景重命名），取不到就留空，不要抛
     */
    private <E> Optional<String> enumValue(String name, Function<String, E> parser, Function<E, String> title) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(title.apply(parser.apply(name)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
