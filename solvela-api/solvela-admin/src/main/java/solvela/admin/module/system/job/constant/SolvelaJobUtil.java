package solvela.admin.module.system.job.constant;

import org.springframework.scheduling.support.CronExpression;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * solvela job util
 *
 * @author huke
 * @date 2024/6/18 20:00
 */
public class SolvelaJobUtil {

    private SolvelaJobUtil() {
    }

    /**
     * 校验cron表达式 是否合法
     */
    public static boolean checkCron(String cron) {
        return CronExpression.isValidExpression(cron);
    }

    /**
     * 打印一些展示信息到控制台（环保绿）
     */
    public static void printInfo(String info) {
        System.out.printf("\033[32;1m %s \033[0m", info);
    }

    // ==================== 下次触发时间 ====================

    /**
     * 计算下一次触发时间（不含 jitter）。
     *
     * <p>🔴 {@code baseTime} 必须由调用方从<b>数据库</b>取，绝不要在这里 {@code now()} ——
     * 整个模块只认数据库一个时钟（铁律 9/10），这个方法是纯函数，不该自己去摸时间。
     *
     * @return 算不出来（表达式非法 / 一次性任务已终结）时返回 null，由调用方决定怎么处理。
     * 刻意不抛异常：一条脏配置不该让整轮扫描炸掉，别的任务还等着跑
     */
    public static LocalDateTime nextTriggerTime(String triggerType, String triggerValue, LocalDateTime baseTime) {
        if (null == baseTime || null == triggerValue || triggerValue.isBlank()) {
            return null;
        }
        if (SolvelaJobTriggerTypeEnum.CRON.equalsValue(triggerType)) {
            if (!checkCron(triggerValue)) {
                return null;
            }
            return CronExpression.parse(triggerValue).next(baseTime);
        }
        // ONE_TIME 没有「下一次」—— 执行完就置 terminal_flag，由调用方处理
        return null;
    }

    /**
     * 🔴 确定性打散：按 jobId 取固定偏移，<b>不是随机数</b>。
     *
     * <p>为什么不用 {@code Random(0, jitter)}：
     * <ul>
     *   <li>「未来 N 次执行时间」的预览会失真，运营看到的和实际跑的对不上；</li>
     *   <li>排查时「昨天 0:37、今天 0:12」很难与其它系统的日志对齐。</li>
     * </ul>
     * 确定性打散下同一任务每天固定偏移，可预测、可预览、日志能对齐。
     * 几十个任务的规模下 hash 撞车概率可忽略。
     *
     * <p>存在的意义：运营和开发最爱配 {@code 0 0 0 * * ?}（零点）和 {@code 0 0 * * * ?}（整点）。
     * 几十个任务同时触发，数据库瞬时压力陡增，抢占的行锁冲突也集中爆发。
     *
     * <p>⚠️ {@code ONE_TIME} 必须传 0：「活动 00:00 开始」是<b>业务时间点</b>而不是调度时间点，
     * 打散 60 秒等于活动晚开一分钟。这一条由保存时的校验强制，这里只是不去特判。
     */
    public static LocalDateTime applyJitter(LocalDateTime triggerTime, Integer jobId, int jitterSeconds) {
        if (null == triggerTime || jitterSeconds <= 0 || null == jobId) {
            return triggerTime;
        }
        int offset = Math.floorMod(Integer.hashCode(jobId), jitterSeconds);
        return triggerTime.plusSeconds(offset);
    }

    /**
     * 从给定时间起算未来 N 次执行时间，供后台预览。
     *
     * <p>会把 jitter 一起算进去 —— 预览必须和实际一致，否则运营会以为系统在乱跑
     */
    public static List<LocalDateTime> queryNextTimeList(String triggerType, String triggerValue,
                                                        LocalDateTime baseTime, Integer jobId,
                                                        int jitterSeconds, int num) {
        if (null == baseTime || SolvelaJobTriggerTypeEnum.ONE_TIME.equalsValue(triggerType)) {
            return Collections.emptyList();
        }
        List<LocalDateTime> timeList = new ArrayList<>(num);
        LocalDateTime cursor = baseTime;
        for (int i = 0; i < num; i++) {
            cursor = nextTriggerTime(triggerType, triggerValue, cursor);
            if (null == cursor) {
                break;
            }
            timeList.add(applyJitter(cursor, jobId, jitterSeconds));
        }
        return timeList;
    }

    // ==================== 节点信息 ====================

    /**
     * 获取当前 Java 应用程序的工作目录
     */
    public static String getProgramPath() {
        return System.getProperty("user.dir");
    }

    /**
     * 获取当前 Java 应用程序的进程id
     */
    public static String getProcessId() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return runtime.getName().split("@")[0];
    }

    // ⚠️ 原来这里有 checkJobClass(String)：用 Class.forName 校验执行类存在且实现了 SolvelaJob。
    //    它已随「按 handler 名注册」一起删除 —— 那个校验正是缺陷 1 最毒的一环：
    //    校验用的是原类名（必然通过），运行期匹配用的却是被 CGLIB 代理后的类名（必然失败），
    //    于是「配置校验通过 + 任务永不执行」，全程没有任何报错。
    //    现在的校验入口是 SolvelaJobHandlerRegistry#contains，与运行期匹配用的是同一份注册表。
    //
    // ⚠️ getFixedDelayVal / checkFixedDelay 随 FIXED_DELAY 触发类型一并删除，理由见
    //    SolvelaJobTriggerTypeEnum 的类注释（它与抢占式调度概念上不相容）。
    //
    // ⚠️ 还删掉了一个 main() 方法（手工验证用），它不该留在生产代码里。
}
