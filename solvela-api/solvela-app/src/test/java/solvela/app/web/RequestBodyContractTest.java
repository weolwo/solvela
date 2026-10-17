package solvela.app.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import solvela.app.domain.DrawRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 C 端每个请求对象都是参数白名单，并且每个字段都有大小（网关方案 §5.6）。
 *
 * <p>严格模式（{@code InboundJsonConfig}）只能保证「请求体里的字段都是请求对象声明过的」。
 * 下面这几条保证声明本身是收紧的 —— 否则白名单从这里漏掉：
 * <ul>
 *   <li><b>不许有「什么都能装」的字段</b>：{@code Map}、{@code JsonNode}、{@code Object}。
 *       唯一例外是 {@link DrawRequest#params()}，它由 {@code DrawParams} 按契约另行收口；</li>
 *   <li><b>每个字符串、数组、集合都有 {@code @Size(max = …)}</b>：没有上限的字段，一个请求就能塞进几百 KB；</li>
 *   <li><b>请求体参数都标了 {@code @Valid}</b>：没标的话，上面那些注解一个都不生效，而且不会有任何报错。</li>
 * </ul>
 * 嵌套的请求对象（{@code solvela.*} 里的类型）递归检查。
 *
 * @Date 2026-10-17
 */
@SpringBootTest
class RequestBodyContractTest {

    /** 「什么都能装」的字段里，唯一允许的那个：类名#字段名 */
    private static final Set<String> OPEN_FIELDS = Set.of(DrawRequest.class.getName() + "#params");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("🔴 每个请求对象：没有 Map / JsonNode / Object 字段，字符串和数组都有 @Size 上限，参数标了 @Valid")
    void everyRequestBodyIsBounded() {
        Set<String> problems = new TreeSet<>();
        int checked = 0;
        for (HandlerMethod method : handlerMapping.getHandlerMethods().values()) {
            if (!method.getBeanType().getPackageName().startsWith("solvela.app")) {
                continue;
            }
            for (MethodParameter parameter : method.getMethodParameters()) {
                if (!parameter.hasParameterAnnotation(RequestBody.class)) {
                    continue;
                }
                Class<?> type = parameter.getParameterType();
                // 原文收字符串的只有 CSP 报告（浏览器自动发，不走 Jackson），体积由 nginx 管
                if (type == String.class) {
                    continue;
                }
                checked++;
                String where = method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
                if (!parameter.hasParameterAnnotation(Valid.class) && !parameter.hasParameterAnnotation(Validated.class)) {
                    problems.add(where + " 的请求体参数没有 @Valid —— 请求对象上的校验注解一个都不会生效");
                }
                problems.addAll(violations(type));
            }
        }
        assertTrue(checked >= 15, "找到的请求对象太少（" + checked + " 个），扫描方式是不是失效了");
        assertTrue(problems.isEmpty(), "这些请求对象没有收紧：\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("检查本身会红：一个什么都不守的请求对象，四条全报")
    void detectsViolations() {
        List<String> found = violations(Loose.class);
        assertEquals(4, found.size(), "实际: " + found);
        assertTrue(found.stream().anyMatch(s -> s.contains("#name") && s.contains("@Size")));
        assertTrue(found.stream().anyMatch(s -> s.contains("#extra") && s.contains("什么都能装")));
        assertTrue(found.stream().anyMatch(s -> s.contains("#tags") && s.contains("@Size")));
        assertTrue(found.stream().anyMatch(s -> s.contains("Inner#note") && s.contains("@Size")),
                "嵌套的请求对象要递归检查");
        assertFalse(found.stream().anyMatch(s -> s.contains("#bounded")));
    }

    /** 测试用：每一条都犯了 */
    @SuppressWarnings("unused")
    record Loose(String name, Map<String, Object> extra, List<Inner> tags, @Size(max = 8) String bounded) {
    }

    record Inner(String note) {
    }

    static List<String> violations(Class<?> root) {
        List<String> out = new ArrayList<>();
        walk(root, out, new HashSet<>());
        return out;
    }

    private static void walk(Class<?> type, List<String> out, Set<Class<?>> seen) {
        if (!seen.add(type)) {
            return;
        }
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            String where = type.getName() + "#" + field.getName();
            Class<?> fieldType = field.getType();
            if (isOpen(fieldType)) {
                if (!OPEN_FIELDS.contains(where)) {
                    out.add(where + " 是「什么都能装」的类型（" + fieldType.getSimpleName() + "）—— 白名单从这里漏掉");
                }
                continue;
            }
            boolean sized = bounded(field.getAnnotation(Size.class));
            if ((fieldType == String.class || fieldType.isArray() || Collection.class.isAssignableFrom(fieldType))
                    && !sized) {
                out.add(where + " 没有 @Size(max = …) 上限");
            }
            for (Class<?> nested : nestedTypes(field)) {
                walk(nested, out, seen);
            }
        }
    }

    private static boolean isOpen(Class<?> type) {
        return type == Object.class || Map.class.isAssignableFrom(type)
                || type.getName().endsWith(".JsonNode") || type.getName().startsWith("tools.jackson.databind.node.");
    }

    private static boolean bounded(Size size) {
        return size != null && size.max() < Integer.MAX_VALUE;
    }

    /** 字段里装着的、要递归检查的我方类型：字段本身、数组元素、集合元素 */
    private static List<Class<?>> nestedTypes(Field field) {
        List<Class<?>> candidates = new ArrayList<>();
        Class<?> type = field.getType();
        while (type.isArray()) {
            type = type.getComponentType();
        }
        candidates.add(type);
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                if (argument instanceof Class<?> c) {
                    candidates.add(c);
                }
            }
        }
        return candidates.stream()
                .filter(c -> !c.isEnum() && !c.isPrimitive() && c.getName().startsWith("solvela."))
                .toList();
    }
}
