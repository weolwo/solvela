package solvela.admin.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 枚举化改造的<b>棘轮</b>：盯住散落在 service 里的「状态魔法常量」，只许变少，不许变多。
 *
 * <h3>为什么需要它</h3>
 * 改造是一列一列推进的，于是长期存在「实体已经是枚举、别处还留着 Integer 常量」的中间态。
 * 这个中间态里藏着一个<b>编译器抓不到</b>的坑：
 *
 * <pre>{@code
 * private static final Integer PRIZE_STATUS_ENABLED = 1;
 * ...
 * if (!PRIZE_STATUS_ENABLED.equals(prize.getStatus())) { ... }   // getStatus() 已经返回枚举
 * }</pre>
 *
 * {@code Integer.equals(Object)} 接受任何类型，编译通过、测试不报，但它<b>恒为 false</b>。
 * 上面那两行真实存在过（PrizeItemStockService / LotteryPrizeAnalysisService）：
 * {@code t_prize_config.status} 改成枚举之后，两个页面把每一个奖品都标成了「已停用」。
 *
 * <h3>怎么用</h3>
 * 下面这份名单是<b>还没枚举化的列</b>残留的常量。每完成一列改造，就把对应的行删掉；
 * 名单为空之日，就是本测试可以删掉之时。
 *
 * <p>两个方向都会红：
 * <ul>
 *   <li>冒出名单外的新常量 —— 有人在往回走；</li>
 *   <li>名单里的常量已经不存在 —— 改造做完了但没销账，名单不缩短棘轮就失去意义。</li>
 * </ul>
 *
 * @Author alaric
 * @Date 2026-08-29
 */
class EnumMigrationRatchetTest {

    /**
     * 抓所有 {@code private/public static final int/Integer NAME = 数字;}，再按名字过滤。
     *
     * <p>⚠️ 必须带上 public：早期版本只盯 private，于是漏掉了 {@code TaskConst} /
     * {@code MallConst} 这类<b>公共常量类</b> —— 而它们恰恰是同一份魔法值被最多地方引用的形态。
     */
    private static final Pattern MAGIC_STATUS_CONST = Pattern.compile(
            "(?:private|public) static final (?:int|Integer) ([A-Z][A-Z0-9_]*)\\s*=\\s*-?\\d+;");

    /**
     * 名字里带这些词才算「状态语义」。
     *
     * <p>{@code DISPATCH} 是补进来的：{@code LotteryDispatchBatchService} 里
     * 有过一个 {@code DISPATCH_FAIL = 2}，名字里没有 STATUS 也没有 TYPE，
     * 棘轮当时看不见它。{@code TYPE} 一并放宽成前缀匹配，
     * 原来只认 FLOW_TYPE / USER_TYPE 两个具体名字，同样漏。
     *
     * <p>没收 {@code LEVEL}：{@code PRIZE_LEVEL_NONE = 99} 和
     * {@code MAX_EXACT_LEVELS} 都是真的算术常量，收进来只会制造噪音。
     */
    private static final List<String> STATUS_WORDS =
            List.of("STATUS", "MODE", "RESULT", "TYPE", "STATE", "APPROVE", "PAY", "DISPATCH");

    /** 带这些词的一律不算：长度/精度这类算术常量，只是碰巧撞词 */
    private static final List<String> NOT_STATUS_WORDS = List.of("LENGTH", "SCALE", "SIZE");

    /**
     * 尚未枚举化的列留下的常量，<b>只许删，不许加</b> —— 现在是空的。
     *
     * <p>A/B/C 三桶全部枚举化之后，仓库里已经没有「状态/类型语义的裸 int 常量」了。
     * 名单空着不是形式：任何新加的这类常量都会被下面那条用例挡下来，
     * 得先解释清楚为什么不该是枚举，才能往这里加行。
     *
     * <p>最后一批被清掉的是 {@code MallConst} 的 14 个（商品/SKU/分类/订单状态），
     * 它们现在分别是 MallCommodityStatusEnum、EnableStatusEnum、MallOrderStatusEnum。
     */
    private static final Set<String> ALLOWED = Set.of();

    @Test
    @DisplayName("状态魔法常量只许变少：新增即视为改造在往回走")
    void 魔法常量清单没有增长() throws IOException {
        Set<String> found = scan();

        Set<String> added = new TreeSet<>(found);
        added.removeAll(ALLOWED);
        assertEquals(Set.of(), added,
                "出现了名单之外的状态魔法常量。要么改用枚举，要么说明理由后加进 ALLOWED —— "
                        + "特别注意：如果对应的列已经枚举化，`常量.equals(实体.getXxx())` 会恒为 false 且编译不报错");

        Set<String> stale = new TreeSet<>(ALLOWED);
        stale.removeAll(found);
        assertEquals(Set.of(), stale,
                "这些常量已经不存在了，请从 ALLOWED 里删掉对应的行 —— 名单不缩短，棘轮就失去意义");
    }

