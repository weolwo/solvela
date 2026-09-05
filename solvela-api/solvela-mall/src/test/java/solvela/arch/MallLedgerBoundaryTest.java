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
 * 商城不许直接碰资产域（ledger）。
 *
 * <h3>这条缝和 {@code LedgerBoundaryTest} 守的是同一条</h3>
 * 目标形态是<b>资产独立成服务</b>。那条测试守的是 marketing → ledger；
 * 2026-09-05 商城拆成独立模块、并且真的开始要扣积分了，于是同一条缝多了一个入口。
 *
 * <p>合法的路只有一条：{@code AssetDebitApi}（契约在 solvela-member-api）。
 * 今天它在本进程里解析成 ledger 的 {@code AssetDebitApiService}，
 * 拆出去之后解析成 HTTP 代理，<b>调用方一行不改</b> ——
 * 这正是当初把契约与实现分开要换来的东西。
 *
 * <h3>为什么不能图省事直接调 MemberWalletService</h3>
 * 今天完全跑得通，坏的是拆的那一天：那些调用点要一个个找出来重写，
 * 而它们<b>不会有任何标记</b>。这个测试就是那个标记。
 *
 * <h3>第一道闸门是 pom，这里是第二道</h3>
 * {@code solvela-mall} 的依赖里刻意没有 solvela-ledger，引用不到就是编译不过。
 * 但 pom 是可以被加回来的，而加的那一刻没有任何提示 —— 这条会红。
 * 读的是 class 常量池，所以连「反射按字符串拿 solvela.ledger.xxx」也拦得住。
 *
 * @Date 2026-09-05
 */
class MallLedgerBoundaryTest {

    /** 资产域。商城一个字都不该认识它 */
    private static final String LEDGER = "solvela/ledger/";

    @Test
    @DisplayName("🔴 商城不许直接引用资产域，扣积分只能走 AssetDebitApi")
    void 商城不许直接调资产域() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path classes = Path.of("target", "classes");
        assertTrue(Files.isDirectory(classes),
                "没找到 target/classes —— 本测试要在 compile 之后跑，否则它会假装通过");

        int scanned = 0;
        try (Stream<Path> walk = Files.walk(classes)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                scanned++;
                String bytes = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                if (bytes.contains(LEDGER)) {
                    offenders.add(classes.relativize(f).toString().replace('\\', '/'));
                }
            }
        }
        /*
         * 一个都没扫到就通过，等于没测 —— 那比没有这个测试更糟。
         * 阈值取 30：本模块四十多个源文件，编译产物只会更多。
         */
        assertTrue(scanned > 30, "只扫到 " + scanned + " 个 class，target/classes 可能不完整");

        assertTrue(offenders.isEmpty(), () -> """
                商城直接引用了资产域：
                  %s

                扣积分 / 退积分只能走 AssetDebitApi（solvela-member-api 里的契约），
                今天它解析成同进程的 bean，拆服务之后解析成 HTTP 代理，调用方一行不改。

                注意：能编译到这一步，说明 solvela-mall 的 pom 里被加了 solvela-ledger 依赖。
                那条依赖本身就该去掉。
                """.formatted(String.join("\n  ", offenders.stream().distinct().toList())));
    }
}
