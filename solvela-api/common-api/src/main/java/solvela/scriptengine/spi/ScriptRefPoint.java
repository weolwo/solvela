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
    ;

    private final String refType;

    private final String refSlot;

    private final ScriptScene expectedScene;

    private final String ownerTitle;

    private final String slotTitle;

    ScriptRefPoint(String refType, String refSlot, ScriptScene expectedScene,
                   String ownerTitle, String slotTitle) {
        this.refType = refType;
        this.refSlot = refSlot;
        this.expectedScene = expectedScene;
        this.ownerTitle = ownerTitle;
        this.slotTitle = slotTitle;
    }

    /** 对应 t_script_ref.ref_type */
    public String getRefType() {
        return refType;
    }

    /** 对应 t_script_ref.ref_slot */
    public String getRefSlot() {
        return refSlot;
    }

    /** 本挂载点只接受这个场景的脚本 */
    public ScriptScene getExpectedScene() {
        return expectedScene;
    }

    /** 挂载对象的中文名，如「奖池」 */
    public String getOwnerTitle() {
        return ownerTitle;
    }

    /** 槽位的中文名，如「准入判定」 */
    public String getSlotTitle() {
        return slotTitle;
    }

    /** 给人看的全名，如「奖池 - 准入判定」 */
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
