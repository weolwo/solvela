package solvela.admin.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 临时方案的数量只许变少：让「临时」不靠人记得拆。
 *
 * <p>约定（全局宪法「三、规则显式化」第 6 条）：写死、绕过、过渡用的代码一律标
 * {@code TEMP(编号): 原因; 移除条件}，编号优先用工单号或方案文档里的任务编号。
 * 本用例按编号数后端所有 Java 源码（main 与 test）里的标记：
 * <ul>
 *   <li>某个编号变多、或冒出名单外的编号 —— 有人在加临时方案，先在这里登记并写明去处；</li>
 *   <li>某个编号变少 —— 拆了一部分，把名单里的数字改小（拆光就删掉那一行）。</li>
 * </ul>
 *
 * <p>⚠️ 只扫 solvela-api。管理端 {@code security-incident-list.vue} 里也有一处同编号标记，
 * 前端不在这条用例里 —— 两个前端各自独立，不从后端测试去扫它们。
 */
class TempMarkerRatchetTest {

    /**
     * {@code TEMP(编号)}。编号不以数字开头，以免把 {@code FileStatusEnum.TEMP(1, "临时")} 这类枚举常量算进来。
     */
    private static final Pattern TEMP_MARKER = Pattern.compile("TEMP\\(([^)0-9][^)]*)\\)");

    /**
     * 编号 → 现存标记数。每个编号写明它的去处在哪份文档里。
     */
    private static final Map<String, Integer> ALLOWED = Map.of(
            // docs/架构/安全事件改名-实施方案.md §5.2：收口后全部删除，这一行跟着删
            "安全事件改名-收口", 10);

    @Test
    @DisplayName("TEMP 标记按编号只许变少")
    void TEMP标记只许变少() throws IOException {
        Path root = Paths.get("..").toAbsolutePath().normalize();
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(root)) {
            sources = paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/") && !s.contains("/target/");
                    })
                    // 本类的注释里写着格式示例，不算
                    .filter(p -> !p.getFileName().toString().equals(TempMarkerRatchetTest.class.getSimpleName() + ".java"))
                    .toList();
        }
        // 空扫通过比没有测试更糟：工作目录不对时它会永远绿着
        assertTrue(sources.size() > 1000, "只扫到 " + sources.size() + " 个 Java 文件，扫描根目录多半不对");

        Map<String, Integer> found = new TreeMap<>();
        for (Path p : sources) {
            Matcher m = TEMP_MARKER.matcher(Files.readString(p, StandardCharsets.UTF_8));
            while (m.find()) {
                found.merge(m.group(1), 1, Integer::sum);
            }
        }

        assertEquals(new TreeMap<>(ALLOWED), found,
                "TEMP 标记的数量与登记不符。变多或出现新编号：先在 ALLOWED 里登记并写明去处文档，"
                        + "别让临时方案悄悄进主干；变少：把对应数字改小，拆光就删掉那一行");
    }
}
