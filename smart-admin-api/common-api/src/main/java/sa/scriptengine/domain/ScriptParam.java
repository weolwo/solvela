package sa.scriptengine.domain;

/**
 * 场景契约里的一个入参声明：脚本能引用哪些变量、什么类型、必不必填。
 *
 * @param name        变量名，脚本里直接按这个名字引用
 * @param type        期望类型。校验用 {@code type.isInstance(value)}，<b>不做数值宽松转换</b> ——
 *                    声明 BigDecimal 就必须传 BigDecimal。三个真实切入点的上游
 *                    （TaskEventContext 等）本来就已经把类型收敛好了，放松只会让契约失去意义
 * @param required    是否必填。必填而未绑定（或绑了 null）→ 执行前直接失败
 * @param description 用途说明，会吐给前端编辑器做变量补全的悬浮提示
 */
public record ScriptParam(String name, Class<?> type, boolean required, String description) {

    public static ScriptParam required(String name, Class<?> type, String description) {
        return new ScriptParam(name, type, true, description);
    }

    public static ScriptParam optional(String name, Class<?> type, String description) {
        return new ScriptParam(name, type, false, description);
    }

    /**
     * 前端补全用的展示串，如 {@code memberId: Long}
     */
    public String signature() {
        return name + ": " + type.getSimpleName();
    }
}
