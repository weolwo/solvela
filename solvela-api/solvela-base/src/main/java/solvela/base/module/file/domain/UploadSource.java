package solvela.base.module.file.domain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 一次上传的来源，是 {@code MultipartFile} 在<b>领域这一侧</b>的样子。
 *
 * <p>存在的理由只有一个：{@code MultipartFile} 是 spring-web 的类型。
 * {@code FileAssetService} 从确认引用、解析 URL 到批量取地址，绝大部分能力跟 HTTP 毫无关系，
 * 却因为两个 {@code upload} 重载而整个类被钉死在 web 层 —— 定时任务想导一批文件、
 * 消息消费想落一个附件、C 端将来走别的上传通道，都得先绕过一个 servlet 类型。
 *
 * <p>接口只留 {@code FileAssetService} 真正用到的四件事。多一个方法，
 * 就多一分把 {@code MultipartFile} 的形状渗回来的机会。
 *
 * <p>适配在端上做：管理端有 {@code MultipartUploadSource}，测试用 {@link #of}。
 */
public interface UploadSource {

    /** 上传时的原始文件名，可能为 null（客户端没给） */
    String originalName();

    /** 字节数。<b>先看它再读字节</b> —— 类型嗅探本身就是攻击面 */
    long size();

    /** 客户端声明的 MIME，仅供参考：真正的类型以嗅探结果为准 */
    String contentType();

    /**
     * 打开内容流。
     *
     * <p>可能被调用<b>多次</b>（一次嗅探类型、一次读图片头、一次落存储），
     * 所以实现必须每次都给一条能从头读的流。
     */
    InputStream open() throws IOException;

    /**
     * 内存字节做成的上传源，给测试和「字节已经在手上」的调用方用。
     */
    static UploadSource of(String originalName, String contentType, byte[] bytes) {
        byte[] copy = bytes == null ? new byte[0] : bytes.clone();
        return new UploadSource() {
            @Override
            public String originalName() {
                return originalName;
            }

            @Override
            public long size() {
                return copy.length;
            }

            @Override
            public String contentType() {
                return contentType;
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(copy);
            }
        };
    }
}
