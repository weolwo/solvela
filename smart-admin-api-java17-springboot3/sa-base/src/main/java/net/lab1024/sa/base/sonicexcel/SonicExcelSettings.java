package net.lab1024.sa.base.sonicexcel;

/**
 * 框架级开关。由 SonicExcelConfiguration 在启动时按 profile 设置。
 *
 * @Date 2026-08-08
 */
public final class SonicExcelSettings {

    /**
     * 严格元数据模式：把"基本类型字段"这类语义隐患从 WARN 升级为异常。
     *
     * <p>dev / test / local 下开启 —— 只打 warn 没人看，要在 CI 阶段就红。
     * 生产环境保持 false，不因为一个建模问题把线上导出打挂。
     */
    private static volatile boolean strictMeta = false;

    private SonicExcelSettings() {
    }

    public static boolean isStrictMeta() {
        return strictMeta;
    }

    public static void setStrictMeta(boolean strict) {
        strictMeta = strict;
    }
}
