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
 * 外部场景域不许直接碰资产域（ledger），也不许碰商城。
 *
 * <h3>两条缝，理由不同</h3>
 * <ul>
 *   <li><b>→ ledger</b>：和 {@code MallLedgerBoundaryTest} 守的是同一条<b>将来的服务边界</b>。
 *       券是资产，目标形态是资产独立成服务。合法的路只有
 *       {@code CouponQueryApi} / {@code CouponWriteOffApi}（契约在 solvela-member-api）；</li>
 *   <li><b>→ mall</b>：这两个模块的共同点<b>只有「都用券」</b>。
 *       让充话费认识商城，下一步就是有人从这里去读商品表、复用订单状态机，
 *       而那正是当初把它拆出来要避免的事。</li>
 * </ul>
 *
 * <h3>第一道闸门是 pom，这里是第二道</h3>
 * {@code solvela-external} 的依赖里刻意没有 solvela-ledger 和 solvela-mall，
 * 引用不到就是编译不过。但 pom 是可以被加回来的，而加的那一刻没有任何提示 ——
 * 这条会红。读的是 class 常量池，所以连「反射按字符串拿 solvela.ledger.xxx」也拦得住。
 *
 * @Date 2026-09-15
 */
class ExternalLedgerBoundaryTest {

    /** 资产域。本模块一个字都不该认识它 */
    private static final String LEDGER = "solvela/ledger/";

    /** 商城。同上 —— 两者的共同点只有「都用券」 */
    private static final String MALL = "solvela/mall/";

    @Test
    @DisplayName("🔴 外部场景域不许直接引用资产域与商城，用券只能走 member-api 的契约")
    void 外部场景域不许越界() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path classes = Path.of("target", "classes");
        assertTrue(Files.isDirectory(classes),
                "没找到 target/classes —— 本测试要在 compile 之后跑，否则它会假装通过");

        int scanned = 0;
        try (Stream<Path> walk = Files.walk(classes)) {
            for (Path f : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                scanned++;
                String bytes = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                String name = classes.relativize(f).toString().replace('\\', '/');
                if (bytes.contains(LEDGER)) {
                    offenders.add(name + "  → " + LEDGER);
                }
                if (bytes.contains(MALL)) {
                    offenders.add(name + "  → " + MALL);
                }
            }
        }
        /*
         * 一个都没扫到就通过，等于没测 —— 那比没有这个测试更糟。
         * 阈值取 5：本模块今天就几个源文件，但绝不会是 0。
         */
        assertTrue(scanned > 5, "只扫到 " + scanned + " 个 class，target/classes 可能不完整");

        assertTrue(offenders.isEmpty(), () -> """
                外部场景域越界引用了：
                  %s

                用券只能走 CouponQueryApi / CouponWriteOffApi（solvela-member-api 里的契约），
                今天它们解析成同进程的 bean，拆服务之后解析成 HTTP 代理，调用方一行不改。

                商城那边则是压根不该认识：两个模块的共同点只有「都用券」。

                注意：能编译到这一步，说明 solvela-external 的 pom 里被加了对应依赖。
                那条依赖本身就该去掉。
                """.formatted(String.join("\n  ", offenders.stream().distinct().toList())));
    }
}
