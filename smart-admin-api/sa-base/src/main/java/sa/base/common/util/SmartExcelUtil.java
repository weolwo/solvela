package sa.base.common.util;

import jakarta.servlet.http.HttpServletResponse;
import sa.base.sonicexcel.SonicExcel;
import sa.base.sonicexcel.SonicTempFiles;
import sa.base.sonicexcel.error.SonicReadResult;
import sa.base.sonicexcel.read.SonicSheetReader;
import sa.base.sonicexcel.write.SonicSheetBuilder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Function;

/**
 * Excel 工具类 —— SonicExcel 的 HTTP 协议防腐层。
 *
 * <p>框架本身只认 OutputStream / Path，HTTP 相关的脏活（下载头、文件名编码、异常契约、
 * 上传文件落盘与清理）全在这里。
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

    // ------------------------------------------------------------------ 导出

    /**
     * 通用单 sheet 导出。
     *
     * <p><b>为什么先攒 byte[] 再写响应</b>：一旦开始往 {@code response.getOutputStream()} 写，
     * 响应头就 committed 了，之后再也返回不了 JSON 错误（commit 后 sendError 直接抛
     * IllegalStateException），前端拿到的是一个损坏的 xlsx。先在内存里把文件生成完，
     * 成功了才落下载头 —— 生成期的任何异常都能正常走全局异常处理器返回 JSON。
     *
     * <p>本方法面向<b>小数据量</b>（入参就是一个已经全在内存里的 Collection）。
     * 真正的大数据量流式导出走 {@link SonicExcel#write} 自己拿 OutputStream，
     * 那条路径必须接受"下载即失败"，见架构文档 §10.3。
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

    // ------------------------------------------------------------------ 导入

    /**
     * 通用导入：上传文件 → 落临时文件 → 解析 → <b>无论如何都删掉临时文件</b>。
     *
     * <p>返回值同时带着数据和行级错误清单，业务侧可以直接告诉用户"第几行第几列错了"。
     */
    public static <T> SonicReadResult<T> importExcel(MultipartFile file, Class<T> head) {
        return importExcel(file, head, Function.identity());
    }

    /**
     * 带定制的导入，用于指定 sheet、表头行、错误策略等。
     */
    public static <T> SonicReadResult<T> importExcel(MultipartFile file, Class<T> head,
                                                     Function<SonicSheetReader<T>, SonicSheetReader<T>> config) {
        Path tmp = null;
        try {
            tmp = SonicTempFiles.create();
            file.transferTo(tmp);
            return config.apply(SonicExcel.read(tmp, head)).doReadAll();
        } catch (IOException e) {
            throw new UncheckedIOException("接收上传文件失败", e);
        } finally {
            // ⚠️ 绝对不要改成 deleteOnExit()：那个钩子只在 JVM 正常退出时跑，
            // K8s 里 Pod 被 SIGKILL / OOMKilled 时根本不执行，而它注册的文件名会被
            // DeleteOnExitHook 的 static Set 永久持有 —— 想兜的底兜不住，还持续漏内存。
            // 崩溃残留由 SonicTempFiles.sweepStale 在启动时清理。
            deleteQuietly(tmp);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 删不掉交给启动扫描兜底，不能因此掩盖业务异常
        }
    }
}
