package solvela.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Objects;

/**
 * 枚举类接口。
 *
 * <p>本接口一处声明，同时打通三端，实现类不需要再各自加注解：
 *
 * <ul>
 *   <li><b>数据库</b>：继承 MyBatis-Plus 的 {@link IEnum}，读写自动在枚举与 {@code value} 之间转换。
 *       不需要 {@code @EnumValue}，也不需要配 {@code mybatis-plus.type-enums-package}。</li>
 *   <li><b>HTTP 出参</b>：{@code getValue()} 上的 {@link JsonValue} 让 Jackson 只序列化 value，
 *       前端拿到的还是那个数字 —— 与 admin-web 现有的 {@code $solvelaEnumPlugin.getDescByValue()}
 *       用法完全兼容，前端不用改。</li>
 *   <li><b>HTTP 入参</b>：Jackson 会用同一个 {@link JsonValue} 反查枚举，前端传数字即可。
 *       传了表里没有的值会抛 {@code HttpMessageNotReadableException}，
 *       由 {@code ApiExceptionHandler} 翻译成 400 / INVALID_ARGUMENT。</li>
 * </ul>
 *
 * <p>⚠️ {@code getValue()} 的返回类型是 {@link Serializable} 而不是 {@code Object}，
 * 这是 {@link IEnum} 的类型上界要求。实现类用 Lombok {@code @Getter} 生成的
 * {@code Integer getValue()} / {@code String getValue()} 是协变返回，不受影响。
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2018-07-17 21:22:12
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
public interface BaseEnum extends IEnum<Serializable> {

    /**
     * 获取枚举类的值。数据库存它，JSON 传它。
     *
     * @return
     */
    @JsonValue
    @Override
    Serializable getValue();

    /**
     * 获取枚举类的说明。
     *
     * <p><b>刻意不参与 JSON 序列化</b>：前端已有一份 value → desc 的映射
     * （admin-web 的 solvela-enums-plugin），每条记录都多带一个中文串是纯粹的带宽浪费。
     *
     * @return String
     */
    String getDesc();

    /**
     * 比较参数是否与枚举类的value相同
     *
     * @param value
     * @return boolean
     */
    default boolean equalsValue(Object value) {
        return Objects.equals(getValue(), value);
    }

    /**
     * 比较枚举类是否相同
     *
     * @param baseEnum
     * @return boolean
     */
    default boolean equals(BaseEnum baseEnum) {
        return Objects.equals(getValue(), baseEnum.getValue()) && Objects.equals(getDesc(), baseEnum.getDesc());
    }
}
