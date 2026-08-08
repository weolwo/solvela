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
 * <p><b>已知限制：只支持导出方向。</b> 反向（标签 → 码）需要字典模块提供一个带缓存的
 * label→value 查询，而 DictManager 现在只有 (code,value) → VO 这一条缓存路径，
 * 用 {@code DictService#getAll()} 现查是直连 DB、每个单元格一次全表读。
 * 补那条反向缓存属于字典模块的改动，不该夹带在 Excel 这一轮里做 —— 现有导入也没有字典列。
 * 真要用时会拿到明确的报错而不是错数据。
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
        throw new SonicExcelException("列「" + ctx.title() + "」的字典转换目前只支持导出方向，"
                + "导入需要字典模块补一条带缓存的 标签→码 查询");
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
