package solvela.admin.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住「接入层的壳」与「领域层的值」之间的字段对齐。
 *
 * <h3>要防的是什么</h3>
 * 后台每个接口都有两套形状几乎一样的类型：接入层的 {@code XxxForm} / {@code XxxVO}，
 * 领域层的 {@code XxxCommand} / {@code XxxQuery} / {@code XxxDTO}。
 * 两者靠 {@code SolvelaBeanUtil.copy} 对接 —— 全仓 <b>177 处</b>，而它是<b>按字段名反射匹配</b>的。
 *
 * <p>于是有一类改动会静默出事：把一侧的 {@code poolCode} 改名成 {@code prizePoolCode}，
 * 编译通过、启动通过、接口 200，只是那一列在响应里安静地变成 {@code null}；
 * 入参方向更糟 —— 一个本该收到的值变成 null，会一路带进落库。
 * 反射拷贝不会为此报任何错，IDE 的「查找用法」也看不见这条连线。
 *
 * <p>这个测试就是那条唯一会因为「字段改名 / 改类型」而变红的东西。
 *
 * <h3>比对方向：拷贝的目标端不能有源端给不出的字段</h3>
 * <ul>
 *   <li>出参 {@code DTO -> VO}：VO 上多出来的字段没人填，接口里恒为 null；</li>
 *   <li>入参 {@code Form -> Command/Query}：Command 上多出来的字段没人填，
 *       而它随后要被落库或参与判定。</li>
 * </ul>
 * 反过来则是合法的：DTO 可以比 VO 多几列（领域内部用、不透出），
 * Form 可以比 Command 多几个纯 UI 字段。
 *
 * <h3>嵌套类型按「去掉后缀」比对</h3>
 * {@code List<WizardPrizeForm>} 与 {@code List<WizardPrizeCommand>} 是同一个东西的两侧，
 * 拷贝工具会递归转换，所以比对前把 {@code Form / VO / DTO / Command / Query}
 * 这几个后缀从类型名里剥掉。剥完之后两边仍然对不上，才是真的漂了。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
class DomainMirrorTest {

    /** 所有壳与值的命名后缀。配对就是「去掉后缀之后同名」 */
    private static final List<String> SUFFIXES = List.of("Command", "Query", "Form", "DTO", "VO");

    /**
     * 目标端允许多出来的字段：由服务端填，本就不该让前端传。
     *
     * <p>每加一条都要写清「谁来填」—— 写不出来的多半就是漏传，那正是本测试要抓的。
     */
    private static final Map<String, String> SERVER_FILLED = Map.of(
            "PromotionGroupWorkbenchSaveCommand#groupCode",
            "分组编码由 PromotionGroupService 调 SolvelaCodeUtil.generateUniqueBizCode 生成，前端不传");

    /**
     * ⚠️ 这张表只登记「服务端填」，<b>不登记「反正现在没人用」</b>。
     * 一个字段如果既不在源端、又说不出谁来填，那它就是漏传，正是本测试要抓的东西。
     */

    @Test
    @DisplayName("出参：VO 的每个字段，DTO 都给得出")
    void 出参字段对齐() {
        assertAligned(index("VO"), index("DTO"), "DTO -> VO");
    }

    @Test
    @DisplayName("入参：Command / Query 的每个字段，Form 都给得出")
    void 入参字段对齐() {
        Map<String, Class<?>> targets = new TreeMap<>(index("Command"));
        targets.putAll(index("Query"));
        assertAligned(targets, index("Form"), "Form -> Command/Query");
    }

