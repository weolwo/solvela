package solvela.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商城与营销玩法之间的那条缝：<b>两边现在互不认识，把它保持住</b>。
 *
 * <h3>为什么值得单独立一条</h3>
 * {@code solvela-marketing} 里装着六个包：task / draw / lottery / activity / stat / mall。
 * 前五个是同一个领域的不同玩法（发奖），而 <b>mall 是电商</b> ——
 * 商品、SKU、库存、订单，跟「抽奖发奖」没有任何领域关系，它只是恰好也归运营管。
 *
 * <p>2026-09-04 立这条测试时两边还在同一个模块里，量出来的结果是缝完全干净
 *（mall 的 41 个源文件对外只认识 enums / base / exception 与一处 member，
 * 而模块内没有任何一个字认识 mall）。<b>2026-09-05 商城已经拆成 solvela-mall。</b>
 *
 * <h3>拆完之后这个测试还剩什么用</h3>
 * 第一道闸门变成了 pom：{@code solvela-marketing} 的依赖里<b>刻意没有</b> solvela-mall，
 * 引用不到就是编译不过。和 {@link LedgerBoundaryTest} 守 marketing↔ledger 是同一个形状。
 *
 * <p>那为什么还留着这个测试 —— 因为 pom 是<b>可以被加回来的</b>，而加的那一刻没有任何提示。
 * 这条测试就是加回来之后会红的那个东西。它读的是 class 常量池，
 * 所以连「反射按字符串拿 solvela.mall.xxx」这种绕过编译期的写法也拦得住。
 *
 * <h3>反方向在另一个模块里守</h3>
 * 「商城不许反手去调玩法的运行态」搬去了 {@code solvela-mall} 的 {@code PlayBoundaryTest} ——
 * 必须在那边，因为它要扫的是<b>商城自己的</b> target/classes。
 * 留在这里的话拆完就成了空扫，而空扫是会通过的（见 {@link #scan} 末尾那条断言的理由）。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
class MallBoundaryTest {

    private static final String MALL = "solvela/mall/";

    @Test
    @DisplayName("🔴 玩法侧（draw/task/lottery/activity/stat）不许引用商城")
    void 玩法不许直接调商城() throws IOException {
        List<String> offenders = new ArrayList<>();
        scan((name, bytes) -> {
            if (isUnder(name, MALL)) {
                return;
            }
            if (bytes.contains(MALL)) {
                offenders.add(name + "  ->  solvela.mall");
            }
        });

        assertTrue(offenders.isEmpty(), () -> """
                营销玩法直接引用了商城：
                  %s

                商城是电商域（商品/SKU/库存/订单），跟发奖玩法没有领域关系 ——
                它今天和玩法装在同一个模块里，只是因为还没到拆的时候。

                玩法要用商品信息，请先定一个契约接口（对齐 MemberProposalApi 的做法），
                直接调商城的 service 在今天完全能跑，坏的是拆的那一天：
                那些调用点要一个个找出来重写，而它们不会有任何标记。
                """.formatted(String.join("\n  ", offenders.stream().distinct().toList())));
    }

    /** 扫本模块编译产物。与 LedgerBoundaryTest 同一个做法：读 class 常量池里的包名 */
    private void scan(ClassVisitor visitor) throws IOException {
        Path classes = Path.of("target", "classes");
        assertTrue(Files.isDirectory(classes),
                "没找到 target/classes —— 本测试要在 compile 之后跑，否则它会假装通过");

        int scanned = 0;
        try (Stream<Path> walk = Files.walk(classes)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                scanned++;
                String name = classes.relativize(f).toString().replace('\\', '/');
                visitor.visit(name, new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1));
            }
        }
        // 一个都没扫到就通过，等于没测 —— 那比没有这个测试更糟，因为它给人已经守住了的错觉
        assertTrue(scanned > 100, "只扫到 " + scanned + " 个 class，target/classes 可能不完整");
    }

    private boolean isUnder(String className, String packagePath) {
        return className.startsWith(packagePath);
    }

    @FunctionalInterface
    private interface ClassVisitor {
        void visit(String className, String bytes);
    }
}
