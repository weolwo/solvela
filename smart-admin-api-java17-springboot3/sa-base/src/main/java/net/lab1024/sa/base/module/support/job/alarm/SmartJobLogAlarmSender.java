package net.lab1024.sa.base.module.support.job.alarm;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认告警通道：落 ERROR 日志，并带<b>抑制</b>。
 *
 * <p>🔴 <b>抑制不是可选项。</b> 健康自检每 5 分钟跑一次，
 * 一个失联的执行器会让它每 5 分钟报一次同样的错 ——
 * 一天 288 条。日志被同一条消息刷屏之后，<b>真正的新问题会被淹没</b>，
 * 而告警的全部价值就在于「新问题能被看见」。
 *
 * <p>抑制窗口内只累计不输出，窗口结束时带上累计次数补一条 ——
 * 这样既不刷屏，也不会让人误以为问题已经消失。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SmartJobLogAlarmSender implements SmartJobAlarmSender {

    /**
     * 同一条告警的最短重复间隔
     */
    private static final Duration SUPPRESS_WINDOW = Duration.ofHours(1);

    private record Suppressed(LocalDateTime lastSentTime, int suppressedCount) {
    }

    private final Map<String, Suppressed> suppressMap = new ConcurrentHashMap<>();

    @Override
    public void send(Type type, String title, String content, String receiver) {
        String key = type.name() + "|" + title;
        LocalDateTime now = LocalDateTime.now();

        Suppressed prev = suppressMap.get(key);
        if (null != prev && Duration.between(prev.lastSentTime(), now).compareTo(SUPPRESS_WINDOW) < 0) {
            suppressMap.put(key, new Suppressed(prev.lastSentTime(), prev.suppressedCount() + 1));
            return;
        }

        int suppressed = null == prev ? 0 : prev.suppressedCount();
        String suffix = suppressed > 0 ? String.format("（过去 1 小时内另有 %d 次同样告警被抑制）", suppressed) : "";
        log.error("==== SmartJob 告警 ==== [{}] {} | {}{}{}", type, title, content, suffix,
                null == receiver || receiver.isBlank() ? "" : " | 接收人=" + receiver);

        suppressMap.put(key, new Suppressed(now, 0));
    }
}
