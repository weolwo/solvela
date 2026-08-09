package net.lab1024.sa.base.storage.impl;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 只允许读出前 N 个字节的包装流，给 {@link LocalFileStorage} 的 Range 读用。
 *
 * <p>自己写而不是用 {@code commons-io} 的 {@code BoundedInputStream}：后者的构造器在 2.16 起
 * 被标了废弃、改推 builder，而这里只需要二十行确定的行为，不值得为它引一条会随版本漂移的 API。
 *
 * <p><b>不覆写 {@code available()} 会怎样</b>：调用方（尤其是 {@code transferTo}）可能按
 * 底层流剩余量去要更多字节。这里一并收窄，保证"看起来剩多少就是真的剩多少"。
 *
 * @Date 2026-08-09
 */
final class LimitedInputStream extends FilterInputStream {

    private long remaining;

    LimitedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = Math.max(0, limit);
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int b = super.read();
        if (b >= 0) {
            remaining--;
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int n = super.read(buf, off, (int) Math.min(len, remaining));
        if (n > 0) {
            remaining -= n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(Math.min(n, remaining));
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(super.available(), remaining);
    }

    @Override
    public boolean markSupported() {
        // mark/reset 会让 remaining 与底层流错位，直接声明不支持比支持一半更安全
        return false;
    }
}
