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
 * 外部场景域不许认识营销玩法 —— <b>尤其是任务引擎</b>。
 *
 * <h3>为什么补这一条：它是打点方案唯一没人守的那条缝</h3>
 * 想给任务引擎打点的四个域里，三个已经拦住了：
 * <ul>
 *   <li>{@code solvela-member}（14）、{@code solvela-ledger}（18）排在
 *       {@code solvela-marketing}（19）<b>之前</b> —— 加依赖 Maven 直接报循环，
 *       是编译器在守；</li>
 *   <li>{@code solvela-mall}（20）由 {@code PlayBoundaryTest} 守着；</li>
 *   <li><b>{@code solvela-external}（21）排在 marketing 之后，pom 里加一行就能直连，
 *       而在本测试之前没有任何东西拦它。</b></li>
 * </ul>
 *
 * <p>直连的诱惑是真实的：充值成功要推任务进度，最短的写法就是注入
 * {@code TaskEventService} 调一下 —— 今天完全能跑，坏的是拆服务的那一天，
 * 那些调用点要一个个找出来重写，而它们不会有任何标记。
 *
 * <h3>合法的路：发一个 {@code BizActionEvent} 就完事</h3>
 * 本域只声明「充值成功了」这件既成事实（{@code solvela.event.BizActionEvent}，住在
 * solvela-model），谁关心谁订阅。翻译成任务事件是<b>营销侧</b>那个防腐层的活
 * （{@code solvela.task.adapter.BizActionEventListener}）。
 *
 * <p>所以本测试与打点功能并不冲突：事件类在 {@code solvela/event/} 下，
 * 不在下面任何一个被禁的包里。反过来，哪天有人想把事件类挪进 {@code solvela/task/}，
 * 这条会当场变红 —— 那正是它该做的。
 *
 * <h3>第一道闸门是 pom，这里是第二道</h3>
 * 形状对齐同模块的 {@link ExternalLedgerBoundaryTest}：读 class 常量池而不是源码，
 * 所以连「反射按字符串拿 solvela.task.xxx」这种绕过编译期的写法也拦得住。
 *
 * @Date 2026-09-17
 */
class ExternalPlayBoundaryTest {

    /** 营销玩法的几个包。外部场景域一个字都不该认识它们 */
    private static final List<String> PLAY_PACKAGES = List.of(
            "solvela/task/", "solvela/draw/", "solvela/lottery/", "solvela/activity/", "solvela/stat/");

    @Test
    @DisplayName("🔴 外部场景域不许直接引用营销玩法，打点只能发 BizActionEvent")
    void 外部场景域不许直接调玩法() throws IOException {
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
                for (String play : PLAY_PACKAGES) {
                    if (bytes.contains(play)) {
                        offenders.add(name + "  → " + play.replace('/', '.'));
                    }
                }
            }
        }
        /*
         * 一个都没扫到就通过，等于没测 —— 那比没有这个测试更糟，因为它给人已经守住了的错觉。
         * 阈值与 ExternalLedgerBoundaryTest 取同一个 5：本模块今天就几个源文件，但绝不会是 0。
         */
        assertTrue(scanned > 5, "只扫到 " + scanned + " 个 class，target/classes 可能不完整");

        assertTrue(offenders.isEmpty(), () -> """
                外部场景域直接引用了营销玩法：
                  %s

                要给任务引擎打点，不要注入 TaskEventService —— 发一个 BizActionEvent：

                    bizEventPublisher.publish(new BizActionEvent(
                            "RECHARGE_PAID", memberId, orderNo, payAmount, LocalDateTime.now(), payload));

                它住在 solvela-model 的 solvela.event 包，本域已经依赖得到；
                翻译成任务事件是营销侧 BizActionEventListener 的活，本域不该知道那件事存在。

                注意：能编译到这一步，说明 solvela-external 的 pom 里被加了 solvela-marketing。
                那条依赖本身就该去掉。
                """.formatted(String.join("\n  ", offenders.stream().distinct().toList())));
    }
}
