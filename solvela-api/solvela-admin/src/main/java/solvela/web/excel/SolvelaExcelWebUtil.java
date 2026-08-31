package solvela.web.excel;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import solvela.base.sonicexcel.SonicExcel;
import solvela.base.sonicexcel.SonicTempFiles;
import solvela.base.sonicexcel.SolvelaExcelUtil;
import solvela.base.sonicexcel.error.SonicReadResult;
import solvela.base.sonicexcel.read.SonicSheetReader;
import solvela.web.SolvelaResponseUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Function;

/**
 * Excel 的<b>HTTP 那一面</b>：往 response 里写文件、从上传件里读表。
 *
 * <p>与 {@link SolvelaExcelUtil} 的分工只有一条：凡是签名里出现 servlet / spring-web
 * 类型的，都在这里；纯粹拿字节和流做事的，在那边。
 *
 * <p>这么切是为了让 solvela-base 不依赖 spring-web ——
 * 共享层的 service 想导一张表时，不该被迫先有一个 HttpServletResponse。
 */
public final class SolvelaExcelWebUtil {

    private SolvelaExcelWebUtil() {
    }

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
        byte[] content = SolvelaExcelUtil.toBytes(sheetName, head, data);

        // 走到这里说明文件已经完整生成，可以安全地 commit 响应头了
        SolvelaResponseUtil.setDownloadFileHeader(response, fileName, null);
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

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
        try {
            return SolvelaExcelUtil.importExcel(file.getInputStream(), head, config);
        } catch (IOException e) {
            throw new UncheckedIOException("接收上传文件失败", e);
        }
    }
}
