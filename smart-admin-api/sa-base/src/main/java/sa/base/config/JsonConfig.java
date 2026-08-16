package sa.base.config;

import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;
import sa.base.common.json.serializer.LongJsonSerializer;
import sa.base.common.util.SmartDateFormatterEnum;
import sa.base.common.util.SmartLocalDateUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * json 序列化配置
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2017-11-28 15:21:10
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Configuration
public class JsonConfig {

    /**
     * Spring Boot 4 起 HTTP 层的 JSON 换成了 Jackson 3（tools.jackson），
     * 原来的 Jackson2ObjectMapperBuilderCustomizer 已不存在，改用 JsonMapperBuilderCustomizer。
     * Jackson 3 的 builder 没有 serializerByType，统一走 SimpleModule 注册。
     */
    @Bean
    public JsonMapperBuilderCustomizer customizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(SmartDateFormatterEnum.YMD.getFormatter()));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(SmartDateFormatterEnum.YMD_HMS.getFormatter()));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(SmartDateFormatterEnum.YMD.getFormatter()));
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(SmartDateFormatterEnum.YMD_HMS.getFormatter()));
            module.addSerializer(Long.class, LongJsonSerializer.INSTANCE);
            module.addSerializer(Long.TYPE, LongJsonSerializer.INSTANCE);
            module.addSerializer(BigInteger.class, ToStringSerializer.instance);
            module.addSerializer(BigDecimal.class, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }


    /**
     * string 转为 LocalDateTime 配置类
     *
     * @author 卓大
     */
    @Configuration
    public static class StringToLocalDateTime implements Converter<String, LocalDateTime> {

        @Override
        public LocalDateTime convert(String str) {
            if (StringUtils.isBlank(str)) {
                return null;
            }
            LocalDateTime localDateTime;
            try {
                localDateTime = SmartLocalDateUtil.parse(str, SmartDateFormatterEnum.YMD_HMS);
            } catch (DateTimeParseException e) {
                throw new RuntimeException("请输入正确的日期格式：yyyy-MM-dd HH:mm:ss");
            }
            return localDateTime;
        }
    }


    /**
     * string 转为 LocalDate 配置类
     *
     * @author 卓大
     */
    @Configuration
    public static class StringToLocalDate implements Converter<String, LocalDate> {

        @Override
        public LocalDate convert(String str) {
            if (StringUtils.isBlank(str)) {
                return null;
            }
            LocalDate localDate;
            try {
                localDate = SmartLocalDateUtil.parseDate(str, SmartDateFormatterEnum.YMD);
            } catch (DateTimeParseException e) {
                throw new RuntimeException("请输入正确的日期格式：yyyy-MM-dd");
            }
            return localDate;
        }
    }
}