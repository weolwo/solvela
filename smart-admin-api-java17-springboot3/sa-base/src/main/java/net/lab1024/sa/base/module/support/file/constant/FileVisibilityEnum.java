package net.lab1024.sa.base.module.support.file.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

/**
 * 文件可见性。<b>从文件路径里解放出来的产物。</b>
 *
 * <p>旧实现把权限编码在 key 的前缀里（{@code private/} / {@code public/}），带来两个问题：
 * <ul>
 *   <li>判定口径分裂 —— 上传时用 {@code contains(PUBLIC)}、取 URL 时用 {@code startsWith(PRIVATE)}，
 *       一个叫 {@code public/private-doc/x.png} 的 key 会被判成公开上传、私有取 URL</li>
 *   <li>改权限要搬对象（跨桶复制 + 删原件 + 更新 key），而且没有事务</li>
 * </ul>
 * 做成元数据之后，改权限就是一次 UPDATE。
 *
 * @Date 2026-08-10
 */
@AllArgsConstructor
@Getter
public enum FileVisibilityEnum implements BaseEnum {

    /**
     * 公开：CDN / 静态 URL，永久有效，可设 {@code Cache-Control: immutable}
     * （因为 storageKey 不可变、永不覆盖）。
     */
    PUBLIC(1, "公开"),

    /**
     * 私有：走后端下载接口 {@code /file/download/{fileId}}，完整登录态鉴权。
     *
     * <p><b>本版不做免登录外发链接</b>（决策 #3），所以本地存储不需要实现签名 URL，
     * local 与 cloud 行为完全一致 —— 旧实现"本地模式下私有文件根本不私有"的问题从根上消失。
     */
    PRIVATE(2, "私有"),

    ;

    private final Integer value;

    private final String desc;
}
