package net.lab1024.sa.base.sonicexcel;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StAX 隔离的断言。
 *
 * <p>第②档引入 fastexcel-reader（带 aalto-xml）之后，这个类就是防线：
 * aalto 的 jar 里带着 {@code META-INF/services/javax.xml.stream.*}，
 * 一进 classpath 就会接管全 JVM 的 StAX，波及 spring-core、spring-web、hibernate-validator、
 * mysql-connector-j、tika-core 和 aws-query-protocol（S3 走的就是它）。
 *
 * @Date 2026-08-08
 */
public class SonicStaxIsolationTest {

    @Test
    public void 三个工厂都被钉回JDK实现() {
        SonicStaxIsolation.install();
        assertTrue(SonicStaxIsolation.isJdkStax(), "实际实现：" + SonicStaxIsolation.selfCheck());
    }

    @Test
    public void XMLEventFactory的实现类在events子包下() {
        // 🔴 这是最容易写错的一处：写成 com.sun.xml.internal.stream.XMLEventFactoryImpl（少了 .events）
        // 不会在启动时报错，要等某个组件首次使用 XMLEventFactory 才抛 FactoryConfigurationError。
        // 典型的"上线三天后随机报错"，所以单独钉死一条断言。
        SonicStaxIsolation.install();
        assertDoesNotThrow(() -> { XMLEventFactory.newFactory(); });
        assertEquals("com.sun.xml.internal.stream.events.XMLEventFactoryImpl",
                XMLEventFactory.newFactory().getClass().getName());
    }

    @Test
    public void 已被JVM参数指定过的属性不覆盖() {
        // 主路径是 -D 参数（运维可见、可摘），代码只做兜底
        String key = "javax.xml.stream.XMLInputFactory";
        String origin = System.getProperty(key);
        try {
            System.setProperty(key, "com.example.Custom");
            SonicStaxIsolation.install();
            assertEquals("com.example.Custom", System.getProperty(key));
        } finally {
            if (origin == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, origin);
            }
            SonicStaxIsolation.install();
        }
    }

    @Test
    public void 自检不会因为解析失败而抛异常() {
        String key = "javax.xml.stream.XMLInputFactory";
        String origin = System.getProperty(key);
        try {
            System.setProperty(key, "com.example.NotExist");
            String report = assertDoesNotThrow(SonicStaxIsolation::selfCheck);
            assertTrue(report.contains("解析失败"), report);
        } finally {
            if (origin == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, origin);
            }
        }
        assertDoesNotThrow(() -> { XMLInputFactory.newFactory(); });
        assertDoesNotThrow(() -> { XMLOutputFactory.newFactory(); });
    }
}
