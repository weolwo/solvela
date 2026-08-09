package net.lab1024.sa.base.module.support.file.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DownloadRangeResolver} 的边界条件。
 *
 * <p>Range 的坑全在边界上：末尾 N 字节、超出末尾、起点越界、畸形头。
 * 这些靠起服务手点是点不全的，所以逻辑必须能单独测。
 *
 * @Date 2026-08-10
 */
class DownloadRangeResolverTest {

    private static final long TOTAL = 100;

    @Test
    @DisplayName("没带 Range → 整个资源，200")
    void noRangeHeader() {
        for (String header : new String[]{null, "", "   "}) {
            DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve(header, TOTAL);
            assertThat(r.satisfiable()).isTrue();
            assertThat(r.partial()).isFalse();
            assertThat(r.range().isAll()).isTrue();
        }
    }

    @Test
    @DisplayName("bytes=10-19 → 206，闭区间")
    void closedRange() {
        DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve("bytes=10-19", TOTAL);
        assertThat(r.partial()).isTrue();
        assertThat(r.range().start()).isEqualTo(10);
        assertThat(r.range().endInclusive()).isEqualTo(19);
        assertThat(r.range().lengthWithin(TOTAL)).isEqualTo(10);
    }

    @Test
    @DisplayName("bytes=50- → 从 50 到末尾")
    void openEndedRange() {
        DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve("bytes=50-", TOTAL);
        assertThat(r.partial()).isTrue();
        assertThat(r.range().start()).isEqualTo(50);
        assertThat(r.range().lengthWithin(TOTAL)).isEqualTo(50);
    }

    @Test
    @DisplayName("bytes=-20 是「末尾 20 字节」，不是「前 20 字节」—— 最容易写反的一条")
    void suffixRange() {
        DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve("bytes=-20", TOTAL);
        assertThat(r.partial()).isTrue();
        assertThat(r.range().start()).isEqualTo(80);
        assertThat(r.range().endInclusive()).isEqualTo(99);
    }

    @Test
    @DisplayName("结束位置超出总长 → 按实际长度截断，仍是 206")
    void endBeyondTotal() {
        DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve("bytes=90-999", TOTAL);
        assertThat(r.partial()).isTrue();
        assertThat(r.range().start()).isEqualTo(90);
        assertThat(r.range().lengthWithin(TOTAL)).isEqualTo(10);
    }

    @Test
    @DisplayName("起点越界 → 416，客户端据此知道该重试什么区间")
    void startBeyondTotal() {
        assertThat(DownloadRangeResolver.resolve("bytes=100-200", TOTAL).satisfiable()).isFalse();
        assertThat(DownloadRangeResolver.resolve("bytes=500-", TOTAL).satisfiable()).isFalse();
    }

    @Test
    @DisplayName("覆盖整个资源的 Range 退化成 200，少一个 Content-Range 头")
    void fullCoverageDegradesTo200() {
        DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve("bytes=0-99", TOTAL);
        assertThat(r.satisfiable()).isTrue();
        assertThat(r.partial()).isFalse();

        assertThat(DownloadRangeResolver.resolve("bytes=0-", TOTAL).partial()).isFalse();
    }

    @Test
    @DisplayName("畸形 Range 按 RFC 9110 忽略并回整个资源，而不是报错")
    void malformedRangeIgnored() {
        for (String header : new String[]{"bytes=abc", "items=0-10", "bytes=", "garbage"}) {
            DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve(header, TOTAL);
            assertThat(r.satisfiable()).as(header).isTrue();
            assertThat(r.partial()).as(header).isFalse();
        }
    }

    @Test
    @DisplayName("空文件带 Range → 416（任何区间都满足不了）")
    void emptyFileWithRange() {
        assertThat(DownloadRangeResolver.resolve("bytes=0-10", 0).satisfiable()).isFalse();
    }

    @Test
    @DisplayName("多区间只认第一个 —— 支持 multipart/byteranges 的复杂度换不来真实收益")
    void onlyFirstRangeHonored() {
        DownloadRangeResolver.Resolved r = DownloadRangeResolver.resolve("bytes=10-19,30-39", TOTAL);
        assertThat(r.range().start()).isEqualTo(10);
        assertThat(r.range().endInclusive()).isEqualTo(19);
    }
}
