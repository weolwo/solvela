package solvela.task.taskconfig.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务中心「出哪些任务」的判据，必须和运行态「推进哪些任务」<b>逐字一致</b>。
 *
 * <h3>这条测试对应一个真实的坑</h3>
 * 2026-09-05：运营配好了「每日签到」，任务中心一条都不出。
 *
 * <p>根因是判据分叉：
 * <ul>
 *   <li>运行态 {@code TaskEventService.findSubscribedConfigs} 判
 *       <b>{@code status != 3 已下线}</b>，因为「全工程没有任何地方把 status 从 1 改成 2，
 *       {@code wizardSubmit} 落的就是 1」——那边的注释白纸黑字写了这句；</li>
 *   <li>而任务中心当时判 <b>{@code status == 2 生效中}</b>。</li>
 * </ul>
 *
 * <p>于是任务中心<b>结构性永远为空</b>：运营配得再对也不会出现一条，
 * 而接口 200、日志干净、一点异常都没有 —— 铁律 16「前提不成立时通过和空过分不出来」。
 *
 * <p>比「空」更糟的是两边不一致时的样子：<b>运行态照常涨进度，用户却看不见</b>，
 * 他在完成一个界面上不存在的任务。
 *
 * <h3>为什么读源码而不是跑查询</h3>
 * 要验的是「两处判据相同」这件事本身，而它是<b>两段分别写在不同类里的条件</b>。
 * 用数据去验，只能验到「某条数据在两边表现一致」，验不到判据本身；
 * 而分叉恰恰是在别人改了其中一处时发生的。
 *
 * @Date 2026-09-05
 */
class TaskCenterCriterionTest {

    private static final Path TASK_CENTER = Path.of("src", "main", "java", "solvela",
            "task", "taskconfig", "spi", "TaskCenterProviderImpl.java");

    private static final Path RUNTIME = Path.of("src", "main", "java", "solvela",
            "task", "runtime", "TaskEventService.java");

    @Test
    @DisplayName("🔴 任务中心不许判 status == ACTIVE —— 那会让它永远为空")
    void 不许判生效中() throws IOException {
        String source = Files.readString(TASK_CENTER, StandardCharsets.UTF_8);

        assertFalse(source.contains("eq(TaskConfig::getStatus, TaskConfigStatusEnum.ACTIVE)"),
                """
                        任务中心又判回 status == ACTIVE 了。

                        向导创建的任务落的是 status=1（待生效），全工程没有任何流程把它推到 2。
                        判 == 2 的结果是任务中心一条都不出，而接口 200、日志干净 ——
                        没有任何信号告诉你它坏了。

                        要判就得连 TaskEventService.findSubscribedConfigs 一起改，
                        否则运行态照常涨进度而用户看不见。
                        """);

        assertTrue(source.contains("ne(TaskConfig::getStatus, TaskConfigStatusEnum.OFFLINE)"),
                "任务中心该判 status != OFFLINE，和运行态一致");
    }

    @Test
    @DisplayName("🔴 运行态的判据没被改成 == ACTIVE —— 改了的话所有任务都不再推进")
    void 运行态判据没变() throws IOException {
        String source = Files.readString(RUNTIME, StandardCharsets.UTF_8);

        assertTrue(source.contains("ne(TaskConfig::getStatus, TaskConfigStatusEnum.OFFLINE)"),
                """
                        运行态的订阅判据变了。它原本是 status != OFFLINE ——
                        改成 == ACTIVE 的话，事件收得到、日志照打，但一条进度都不涨。
                        """);
    }

    @Test
    @DisplayName("两处都按时间窗过滤：没开始和已结束的任务不该出现，也不该推进")
    void 两处都有时间窗() throws IOException {
        for (Path path : new Path[]{TASK_CENTER, RUNTIME}) {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertTrue(source.contains("TaskConfig::getStartTime")
                            && source.contains("TaskConfig::getEndTime"),
                    path.getFileName() + " 少了任务自身的时间窗过滤");
        }
    }
}
