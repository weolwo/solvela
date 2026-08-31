package solvela.scriptengine.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaCodeUtil;
import solvela.enums.ScriptSourceEnum;
import solvela.exception.BusinessException;
import solvela.scriptengine.Script;
import solvela.scriptengine.ScriptRef;
import solvela.scriptengine.domain.ExecutableScript;
import solvela.scriptengine.domain.ScriptSaveCommand;
import solvela.scriptengine.manager.ScriptManager;
import solvela.scriptengine.manager.ScriptRefManager;
import solvela.scriptengine.spi.ScriptEngine;
import solvela.scriptengine.spi.ScriptScene;
import solvela.scriptengine.store.ScriptStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 脚本的写入：<b>保存版本</b> 与 <b>激活版本</b>。
 *
 * <h3>这两件事刻意分开</h3>
 * 保存只是往 {@code t_script} 加一行，线上跑的还是原来那版；激活才改变线上行为。
 * 合成一步的话，「我先存一下、等会儿再看」这种再正常不过的操作会直接改掉生产逻辑。
 *
 * <p>激活与回滚是<b>同一个动作</b> —— 都是把 active 标记挪到某一行上，
 * 所以不存在「回滚功能没测过」这回事：每次发布都在测它。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptEditService {

    private final ScriptManager scriptManager;

    private final ScriptRefManager scriptRefManager;

    private final ScriptEngine scriptEngine;

    private final ScriptStore scriptStore;

    /**
     * 保存一个新版本。<b>不激活</b>（除非这是该编码的第一版，见下）。
     *
     * <p>三道校验，都在写库之前：
     * <ol>
     *   <li><b>场景合法</b>，且与该编码已有版本的场景一致；</li>
     *   <li><b>语法通过</b> —— 坏脚本进不了库；</li>
     *   <li><b>内容确实变了</b> —— 与已有版本一字不差时不产生新版本。</li>
     * </ol>
     *
     * <p>🔴 <b>场景在同一个编码下不许改。</b>改场景意味着入参和返回值契约整个换掉，
     * 而挂载点是按场景校验后才挂上去的 —— 悄悄换掉场景等于让所有已有挂载失效，
     * 且要等真有用户触发时才炸。要换场景，请用一个新的脚本编码。
     *
     * <p>第一版会自动激活：此时这个编码还没被任何东西挂载（挂载要求先有激活版本），
     * 激活它不改变任何线上行为，却省掉一次「保存完为什么用不了」的困惑。
     *
     * @return 新版本的行 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScriptSaveCommand command) {
        ScriptScene scene = ScriptScene.of(command.scene()).orElseThrow(() -> new BusinessException(
                "场景 [" + command.scene() + "] 不存在，合法值见 /script/scene/list"));

        List<Script> versions = scriptManager.lambdaQuery()
                .eq(Script::getScriptCode, command.scriptCode())
                .orderByDesc(Script::getVersion)
                .list();

        if (!versions.isEmpty()) {
            String existingScene = versions.getFirst().getScene();
            if (!existingScene.equals(scene.name())) {
                throw new BusinessException(String.format(
                        "脚本 [%s] 已有版本的场景是 %s，不能改成 %s。"
                                + "换场景等于换掉入参与返回值契约，已有挂载会全部失效 —— 请改用一个新的脚本编码。",
                        command.scriptCode(), existingScene, scene.name()));
            }
        }

        // UNTRUSTED：内容直接来自后台请求。check() 只走语法树、不产生编译缓存，
        // 但来源标注仍要如实写 —— 让「这段内容是谁给的」在任何调用点都一目了然
        scriptEngine.check(ExecutableScript.untrusted(command.scriptCode(), command.content()));

        String hash = sha256(command.content());
        versions.stream()
                .filter(version -> hash.equals(version.getContentHash()))
                .findFirst()
                .ifPresent(same -> {
                    throw new BusinessException(String.format(
                            "内容与 v%d 一字不差，没有产生新版本。要让它生效，直接激活 v%d 即可",
                            same.getVersion(), same.getVersion()));
                });

        boolean first = versions.isEmpty();
        Script row = new Script();
        row.setScriptCode(command.scriptCode());
        row.setScriptName(command.scriptName());
        row.setDomain(scene.getDomain().name());
        row.setScene(scene.name());
        row.setContent(command.content());
        row.setContentHash(hash);
        row.setVersion(first ? 1 : versions.getFirst().getVersion() + 1);
        row.setActiveFlag(first ? Boolean.TRUE : null);
        row.setSource(ScriptSourceEnum.MANUAL);
        // params_schema 不人工维护，直接由场景契约生成 —— 两处定义必然漂移，一处生成不会
        row.setParamsSchema(JsonUtils.toJson(scene.getParams().stream()
                .map(param -> Map.of(
                        "name", param.name(),
                        "type", param.type().getSimpleName(),
                        "required", param.required(),
                        "description", param.description()))
                .toList()));
        row.setReturnType(scene.getReturnType().getSimpleName());
        row.setDescription(command.description());
        row.setChangeLog(command.changeLog());
        row.setCreateBy(command.operator());
        row.setUpdateBy(command.operator());
        scriptManager.save(row);

        if (first) {
            scriptStore.evict(command.scriptCode());
            log.info("[ScriptEngine] 脚本 [{}] 首版已保存并自动激活（操作人 {}）",
                    command.scriptCode(), command.operator());
        } else {
            log.info("[ScriptEngine] 脚本 [{}] 保存 v{}，未激活（操作人 {}）",
                    command.scriptCode(), row.getVersion(), command.operator());
        }
        return row.getId();
    }

    /**
     * 生成一个库里没有的脚本编码。
     *
     * <p>与活动、奖池、奖品用的是<b>同一套</b>编码约定（10 位大写字母数字，首位是业务前缀），
     * 所以运营在哪个页面点「生成」拿到的东西形状都一样。
     *
     * <p>⚠️ 生成的编码是随机串，在日志与报错里的可读性不如 {@code activity/draw_play}
     * 这种手写路径 —— 排查时认的是 {@code scriptName}。手写编码依然合法，
     * 这个按钮只是给不想起名的人一条快路。
     */
    public String generateScriptCode() {
        return SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.SCRIPT,
                code -> scriptManager.lambdaQuery().eq(Script::getScriptCode, code).exists());
    }

    /**
     * 激活某个版本 —— 发布与回滚都是这个方法。
     *
     * <p>两步在同一个事务里：把该编码当前激活的那行置空，再把目标行置为激活。
     * 唯一键 {@code uk_script_active} 保证了「至多一个激活版本」这件事
     * <b>不依赖这段代码写得对</b>：真出现并发，第二个事务会撞唯一键失败，而不是两版同时生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id, String operator) {
        Script target = scriptManager.getById(id);
        if (target == null) {
            throw new BusinessException("脚本版本 [" + id + "] 不存在");
        }
        if (Boolean.TRUE.equals(target.getActiveFlag())) {
            return;
        }

        // 🔴 必须用 UpdateWrapper 显式 set(null)：MyBatis-Plus 的 updateById 默认跳过 null 字段，
        //    拿实体去 update 的话这条「取消激活」会静默不生效，接着目标行就撞上唯一键
        scriptManager.update(Wrappers.<Script>lambdaUpdate()
                .set(Script::getActiveFlag, null)
                .set(Script::getUpdateBy, operator)
                .eq(Script::getScriptCode, target.getScriptCode())
                .eq(Script::getActiveFlag, true));

        scriptManager.update(Wrappers.<Script>lambdaUpdate()
                .set(Script::getActiveFlag, true)
                .set(Script::getUpdateBy, operator)
                .eq(Script::getId, id));

        scriptStore.evict(target.getScriptCode());
        long refCount = scriptRefManager.lambdaQuery()
                .eq(ScriptRef::getScriptCode, target.getScriptCode())
                .count();
        log.info("[ScriptEngine] 脚本 [{}] 激活 v{}（操作人 {}），当前挂在 {} 个业务对象上",
                target.getScriptCode(), target.getVersion(), operator, refCount);
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
