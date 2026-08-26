package solvela.scriptengine.spi;

import solvela.exception.BusinessException;
import solvela.scriptengine.domain.ScriptParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 脚本场景 —— 一个「脚本槽位」的完整契约：<b>会拿到哪些变量、必须返回什么</b>。
 *
 * <p><b>为什么需要它：</b>在它出现之前，「这段脚本挂在这个位置上合不合法」这个问题
 * 只能等脚本真跑起来才有答案，而且答案通常是一个 NPE 或者一个静默的 null。
 * 有了场景之后：
 * <ol>
 *   <li><b>执行前</b>就能发现「必填变量没绑」，报的是变量名而不是脚本内部的空指针；</li>
 *   <li><b>执行后</b>能发现「脚本没有返回值」—— 在 QL 下这通常是分支没覆盖全（详见
 *       {@link #validateOutput}）。没有场景时它表现为静默返回 null，被当成「判定不通过」，
 *       不报错不告警，只是活动少发一批奖；</li>
 *   <li><b>编辑器</b>能提示当前场景有哪些变量可用 —— 之前只能提示函数，变量全靠运营记；</li>
 *   <li><b>加载期</b>（下一轮的脚本文件加载器）能校验脚本声明的 scene 是否存在。</li>
 * </ol>
 *
 * <p>本枚举与数据库 {@code t_script.scene} 字段一一对应，是那个字段的<b>代码侧真相源</b>。
 * 新增槽位只需在这里加一个枚举常量。
 *
 * <p>⚠️ 变量清单必须来自<b>调用点真实存在的数据</b>，不能凭想象写。
 * 下面三个场景分别对应 {@code TaskEventContext}、{@code DrawExecuteForm} 和活动准入，
 * 改动前请先回到调用点确认。
 */
public enum ScriptScene {

    /**
     * 任务完成判定。
     *
     * <p>任务引擎是三层结构：① task_type 决定进度怎么累积、② rule_config 是运营填的参数、
     * ③ 本场景的脚本只在前两层<b>表达不了</b>时才用（例如"邀请 3 个新用户且他们都下过单"）。
     * 变量取自 {@code solvela.task.runtime.domain.TaskEventContext} + 当前 {@code TaskRecord}。
     */
    TASK_RULE(ScriptDomain.TASK, "任务完成判定", Boolean.class,
            "返回 true 表示本次事件让该任务达标。仅在 task_type + rule_config 两层表达不了时才用。",
            List.of(
                    ScriptParam.required("memberId", Long.class, "会员号（关联键）"),
                    ScriptParam.required("eventCode", String.class, "事件编码，如 DAILY_SIGN / ORDER_PAID"),
                    ScriptParam.required("amount", BigDecimal.class, "本次事件的计量值，无金额语义时为 0"),
                    ScriptParam.required("eventTime", LocalDateTime.class, "事件实际发生时间（不是处理时间）"),
                    ScriptParam.required("currentMetric", BigDecimal.class, "任务当前已累积的进度"),
                    ScriptParam.optional("target", BigDecimal.class, "达标目标值，未配置时为 null"),
                    ScriptParam.optional("isNewMember", Boolean.class, "是否新会员，上游未告知时为 null"),
                    ScriptParam.required("payload", Map.class, "事件原文，上游自定义字段都在这里")
            )),

    /**
     * 奖池准入判定。
     *
     * <p>决定某个会员这一次能不能进这个奖池（VIP 池、新人池等）。
     * 变量取自 {@code solvela.draw.runtime.domain.DrawExecuteForm}。
     */
    POOL_ENTRY(ScriptDomain.DRAW, "奖池准入判定", Boolean.class,
            "返回 true 表示允许该会员本次进入此奖池。挂在 t_prize_pool_config 上。",
            List.of(
                    ScriptParam.required("memberId", Long.class, "会员号（关联键）"),
                    ScriptParam.required("activityCode", String.class, "活动编码"),
                    ScriptParam.required("poolCode", String.class, "奖池编码")
            )),

    /**
     * 活动准入判定。
     *
     * <p>决定某个会员能不能参与这个活动，是所有玩法之前的第一道闸。
     * 挂在 {@code t_activity_config} 上。
     */
    ACTIVITY_RULE(ScriptDomain.ACTIVITY, "活动准入判定", Boolean.class,
            "返回 true 表示允许该会员参与此活动。所有玩法之前的第一道闸。",
            List.of(
                    ScriptParam.required("memberId", Long.class, "会员号（关联键）"),
                    ScriptParam.required("activityCode", String.class, "活动编码")
            )),
    ;

    private final ScriptDomain domain;

    private final String title;

    private final Class<?> returnType;

    private final String description;

    private final List<ScriptParam> params;

    ScriptScene(ScriptDomain domain, String title, Class<?> returnType,
                String description, List<ScriptParam> params) {
        this.domain = domain;
        this.title = title;
        this.returnType = returnType;
        this.description = description;
        this.params = List.copyOf(params);
    }

    public ScriptDomain getDomain() {
        return domain;
    }

    public String getTitle() {
        return title;
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public String getDescription() {
        return description;
    }

    public List<ScriptParam> getParams() {
        return params;
    }

    public static Optional<ScriptScene> of(String name) {
        return Arrays.stream(values())
                .filter(scene -> scene.name().equalsIgnoreCase(name))
                .findFirst();
    }

    // ------------------------------------------------------------------
    // 契约校验
    // ------------------------------------------------------------------

    /**
     * 执行前校验：必填变量都绑了吗、类型对吗。
     *
     * <p>只看<b>脚本可见</b>的那条通道 —— 内部数据通道不属于脚本契约的一部分。
     * 允许绑定契约之外的额外变量（无害），但必填的一个都不能少。
     */
    public void validateInput(EngineContext context) {
        Map<String, Object> variables = context.getScriptVariables();
        for (ScriptParam param : params) {
            Object value = variables.get(param.name());
            if (value == null) {
                if (param.required()) {
                    throw new BusinessException(String.format(
                            "场景 [%s] 缺少必填变量 [%s]（%s）。请检查调用方是否漏了 bind(\"%s\", ...)",
                            title, param.name(), param.description(), param.name()));
                }
                continue;
            }
            if (!param.type().isInstance(value)) {
                throw new BusinessException(String.format(
                        "场景 [%s] 变量 [%s] 类型不匹配。期望: %s, 实际: %s",
                        title, param.name(), param.type().getSimpleName(), value.getClass().getSimpleName()));
            }
        }
    }

    /**
     * 执行后校验：返回值类型对吗、是不是压根没返回。
     *
     * <p>🔴 <b>null 一律视为契约违反</b>。场景声明了返回类型，脚本就必须给一个值。
     *
     * <p>⚠️ 注意 QLExpress 4.x 的语义：<b>最后一个表达式的值就是返回值</b>，
     * 所以「漏写 return」通常并不产生 null。真正会产生 null 的是
     * <b>分支没覆盖全</b> —— 只在 {@code if} 里写了 return，走另一条路时整段脚本没有值。
     * 这种形态比漏写 return 隐蔽得多：调试用的账号刚好走了有 return 的那条分支，
     * 上线换个人就静默变成「判定不通过」，不报错、不告警，只是少发一批奖。
     */
    public void validateOutput(String scriptName, Object result) {
        if (result == null) {
            throw new BusinessException(String.format(
                    "脚本 [%s] 没有返回值，而场景 [%s] 要求返回 %s。"
                            + "最常见的原因是分支没覆盖全：只在 if 里写了 return，走另一条分支时没有任何返回值。",
                    scriptName, title, returnType.getSimpleName()));
        }
        if (!returnType.isInstance(result)) {
            throw new BusinessException(String.format(
                    "脚本 [%s] 返回值类型不匹配。场景 [%s] 要求: %s, 实际: %s",
                    scriptName, title, returnType.getSimpleName(), result.getClass().getSimpleName()));
        }
    }
}
