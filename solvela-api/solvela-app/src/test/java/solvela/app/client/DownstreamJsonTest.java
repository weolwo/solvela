package solvela.app.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import solvela.app.config.AppJsonConfig;
import solvela.marketing.api.ActivityBriefView;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 网关<b>解析下游响应</b>时的 JSON 形状。
 *
 * <h3>这个测试为什么存在（2026-09-05 的一次线上表现）</h3>
 * {@code listOpenActivities} 是第一个把 {@code LocalDateTime} 跨进程传回网关的契约，
 * 一接通就炸：
 * <pre>
 *   Failed to deserialize java.time.LocalDateTime from String "2026-08-30 00:00:00"
 *   (solvela.marketing.api.ActivityBriefView["startTime"])
 * </pre>
 *
 * <p>根因不在 {@link AppJsonConfig} —— 它一直配着 {@code yyyy-MM-dd HH:mm:ss}。
 * 问题是那份配置走的是 {@code JsonMapperBuilderCustomizer}，只作用于 <b>Spring MVC</b>
 * 的 mapper；而 {@code RestClient.builder()} 会自己造一套<b>默认</b>消息转换器，
 * 里面的 mapper 只认 ISO-8601 的 {@code 2026-08-30T00:00:00}。
 *
 * <p>两个 mapper 各活各的，而它们之间没有任何编译期或启动期的关联 ——
 * 这个测试就是那个关联。
 *
 * <h3>🔴 它测的是「格式契约」，不是「配置写没写」</h3>
 * 下面第二条断言（默认 mapper 必须解析<b>失败</b>）是关键：
 * 没有它的话，哪天 Jackson 默认开始接受空格分隔的格式，这个测试会在
 * {@code DownstreamClientConfig} 已经改回默认转换器的情况下照样绿。
 */
class DownstreamJsonTest {

    /** 与真实响应同形：app-biz 序列化出来的就是这个格式 */
    private static final String PAYLOAD = """
            {"activityCode":"AP0TASKRUN","activityName":"验收活动","activityType":"TASK",
             "status":2,"startTime":"2026-08-30 00:00:00","dataEndTime":null,
             "endTime":"2099-12-31 23:59:59","subTitle":null,"themeColor":null,"mainImageId":null}
            """;

    @Test
    @DisplayName("🔴 用应用配好的 JsonMapper，能解析 yyyy-MM-dd HH:mm:ss")
    void appMapperParsesSpaceSeparatedDateTime() throws Exception {
        ActivityBriefView view = read(appMapper());

        assertNotNull(view);
        assertEquals(LocalDateTime.of(2026, 8, 30, 0, 0, 0), view.startTime(),
                "网关解析下游响应用的必须是 AppJsonConfig 那份 mapper");
        assertEquals("AP0TASKRUN", view.activityCode());
    }

    @Test
    @DisplayName("🔴 默认 mapper 解析不了 —— 所以 RestClient 不能用它自带的转换器")
    void defaultMapperCannotParseIt() {
        /*
         * 这一条不是为了证明 Jackson 有问题，而是为了让「必须显式塞 mapper 进去」
         * 这件事有一个会红的守卫：默认 mapper 只认 2026-08-30T00:00:00。
         * 哪天它开始接受空格分隔，这条会红 —— 那时才可以考虑简化上面那段配置。
         */
        assertThrows(Exception.class, () -> read(JsonMapper.builder().build()),
                "默认 mapper 居然能解析空格分隔的时间了？那就回去看 DownstreamClientConfig 还需不需要显式塞 mapper");
    }

    /** 按 AppJsonConfig 的 customizer 造一个等价的 mapper */
    private static JsonMapper appMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new AppJsonConfig().appJsonCustomizer().customize(builder);
        return builder.build();
    }

    private static ActivityBriefView read(JsonMapper mapper) throws Exception {
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(mapper);
        return (ActivityBriefView) converter.read(ActivityBriefView.class, message(PAYLOAD));
    }

    private static HttpInputMessage message(String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
