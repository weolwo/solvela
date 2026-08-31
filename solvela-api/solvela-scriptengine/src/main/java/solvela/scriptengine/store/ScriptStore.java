package solvela.scriptengine.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.scriptengine.Script;
import solvela.scriptengine.core.ScriptEngineProperties;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.manager.ScriptManager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行期取「当前激活的脚本内容」的<b>唯一入口</b>。
 *
 * <p>{@code t_script} 里一个 {@code script_code} 有 N 行，本类负责找出 {@code active_flag = 1}
 * 的那一行，并把结果缓存一小段时间。业务代码不该自己查这张表 ——
 * 自己查的地方就是下一个忘记处理「激活版本换了」的地方。
 *
 * <h3>缓存为什么必须带 TTL，而不是永久缓存 + 主动失效</h3>
 * 后台是在 <b>admin 进程</b>里点的激活，而脚本是在 <b>marketing 进程</b>里执行的。
 * {@link #evict} 只能清掉本进程的那一份，别的进程<b>收不到任何通知</b>。
 * 永久缓存的后果是：运营点了发布、admin 里预览也对，线上却一直跑老版本，
 * 而且<b>重启之前不会自愈</b> —— 这种 bug 查起来极贵。
 *
 * <p>所以到期就回库看一眼版本号，{@link ScriptEngineProperties#getActiveCacheMillis()}
 * 就是跨进程生效的延迟上限。
 *
 * <h3>「没有激活版本」也缓存</h3>
 * 绝大多数挂载点没挂脚本，或挂了但没激活。不缓存缺失的话，
 * 每一次抽奖都会为「确认它确实没有」打一次库 —— 这是缓存最容易漏掉的一半。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptStore {

    private final ScriptManager scriptManager;

    private final ScriptEngineProperties properties;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /**
     * 取某个脚本当前激活版本的可执行体。没有激活版本时返回 empty。
     */
    public Optional<ExecutableScript> findActive(String scriptCode) {
        return Optional.ofNullable(resolve(scriptCode).script());
    }

    /**
     * 取某个脚本当前激活版本的完整行（含 version、scene 等元数据），供管理页与校验用。
     *
     * <p>⚠️ 这个方法<b>不走缓存</b>：它的调用方是后台页面，要看到的是此刻库里的真相，
     * 而不是运行期为了少打几次库而留的快照。
     */
    public Optional<Script> findActiveRow(String scriptCode) {
        return activeRow(scriptCode);
    }

    /**
     * 清掉本进程对某个脚本的缓存。激活/回滚之后调用，让当前进程立刻生效。
     *
     * <p>🔴 <b>只影响本进程。</b>别的进程要等 TTL 自己到期 —— 这是设计，不是遗漏，理由见类注释。
     */
    public void evict(String scriptCode) {
        cache.remove(scriptCode);
    }

    public void evictAll() {
        cache.clear();
    }

    private Cached resolve(String scriptCode) {
        Cached cached = cache.get(scriptCode);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAt() > now) {
            return cached;
        }

        Script row = activeRow(scriptCode).orElse(null);
        long expireAt = now + properties.getActiveCacheMillis();

        if (row == null) {
            Cached miss = new Cached(null, null, expireAt);
            cache.put(scriptCode, miss);
            return miss;
        }
        // 版本号没变就复用原来的可执行体，省掉一次对象构造与引擎的编译缓存查找
        if (cached != null && row.getVersion().equals(cached.version())) {
            Cached refreshed = new Cached(cached.version(), cached.script(), expireAt);
            cache.put(scriptCode, refreshed);
            return refreshed;
        }

        if (cached != null && cached.version() != null) {
            log.info("[ScriptEngine] 脚本 [{}] 激活版本变化：v{} -> v{}",
                    scriptCode, cached.version(), row.getVersion());
        }
        // TRUSTED：库里的激活脚本是<b>有界集合</b>（脚本数可数、内容稳定），
        // 引擎按原文缓存编译产物是安全的。判据见 ScriptSource 的类注释 ——
        // 关键性质从来是「集合有界」，而不是「文件在 git 里」
        ExecutableScript executable = ExecutableScript.trusted(scriptCode, row.getContent());
        Cached fresh = new Cached(row.getVersion(), executable, expireAt);
        cache.put(scriptCode, fresh);
        return fresh;
    }

    private Optional<Script> activeRow(String scriptCode) {
        if (scriptCode == null || scriptCode.isBlank()) {
            return Optional.empty();
        }
        // 走唯一键 uk_script_active (script_code, active_flag)
        return scriptManager.lambdaQuery()
                .eq(Script::getScriptCode, scriptCode)
                .eq(Script::getActiveFlag, true)
                .oneOpt();
    }

    /**
     * @param version 激活版本号；{@code null} 表示「查过了，确实没有激活版本」
     * @param script  可执行体，与 version 同生同灭
     */
    private record Cached(Integer version, ExecutableScript script, long expireAt) {
    }
}
