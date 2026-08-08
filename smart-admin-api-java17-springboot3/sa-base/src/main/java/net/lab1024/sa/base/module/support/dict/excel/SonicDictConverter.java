package net.lab1024.sa.base.module.support.dict.excel;

import jakarta.annotation.Resource;
import net.lab1024.sa.base.module.support.dict.service.DictService;
import net.lab1024.sa.base.sonicexcel.SonicExcelException;
import net.lab1024.sa.base.sonicexcel.converter.SonicContext;
import net.lab1024.sa.base.sonicexcel.converter.SonicConverter;
import org.springframework.stereotype.Component;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 字典码 → 字典标签的翻译。
 *
 * <p><b>这个类的存在本身就是 SonicExcel 相对阿里系最实质的差异点。</b>
 * EasyExcel 的 Converter 同样靠反射无参构造实例化，够不到 Spring 容器，
 * 所以字典翻译只能一直手写在 service 里拼 VO。SonicConverterFactory 会优先从容器取 Bean，
 * 这个转换器才能注入 DictService。
 *
 * <p><b>双向可用。</b> 反向（标签 → 码）走 {@code DictService#getDictDataValueByLabel}，
 * 底层是 {@code DICT_DATA_LABEL} 这个独立缓存 —— 不能用 {@code DictService#getAll()} 现查，
 * 那是直连 DB、每个单元格一次全表读。
 *
 * <p>同一字典下出现同名标签时直接报错而不是随便挑一个：映射有歧义还硬映射，
 * 就是往库里写脏数据。
 *
 * @Date 2026-08-08
 */
@Component
public class SonicDictConverter implements SonicConverter<String, String> {

    @Resource
    private DictService dictService;

    /**
     * 每个单元格都会走一次转换，注解不能每次都反射读 —— 按字段缓存。
     */
    private final Map<AnnotatedElement, SonicDict> configs = new ConcurrentHashMap<>();

    @Override
    public String exportConvert(String value, SonicContext ctx) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        SonicDict config = config(ctx);
        return Arrays.stream(value.split(java.util.regex.Pattern.quote(config.separator())))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> {
                    String label = dictService.getDictDataLabel(config.value(), code);
                    // 查不到就原样保留码值，比吐一个空字符串更容易排查
                    return label == null || label.isEmpty() ? code : label;
                })
                .collect(Collectors.joining(config.separator()));
    }

    @Override
    public String importConvert(String value, SonicContext ctx) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        SonicDict config = config(ctx);
        StringBuilder codes = new StringBuilder();
        for (String label : value.split(java.util.regex.Pattern.quote(config.separator()))) {
            String trimmed = label.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String code = dictService.getDictDataValueByLabel(config.value(), trimmed);
            if (code == null) {
                // 报到行级错误里，用户能看到是哪一行的哪个值不认识
                throw new SonicExcelException("「" + trimmed + "」不是字典 " + config.value() + " 中的合法取值");
            }
            if (!codes.isEmpty()) {
                codes.append(config.separator());
            }
            codes.append(code);
        }
        return codes.toString();
    }

    private SonicDict config(SonicContext ctx) {
        return configs.computeIfAbsent(ctx.element(), element -> {
            SonicDict annotation = element.getAnnotation(SonicDict.class);
            if (annotation == null) {
                throw new SonicExcelException("列「" + ctx.title() + "」用了 SonicDictConverter，"
                        + "但字段上没有 @SonicDict 指定字典编码");
            }
            return annotation;
        });
    }
}
