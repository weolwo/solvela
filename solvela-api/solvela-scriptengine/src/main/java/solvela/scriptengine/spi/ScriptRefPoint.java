package solvela.scriptengine.spi;

import java.util.Arrays;
import java.util.Optional;

/**
 * 脚本挂载点 —— 业务侧「哪个对象的哪个位置可以挂脚本」的完整枚举。
 *
 * <p>它对应数据库 {@code t_script_ref} 的 {@code (ref_type, ref_slot)} 两列。
 * 代码侧合成一个枚举而不是两个，是因为这两列<b>从来不会自由组合</b> ——
 * 只有固定的几种搭配有意义，拆成两个枚举等于允许写出 {@code (ACTIVITY, RULE)} 这种不存在的挂载点。
 *
 * <p><b>每个挂载点声明它期望的 {@link ScriptScene}</b>，这是本枚举最重要的作用：
 * 挂载时校验「这个脚本的场景 == 这个挂载点期望的场景」，
 * 于是「把任务判定脚本挂到奖池准入上」这种错误在<b>写入 t_script_ref 的那一刻</b>就被拒绝，
 * 而不是等到某天真有人来抽奖时才炸。
 *
 * <p>新增挂载点只需在这里加一个枚举常量。
 */
public enum ScriptRefPoint {

    /**
     * 任务模板的完成判定规则。挂在 {@code t_task_template.template_code} 上。
     */
    TASK_TEMPLATE_RULE("TASK_TEMPLATE", "RULE", ScriptScene.TASK_RULE,
            "任务模板", "完成判定规则"),

    /**
     * 奖池的准入判定。挂在 {@code t_prize_pool_config.pool_code} 上。
     */
    PRIZE_POOL_ENTRY("PRIZE_POOL", "ENTRY", ScriptScene.POOL_ENTRY,
            "奖池", "准入判定"),

    /**
     * 活动的准入判定。挂在 {@code t_activity_config.activity_code} 上。
     */
    ACTIVITY_ENTRY("ACTIVITY", "ENTRY", ScriptScene.ACTIVITY_RULE,
            "活动", "准入判定"),

    /**
     * 活动的玩法编排。挂在 {@code t_activity_config.activity_code} 上。
     *
     * <p>与 {@link #ACTIVITY_ENTRY} 的区别是「判定」与「执行」：那个回答「能不能参与」，
     * 这个真的去参与（抽奖、发奖）。两个挂载点<b>各挂各的脚本</b>，
     * 因为它们的失败含义完全不同 —— 准入不通过是正常拒绝，编排出错是事故。
     */
    ACTIVITY_PLAY("ACTIVITY", "PLAY", ScriptScene.ACTIVITY_PLAY,
            "活动", "玩法编排"),

    /**
     * 抽奖的玩法编排。挂在 {@code t_draw_config.draw_code} 上。
     *
     * <p>与 {@link #ACTIVITY_PLAY} 是同一个场景，区别只在<b>挂在谁身上</b>：
     * 抽奖有自己的玩法配置，脚本就该挂在那上面，而不是挂在活动上。
     * 一个活动一套抽奖，所以两者今天是一一对应的 —— 但「一次抽奖的脚本」
     * 这个说法只有挂在抽奖配置上才立得住，将来放开成一活动多套抽奖时不用再搬一次。
     */
    DRAW_PLAY("DRAW", "PLAY", ScriptScene.ACTIVITY_PLAY,
            "抽奖配置", "玩法编排"),
    ;

    /**
     * 单值槽位的 {@code ref_key} 取值：空串。
     *
     * <p>🔴 不是 null。唯一键 {@code (ref_type, ref_id, ref_slot, ref_key)} 靠它挡住重复挂载，
     * 而 NULL 在 MySQL 唯一索引里不参与判重 —— 用 null 等于这些槽位完全没有约束。
     */
    public static final String SINGLE_KEY = "";

    private final String refType;

    private final String refSlot;

    private final ScriptScene expectedScene;

    private final String ownerTitle;

    private final String slotTitle;

    private final String keyTitle;

    /**
     * 单值槽位：一个业务对象在这个槽位上只能挂一个脚本。
     */
    ScriptRefPoint(String refType, String refSlot, ScriptScene expectedScene,
                   String ownerTitle, String slotTitle) {
        this(refType, refSlot, expectedScene, ownerTitle, slotTitle, null);
    }

    /**
     * 多值槽位：按 {@code keyTitle} 描述的键分组，一个业务对象可以挂 N 个脚本。
     *
     * <p>{@code keyTitle} 是必填的，这不是形式主义 —— 一个多值槽位如果说不清
     * 「这个 key 是什么」，那它多半只是想绕开单值约束，而不是真的有分组语义。
     */
    ScriptRefPoint(String refType, String refSlot, ScriptScene expectedScene,
                   String ownerTitle, String slotTitle, String keyTitle) {
        this.refType = refType;
        this.refSlot = refSlot;
        this.expectedScene = expectedScene;
        this.ownerTitle = ownerTitle;
        this.slotTitle = slotTitle;
        this.keyTitle = keyTitle;
    }

    /**
     * 对应 t_script_ref.ref_type
     */
    public String getRefType() {
        return refType;
    }

    /**
     * 对应 t_script_ref.ref_slot
     */
    public String getRefSlot() {
        return refSlot;
    }

    /**
     * 本挂载点只接受这个场景的脚本
     */
    public ScriptScene getExpectedScene() {
        return expectedScene;
    }

    /**
     * 挂载对象的中文名，如「奖池」
     */
    public String getOwnerTitle() {
        return ownerTitle;
    }

    /**
     * 槽位的中文名，如「准入判定」
     */
    public String getSlotTitle() {
        return slotTitle;
    }

    /**
     * 本槽位是不是按 key 分组的多值槽位。
     *
     * <p>判据就是「有没有说清 key 是什么」：说得清就是多值，说不清就是单值。
     */
    public boolean isKeyed() {
        return keyTitle != null;
    }

    /**
     * 分组键的中文名（如「事件编码」）。单值槽位返回 null
     */
    public String getKeyTitle() {
        return keyTitle;
    }

    /**
     * 校验并规整挂载键，写库与查询都必须先过这里。
     *
     * <p>单值槽位一律归成 {@link #SINGLE_KEY}，<b>而不是接受调用方传来的 null</b> ——
     * 让空值只有一种形态，是这套唯一键能成立的前提。
     */
    public String normalizeKey(String refKey) {
        String trimmed = refKey == null ? "" : refKey.trim();
        if (!isKeyed()) {
            if (!trimmed.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "挂载点 [%s] 是单值槽位，不接受挂载键，但收到了 [%s]", getTitle(), trimmed));
            }
            return SINGLE_KEY;
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "挂载点 [%s] 按「%s」分组，挂载键不能为空", getTitle(), keyTitle));
        }
        return trimmed;
    }

    /**
     * 给人看的全名，如「奖池 - 准入判定」
     */
    public String getTitle() {
        return ownerTitle + " - " + slotTitle;
    }

    public static Optional<ScriptRefPoint> of(String refType, String refSlot) {
        return Arrays.stream(values())
                .filter(point -> point.refType.equals(refType) && point.refSlot.equals(refSlot))
                .findFirst();
    }

    public static Optional<ScriptRefPoint> of(String name) {
        return Arrays.stream(values())
                .filter(point -> point.name().equalsIgnoreCase(name))
                .findFirst();
    }
}
