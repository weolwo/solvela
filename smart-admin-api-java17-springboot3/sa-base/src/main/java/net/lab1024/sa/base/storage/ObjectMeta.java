package net.lab1024.sa.base.storage;

import java.util.Map;

/**
 * 写入对象时附带的元数据。
 *
 * <p><b>刻意不含 contentDisposition</b>（设计文档 §7.5）：旧实现在上传时就把
 * {@code attachment;filename=...} 烧进了对象元数据，一旦写进去就固定了 ——
 * 以后想让图片 / PDF 在线预览（{@code inline}）得重新上传或 copyObject。
 * 正确做法是在预签名或响应时按次覆盖，见 {@link PresignCapable#presignedGet}。
 *
 * @param contentType  真实 MIME（Tika 嗅探结果），空则兜底 application/octet-stream
 * @param userMetadata 自定义元数据，不可变
 * @Date 2026-08-09
 */
public record ObjectMeta(String contentType, Map<String, String> userMetadata) {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public ObjectMeta {
        // 旧的 getContentType 兜底返回空串，导致 S3 上存了一堆 Content-Type 为空的对象，
        // 浏览器只能靠猜。兜底必须是一个合法 MIME
        contentType = contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType;
        userMetadata = userMetadata == null ? Map.of() : Map.copyOf(userMetadata);
    }

    public static ObjectMeta of(String contentType) {
        return new ObjectMeta(contentType, Map.of());
    }
}
