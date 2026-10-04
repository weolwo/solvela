package solvela.base.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 生产强化版 Jackson 3 JSON 工具类
 */
@Slf4j
public final class JsonUtils {

    private JsonUtils() {} // 规约：工具类禁止实例化

    public static final String NORM_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String NORM_DATE_PATTERN = "yyyy-MM-dd";
    public static final String NORM_TIME_PATTERN = "HH:mm:ss";

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        SimpleModule timeModule = new SimpleModule();
        // 1. 完善整个 java.time 族群的格式化规范
        timeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(NORM_DATETIME_PATTERN)));
        timeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(NORM_DATETIME_PATTERN)));
        timeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(NORM_DATE_PATTERN)));
        timeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(NORM_DATE_PATTERN)));
        timeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(NORM_TIME_PATTERN)));
        timeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(NORM_TIME_PATTERN)));

        return JsonMapper.builder()
                // 忽略未知字段（容错）
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // 允许空对象序列化，不抛异常
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                // 过滤 null 属性
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(timeModule)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 对象序列化为标准 JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JacksonException e) {
            log.error("JSON 序列化失败, obj: {}", obj, e);
            throw new IllegalArgumentException("JSON 序列化异常", e);
        }
    }

    /**
     * JSON 解析为普通 Java 对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (isBlank(json) || clazz == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JacksonException e) {
            log.error("JSON 反序列化失败, json: {}, targetClass: {}", json, clazz.getName(), e);
            throw new IllegalArgumentException("JSON 反序列化异常", e);
        }
    }

    /**
     * JSON 解析为带复杂嵌套泛型的对象（如 Result<List<User>>）
     */
    public static <T> T parseType(String json, TypeReference<T> typeReference) {
        if (isBlank(json) || typeReference == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JacksonException e) {
            log.error("JSON 泛型反序列化失败, json: {}", json, e);
            throw new IllegalArgumentException("JSON 复杂泛型反序列化异常", e);
        }
    }

    /**
     * 通用泛型列表反序列化：直接返回 List<T>
     */
    public static <T> List<T> parseList(String json, Class<T> elementClass) {
        if (isBlank(json) || elementClass == null) {
            return Collections.emptyList(); // 防御式编程：集合反序列化为空时优先返回空集合，避免调用方 NPE
        }
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructParametricType(List.class, elementClass);
            return MAPPER.readValue(json, javaType);
        } catch (JacksonException e) {
            log.error("JSON 转 List 失败, json: {}, elementClass: {}", json, elementClass.getName(), e);
            throw new IllegalArgumentException("JSON 转 List 异常", e);
        }
    }

    /**
     * 通用键值对 Map 反序列化：直接返回 Map<K, V>
     */
    public static <K, V> Map<K, V> parseMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (isBlank(json) || keyClass == null || valueClass == null) {
            return Collections.emptyMap();
        }
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructParametricType(Map.class, keyClass, valueClass);
            return MAPPER.readValue(json, javaType);
        } catch (JacksonException e) {
            log.error("JSON 转 Map 失败, json: {}", json, e);
            throw new IllegalArgumentException("JSON 转 Map 异常", e);
        }
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}