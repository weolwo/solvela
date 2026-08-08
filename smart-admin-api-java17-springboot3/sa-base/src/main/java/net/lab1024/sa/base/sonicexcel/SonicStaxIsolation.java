package net.lab1024.sa.base.sonicexcel;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 拆除 aalto-xml 的 StAX 全局劫持。
 *
 * <p><b>背景</b>：{@code fastexcel-reader} 依赖 {@code com.fasterxml:aalto-xml}，
 * 后者 jar 里带着 {@code META-INF/services/javax.xml.stream.*}，一旦进入 classpath，
 * 全 JVM 的 {@code XMLInputFactory.newFactory()} 都会返回 Aalto。
 * 实测本项目 214 个 runtime jar 中，摘掉 POI 后仍有 6 个会走 SPI 查找：
 * spring-core、spring-web、hibernate-validator、mysql-connector-j、tika-core，
 * 以及 <b>aws-query-protocol（S3 客户端解析响应/错误走的就是它）</b>。
 *
 * <p><b>为什么这么做是安全的</b>：fastexcel-reader 自己有一个 {@code DefaultXMLInputFactory}，
 * 直接 {@code new com.fasterxml.aalto.stax.InputFactoryImpl()}，压根不走 SPI ——
 * 所以把 SPI 拨回 JDK 实现不影响它自身的解析性能。
 *
 * <p><b>落地方式</b>：主路径是 JVM 参数（运维可见、可摘、可回滚），本类只做兜底 ——
 * 已经被外部设置过的属性不会被覆盖。
 *
 * @Date 2026-08-08
 */
public final class SonicStaxIsolation {

    /**
     * JDK 内置 StAX 实现的类名。
     *
     * <p>⚠️ XMLEventFactory 的实现类在 <b>{@code .events.} 子包</b>下，这一处极易写错，
     * 而且写错不会在启动时暴露 —— 要等某个组件首次使用 XMLEventFactory 才抛
     * {@code FactoryConfigurationError: Provider ... not found}，典型的"上线三天后随机报错"。
     */
    private static final Map<String, String> JDK_FACTORIES = new LinkedHashMap<>();

    static {
        JDK_FACTORIES.put("javax.xml.stream.XMLInputFactory", "com.sun.xml.internal.stream.XMLInputFactoryImpl");
        JDK_FACTORIES.put("javax.xml.stream.XMLOutputFactory", "com.sun.xml.internal.stream.XMLOutputFactoryImpl");
        JDK_FACTORIES.put("javax.xml.stream.XMLEventFactory", "com.sun.xml.internal.stream.events.XMLEventFactoryImpl");
    }

    private SonicStaxIsolation() {
    }

    /**
     * 把三个 StAX 工厂钉回 JDK 实现。已被 -D 指定过的保持不动。
     */
    public static void install() {
        JDK_FACTORIES.forEach((key, impl) -> {
            if (System.getProperty(key) == null) {
                System.setProperty(key, impl);
            }
        });
    }

    /**
     * 启动自检：返回三个工厂的<b>实际</b>实现类名，供日志打印。
     *
     * <p>只报告、不抛异常 —— 自检本身不该有能力搞挂启动。
     */
    public static String selfCheck() {
        return "XMLInputFactory=" + probe(() -> XMLInputFactory.newFactory().getClass().getName())
                + ", XMLOutputFactory=" + probe(() -> XMLOutputFactory.newFactory().getClass().getName())
                + ", XMLEventFactory=" + probe(() -> XMLEventFactory.newFactory().getClass().getName());
    }

    /**
     * 当前三个工厂是否都是 JDK 内置实现。给单测断言用。
     */
    public static boolean isJdkStax() {
        return probe(() -> XMLInputFactory.newFactory().getClass().getName()).startsWith("com.sun.xml.internal.")
                && probe(() -> XMLOutputFactory.newFactory().getClass().getName()).startsWith("com.sun.xml.internal.")
                && probe(() -> XMLEventFactory.newFactory().getClass().getName()).startsWith("com.sun.xml.internal.");
    }

    private static String probe(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return "<解析失败: " + t.getClass().getSimpleName() + ": " + t.getMessage() + ">";
        }
    }
}
