package solvela.base.sonicexcel.meta;

import solvela.base.sonicexcel.SonicExcelException;
import solvela.base.sonicexcel.SonicExcelSettings;
import solvela.base.sonicexcel.annotation.SonicTitle;
import solvela.base.sonicexcel.converter.SonicConverterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 元数据解析：把 {@code @SonicTitle} 标注的类编译成一份 {@link SheetMeta}，每个类只做一次。
 *
 * <p><b>访问器为什么用 LambdaMetafactory 而不是 MethodHandle</b>：
 * 从缓存里取出来的 MethodHandle 只能走 {@code invoke}（非 {@code invokeExact}、非 static final 常量），
 * JIT 拿不到常量折叠，实测通常不比 {@code setAccessible} 后的 Field 快。
 * LMF 把 getter 编成 {@code Function}，调用点退化成普通 invokeinterface，JIT 可内联。
 * 代价是每个访问器生成一个 hidden class —— 所以必须靠 ClassValue 保证<b>每个 DTO 只生成一次</b>。
 *
 * <p>顺带说明：这条路径的真实价值是"不用 setAccessible、不和将来的 JPMS 打架"，
 * 不是"快 10 倍"。导出的瓶颈在 XML 序列化 + Deflate + IO，属性访问占比不到 5%。
 *
 * @Date 2026-08-08
 */
public final class MetaResolver {

    private static final Logger log = LoggerFactory.getLogger(MetaResolver.class);

    /**
     * 用 ClassValue 而不是 ConcurrentHashMap&lt;Class, SheetMeta&gt; —— 后者持有 Class 强引用，
     * 是经典的类加载器泄漏源（热部署 / 多 war 场景会炸）。
     */
    private static final ClassValue<SheetMeta> CACHE = new ClassValue<>() {
        @Override
        protected SheetMeta computeValue(Class<?> type) {
            return doResolve(type);
        }
    };

    private MetaResolver() {
    }

    public static SheetMeta resolve(Class<?> type) {
        return CACHE.get(type);
    }

    // ------------------------------------------------------------------ 解析

