package sa.base.module.support.file.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RichTextImageExtractor}。
 *
 * <p>这些用例守的是一个会静默发生的事故：漏抓一张图 = 那张图不在引用表里 =
 * 孤儿清理任务把它删掉 = 三个月后规则说明变叉图且查不出原因。
 * 所以宁可写宽松些多抓，也不能漏抓。
 *
 * @Date 2026-08-10
 */
class RichTextImageExtractorTest {

    @Test
    @DisplayName("基本抽取，保持顺序并去重")
    void extractsInOrderAndDeduplicates() {
        String html = "<p>规则</p><img src=\"/a.png\"><img src='/b.png'/><img src=\"/a.png\">";
        assertThat(RichTextImageExtractor.extractImageSources(html))
                .containsExactly("/a.png", "/b.png");
    }

    @Test
    @DisplayName("src 不在第一个属性、大小写混杂、跨行 —— 都要抓到")
    void toleratesRealEditorOutput() {
        String html = """
                <IMG
                   class="rich-img" data-w="750"
                   SRC = '/file/download/12'
                   alt="主视觉">
                """;
        assertThat(RichTextImageExtractor.extractImageSources(html))
                .containsExactly("/file/download/12");
    }

    @Test
    @DisplayName("不误抓非 img 标签的 src")
    void ignoresOtherTags() {
        String html = "<video src=\"/v.mp4\"></video><iframe src=\"/x\"></iframe><img src=\"/ok.png\">";
        assertThat(RichTextImageExtractor.extractImageSources(html))
                .containsExactly("/ok.png");
    }

    @Test
    @DisplayName("不误抓 imgxxx 这类前缀相同的标签名")
    void doesNotMatchPrefixedTagNames() {
        assertThat(RichTextImageExtractor.extractImageSources("<imgfoo src=\"/a.png\">")).isEmpty();
    }

    @Test
    @DisplayName("空输入不炸")
    void handlesEmpty() {
        assertThat(RichTextImageExtractor.extractImageSources(null)).isEmpty();
        assertThat(RichTextImageExtractor.extractImageSources("")).isEmpty();
        assertThat(RichTextImageExtractor.extractImageSources("<p>没有图</p>")).isEmpty();
    }

    @Test
    @DisplayName("识别 base64 内联图片 —— 编辑器粘贴图片的默认行为之一")
    void detectsBase64() {
        assertThat(RichTextImageExtractor.containsBase64Image(
                "<img src=\"data:image/png;base64,iVBORw0KGgo=\">")).isTrue();
        assertThat(RichTextImageExtractor.containsBase64Image(
                "<img src=\"DATA:IMAGE/JPEG;BASE64,xxxx\">")).isTrue();
        assertThat(RichTextImageExtractor.containsBase64Image("<img src=\"/a.png\">")).isFalse();
        assertThat(RichTextImageExtractor.containsBase64Image(null)).isFalse();
    }

    @Test
    @DisplayName("base64 的 src 照样被抽出来 —— 抽取和校验是两件事，别在抽取里偷偷过滤")
    void base64StillExtracted() {
        assertThat(RichTextImageExtractor.extractImageSources("<img src=\"data:image/png;base64,AAA\">"))
                .hasSize(1);
    }
}
