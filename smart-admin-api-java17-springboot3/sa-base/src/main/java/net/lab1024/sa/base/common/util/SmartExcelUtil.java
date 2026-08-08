package net.lab1024.sa.base.common.util;

import jakarta.servlet.http.HttpServletResponse;
import net.lab1024.sa.base.sonicexcel.SonicExcel;
import net.lab1024.sa.base.sonicexcel.write.SonicSheetBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

/**
 * Excel 工具类 —— SonicExcel 的 HTTP 协议防腐层。
 *
 * <p>框架本身只认 OutputStream，HTTP 相关的脏活（下载头、文件名编码、异常契约）全在这里。
 *
 * <p><b>为什么先攒 byte[] 再写响应</b>：一旦开始往 {@code response.getOutputStream()} 写，
 * 响应头就 committed 了，之后再也返回不了 JSON 错误（commit 后 sendError 直接抛 IllegalStateException），
 * 前端拿到的是一个损坏的 xlsx。先在内存里把文件生成完，成功了才落下载头 ——
 * 生成过程中的任何异常都能正常走全局异常处理器返回 JSON。
 *
 * <p>本方法面向<b>小数据量</b>（入参就是一个已经全在内存里的 Collection）。
 * 真正的大数据量流式导出走 {@link SonicExcel#write} 自己拿 OutputStream，
 * 那条路径必须接受"下载即失败"，见架构文档 §10.3。
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2024/4/22 22:49:07
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright 1024创新实验室 （ https://1024lab.net ），2012-2024
 */
public final class SmartExcelUtil {

    private SmartExcelUtil() {
    }

    /**
     * 通用单 sheet 导出。
     */
    public static <T> void exportExcel(HttpServletResponse response, String fileName, String sheetName,
                                       Class<T> head, Collection<? extends T> data) throws IOException {
        byte[] content = toBytes(sheetName, head, data);

        // 走到这里说明文件已经完整生成，可以安全地 commit 响应头了
        SmartResponseUtil.setDownloadFileHeader(response, fileName, null);
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

    /**
     * 生成 xlsx 字节。单测与"先生成后下发"都走这里。
     */
    public static <T> byte[] toBytes(String sheetName, Class<T> head, Collection<? extends T> data) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (SonicSheetBuilder<T> builder = SonicExcel.write(buffer, head).sheet(sheetName)) {
            builder.append(data);
        }
        return buffer.toByteArray();
    }
}
