package net.lab1024.sa.base.storage;

/**
 * 存储介质。<b>必须落库</b>（{@code t_file.storage_kind}）。
 *
 * <p>不存这个字段的后果是确定的：系统从 local 切到 cloud 之后，历史文件还躺在本地磁盘、
 * 新文件在 S3，而代码只能按当前全局配置去读 —— <b>切换那一刻所有历史文件立即失效</b>。
 * 旧表就没有这个字段。
 *
 * @Date 2026-08-09
 */
public enum StorageKind {

    /**
     * 本地磁盘。单机可用；多副本部署下上传到 pod A 的文件在 pod B 上读不到，仅适合开发与私有化单机。
     */
    LOCAL,

    /**
     * S3 协议对象存储（AWS S3 / MinIO / OSS、COS 的 S3 兼容模式）。
     */
    S3
}
