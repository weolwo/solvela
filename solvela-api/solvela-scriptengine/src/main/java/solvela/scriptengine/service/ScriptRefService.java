package solvela.scriptengine.service;

import solvela.enums.EnableStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.exception.BusinessException;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.Script;
import solvela.scriptengine.domain.dto.ScriptRefDTO;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.scriptengine.spi.ScriptScene;
import solvela.scriptengine.store.ScriptStore;

import java.util.List;
import java.util.Optional;

/**
 * 脚本挂载关系的读写。
 *
 * <p>这套东西存在的<b>唯一理由</b>是回答一个问题：
 * 「改这个脚本会影响哪些业务对象」——见 {@link #findRefsOfScript}。
 * 所有设计取舍都以能不能干净地回答它为准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptRefService {

    private final ScriptRefManager scriptRefManager;

    private final ScriptStore scriptStore;

    /**
     * 单值槽位的挂载。等价于 {@code bind(point, refId, "", scriptCode, operator)}。
     */
    public void bind(ScriptRefPoint point, String refId, String scriptCode, String operator) {
        bind(point, refId, ScriptRefPoint.SINGLE_KEY, scriptCode, operator);
    }

    /**
     * 把脚本挂到某个挂载点上。同一个挂载点 + 同一个 key 只能挂一个脚本，重复挂等于替换。
     *
     * <p>🔴 <b>会校验脚本的场景与挂载点期望的场景一致</b> ——
     * 「把任务判定脚本挂到奖池准入上」这种错误在写入的这一刻就被拒绝，
     * 而不是等某天真有人来抽奖时才炸。
     *
     * <p>校验的依据是脚本<b>当前激活版本</b>的 scene。没有激活版本的脚本不许挂：
     * 挂上去也是一个执行时才报错的空壳。
     */
    public void bind(ScriptRefPoint point, String refId, String refKey, String scriptCode, String operator) {
        if (refId == null || refId.isBlank()) {
            throw new BusinessException("业务对象编码不能为空");
        }
        String normalizedKey = point.normalizeKey(refKey);

        Script active = scriptStore.findActiveRow(scriptCode).orElseThrow(() -> new BusinessException(
                "脚本 [" + scriptCode + "] 没有激活版本，不能挂载。请先到「脚本管理」激活一个版本"));

        ScriptScene scene = ScriptScene.of(active.getScene()).orElseThrow(() -> new BusinessException(
                "脚本 [" + scriptCode + "] 的场景 [" + active.getScene() + "] 在代码里已不存在，无法挂载"));

        if (scene != point.getExpectedScene()) {
            throw new BusinessException(String.format(
                    "场景不匹配：挂载点 [%s] 要求 %s（%s），而脚本 [%s] 的场景是 %s（%s）",
                    point.getTitle(),
                    point.getExpectedScene().name(), point.getExpectedScene().getTitle(),
                    scriptCode, scene.name(), scene.getTitle()));
        }

        ScriptRef existing = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getRefType, point.getRefType())
                .eq(ScriptRef::getRefId, refId)
                .eq(ScriptRef::getRefSlot, point.getRefSlot())
                .eq(ScriptRef::getRefKey, normalizedKey)
                .oneOpt().orElse(null);

        if (existing == null) {
            ScriptRef ref = new ScriptRef();
            ref.setScriptCode(scriptCode);
            ref.setRefType(point.getRefType());
            ref.setRefId(refId);
            ref.setRefSlot(point.getRefSlot());
            ref.setRefKey(normalizedKey);
            ref.setStatus(EnableStatusEnum.ENABLED);
            ref.setCreateBy(operator);
            ref.setUpdateBy(operator);
            scriptRefManager.save(ref);
        } else {
            existing.setScriptCode(scriptCode);
            existing.setStatus(EnableStatusEnum.ENABLED);
            existing.setUpdateBy(operator);
            scriptRefManager.updateById(existing);
        }
        log.info("[ScriptEngine] 脚本挂载：{} [{}] -> {}（操作人 {}）",
                point.getTitle(), describe(refId, normalizedKey), scriptCode, operator);
    }

    /**
     * 单值槽位的摘除。
     */
    public void unbind(ScriptRefPoint point, String refId) {
        unbind(point, refId, ScriptRefPoint.SINGLE_KEY);
    }

    /**
     * 摘除挂载。物理删除 —— 引用关系没有保留历史的价值，谁挂的、什么时候挂的在操作日志里。
     */
    public void unbind(ScriptRefPoint point, String refId, String refKey) {
        String normalizedKey = point.normalizeKey(refKey);
        Optional<ScriptRef> existing = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getRefType, point.getRefType())
                .eq(ScriptRef::getRefId, refId)
                .eq(ScriptRef::getRefSlot, point.getRefSlot())
                .eq(ScriptRef::getRefKey, normalizedKey)
                .oneOpt();
        existing.ifPresent(ref -> {
            scriptRefManager.removeById(ref.getId());
            log.info("[ScriptEngine] 脚本摘除：{} [{}] -x- {}",
                    point.getTitle(), describe(refId, normalizedKey), ref.getScriptCode());
        });
    }

    /**
     * 🌟 <b>改这个脚本会影响谁。</b>整套引用表就是为这个方法存在的。
     */
    public List<ScriptRefDTO> findRefsOfScript(String scriptCode) {
        return scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getScriptCode, scriptCode)
                .list().stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 某个业务对象身上挂了哪些脚本
     */
    public List<ScriptRefDTO> findRefsOfOwner(String refType, String refId) {
        return scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getRefType, refType)
                .eq(ScriptRef::getRefId, refId)
                .list().stream()
                .map(this::toVO)
                .toList();
    }

    private ScriptRefDTO toVO(ScriptRef ref) {
        ScriptRefDTO vo = new ScriptRefDTO();
        vo.setId(ref.getId());
        vo.setScriptCode(ref.getScriptCode());
        vo.setRefType(ref.getRefType());
        vo.setRefId(ref.getRefId());
        vo.setRefSlot(ref.getRefSlot());
        vo.setRefKey(ref.getRefKey());
        vo.setStatus(ref.getStatus());
        vo.setUpdateTime(ref.getUpdateTime());
        ScriptRefPoint.of(ref.getRefType(), ref.getRefSlot()).ifPresent(point -> {
            vo.setRefPoint(point.name());
            vo.setRefPointTitle(point.getTitle());
        });
        scriptStore.findActiveRow(ref.getScriptCode())
                .ifPresent(script -> {
                    vo.setScriptName(script.getScriptName());
                    vo.setScriptVersion(script.getVersion());
                });
        return vo;
    }

    private String describe(String refId, String refKey) {
        return refKey == null || refKey.isEmpty() ? refId : refId + " / " + refKey;
    }
}
