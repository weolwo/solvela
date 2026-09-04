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
 * <p>2026-09-04 实测：mall 的 41 个源文件对外只认识 enums / base / exception 与一处 member，
 * 而<b>模块内没有任何一个字认识 mall</b>。也就是说这条缝今天是干净的，
 * 将来要把商城拆成独立模块（甚至独立服务）是<b>移动文件 + 加一个 pom</b>，不是重写。
 *
 * <p>但干净是不会自己保持的：两边在同一个模块、同一个进程、同一个库，
 * 明天有人在抽奖里直接 {@code new MallOrderService()} 拿商品名，
 * 今天完全跑得通，坏的是拆的那一天。这个测试就是那条缝剩下的全部保障。
 *
 * <h3>两个方向都要守</h3>
 * <ul>
 *   <li><b>玩法 -> 商城</b>：抽奖/任务/彩票要商品信息，说明该走一个契约接口，
 *       而不是直接调商城的 service；</li>
 *   <li><b>商城 -> 玩法</b>：商城要发一个奖，同样该走既有的发奖契约
 *       （{@code UserPrizeEvent} / {@code MemberProposalApi}），
 *       而不是反手去调 draw 的运行态。</li>
 * </ul>
 *
 * <p>⚠️ 与 {@code LedgerBoundaryTest} 的区别：那条缝是<b>已经确定要拆</b>的服务边界，
 * 这条只是「保持可拆」。所以这里不禁 pom 依赖（同一个模块，无从禁起），
 * 只禁两个包之间的直接引用。
 *
 * @Author alaric
 * @Date 2026-09-04
 */
class MallBoundaryTest {

    private static final String MALL = "solvela/mall/";

    /** 营销玩法的几个包。mall 不该认识它们中的任何一个 */
    private static final List<String> PLAY_PACKAGES = List.of(
            "solvela/draw/", "solvela/task/", "solvela/lottery/", "solvela/activity/", "solvela/stat/");

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

    @Test
    @DisplayName("🔴 商城不许反手去调玩法的运行态")
    void 商城不许直接调玩法() throws IOException {
        List<String> offenders = new ArrayList<>();
        scan((name, bytes) -> {
            if (!isUnder(name, MALL)) {
                return;
            }
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
