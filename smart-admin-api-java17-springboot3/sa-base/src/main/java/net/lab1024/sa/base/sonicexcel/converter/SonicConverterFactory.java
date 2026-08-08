package net.lab1024.sa.base.sonicexcel.converter;

import net.lab1024.sa.base.sonicexcel.SonicExcelException;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Constructor;

/**
 * 转换器实例解析：<b>Spring Bean 优先，回退无参构造</b>。
 *
 * <p>这一条是 SonicExcel 相对阿里系唯一主动多做的设计。EasyExcel 的 Converter 同样靠反射无参构造
 * 实例化，够不到 Spring 容器 —— 于是字典翻译只能继续手写在 service 里。允许转换器是 Bean 之后，
 * {@code @SonicTitle(converter = DictConverter.class)} 才能真正把那段代码收走。
 *
 * @Date 2026-08-08
 */
public final class SonicConverterFactory {

    private static final SonicConverter<Object, Object> IDENTITY = new SonicConverter.None();

    /**
     * 由 SonicExcelConfiguration 在容器启动时注入。为 null 时（单测、无 Spring 环境）退化为反射构造。
     */
    private static volatile BeanFactory beanFactory;

    /**
     * 每个转换器类只解析一次。用 ClassValue 而不是 Map&lt;Class,?&gt;，避免持有 Class 强引用导致类加载器泄漏。
     */
    private static final ClassValue<SonicConverter<Object, Object>> CACHE = new ClassValue<>() {
        @Override
        protected SonicConverter<Object, Object> computeValue(Class<?> type) {
            return doResolve(type);
        }
    };

    private SonicConverterFactory() {
    }

    public static void setBeanFactory(BeanFactory factory) {
        beanFactory = factory;
    }

    @SuppressWarnings("rawtypes")
    public static SonicConverter<Object, Object> resolve(Class<? extends SonicConverter> type) {
        if (type == null || type == SonicConverter.None.class) {
            return IDENTITY;
        }
        return CACHE.get(type);
    }

    /**
     * 同样的「Bean 优先、无参构造兜底」规则，给转换器之外的扩展点用（如选项提供器）。
     */
    @SuppressWarnings("unchecked")
    public static <T> T resolveExtension(Class<T> type) {
        return (T) EXTENSIONS.get(type);
    }

    private static final ClassValue<Object> EXTENSIONS = new ClassValue<>() {
        @Override
        protected Object computeValue(Class<?> type) {
            return instantiate(type);
        }
    };

    @SuppressWarnings("unchecked")
    private static SonicConverter<Object, Object> doResolve(Class<?> type) {
        return (SonicConverter<Object, Object>) instantiate(type);
    }

    private static Object instantiate(Class<?> type) {
        BeanFactory factory = beanFactory;
        if (factory != null) {
            // getBeanProvider 拿不到就返回 null，不抛异常 —— 没被声明成 Bean 是常态，不是错误
            Object bean = factory.getBeanProvider(type).getIfAvailable();
            if (bean != null) {
                return bean;
            }
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new SonicExcelException(
                    type.getName() + " 实例化失败：既不是 Spring Bean，也没有可用的无参构造", e);
        }
    }
}
