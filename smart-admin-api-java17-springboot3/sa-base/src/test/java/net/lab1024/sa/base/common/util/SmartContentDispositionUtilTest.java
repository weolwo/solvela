package net.lab1024.sa.base.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SmartContentDispositionUtil}：RFC 6266 / 8187 一致性，以及 header 截断防护。
 *
 * @Date 2026-08-10
 */
class SmartContentDispositionUtilTest {

    @Test
    @DisplayName("双写法：filename= 在前给老 UA，filename*= 在后给现代浏览器")
    void bothForms() {
        String header = SmartContentDispositionUtil.attachment("年会.png");
        assertThat(header).isEqualTo(
                "attachment; filename=\"download.png\"; filename*=UTF-8''%E5%B9%B4%E4%BC%9A.png");
        assertThat(header.indexOf("filename=")).isLessThan(header.indexOf("filename*="));
    }

    @Test
    @DisplayName("空格编成 %20 而不是 +（URLEncoder 的老毛病）")
    void spaceIsPercentTwenty() {
        String header = SmartContentDispositionUtil.attachment("my report.pdf");
        assertThat(header).contains("filename*=UTF-8''my%20report.pdf");
        assertThat(header).doesNotContain("+");
    }

    @Test
    @DisplayName("分号与逗号必须被编码 —— 不编码就能把 header 撕成两截")
    void semicolonAndCommaEncoded() {
        String header = SmartContentDispositionUtil.attachment("a;b,c.png");
        assertThat(header).contains("%3B").contains("%2C");
        // 除了两处分隔用的分号，不该再有裸分号
        assertThat(header.chars().filter(c -> c == ';').count()).isEqualTo(2);
    }

    @Test
    @DisplayName("引号和反斜杠不能出现在降级文件名里，否则 quoted-string 会破")
    void quotesNeutralizedInFallback() {
        String header = SmartContentDispositionUtil.attachment("a\"b\\c.png");
        String fallback = header.substring(header.indexOf("filename=\"") + 10,
                header.indexOf("\"; filename*="));
        assertThat(fallback).doesNotContain("\"").doesNotContain("\\");
    }

    @Test
    @DisplayName("RFC 8187 的 attr-char 保持原样，不做多余编码")
    void attrCharsStayLiteral() {
        assertThat(SmartContentDispositionUtil.attachment("a-b_c.d~e.png"))
                .contains("filename*=UTF-8''a-b_c.d~e.png");
    }

    @Test
    @DisplayName("纯中文名：降级名退成 download 但扩展名保住 —— 老 UA 可以名字难看，不能打不开")
    void pureChineseKeepsExtension() {
        assertThat(SmartContentDispositionUtil.attachment("活动主视觉.jpeg"))
                .contains("filename=\"download.jpeg\"");
    }

    @Test
    @DisplayName("没有扩展名也不会拼出一个尾点")
    void noExtension() {
        assertThat(SmartContentDispositionUtil.attachment("报告"))
                .contains("filename=\"download\"")
                .doesNotContain("download.\"");
    }

    @Test
    @DisplayName("换行符不能带进 header —— 那是响应拆分注入")
    void controlCharsRemoved() {
        String header = SmartContentDispositionUtil.attachment("a\r\nSet-Cookie: x=1.png");
        assertThat(header).doesNotContain("\r").doesNotContain("\n");
        assertThat(header).contains("%0D%0A");
    }

    @Test
    @DisplayName("inline 与 attachment 只差 type，同一对象可以两种都发")
    void inlineVariant() {
        assertThat(SmartContentDispositionUtil.inline("年会.png")).startsWith("inline; ");
        assertThat(SmartContentDispositionUtil.attachment("年会.png")).startsWith("attachment; ");
    }

    @Test
    @DisplayName("空文件名只返回 type，不拼出半截参数")
    void blankFileName() {
        assertThat(SmartContentDispositionUtil.attachment(null)).isEqualTo("attachment");
        assertThat(SmartContentDispositionUtil.attachment("  ")).isEqualTo("attachment");
    }
}
