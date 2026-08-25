package solvela.base.common.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
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
