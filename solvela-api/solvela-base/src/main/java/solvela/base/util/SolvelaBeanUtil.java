package solvela.base.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
import solvela.base.json.JsonUtils;
import org.springframework.util.ReflectionUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * bean相关工具类
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2018-01-15 10:48:23
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public class SolvelaBeanUtil {

    /**
     * 验证器
     */
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * bean 转 Map，key 为属性名，value 为**原始对象**（不做任何序列化转换）。
     *
     * 移除 hutool 后接管 BeanUtil.beanToMap，代码生成器把结果直接喂给 velocity 模板。
     *
     * 🔴 不要改成 Jackson 的 convertValue：本项目的 JSON 层挂了 LongJsonSerializer
     * （Long 序列化成 String，防前端精度丢失）以及各种自定义序列化器，
     * 走 Jackson 会让模板里拿到的类型悄悄变掉 —— 生成出来的代码能编译，只是内容不对。
     *
     * null 值会保留（模板里靠 key 是否存在做判断），"class" 属性剔除。
     *
     * @param bean 待转换对象，为 null 时返回空 Map
     */
    public static Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (bean == null) {
            return map;
        }
        for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(bean.getClass())) {
            String name = descriptor.getName();
            Method readMethod = descriptor.getReadMethod();
            if (readMethod == null || "class".equals(name)) {
                continue;
            }
            ReflectionUtils.makeAccessible(readMethod);
            map.put(name, ReflectionUtils.invokeMethod(readMethod, bean));
        }
        return map;
    }

    /**
     * 复制bean的属性
     *
     * @param source 源 要复制的对象
     * @param target 目标 复制到此对象
     */
    public static void copyProperties(Object source, Object target) {
        BeanUtils.copyProperties(source, target);
    }

    /**
     * 复制对象
     *
     * @param source 源 要复制的对象
     * @param target 目标 复制到此对象
     * @param <T>
     * @return
     */
    public static <T> T copy(Object source, Class<T> target) {
        if (source == null || target == null) {
            return null;
        }
        try {
            T newInstance = target.newInstance();
            BeanUtils.copyProperties(source, newInstance);
            return newInstance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 复制list
     *
     * @param source
     * @param target
     * @param <T>
     * @param <K>
     * @return
     */
    /**
     * <b>深拷贝</b>到目标类型：用 Jackson 走一遍序列化/反序列化，嵌套集合与嵌套对象
     * 都会按目标类型<b>逐层重建</b>。
     *
     * <h3>什么时候必须用它而不是 {@link #copy}</h3>
     * {@link #copy} 底层是 Spring 的 {@code BeanUtils.copyProperties}，只做浅拷贝。
     * 它会解析泛型：发现 {@code List<A>} 与 {@code List<B>} 不兼容后<b>直接跳过该属性</b>，
     * 既不报错也不转换 —— 目标字段留在 {@code null}。
     * 表现极其隐蔽：编译通过、接口照常返回，只是那一段数据凭空消失了。
     * 行为由 {@code SolvelaBeanUtilCopyTest} 钉住。
     *
     * <p>所以：<b>目标类型含嵌套的自定义对象或集合时，一律用本方法</b>；
     * 只有确认两边都是扁平结构（基本类型、String、日期、同类型集合）才用 copy。
     *
     * <p>代价是一次 JSON 往返，比 copy 慢一个量级。用在配置保存这类低频写入路径上
     * 完全够用；分页列表那种一次几十条的装配仍应走 copy。
     */
    public static <T> T deepCopy(Object source, Class<T> target) {
        if (source == null || target == null) {
            return null;
        }
        return JsonUtils.getMapper().convertValue(source, target);
    }

    public static <T, K> List<K> copyList(List<T> source, Class<K> target) {
        if (null == source || source.isEmpty()) {
            return Collections.emptyList();
        }
        return source.stream().map(e -> copy(e, target)).collect(Collectors.toList());
    }

    /**
     * 手动验证对象 Model的属性
     * 需要配合 hibernate-validator 校验注解
     *
     * @param t
     * @return String 返回null代表验证通过，否则返回错误的信息
     */
    public static <T> String verify(T t) {
        // 获取验证结果
        Set<ConstraintViolation<T>> validate = VALIDATOR.validate(t);
        if (validate.isEmpty()) {
            // 验证通过
            return null;
        }
        // 返回错误信息
        List<String> messageList = validate.stream().map(ConstraintViolation::getMessage).collect(Collectors.toList());
        return messageList.toString();
    }
}
