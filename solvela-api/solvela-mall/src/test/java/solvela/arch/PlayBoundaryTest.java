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
 * 商城不许反手去调营销玩法的运行态。
 *
 * <h3>这半条缝为什么必须住在这个模块</h3>
 * 它扫的是<b>商城自己的</b> {@code target/classes}。2026-09-05 之前商城还是
 * {@code solvela-marketing} 里的一个包，这条断言和它的反方向一起放在
 * {@code MallBoundaryTest} 里；拆模块之后再留在那边就成了<b>空扫</b> ——
 * 那边的 target/classes 里一个 {@code solvela/mall/} 都没有了，
 * 于是「没找到违规」永远成立，测试绿着，实际什么都没测。
 *
 * <p>这正是本文件末尾那条 {@code scanned > 30} 断言存在的理由：
 * 空扫通过比没有测试更糟，因为它给人已经守住了的错觉。
 *
 * <h3>第一道闸门是 pom，这里是第二道</h3>
 * {@code solvela-mall} 的依赖里<b>刻意没有</b> solvela-marketing（理由写在它的 pom 里）：
 * 引用不到就是编译不过。但 pom 是可以被加回来的，而加的那一刻没有任何提示 ——
 * 这条测试就是加回来之后会红的那个东西。读 class 常量池而不是源码，
 * 所以连「反射按字符串拿 solvela.draw.xxx」这种绕过编译期的写法也拦得住。
 *
 * <p>形状对齐 {@code LedgerBoundaryTest}（守 marketing↔ledger 那条服务边界），
 * 反方向的一半在 {@code solvela-marketing} 的 {@code MallBoundaryTest} 里。
 *
 * @Date 2026-09-05
 */
class PlayBoundaryTest {

    /** 营销玩法的几个包。商城一个字都不该认识它们 */
    private static final List<String> PLAY_PACKAGES = List.of(
            "solvela/draw/", "solvela/task/", "solvela/lottery/", "solvela/activity/", "solvela/stat/");

    @Test
    @DisplayName("🔴 商城不许直接调玩法的运行态")
    void 商城不许直接调玩法() throws IOException {
        List<String> offenders = new ArrayList<>();
        scan((name, bytes) -> {
            for (String play : PLAY_PACKAGES) {
                if (bytes.contains(play)) {
                    offenders.add(name + "  ->  " + play.replace('/', '.'));
                }
            }
        });

        assertTrue(offenders.isEmpty(), () -> """
                商城直接引用了营销玩法：
                  %s

                商城要发奖（比如下单送积分），走既有的发奖契约 —— UserPrizeEvent 或
                MemberProposalApi，与四个 @PrizeStrategy handler 用的是同一条路。
                反手去调 draw/task 的运行态，等于把电商域和玩法域焊死。

                注意：能编译到这一步，说明 solvela-mall 的 pom 里被加了 solvela-marketing 依赖。
                那条依赖本身就该去掉，理由见 solvela-mall/pom.xml。
                """.formatted(String.join("\n  ", offenders.stream().distinct().toList())));
    }

    /** 扫本模块编译产物。与 LedgerBoundaryTest / MallBoundaryTest 同一个做法：读 class 常量池里的包名 */
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
        /*
         * 一个都没扫到就通过，等于没测。阈值取 30：本模块 41 个源文件，
         * 编译产物只会更多（内部类、record 的合成类）。
         * 🔴 如果哪天这条红了，先确认是不是有人把商城的包又搬走了 —— 别直接调小这个数。
         */
        assertTrue(scanned > 30, "只扫到 " + scanned + " 个 class，target/classes 可能不完整");
    }

    @FunctionalInterface
    private interface ClassVisitor {
        void visit(String className, String bytes);
    }
}
