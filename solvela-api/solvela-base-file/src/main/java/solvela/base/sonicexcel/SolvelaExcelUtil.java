package solvela.base.sonicexcel;


import solvela.base.sonicexcel.SonicExcel;
import solvela.base.sonicexcel.SonicTempFiles;
import solvela.base.sonicexcel.error.SonicReadResult;
import solvela.base.sonicexcel.read.SonicSheetReader;
import solvela.base.sonicexcel.write.SonicSheetBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
public final class SolvelaExcelUtil {

    private SolvelaExcelUtil() {
    }

    // ------------------------------------------------------------------ 导出

    

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
     * 通用导入的<b>无 Web 依赖</b>版本：只要一段字节流就能导。
     *
     * <p>存在的理由是分层，不是省事：{@code MultipartFile} 是 spring-web 的类型，
     * 一旦出现在共享层的 service 签名上，那个模块就等于宣称「我只能被 HTTP 调用」——
     * 定时任务、消息消费、其它端想复用同一套导入校验时都得绕路。
     * 端负责把上传解成流，共享层只认流。
     *
     * <p><b>调用方负责关流</b>：这里不 close，因为 {@code MultipartFile.getInputStream()}
     * 与 servlet 容器持有的流生命周期由容器管，这里替它关反而越界。
     */
    public static <T> SonicReadResult<T> importExcel(InputStream in, Class<T> head) {
        return importExcel(in, head, Function.identity());
    }

    /**
     * 带定制的流式导入，见 {@link #importExcel(InputStream, Class)}。
     */
    public static <T> SonicReadResult<T> importExcel(InputStream in, Class<T> head,
                                                     Function<SonicSheetReader<T>, SonicSheetReader<T>> config) {
        Path tmp = null;
        try {
            tmp = SonicTempFiles.create();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
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
