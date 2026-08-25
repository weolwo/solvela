package solvela.base.sonicexcel;

import solvela.base.common.util.SolvelaExcelUtil;
import solvela.base.sonicexcel.annotation.SonicTitle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全与资源生命周期。
 *
 * <p>摘掉 POI 之后这几条保护没有别人替我们做了，只能自己钉住。
 *
 * @Date 2026-08-08
 */
public class SonicExcelSecurityTest {

    @TempDir
    Path dir;

    @Test
    public void 带DTD的xlsx会被拒绝而不是去解析外部实体() throws IOException {
        // XXE。fastexcel-reader 自己的 DefaultXMLInputFactory 已经把 SUPPORT_DTD 和
        // IS_SUPPORTING_EXTERNAL_ENTITIES 关掉了 —— 这条测试是它的回归哨兵：
        // 哪天升级把这个行为改没了，这里会立刻红
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "TOP-SECRET-CONTENT");

        Path original = dir.resolve("plain.xlsx");
        Files.write(original, SolvelaExcelUtil.toBytes("数据", Row.class, List.of(new Row("hello"))));

        String doctype = "<!DOCTYPE worksheet [<!ENTITY xxe SYSTEM \""
                + secret.toUri() + "\">]>";
        Path poisoned = rewriteEntry(original, "xl/worksheets/sheet1.xml",
                xml -> xml.replaceFirst("(<\\?xml[^>]*\\?>)", "$1" + doctype).replace("hello", "&xxe;"));

        Exception e = assertThrows(Exception.class,
                () -> SonicExcel.read(poisoned, Row.class).doReadAll());
        assertFalse(String.valueOf(e.getMessage()).contains("TOP-SECRET"),
                "异常信息里都不该出现被引用文件的内容");
    }

    @Test
    public void 压缩比异常的文件被拒绝() throws IOException {
        // zip 炸弹的典型特征。20MB 的 0 压完只有几十 KB，压缩比上千
        Path bomb = dir.resolve("bomb.xlsx");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(bomb))) {
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(new byte[20 * 1024 * 1024]);
            zos.closeEntry();
        }

        SonicExcelException e = assertThrows(SonicExcelException.class,
                () -> SonicExcel.read(bomb, Row.class).doReadAll());
        assertTrue(e.getMessage().contains("压缩比异常"), e.getMessage());
    }

    @Test
    public void 导入用的临时文件解析失败也会被删掉() throws IOException {
        // 临时文件泄漏在 K8s 里的后果是 Pod 因 ephemeral-storage 超限被驱逐
        int before = countTempFiles();

        MockMultipartFile broken = new MockMultipartFile("file", "x.xlsx",
                "application/vnd.ms-excel", "根本不是 Excel".getBytes(StandardCharsets.UTF_8));
        assertThrows(SonicExcelException.class, () -> SolvelaExcelUtil.importExcel(broken, Row.class));

        assertTrue(countTempFiles() <= before, "解析失败也必须把临时文件删干净");
    }

    @Test
    public void 启动扫描清理崩溃残留() throws IOException {
        // finally 只管正常路径；Pod 被 SIGKILL / OOMKilled 时残留只能靠这一轮扫描。
        // 这也是为什么绝对不用 File#deleteOnExit()：那个钩子在被 kill 时根本不执行
        Path stale = SonicTempFiles.create();
        Files.setLastModifiedTime(stale,
                java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(7200)));

        SonicTempFiles.sweepStale(Duration.ofMinutes(30));

        assertFalse(Files.exists(stale), "超过阈值的残留应该被删掉");
    }

    @Test
    public void 启动扫描不碰还在用的文件() throws IOException {
        Path fresh = SonicTempFiles.create();
        try {
            SonicTempFiles.sweepStale(Duration.ofMinutes(30));
            assertTrue(Files.exists(fresh), "刚创建的文件可能正在被解析，绝不能删");
        } finally {
            Files.deleteIfExists(fresh);
        }
    }

    // ------------------------------------------------------------------

    private static int countTempFiles() throws IOException {
        try (var files = Files.list(SonicTempFiles.directory())) {
            return (int) files.count();
        }
    }

    /**
     * 原样重打包，只改其中一个部件的内容。
     */
    private Path rewriteEntry(Path source, String entryName,
                              java.util.function.UnaryOperator<String> transform) throws IOException {
        Path target = dir.resolve("rewritten-" + System.nanoTime() + ".xlsx");
        try (ZipFile zip = new ZipFile(source.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                zos.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] content = in.readAllBytes();
                    if (entry.getName().equals(entryName)) {
                        content = transform.apply(new String(content, StandardCharsets.UTF_8))
                                .getBytes(StandardCharsets.UTF_8);
                    }
                    zos.write(content);
                }
                zos.closeEntry();
            }
        }
        return target;
    }

    public static class Row {
        @SonicTitle("值")
        private String value;

        public Row() {
        }

        public Row(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
