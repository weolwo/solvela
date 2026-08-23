package sa.scriptengine.core;

import org.springframework.stereotype.Component;
import sa.scriptengine.domain.EngineFunctionMeta;
import sa.scriptengine.domain.ScriptFunctionDocDTO;
import sa.scriptengine.spi.ScriptDomain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 脚本函数中央注册表（Single Source of Truth）
 */
@Component
public class EngineFunctionRegistry {

    private final Map<String, EngineFunctionMeta> functionMap = new ConcurrentHashMap<>();

    /**
     * 注册函数。
     *
     * <p>同名<b>直接抛异常让应用起不来</b>，绝不静默覆盖：域前缀已经保证跨域不可能撞名，
     * 能撞上的一定是同域内两个 Handler 写了同一个短名，属于开发期错误，越早炸越好。
     */
    public void register(EngineFunctionMeta meta) {
        EngineFunctionMeta exists = functionMap.putIfAbsent(meta.getFunctionName(), meta);
        if (exists != null) {
            throw new IllegalStateException(String.format(
                    "脚本函数名冲突：[%s] 被重复注册。已存在于 %s#%s，又出现在 %s#%s",
                    meta.getFunctionName(),
                    exists.declaringClassName(), exists.getMethod().getName(),
                    meta.declaringClassName(), meta.getMethod().getName()));
        }
    }

    public List<EngineFunctionMeta> getAllFunctions() {
        return new ArrayList<>(functionMap.values());
    }

    public int size() {
        return functionMap.size();
    }

    /**
     * 按域统计，启动日志用
     */
    public Map<ScriptDomain, Long> countByDomain() {
        return functionMap.values().stream()
                .collect(Collectors.groupingBy(EngineFunctionMeta::getDomain, Collectors.counting()));
    }

    /**
     * 导出函数文档，供前端 Monaco 编辑器做分组补全与悬浮提示
     */
    public List<ScriptFunctionDocDTO> exportDocs() {
        return functionMap.values().stream()
                .sorted(Comparator.comparing((EngineFunctionMeta meta) -> meta.getDomain().ordinal())
                        .thenComparing(EngineFunctionMeta::getFunctionName))
                .map(meta -> {
                    ScriptFunctionDocDTO doc = new ScriptFunctionDocDTO();
                    doc.setDomain(meta.getDomain().name());
                    doc.setDomainTitle(meta.getDomain().getTitle());
                    doc.setFunctionName(meta.getFunctionName());
                    doc.setSimpleName(meta.getSimpleName());
                    doc.setDescription(meta.getDescription());
                    doc.setReturnType(meta.getReturnType());
                    doc.setParams(meta.getParams());
                    doc.setSignature(buildSignature(meta));
                    doc.setClassName(meta.getTargetBean().getClass().getSimpleName());
                    doc.setMethodName(meta.getMethod().getName());
                    return doc;
                })
                .collect(Collectors.toList());
    }

    /**
     * params 里存的是 "Long memberId" 这种带类型的形式，补全签名只要变量名
     */
    private String buildSignature(EngineFunctionMeta meta) {
        String args = meta.getParams().stream()
                .map(param -> param.substring(param.lastIndexOf(" ") + 1))
                .collect(Collectors.joining(", "));
        return meta.getFunctionName() + "(" + args + ")";
    }
}
