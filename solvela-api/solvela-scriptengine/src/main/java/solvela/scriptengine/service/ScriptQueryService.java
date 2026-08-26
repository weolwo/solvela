package solvela.scriptengine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.exception.BusinessException;
import solvela.scriptengine.Script;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.domain.vo.ScriptVO;
import solvela.scriptengine.manager.ScriptManager;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.ScriptDomain;
import solvela.scriptengine.spi.ScriptScene;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 脚本的只读查询。
 *
 * <p>数据源是 {@code t_script}（文件的镜像）而不是内存缓存 —— 这样列表页能顺带看到
 * 「文件已删除但还被引用」的停用记录，那是内存里没有的。
 */
@Service
@RequiredArgsConstructor
public class ScriptQueryService {

    private final ScriptManager scriptManager;

    private final ScriptRefManager scriptRefManager;

    public List<ScriptVO> listAll() {
        Map<String, Long> refCounts = scriptRefManager.list().stream()
                .collect(Collectors.groupingBy(ScriptRef::getScriptCode, Collectors.counting()));
        return scriptManager.list().stream()
                .map(script -> toVO(script, refCounts.getOrDefault(script.getScriptCode(), 0L).intValue(), false))
                .sorted(java.util.Comparator.comparing(ScriptVO::getScriptCode))
                .toList();
    }

    public ScriptVO detail(String scriptCode) {
        Script script = scriptManager.lambdaQuery()
                .eq(Script::getScriptCode, scriptCode)
                .oneOpt()
                .orElseThrow(() -> new BusinessException("脚本 [" + scriptCode + "] 不存在"));
        long refCount = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getScriptCode, scriptCode)
                .count();
        return toVO(script, (int) refCount, true);
    }

    private ScriptVO toVO(Script script, int refCount, boolean withContent) {
        ScriptVO vo = new ScriptVO();
        vo.setScriptCode(script.getScriptCode());
        vo.setScriptName(script.getScriptName());
        vo.setDomain(script.getDomain());
        vo.setScene(script.getScene());
        vo.setReturnType(script.getReturnType());
        vo.setDescription(script.getDescription());
        vo.setFilePath(script.getFilePath());
        vo.setVersion(script.getVersion());
        vo.setStatus(script.getStatus());
        vo.setUpdateTime(script.getUpdateTime());
        vo.setRefCount(refCount);
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
    private <E> java.util.Optional<String> enumValue(String name, Function<String, E> parser, Function<E, String> title) {
        if (name == null || name.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(title.apply(parser.apply(name)));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
