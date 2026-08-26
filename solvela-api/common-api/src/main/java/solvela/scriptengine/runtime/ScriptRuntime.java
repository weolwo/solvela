package solvela.scriptengine.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.exception.BusinessException;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.domain.entity.ScriptRef;
import solvela.scriptengine.loader.ScriptFileLoader;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptRefPoint;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptRuntime {

    private final ScriptFileLoader scriptFileLoader;

    private final ScriptRefManager scriptRefManager;

    private final ScriptEngine scriptEngine;

    /**
     * 取挂载点上的脚本并按场景契约执行。
     *
     * @param point      挂载点，它自带期望的场景
     * @param refId      业务对象编码（pool_code / template_code / activity_code）
     * @param context    执行上下文，必须满足 {@code point.getExpectedScene()} 的入参契约
     * @param returnType 返回值类型，必须与场景声明一致
     * @return 该挂载点没挂启用中的脚本时返回 {@link Optional#empty()}，这是正常情况
     * @throws BusinessException 挂了脚本但脚本文件不存在、入参不满足契约、返回值不合契约
     */
    public <T> Optional<T> evaluate(ScriptRefPoint point, String refId,
                                    EngineContext context, Class<T> returnType) {
        Optional<String> scriptCode = findBoundScriptCode(point, refId);
        if (scriptCode.isEmpty()) {
            return Optional.empty();
        }

        ExecutableScript script = scriptFileLoader.find(scriptCode.get())
                .orElseThrow(() -> new BusinessException(String.format(
                        "%s [%s] 挂载的脚本 [%s] 在项目里不存在。"
                                + "多半是脚本文件被删了但引用没摘除 —— 请检查 t_script_ref。",
                        point.getTitle(), refId, scriptCode.get())));

        return Optional.of(scriptEngine.evaluate(point.getExpectedScene(), script, context, returnType));
    }

    /**
     * 查某个挂载点当前挂的是哪个脚本。
     *
     * <p>每次执行一次唯一索引查询（{@code uk_script_ref_point}）。引用变更极少，
     * 后续如果这条路径成为热点，这里是最该加缓存的地方 —— 但缓存要连着失效一起做，
     * 现在不做是因为还没有证据说明它是瓶颈。
     */
    public Optional<String> findBoundScriptCode(ScriptRefPoint point, String refId) {
        if (refId == null || refId.isBlank()) {
            return Optional.empty();
        }
        return scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getRefType, point.getRefType())
                .eq(ScriptRef::getRefId, refId)
                .eq(ScriptRef::getRefSlot, point.getRefSlot())
                .eq(ScriptRef::getStatus, 1)
                .oneOpt()
                .map(ScriptRef::getScriptCode);
    }
}
