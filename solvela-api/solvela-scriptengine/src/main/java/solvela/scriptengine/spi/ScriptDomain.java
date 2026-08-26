package solvela.scriptengine.spi;

import java.util.Arrays;
import java.util.Optional;

/**
 * 脚本函数所属业务域。
 *
 * <p><b>为什么是 enum 而不是 sealed interface：</b>
 * sealed 在未命名模块（本工程没有 module-info）下要求 {@code permits} 列出的实现类
 * 与接口<b>处于同一个包</b>（JLS 8.1.6）。而各域的 Handler 必然散落在
 * {@code solvela.member} / {@code solvela.mall} / {@code solvela.task} 等包里，甚至跨 Maven 模块，
 * 写成 sealed 直接编译不过。enum 本身就是封闭集合，同样保证「域的取值不会被业务方随手扩散」，
 * 且天生能被 switch 穷举、能当数据库字段值，比 sealed 更适合这里。
 *
 * <p><b>namespace 的作用：</b>每个域的函数注册进引擎时会自动加上
 * {@code namespace + "_"} 前缀（见 {@link #qualify}）。业务方只写域内短名
 * {@code @ScriptFunction(name = "getLevel")}，实际注册的是 {@code member_getLevel}。
 * 这样跨域重名在物理上不可能发生，前端编辑器也能直接按前缀分组做补全。
 *
 * <p>新增域只需要在这里加一个枚举常量 —— 这是唯一的登记处。
 */
public enum ScriptDomain {

    MEMBER("member", "会员域", "会员身份、等级、标签、实名状态等"),

    MALL("mall", "商城域", "商品、库存、订单、兑换限制等"),

    TASK("task", "任务域", "任务进度、完成判定、周期流转等"),

    ACTIVITY("activity", "活动域", "活动准入、时间窗、人群圈选等"),

    DRAW("draw", "抽奖域", "奖池准入、概率修正、保底等"),

    LOTTERY("lottery", "彩票域", "票号发放、开奖比对、兑奖等"),

    LEDGER("ledger", "账务域", "积分/余额/优惠券的查询与动账"),

    PRIZE("prize", "奖品域", "奖品配置查询与发放"),

    RISK("risk", "风控域", "频次限制、黑名单、设备指纹校验等"),

    TOOL("tool", "通用工具", "字符串、日期、数学等与业务无关的纯函数"),
    ;

    private final String namespace;

    private final String title;

    private final String description;

    ScriptDomain(String namespace, String title, String description) {
        this.namespace = namespace;
        this.title = title;
        this.description = description;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 把域内短名拼成全局唯一的脚本函数名。
     *
     * <p>例：{@code MEMBER.qualify("getLevel")} → {@code "member_getLevel"}
     */
    public String qualify(String simpleName) {
        return namespace + "_" + simpleName;
    }

    public static Optional<ScriptDomain> ofNamespace(String namespace) {
        return Arrays.stream(values())
                .filter(domain -> domain.namespace.equals(namespace))
                .findFirst();
    }
}
