package solvela.base.sonicexcel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * 导入临时文件的生命周期。
 *
 * <p><b>为什么必须落盘</b>：解析 OOXML 要 zip 随机访问，把 InputStream 交给 fastexcel-reader 时，
 * 它内部用 {@code SeekableInMemoryByteChannel} 把整个 xlsx 读成堆里的 byte[] ——
 * 100MB 的上传文件在读第一行之前就先吃掉 100MB 连续堆内存。
 *
 * <p><b>为什么不用 {@code File#deleteOnExit()}</b>：它把文件名注册进 {@code DeleteOnExitHook}
 * 的一个 static Set <b>永久持有</b>，只在 JVM 正常退出时才执行。K8s 里 Pod 被 SIGKILL / OOMKilled
 * 时钩子根本不跑 —— 它想兜的底恰恰兜不住，运行期还持续漏内存。
 * 正确的兜底是<b>「finally 删 + 启动扫」</b>：finally 管正常路径，启动扫描覆盖 crash 残留。
 *
 * <p>文件统一放在 {@code ${java.io.tmpdir}/sonic-excel/} 子目录下，
 * 这样启动扫描只碰自己的东西，不会误删别人的临时文件。
 *
 * @Date 2026-08-08
 */
public final class SonicTempFiles {

    private static final Logger log = LoggerFactory.getLogger(SonicTempFiles.class);

    private static final String DIR_NAME = "sonic-excel";
    private static final String PREFIX = "sonic-";
    private static final String SUFFIX = ".xlsx";

    /**
     * 启动扫描的默认阈值。比任何一次合理的导入都长得多，不会误删正在用的文件。
     */
    public static final Duration DEFAULT_STALE_AGE = Duration.ofHours(2);

    private SonicTempFiles() {
    }

    public static Path directory() throws IOException {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), DIR_NAME);
        Files.createDirectories(dir);
        return dir;
    }

    public static Path create() throws IOException {
        return Files.createTempFile(directory(), PREFIX, SUFFIX);
    }

    /**
     * 删掉超过 age 没被动过的残留。只有进程崩溃 / 被 kill 时才会有残留，正常路径 finally 已经删了。
     *
     * @return 删掉的文件数
     */
    public static int sweepStale(Duration age) {
        Instant deadline = Instant.now().minus(age);
        int deleted = 0;
        try (Stream<Path> files = Files.list(directory())) {
            for (Path p : files.toList()) {
                try {
                    if (!Files.isRegularFile(p) || !p.getFileName().toString().startsWith(PREFIX)) {
                        continue;
                    }
                    FileTime modified = Files.getLastModifiedTime(p);
                    if (modified.toInstant().isBefore(deadline) && Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    // 单个文件删不掉（被占用等）不该影响整轮清理，更不该影响启动
                    log.debug("[SonicExcel] 清理临时文件失败：{}", p, e);
                }
            }
        } catch (IOException e) {
            log.warn("[SonicExcel] 扫描临时目录失败，跳过本轮清理", e);
        }
        if (deleted > 0) {
            log.info("[SonicExcel] 启动清理：删除 {} 个超过 {} 的残留临时文件", deleted, age);
        }
        return deleted;
    }
}
