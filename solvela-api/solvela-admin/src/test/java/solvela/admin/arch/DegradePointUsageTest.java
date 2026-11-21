package solvela.admin.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.observe.DegradePoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 每个 {@link DegradePoint} 在生产代码里至少被记录一次。
 *
 * <p>枚举里有、代码里没人记的点位，面板上那条序列永远是空的 —— 看着像「从没降级过」，其实是没在数。
 * 删掉一处降级时把枚举值一起删，这条用例会提醒。
 */
class DegradePointUsageTest {

    @Test
    @DisplayName("每个降级点位都有生产代码在记")
    void 每个点位都有人记() throws IOException {
        Path root = Paths.get("..").toAbsolutePath().normalize();
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(root)) {
            sources = paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/") && !s.contains("/target/");
                    })
                    .filter(p -> !p.getFileName().toString().equals("DegradePoint.java"))
                    .toList();
        }
        // 空扫通过比没有测试更糟：工作目录不对时它会永远绿着
        assertTrue(sources.size() > 1000, "只扫到 " + sources.size() + " 个主代码文件，扫描根目录多半不对");

        StringBuilder all = new StringBuilder();
        for (Path p : sources) {
            all.append(Files.readString(p, StandardCharsets.UTF_8));
        }
        String text = all.toString();
        Set<String> unused = Arrays.stream(DegradePoint.values())
                .map(Enum::name)
                .filter(name -> !text.contains("Degradations.record(DegradePoint." + name + ")"))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(Set.of(), unused, "这些降级点位没有任何生产代码在记：要么补上记录，要么从 DegradePoint 里删掉");
    }
}
