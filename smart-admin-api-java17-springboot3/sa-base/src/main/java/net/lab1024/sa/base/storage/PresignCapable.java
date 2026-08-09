package net.lab1024.sa.base.storage;

import java.net.URI;
import java.time.Duration;

/**
 * 预签名能力。<b>刻意做成独立接口，不塞进 {@link ObjectStorage}。</b>
 *
 * <p>本地存储做不了真正的预签名。旧实现让它"假装能做"——
 * {@code getFileUrl} 对私有文件也只是拼一个 {@code urlPrefix + key} 的静态 URL，
 * 而那个路径挂在 Spring 静态资源映射上，<b>谁拿到 URL 谁就能下，永不过期</b>。
 * "本地模式下私有文件根本不私有"这个事故，就是"大接口塞满、实现假装支持"的必然结果。
 *
 * <p>拆成能力接口之后，调用方必须 {@code if (storage instanceof PresignCapable p)} 显式分支 ——
 * 编译期就看得见"这条路本地模式走不通"。
 *
 * <p>本版私有文件走后端下载接口（走登录态鉴权），<b>不做免登录外发链接</b>（决策 #3），
 * 所以这个接口目前只有 S3 实现，且只在需要把 URL 直接交给 CDN / 外部时使用。
 *
 * @Date 2026-08-09
 */
public interface PresignCapable {

    /**
     * 生成一个限时有效的 GET URL。
     *
     * @param responseContentDisposition 按次覆盖响应头，可为 null。
     *                                   <b>这正是 disposition 不该被烧进对象元数据的原因</b>：
     *                                   同一个对象，下载给 {@code attachment}、预览给 {@code inline}，
     *                                   零成本。值应当已按 RFC 6266 编码好。
     */
    URI presignedGet(StorageKey key, Duration ttl, String responseContentDisposition);
}
