package solvela.app.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * C 端 JSON 的形状。<b>网关自己的一份</b>，与 base-core 的 {@code JsonConfig} 内容一致但独立。
 *
 * <h3>🔴 这三条是【对前端的契约】，改之前先读 solvela-app-web/src/types/contract.ts</h3>
 * 那个文件整篇都在讲这里定的三件事，而且给每一条都配了 branded type 让误用编译不过：
 * <ul>
 *   <li><b>Long 会变类型</b>：|value| ≤ 2^53-1 输出 JSON 数字，超出输出字符串。
 *       前端 {@code toId()} 两种都收，内部一律归一成字符串；</li>
 *   <li><b>金额永远是字符串</b>：BigDecimal 走 ToStringSerializer。前端对它做
 *       {@code +} / {@code toFixed} 都是错的，必须走 Decimal 封装；</li>
 *   <li><b>时间是无时区字符串</b> {@code yyyy-MM-dd HH:mm:ss}。前端既不能
 *       {@code new Date(它)}，也不能拿本地时间去减它做倒计时。</li>
 * </ul>
 *
 * <h3>为什么网关不复用 base-core 的 JsonConfig</h3>
 * 网关的 pom 里刻意没有任何 base 模块 —— 为这一个类依赖 base-core，代价是连带
 * solvela-model、mybatis-plus-core、bcprov、ip2region(11MB) 等 10 个 jar。
 *
 * <p>⚠️ 代价是这套形状现在有两处定义（这里 + base-core）。它们必须一致：
 * 网关不只把 JSON 发给前端，还要<b>解析 biz 的响应</b>，两边配置漂移的表现是
 * 「某个接口回来的 ID 突然变成数字」这类只在特定数据下出现的问题。
 * 改任何一条，两处都要改，并且对着 contract.ts 核一遍。
 */
@Configuration
public class AppJsonConfig {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter YMD_HMS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer appJsonCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(YMD));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(YMD_HMS));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(YMD));
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(YMD_HMS));
            module.addSerializer(Long.class, AppLongSerializer.INSTANCE);
            module.addSerializer(Long.TYPE, AppLongSerializer.INSTANCE);
            module.addSerializer(BigInteger.class, ToStringSerializer.instance);
            module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }
}