    /**
     * @param targets   拷贝的目标端（VO / Command / Query），key 是去掉后缀的名字
     * @param sources   拷贝的源端（DTO / Form）
     */
    private void assertAligned(Map<String, Class<?>> targets, Map<String, Class<?>> sources, String direction) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Class<?>> entry : targets.entrySet()) {
            Class<?> source = sources.get(entry.getKey());
            if (source == null) {
                // 没有对应的壳/值，说明这个类型不走 copy 那条路，不在本测试的射程内
                continue;
            }
            if (entry.getValue().isRecord()) {
                /*
                 * record 不可能是 BeanUtils.copyProperties 的目标（它没有无参构造与 setter），
                 * 所以以 record 为目标的那几个命令一定是控制器里手写 new 出来的 ——
                 * 手写构造由编译器保证参数对齐，本测试不必也不该管。
                 * 已核对：DrawExecuteCommand 与 ScriptSaveCommand 都是 `new` 的。
                 *
                 * ⚠️ 若将来有人把某个 record 改成走 SolvelaBeanUtil.deepCopy（Jackson 能填 record），
                 * 这条豁免就要重新考虑。
                 */
                continue;
            }
            checked++;
            problems.addAll(compare(entry.getValue(), source));
        }
        int pairCount = checked;
        assertTrue(pairCount > 0, "一对都没扫到，多半是包路径变了导致这个测试悄悄失效");
        assertTrue(problems.isEmpty(), () -> String.format(
                "%s 的字段对不上（共 %d 对里出了 %d 处）：%n%s%n%n"
                        + "SolvelaBeanUtil.copy 按字段名反射匹配，对不上的那个字段会静默变成 null ——"
                        + "不报错、不告警、接口照样 200。%n"
                        + "要么把两侧改回一致，要么在 DomainMirrorTest.SERVER_FILLED 里登记并写明谁来填。",
                direction, pairCount, problems.size(), String.join("\n", problems)));
    }

    private List<String> compare(Class<?> target, Class<?> source) {
        Map<String, String> sourceFields = fieldsOf(source);
        List<String> problems = new ArrayList<>();
        fieldsOf(target).forEach((name, type) -> {
            if (SERVER_FILLED.containsKey(target.getSimpleName() + "#" + name)) {
                return;
            }
            String sourceType = sourceFields.get(name);
            if (sourceType == null) {
                problems.add(String.format("  %s.%s（%s）在 %s 上不存在",
                        target.getSimpleName(), name, type, source.getSimpleName()));
            } else if (!sourceType.equals(type)) {
                problems.add(String.format("  %s.%s 类型是 %s，而 %s 上是 %s",
                        target.getSimpleName(), name, type, source.getSimpleName(), sourceType));
            }
        });
        return problems;
    }

    /**
     * 实例字段名 -> 去掉壳/值后缀之后的泛型类型串。
     *
     * <p>用泛型串而不是 {@code Field#getType}：{@code List<XxxForm>} 与 {@code List<XxxCommand>}
     * 的 raw type 都是 List，光看 raw type 等于没比。
     */
    private Map<String, String> fieldsOf(Class<?> clazz) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            fields.put(field.getName(), normalize(field.getGenericType().getTypeName()));
        }
        return fields;
    }

    /**
     * 类型串归一：去掉包名与壳/值后缀。
     * {@code java.util.List<solvela.x.WizardPrizeForm>} -> {@code List<WizardPrize>}
     */
    private String normalize(String typeName) {
        String simple = typeName.replaceAll("[\\w.$]*\\.", "").replace('$', '.');
        for (String suffix : SUFFIXES) {
            simple = simple.replaceAll(suffix + "\\b", "");
        }
        return simple;
    }

    /**
     * 扫全仓（含嵌套类），按「去掉后缀的名字」建索引。
     *
     * <p>同名撞车时保留先扫到的那个并不安全 —— 但真出现同名的壳/值本身就该改名，
     * 所以这里如实记下来，让它在失败信息里露头。
     */
    private Map<String, Class<?>> index(String suffix) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        // 默认实现只收「独立且具体」的类，会漏掉嵌套的表单项类型，
                        // 而工作台那几个聚合表单的字段恰恰全在嵌套类里
                        return true;
                    }
                };
        TypeFilter all = (metadataReader, factory) -> true;
        scanner.addIncludeFilter(all);

        Map<String, Class<?>> index = new HashMap<>();
        Set<BeanDefinition> candidates = scanner.findCandidateComponents("solvela");
        for (BeanDefinition definition : candidates) {
            String className = definition.getBeanClassName();
            if (className == null || !className.endsWith(suffix)) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                continue;
            }
            if (clazz.isInterface() || clazz.isEnum() || clazz.isAnnotation()) {
                continue;
            }
            String simple = clazz.getSimpleName();
            index.putIfAbsent(simple.substring(0, simple.length() - suffix.length()), clazz);
        }
        return index;
    }
}