    private static SheetMeta doResolve(Class<?> type) {
        if (type.isInterface() || type.isArray() || type.isPrimitive() || Modifier.isAbstract(type.getModifiers())) {
            throw new SonicExcelException("SonicExcel 不支持的实体类型：" + type.getName());
        }

        MethodHandles.Lookup lookup;
        try {
            lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            throw new SonicExcelException("无法访问 " + type.getName() + "，请确认它不在被封闭的模块里", e);
        }

        List<Raw> raws = type.isRecord() ? collectFromRecord(type) : collectFromPojo(type);
        if (raws.isEmpty()) {
            throw new SonicExcelException(type.getName() + " 上没有任何 @SonicTitle 字段，无法作为 Excel 实体");
        }

        checkPrimitives(type, raws);
        List<Raw> ordered = applyOrder(type, raws);
        checkDuplicateTitles(type, ordered);

        List<ColumnMeta> columns = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            columns.add(toColumnMeta(lookup, type, ordered.get(i), i));
        }
        return new SheetMeta(type, List.copyOf(columns), buildConstructor(lookup, type));
    }

    /**
     * 解析中间态。declarationOrder 是"没写 index 时"的兜底列序。
     */
    private record Raw(String name,
                       Class<?> valueType,
                       SonicTitle annotation,
                       Field field,
                       Method accessor,
                       java.lang.reflect.AnnotatedElement element,
                       int componentIndex,
                       int declarationOrder) {
    }

    private static List<Raw> collectFromRecord(Class<?> type) {
        RecordComponent[] components = type.getRecordComponents();
        List<Raw> raws = new ArrayList<>();
        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            SonicTitle ann = rc.getAnnotation(SonicTitle.class);
            if (ann == null) {
                // 注解写在组件上时，是否传播到合成字段取决于 @Target；两头都找一次最稳
                ann = findFieldAnnotation(type, rc.getName());
            }
            if (ann == null) {
                continue;
            }
            raws.add(new Raw(rc.getName(), rc.getType(), ann, null, rc.getAccessor(), rc, i, raws.size()));
        }
        return raws;
    }

    private static List<Raw> collectFromPojo(Class<?> type) {
        // 父类字段排在子类前面，和"声明顺序"的直觉一致
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.add(c);
        }
        java.util.Collections.reverse(hierarchy);

        List<Raw> raws = new ArrayList<>();
        for (Class<?> c : hierarchy) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Method accessor = findAccessor(type, f);
                SonicTitle ann = f.getAnnotation(SonicTitle.class);
                if (ann == null && accessor != null) {
                    ann = accessor.getAnnotation(SonicTitle.class);
                }
                if (ann == null) {
                    continue;
                }
                raws.add(new Raw(f.getName(), f.getType(), ann, f, accessor, f, -1, raws.size()));
            }
        }
        return raws;
    }

    private static SonicTitle findFieldAnnotation(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name).getAnnotation(SonicTitle.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 校验

    /**
     * 基本类型的语义污染拦截。
     *
     * <p>导入时缺列，包装类型得 null（"这一列没填"），而 int / boolean 被强制赋 0 / false
     * （"库存是 0"）—— 两者在业务上完全不是一回事，而且静默发生。record 和 POJO 一样中招。
     */
    private static void checkPrimitives(Class<?> type, List<Raw> raws) {
        List<String> bad = raws.stream()
                .filter(r -> r.valueType().isPrimitive())
                .map(r -> r.name() + "(" + r.valueType().getSimpleName() + ")")
                .toList();
        if (bad.isEmpty()) {
            return;
        }
        String msg = type.getName() + " 的 @SonicTitle 标注在基本类型上：" + String.join(", ", bad)
                + "。导入缺列时会被静默赋 0/false 而不是 null，请改成包装类型";
        if (SonicExcelSettings.isStrictMeta()) {
            throw new SonicExcelException(msg);
        }
        log.warn("[SonicExcel] {}", msg);
    }

    /**
     * index 规则：要么全不写，要么全写且构成 0..n-1 的连续序列。
     *
     * <p>允许空洞的话表头行会出现空单元格，而空标题在导入侧无法寻址 —— 与其让它半坏，不如直接拒绝。
     */
    private static List<Raw> applyOrder(Class<?> type, List<Raw> raws) {
        long explicit = raws.stream().filter(r -> r.annotation().index() >= 0).count();
        if (explicit == 0) {
            return raws;
        }
        if (explicit != raws.size()) {
            throw new SonicExcelException(type.getName() + " 的 @SonicTitle index 只写了一部分（"
                    + explicit + "/" + raws.size() + "）。要么全不写按声明顺序，要么全写");
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (Raw r : raws) {
            if (!seen.add(r.annotation().index())) {
                throw new SonicExcelException(type.getName() + " 的 @SonicTitle index 重复：" + r.annotation().index());
            }
        }
        for (int i = 0; i < raws.size(); i++) {
            if (!seen.contains(i)) {
                throw new SonicExcelException(type.getName() + " 的 @SonicTitle index 必须是 0.."
                        + (raws.size() - 1) + " 的连续序列，缺少 " + i);
            }
        }
        return raws.stream()
                .sorted(Comparator.comparingInt(r -> r.annotation().index()))
                .toList();
    }

    private static void checkDuplicateTitles(Class<?> type, List<Raw> raws) {
        Set<String> seen = new HashSet<>();
        for (Raw r : raws) {
            if (!seen.add(r.annotation().value())) {
                throw new SonicExcelException(type.getName() + " 的表头重复：" + r.annotation().value());
            }
        }
    }

    // ------------------------------------------------------------------ 构建

    private static ColumnMeta toColumnMeta(MethodHandles.Lookup lookup, Class<?> type, Raw raw, int order) {
        SonicTitle ann = raw.annotation();
        return new ColumnMeta(
                ann.value(),
                List.of(ann.alias()),
                order,
                ann.format(),
                ann.width(),
                ann.forceText(),
                raw.valueType(),
                raw.name(),
                raw.element(),
                buildGetter(lookup, type, raw),
                buildSetter(lookup, type, raw),
                raw.componentIndex(),
                SonicConverterFactory.resolve(ann.converter()));
    }

    @SuppressWarnings("unchecked")
    private static Function<Object, Object> buildGetter(MethodHandles.Lookup lookup, Class<?> type, Raw raw) {
        try {
            if (raw.accessor() == null) {
                return fieldGetter(lookup, raw);
            }
            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "apply",
                    MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class),
                    lookup.unreflect(raw.accessor()),
                    // 用包装类型做 instantiatedMethodType，让 LMF 自己插入装箱适配
                    MethodType.methodType(box(raw.valueType()), type));
            return (Function<Object, Object>) site.getTarget().invoke();
        } catch (Throwable t) {
            throw new SonicExcelException(
                    "生成取值器失败：" + type.getName() + "#" + raw.name(), t);
        }
    }

    /**
     * 没有 getter 的字段只能走 MethodHandle。
     *
     * <p>LambdaMetafactory <b>只接受方法/构造器句柄，不接受字段句柄</b>
     * （直接喂 unreflectGetter 会抛 {@code LambdaConversionException: Unsupported MethodHandle kind: getField}）。
     * 实际业务 DTO 基本都有 Lombok 生成的 getter，走的是上面可内联的快路径；这里是兜底。
     */
    private static Function<Object, Object> fieldGetter(MethodHandles.Lookup lookup, Raw raw)
            throws IllegalAccessException {
        MethodHandle handle = lookup.unreflectGetter(raw.field())
                .asType(MethodType.methodType(Object.class, Object.class));
        return target -> {
            try {
                return handle.invokeExact(target);
            } catch (Throwable t) {
                throw new SonicExcelException("读取字段失败：" + raw.name(), t);
            }
        };
    }

    /**
     * 导入用。<b>取不到就返回 null 而不是报错</b> —— 只用于导出的 DTO 完全可以没有 setter，
     * 那种情况下应该在真正导入时才失败，而不是连导出都跑不起来。
     */
    @SuppressWarnings("unchecked")
    private static BiConsumer<Object, Object> buildSetter(MethodHandles.Lookup lookup, Class<?> type, Raw raw) {
        if (type.isRecord()) {
            return null;
        }
        try {
            Method setter = findSetter(type, raw);
            if (setter == null) {
                if (raw.field() == null || Modifier.isFinal(raw.field().getModifiers())) {
                    return null;
                }
                // 同 fieldGetter：LMF 不吃字段句柄，退回 MethodHandle
                MethodHandle handle = lookup.unreflectSetter(raw.field())
                        .asType(MethodType.methodType(void.class, Object.class, Object.class));
                return (target, value) -> {
                    try {
                        handle.invokeExact(target, value);
                    } catch (Throwable t) {
                        throw new SonicExcelException("写入字段失败：" + raw.name(), t);
                    }
                };
            }
            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    MethodType.methodType(BiConsumer.class),
                    MethodType.methodType(void.class, Object.class, Object.class),
                    lookup.unreflect(setter),
                    MethodType.methodType(void.class, type, box(raw.valueType())));
            return (BiConsumer<Object, Object>) site.getTarget().invoke();
        } catch (Throwable t) {
            log.debug("[SonicExcel] {}#{} 生成赋值器失败，该字段将无法导入", type.getName(), raw.name(), t);
            return null;
        }
    }

    private static RowConstructor buildConstructor(MethodHandles.Lookup lookup, Class<?> type) {
        try {
            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Class<?>[] params = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
                MethodHandle mh = lookup.unreflectConstructor(type.getDeclaredConstructor(params));
                return new RowConstructor.RecordCanonical(mh, List.of(params));
            }
            return new RowConstructor.PojoNoArg(lookup.unreflectConstructor(type.getDeclaredConstructor()));
        } catch (ReflectiveOperationException e) {
            return new RowConstructor.Unavailable(type.getName() + " 没有可用的无参构造，只能用于导出");
        }
    }

    // ------------------------------------------------------------------ 小工具

    private static Method findAccessor(Class<?> type, Field f) {
        String cap = capitalize(f.getName());
        for (String name : new String[]{"get" + cap, "is" + cap}) {
            try {
                Method m = type.getMethod(name);
                if (m.getParameterCount() == 0 && m.getReturnType() == f.getType()) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // 换下一个候选名
            }
        }
        return null;
    }

    private static Method findSetter(Class<?> type, Raw raw) {
        if (raw.field() == null) {
            return null;
        }
        try {
            return type.getMethod("set" + capitalize(raw.name()), raw.valueType());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Class<?> box(Class<?> t) {
        if (!t.isPrimitive()) {
            return t;
        }
        return switch (t.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "short" -> Short.class;
            case "byte" -> Byte.class;
            case "char" -> Character.class;
            case "boolean" -> Boolean.class;
            default -> t;
        };
    }
}