    /**
     * 抓 {@code Integer.valueOf(x).equals(表达式.getFoo())} 这种<b>装箱比较</b>。
     *
     * <p>它和上面那份常量名单是同一个坑的两种形态：常量那种有名字，能列名单盯住；
     * 这种是<b>字面量写在表达式里</b>，没有名字，名单看不见它。
     */
    private static final Pattern BOXED_EQUALS = Pattern.compile(
            "Integer\\.valueOf\\([^)]*\\)\\.equals\\([^)]*?\\bget([A-Z]\\w*)\\(\\)\\)");

    /** {@code private <类型> <字段名>;} —— 用来建「字段名 → 声明过的类型」索引 */
    private static final Pattern FIELD_DECL = Pattern.compile(
            "private\\s+([A-Za-z][\\w.]*(?:<[^>]*>)?)\\s+(\\w+)\\s*;");

    private static final Set<String> INTEGERISH = Set.of("int", "Integer", "long", "Long", "short", "Short");

    @Test
    @DisplayName("装箱比较：Integer.valueOf(x).equals(枚举/布尔) 恒为 false，编译器不管")
    void 没有拿Integer去比非Integer字段() throws IOException {
        List<Path> sources = mainSources();

        // 1. 建索引：字段名 -> 这个名字在全仓被声明过的所有类型
        Map<String, Set<String>> fieldTypes = new TreeMap<>();
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Path p : sources) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            texts.put(p, text);
            Matcher m = FIELD_DECL.matcher(text);
            while (m.find()) {
                fieldTypes.computeIfAbsent(m.group(2), k -> new TreeSet<>()).add(m.group(1));
            }
        }

        // 2. 找出所有装箱比较，看被比的那个字段是不是真的 Integer
        Set<String> suspects = new TreeSet<>();
        for (Map.Entry<Path, String> e : texts.entrySet()) {
            Matcher m = BOXED_EQUALS.matcher(e.getValue());
            while (m.find()) {
                String getter = m.group(1);
                String field = Character.toLowerCase(getter.charAt(0)) + getter.substring(1);
                Set<String> types = fieldTypes.get(field);
                if (types == null) {
                    // 索引里没有这个字段（record 组件、继承来的 getter 等），无法判定，放过
                    continue;
                }
                if (types.stream().anyMatch(t -> !INTEGERISH.contains(t))) {
                    suspects.add(shorten(root(), e.getKey()) + "#" + field + " 被声明过的类型=" + types);
                }
            }
        }

        assertEquals(Set.of(), suspects,
                "这些地方拿 Integer.valueOf(...).equals(...) 去比一个并非处处都是 Integer 的字段。"
                        + "Integer.equals(Object) 接受任何类型，编译通过但恒为 false —— "
                        + "改成 == 枚举常量，或 Boolean.TRUE.equals(...)");
    }

    private Set<String> scan() throws IOException {
        Set<String> found = new TreeSet<>();
        for (Path p : mainSources()) {
            Matcher m = MAGIC_STATUS_CONST.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) {
                String name = m.group(1);
                boolean statusLike = STATUS_WORDS.stream().anyMatch(name::contains)
                        && NOT_STATUS_WORDS.stream().noneMatch(name::contains);
                if (statusLike) {
                    found.add(shorten(root(), p) + "#" + name);
                }
            }
        }
        return found;
    }

    /** surefire 的工作目录是模块目录（solvela-api/solvela-admin），上一级才是所有模块的根 */
    private Path root() {
        return Paths.get("..").toAbsolutePath().normalize();
    }

    private List<Path> mainSources() throws IOException {
        try (Stream<Path> paths = Files.walk(root())) {
            return paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/") && !s.contains("/target/");
                    })
                    .collect(Collectors.toList());
        }
    }

    /** solvela-api/solvela-marketing/src/main/java/solvela/draw/X.java 缩成 solvela-marketing/draw/X.java */
    private String shorten(Path root, Path file) {
        String rel = root.relativize(file).toString().replace('\\', '/');
        int i = rel.indexOf("/src/main/java/");
        if (i < 0) {
            return rel;
        }
        String module = rel.substring(0, i);
        String pkg = rel.substring(i + "/src/main/java/".length());
        return module + "/" + pkg.replaceFirst("^solvela/", "");
    }
}
