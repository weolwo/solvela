package solvela.app.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.app.auth.CurrentDevice;
import solvela.app.auth.CurrentMember;
import solvela.app.web.ClientIp;
import solvela.app.web.Trace;
import solvela.security.SecurityIncidentEvent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求体里出现了请求对象没有的字段（严格模式，见 {@code InboundJsonConfig}）→ 记一条<b>低置信</b>安全事件。
 *
 * <h3>只记录，不处罚</h3>
 * 前后端版本错开时真人也会撞到（前端先发了一个新字段，后端还没上线）。它的价值在事后：
 * 一个 IP 拿各种字段名去试一个接口，是在摸这个接口认什么；而一个字段突然被大量真人带上，是有人发错了版本。
 *
 * <h3>同一个 IP 带同一个未知字段，十分钟只记一次</h3>
 * 发错版本时，每一个打开页面的人、每一次点击都会撞上 —— 每次都记的话，事件表里全是同一句话。
 * 进程内去重，多实例各记各的，对这个用途够了。
 *
 * @Date 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class UnknownFieldReporter {

    static final String RULE_CODE = "UNKNOWN_FIELD";

    static final long QUIET_MILLIS = 10 * 60_000L;

    /** 去重表最多记多少条；满了整张清掉 —— 宁可多记几条，不让它无限长 */
    static final int MAX_TRACKED = 10_000;

    private final IncidentPublisher incidents;

    private final ConcurrentHashMap<String, Long> lastReported = new ConcurrentHashMap<>();

    public void report(HttpServletRequest request, String field) {
        String ip = ClientIp.of(request);
        String key = ip + '|' + field;
        long now = System.currentTimeMillis();
        Long last = lastReported.get(key);
        if (last != null && now - last < QUIET_MILLIS) {
            return;
        }
        if (lastReported.size() >= MAX_TRACKED) {
            lastReported.clear();
        }
        lastReported.put(key, now);
        incidents.publish(SecurityIncidentEvent.builder(SecurityIncidentEvent.UNKNOWN_FIELD, RULE_CODE)
                .who(ip, CurrentDevice.deviceIdOrNull(), CurrentMember.memberIdOrNull())
                .request(request.getMethod(), request.getRequestURI(), request.getHeader("User-Agent"), Trace.id())
                .matched(SecurityIncidentEvent.FIELD_BODY, field, null)
                .build());
    }
}
