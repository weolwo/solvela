package solvela.base.module.support.file.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从富文本 HTML 里抠出图片引用。
 *
 * <p><b>这个类存在的理由是一个会静默发生的事故</b>：运营在富文本编辑器里插图，编辑器直接写入
 * {@code <img src="http://cdn/xxx.png">}。这些图<b>不在 {@code t_file_relation} 里</b>，
 * 于是孤儿清理任务会把它们当成"上传了但没人引用"的垃圾删掉 ——
 * 表现是活动上线三个月后规则说明里的图突然全变叉，而且查不出是谁删的，因为是定时任务按规则删的。
 *
 * <p><b>用正则而不是引 jsoup</b>：这里只需要从编辑器产出的规范 HTML 里取 img 的 src，
 * 不需要构建 DOM、不需要容错任意手写 HTML。为这一件事引一个解析库不划算 ——
 * 本项目刚花了几轮把 8.37MB 的传递依赖摘掉。
 * 代价是遇到畸形 HTML 可能少抓或多抓，而<b>少抓的后果（图被清理）比多抓（多一条引用记录）严重得多</b>，
 * 所以下面的正则刻意写得宽松：属性顺序、单双引号、大小写、换行都吃得下。
 *
 * @Date 2026-08-10
 */
public final class RichTextImageExtractor {

    /**
     * {@code DOTALL} 让 {@code [^>]} 能跨行匹配（编辑器会把长属性折行）；
     * {@code CASE_INSENSITIVE} 因为 {@code <IMG SRC=} 也是合法 HTML。
     */
    private static final Pattern IMG_SRC = Pattern.compile(
            "<img\\b[^>]*?\\bsrc\\s*=\\s*([\"'])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * base64 内联图片。富文本编辑器粘贴图片时的默认行为之一。
     */
    private static final Pattern BASE64_IMAGE = Pattern.compile(
            "data:image/[a-z0-9.+-]+;base64,", Pattern.CASE_INSENSITIVE);

    private RichTextImageExtractor() {
    }

    /**
     * 抠出全部 img 的 src，保持出现顺序、去重。
     */
    public static Set<String> extractImageSources(String html) {
        Set<String> sources = new LinkedHashSet<>();
        if (html == null || html.isEmpty()) {
            return sources;
        }
        Matcher matcher = IMG_SRC.matcher(html);
        while (matcher.find()) {
            String src = matcher.group(2).trim();
            if (!src.isEmpty()) {
                sources.add(src);
            }
        }
        return sources;
    }

    /**
     * 是否含 base64 内联图片。
     *
     * <p>必须在后端拦一道：编辑器那边的配置是会被人改回来的。一张图就是几百 KB 的 base64
     * 塞进 {@code mediumtext}，几十个活动就把表撑爆，将来推 CDN 的 JSON 也会大到离谱。
     */
    public static boolean containsBase64Image(String html) {
        return html != null && BASE64_IMAGE.matcher(html).find();
    }
}
