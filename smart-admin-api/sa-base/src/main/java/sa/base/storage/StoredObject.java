package sa.base.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 一次读取的结果：流 + 尺寸信息。
 *
 * <p><b>返回流而不是 {@code byte[]}</b> —— 旧的两个实现都是
 * {@code FileCopyUtils.copyToByteArray} / {@code ResponseTransformer.toBytes()}，
 * 名字叫"流式下载"，实际 100MB 文件就是 100MB 堆。
 *
 * <p><b>为什么要同时有 length 和 totalLength</b>：HTTP 206 的
 * {@code Content-Range: bytes {start}-{end}/{total}} 两个数都要。
 * 只带一个的话，档③ 做 Range 时还得再查一次对象大小。
 *
 * @param length      本次返回的字节数（Range 请求时是区间长度）
 * @param totalLength 对象总字节数；整读时与 length 相等
 * @Date 2026-08-09
 */
public record StoredObject(InputStream stream,
                           long length,
                           long totalLength,
                           String contentType) implements AutoCloseable {

    public static StoredObject whole(InputStream stream, long length, String contentType) {
        return new StoredObject(stream, length, length, contentType);
    }

    public boolean isPartial() {
        return length != totalLength;
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (IOException e) {
            throw new UncheckedIOException("关闭对象流失败", e);
        }
    }
}
