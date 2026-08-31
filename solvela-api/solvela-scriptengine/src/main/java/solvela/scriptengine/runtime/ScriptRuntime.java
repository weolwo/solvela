package solvela.scriptengine.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.exception.BusinessException;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptRefPoint;
import solvela.scriptengine.store.ScriptStore;

import java.util.Optional;

/**
 * 业务侧执行脚本的<b>唯一入口</b>。
 *
 * <p>业务代码不应该自己拼 {@code ExecutableScript}，也不应该自己查 {@code t_script_ref} ——
 * 它只需要说清楚「我是哪个挂载点、我的业务对象编码是什么」，剩下的都在这里：
 *
 * <pre>
 * Optional&lt;Boolean&gt; allowed = scriptRuntime.evaluate(
 *         ScriptRefPoint.PRIZE_POOL_ENTRY, poolCode,
 *         EngineContext.create()
 *                 .bind("memberId", memberId)
 *                 .bind("activityCode", activityCode)
 *                 .bind("poolCode", poolCode),
 *         Boolean.class);
 *
 * // empty = 这个奖池没挂准入脚本，按「不限制」处理
 * if (allowed.isPresent() &amp;&amp; !allowed.get()) {
 *     return 拒绝;
 * }
 * </pre>
 *
 * <p><b>返回 {@code Optional} 而不是 boolean 是刻意的</b>：「没挂脚本」和「脚本判定为 false」
 * 是两件完全不同的事，绝大多数业务对象都没挂脚本，把两者混成一个 false
 * 会让「忘了挂脚本」和「脚本拒绝」长得一模一样。默认行为该是什么，由调用方决定。
 *
 * <h3>多值槽位</h3>
 * 事件订阅这类槽位上，同一个业务对象会按 key（如事件编码）挂 N 个脚本，
 * 用带 {@code refKey} 的重载。单值槽位用不带 key 的重载即可，
 * 键的规整统一交给 {@link ScriptRefPoint#normalizeKey}，调用方不需要知道空串这回事。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptRuntime {

    private final ScriptStore scriptStore;

    private final ScriptRefManager scriptRefManager;

    private final ScriptEngine scriptEngine;

    /**
     * 取<b>单值槽位</b>上的脚本并按场景契约执行。
     *
     * @param point      挂载点，它自带期望的场景
     * @param refId      业务对象编码（pool_code / template_code / activity_code）
     * @param context    执行上下文，必须满足 {@code point.getExpectedScene()} 的入参契约
     * @param returnType 返回值类型，必须与场景声明一致
     * @return 该挂载点没挂脚本、或挂的脚本没有激活版本时返回 {@link Optional#empty()}
     */
    public <T> Optional<T> evaluate(ScriptRefPoint point, String refId,
                                    EngineContext context, Class<T> returnType) {
        return evaluate(point, refId, ScriptRefPoint.SINGLE_KEY, context, returnType);
    }

    /**
     * 取<b>多值槽位</b>上某个 key 对应的脚本并执行。
     *
     * @param refKey 槽位内的分组键，含义由挂载点定义（如事件编码）
     * @throws BusinessException 挂了脚本但该脚本没有激活版本、入参不满足契约、返回值不合契约
     */
    public <T> Optional<T> evaluate(ScriptRefPoint point, String refId, String refKey,
                                    EngineContext context, Class<T> returnType) {
        Optional<String> scriptCode = findBoundScriptCode(point, refId, refKey);
        if (scriptCode.isEmpty()) {
            return Optional.empty();
        }

        ExecutableScript script = scriptStore.findActive(scriptCode.get())
                .orElseThrow(() -> new BusinessException(String.format(
                        "%s [%s] 挂载的脚本 [%s] 没有激活版本。"
                                + "多半是版本被停用了但引用还挂着 —— 请到「脚本管理」激活一个版本，或摘除挂载。",
                        point.getTitle(), describe(refId, refKey), scriptCode.get())));

        return Optional.of(scriptEngine.evaluate(point.getExpectedScene(), script, context, returnType));
    }

    /**
     * 查某个单值挂载点当前挂的是哪个脚本。
     */
    public Optional<String> findBoundScriptCode(ScriptRefPoint point, String refId) {
        return findBoundScriptCode(point, refId, ScriptRefPoint.SINGLE_KEY);
    }

    /**
     * 查某个挂载点 + key 当前挂的是哪个脚本。
     *
     * <p>每次执行一次唯一索引查询（{@code uk_script_ref_point}）。引用变更极少，
     * 后续如果这条路径成为热点，这里是最该加缓存的地方 —— 但缓存要连着失效一起做，
     * 现在不做是因为还没有证据说明它是瓶颈。
     */
    public Optional<String> findBoundScriptCode(ScriptRefPoint point, String refId, String refKey) {
        if (refId == null || refId.isBlank()) {
            return Optional.empty();
        }
        return scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getRefType, point.getRefType())
                .eq(ScriptRef::getRefId, refId)
                .eq(ScriptRef::getRefSlot, point.getRefSlot())
                .eq(ScriptRef::getRefKey, point.normalizeKey(refKey))
                .eq(ScriptRef::getStatus, 1)
                .oneOpt()
                .map(ScriptRef::getScriptCode);
    }

    private String describe(String refId, String refKey) {
        return refKey == null || refKey.isEmpty() ? refId : refId + " / " + refKey;
    }
}
