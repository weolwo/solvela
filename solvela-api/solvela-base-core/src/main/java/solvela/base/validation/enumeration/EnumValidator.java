package solvela.base.validation.enumeration;


import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import solvela.enums.BaseEnum;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 枚举类校验器
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2017/11/11 15:34
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public class EnumValidator implements ConstraintValidator<CheckEnum, Object> {

    /**
     * 枚举类实例集合
     */
    private List<Object> enumValList;

    /**
     * 是否必须
     */
    private boolean required;

    @Override
    public void initialize(CheckEnum constraintAnnotation) {
        // 获取注解传入的枚举类对象
        required = constraintAnnotation.required();
        Class<? extends BaseEnum> enumClass = constraintAnnotation.value();
        enumValList = Stream.of(enumClass.getEnumConstants()).map(BaseEnum::getValue).collect(Collectors.toList());
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext constraintValidatorContext) {
        // 判断是否必须
        if (null == value) {
            return !required;
        }

        if (value instanceof BaseEnum) {
            // 字段类型已经是枚举本身（枚举化改造之后的形态）。
            // 这一支不能省：enumValList 装的是各常量的 value（Integer / String），
            // contains(枚举实例) 恒为 false，整个接口会变成恒返回 400 ——
            // TicketStatusEnum 当年就是这个症状（见其 javadoc）。
            //
            // 能走到这里说明 Jackson 已经成功把入参反序列化成了枚举常量，
            // 也就是说取值天然合法；非法值在反序列化阶段就被拒了（400 / INVALID_ARGUMENT）。
            // 本注解此时只剩 required 的语义，而它在上面的 null 判断里已经处理过了。
            return true;
        }

        if (value instanceof List) {
            // 如果为 List 集合数据
            return this.checkList((List<Object>) value);
        }

        // 校验是否为合法的枚举值
        return enumValList.contains(value);
    }

    /**
     * 校验集合类型
     *
     */
    private boolean checkList(List<Object> list) {
        if (required && list.isEmpty()) {
            // 必须的情况下 list 不能为空
            return false;
        }
        // 校验是否重复
        long count = list.stream().distinct().count();
        if (count != list.size()) {
            return false;
        }
        // 元素已经是枚举常量时同上：能反序列化出来就说明合法
        if (list.stream().allMatch(e -> e instanceof BaseEnum)) {
            return true;
        }
        return enumValList.containsAll(list);
    }
}
