package solvela.base.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import solvela.enums.BaseEnum;

import java.util.Arrays;

/**
 * 让 {@code @PathVariable} / {@code @RequestParam} 也能按 <b>value</b> 转成枚举。
 *
 * <h3>为什么单独需要它</h3>
 * 请求体走 Jackson，{@code BaseEnum.getValue()} 上的 {@code @JsonValue} 已经管住了；
 * 但<b>路径变量与查询参数不走 Jackson</b>，走的是 Spring 的 ConversionService，
 * 而它对枚举的默认转换是 {@code Enum.valueOf(name)} —— 按<b>常量名</b>匹配。
 *
 * <p>后果很具体：{@code GET /member/updateStatus/{memberId}/{status}} 传 1 过去，
 * Spring 会去找一个名叫 "1" 的常量，找不到 → 400，而且错误信息里只说
 * "Failed to convert value of type String to MemberStatusEnum"，
 * 看不出「是按名字还是按值匹配」这个关键差别。
 *
 * <p>这是围绕枚举的<b>第五套</b>设施 —— 前四套是 MyBatis 的 TypeHandler、
 * {@code @EnumSerialize}、{@code @CheckEnum}、{@code @SonicEnum}，
 * 它们都曾假定字段是 Integer。这一套的成因不同（默认行为按名字而不是按值），
 * 但表现一样：不补就在某条路径上静默失效。
 *
 * <p>ConverterFactory 而不是逐个枚举写 Converter：一个工厂覆盖所有
 * {@link BaseEnum} 实现类，新增枚举不需要再来这里登记。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Configuration
public class EnumConverterConfig {

    @Bean
    public ConverterFactory<String, BaseEnum> baseEnumConverterFactory() {
        return new BaseEnumConverterFactory();
    }

    /**
     * 不用 lambda：ConverterFactory 的泛型信息要靠具体类保留下来，
     * lambda 会被擦成 {@code ConverterFactory<String, BaseEnum>} 之外的形状，
     * Spring 解析不出目标类型上界。
     */
    static class BaseEnumConverterFactory implements ConverterFactory<String, BaseEnum> {

        @Override
        public <T extends BaseEnum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> {
                if (source == null || source.isBlank()) {
                    return null;
                }
                String trimmed = source.trim();
                T[] constants = targetType.getEnumConstants();
                if (constants == null) {
                    throw new IllegalArgumentException(targetType.getSimpleName() + " 不是枚举类型");
                }
                return Arrays.stream(constants)
                        .filter(e -> String.valueOf(e.getValue()).equals(trimmed))
                        .findFirst()
                        // 兜底按常量名匹配：少数接口的路径里传的就是枚举名（如 SPI 的 handler 名）
                        .orElseGet(() -> Arrays.stream(constants)
                                .filter(e -> ((Enum<?>) e).name().equals(trimmed))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "「" + trimmed + "」不是合法的 " + targetType.getSimpleName() + " 取值")));
            };
        }
    }
}
