package net.lab1024.sa.base.module.support.file.controller;

import net.lab1024.sa.base.storage.ByteRange;
import org.springframework.http.HttpRange;

import java.util.List;

/**
 * HTTP {@code Range} 头 → {@link ByteRange}。
 *
 * <p>单独抽出来是为了可测：这段逻辑的边界条件（末尾 N 字节、超出末尾、起点越界、
 * 畸形头）全在这里，塞在 Controller 方法里就只能靠起服务手点。
 *
 * <p><b>只认第一个区间</b>。多区间要回 {@code multipart/byteranges}，
 * 而实际用到 Range 的场景（视频拖动、大 PDF 分段、断点续传）浏览器发的都是单区间。
 * 支持多区间的复杂度换不来任何真实收益。
 *
 * @Date 2026-08-10
 */
final class DownloadRangeResolver {

    /**
     * 解析结果。区分「没带 Range」「带了且能满足」「带了但满足不了」——
     * 三种情况的 HTTP 语义完全不同（200 / 206 / 416），合并成一个可空返回值必然出错。
     */
    record Resolved(ByteRange range, boolean partial, boolean satisfiable) {

        static Resolved full() {
            return new Resolved(ByteRange.all(), false, true);
        }

        static Resolved partial(ByteRange range) {
            return new Resolved(range, true, true);
        }

        static Resolved unsatisfiable() {
            return new Resolved(null, false, false);
        }
    }

    private DownloadRangeResolver() {
    }

    static Resolved resolve(String rangeHeader, long totalLength) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return Resolved.full();
        }
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            // RFC 9110 §14.2：无法解析的 Range 应当被忽略并返回整个资源，而不是报错
            return Resolved.full();
        }
        if (ranges.isEmpty()) {
            return Resolved.full();
        }
        if (totalLength <= 0) {
            return Resolved.unsatisfiable();
        }

        HttpRange first = ranges.getFirst();
        long start;
        long end;
        try {
            // Spring 已经处理了 "bytes=-500"（末尾 500 字节）这种后缀形式的换算
            start = first.getRangeStart(totalLength);
            end = first.getRangeEnd(totalLength);
        } catch (IllegalArgumentException e) {
            return Resolved.unsatisfiable();
        }
        if (start >= totalLength) {
            return Resolved.unsatisfiable();
        }
        if (start == 0 && end >= totalLength - 1) {
            // 覆盖整个资源的 Range 按 200 回更省事，也少一个 Content-Range 头
            return Resolved.full();
        }
        return Resolved.partial(ByteRange.of(start, end));
    }
}
