package sa.base.storage;

/**
 * 字节区间，语义与 HTTP {@code Range: bytes=start-endInclusive} 完全一致（两端都闭）。
 *
 * <p><b>为什么档① 就把它放进接口签名</b>：Range 支持是档③ 才做的功能，但它决定了
 * {@code open} 的形状。等到档③ 再加参数，三个实现和所有调用点都要改一遍 ——
 * 接口形状的返工成本远高于提前想清楚。
 *
 * @param start        起始偏移，从 0 起，闭区间
 * @param endInclusive 结束偏移，闭区间；<b>负数表示"到文件末尾"</b>
 * @Date 2026-08-09
 */
public record ByteRange(long start, long endInclusive) {

    private static final ByteRange ALL = new ByteRange(0, -1);

    public ByteRange {
        if (start < 0) {
            throw new IllegalArgumentException("Range 起始偏移不能为负：" + start);
        }
        if (endInclusive >= 0 && endInclusive < start) {
            throw new IllegalArgumentException("Range 结束偏移小于起始：" + start + "-" + endInclusive);
        }
    }

    /**
     * 整个对象。
     */
    public static ByteRange all() {
        return ALL;
    }

    /**
     * 从 start 到末尾，对应 {@code Range: bytes=start-}。
     */
    public static ByteRange from(long start) {
        return new ByteRange(start, -1);
    }

    public static ByteRange of(long start, long endInclusive) {
        return new ByteRange(start, endInclusive);
    }

    public boolean isAll() {
        return start == 0 && endInclusive < 0;
    }

    /**
     * 在已知对象总长的前提下，算出本区间实际能取到多少字节。
     *
     * @return 0 表示区间完全落在对象之外（调用方应回 416）
     */
    public long lengthWithin(long totalLength) {
        if (start >= totalLength) {
            return 0;
        }
        long last = endInclusive < 0 ? totalLength - 1 : Math.min(endInclusive, totalLength - 1);
        return last - start + 1;
    }

    /**
     * 序列化成 HTTP Range 头的值，给 S3 用。
     */
    public String toHeaderValue() {
        return endInclusive < 0 ? "bytes=" + start + "-" : "bytes=" + start + "-" + endInclusive;
    }
}
