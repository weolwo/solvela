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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 玩法侧不许绕过 {@code PrizeEventPublisher} 直接调发奖处理器。
 *
 * <h3>这条规矩此前是 Maven 守的，2026-08-31 起由本测试守</h3>
 * 在那之前 {@code solvela-consumer} 是独立模块，而 {@code solvela-marketing} 不依赖它 ——
 * 一行 {@code @Autowired PrizeDispatchHandler} 根本编不过。三个模块合并之后
 * （足迹完全相同，合并不改变任何进程的 classpath），那道物理边界没了，
 * 于是把它降级成一条会失败的断言，而不是让它悄悄消失。
 *
 * <h3>绕过去会发生什么</h3>
 * 正常路径是：业务在<b>事务内</b>调 {@code PrizeEventPublisher.publish}，
 * {@code GlobalEventDispatcher.dispatch} 挂在 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 上，事务<b>提交之后</b>才派发。
 *
 * <p>直接方法调用则是在<b>事务内</b>执行派发 —— 后面业务事务一回滚，
 * 提案已经建了、资产已经动了，而回滚撤不掉它们。这类 bug 的特征是
 * 「平时完全正常，只在回滚路径上错，且错完没有任何痕迹」。
 *
 * <h3>为什么扫 class 而不是扫 import</h3>
 * 全限定名直接写在代码里（{@code new solvela.consumer.handler.PrizeDispatchHandler(...)}）
 * 是没有 import 行的。class 文件的常量池里躲不掉。
 */
class PrizeDispatchBypassTest {

    /** 玩法侧：抽奖、任务、彩票、商城。它们只该经 solvela.dispatch 的出口发奖 */
    private static final List<String> PLAY_PACKAGES = List.of("draw", "task", "lottery", "mall");

    /** 禁止被玩法侧直接引用的包（class 文件里是斜杠形式） */
    private static final String FORBIDDEN = "solvela/consumer/handler";

    @Test
    @DisplayName("🔴 玩法侧不许直接引用 solvela.consumer.handler")
    void 玩法侧不许绕过发奖出口() throws IOException {
        Path classes = Path.of("target", "classes", "solvela");
        assertTrue(Files.isDirectory(classes),
                "没找到 target/classes —— 本测试要在 compile 之后跑，否则它会假装通过");

        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (String pkg : PLAY_PACKAGES) {
            Path dir = classes.resolve(pkg);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path f : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                    scanned++;
                    String bytes = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                    if (bytes.contains(FORBIDDEN)) {
                        offenders.add(classes.relativize(f).toString());
                    }
                }
            }
        }

        // 一个都没扫到就通过，等于没测 —— 那比没有这个测试更糟，因为它给人已经守住了的错觉
        assertTrue(scanned > 0, "一个 class 都没扫到，PLAY_PACKAGES 的包名可能已经改了：" + PLAY_PACKAGES);

        assertFalse(!offenders.isEmpty(),
                () -> """
                        这些玩法侧的类直接引用了 %s：
                          %s

                        发奖的唯一出口是 solvela.dispatch.outbox.PrizeEventPublisher.publish(UserPrizeEvent)，
                        必须【在业务事务内】调用。接住它的 GlobalEventDispatcher.dispatch 挂在
                        @TransactionalEventListener(AFTER_COMMIT) 上，事务提交之后才派发。

                        直接调 PrizeDispatchHandler 会把派发拉进事务里执行：
                        业务事务后续回滚时，提案已建、资产已动，回滚撤不掉 ——
                        平时完全正常，只在回滚路径上错，且错完没有任何痕迹。

                        要加新的发奖触发点，就多调一次 publish；要改派发行为，改 handler 本身。
                        """.formatted(FORBIDDEN.replace('/', '.'), String.join("\n  ", offenders)));
    }
}
