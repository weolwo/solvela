package sa.base.module.support.codegenerator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码生成器打包链路的验证。
 *
 * 这三个方法原来是 hutool 的 FileUtil.appendUtf8String / ZipUtil.zip / FileUtil.del，
 * 移除 hutool 时手抄了回来。它们只在「运营点了下载代码」时才跑，
 * 编译期和现有单测都盖不到 —— 写坏了要等到有人下到一个打不开的 zip 才知道，所以在这里钉死。
 *
 * @Date 2026-08-08
 */
public class CodeGeneratorTemplateZipTest {

    @Test
    public void appendUtf8_不存在时创建_存在时追加(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("a.txt").toFile();

        CodeGeneratorTemplateService.appendUtf8(file, "第一段");
        assertEquals("第一段", Files.readString(file.toPath(), StandardCharsets.UTF_8));

        // 是追加不是覆盖：模板渲染会往同一个文件里多次写
        CodeGeneratorTemplateService.appendUtf8(file, "第二段");
        assertEquals("第一段第二段", Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void zip_不含最外层目录_且条目名用正斜杠(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("9f2c");
        Files.createDirectories(root.resolve("java/order/constant"));
        Files.createDirectories(root.resolve("js/order"));
        Files.writeString(root.resolve("java/order/OrderEntity.java"), "class OrderEntity {}");
        Files.writeString(root.resolve("java/order/constant/OrderStatusEnum.java"), "enum 状态 {}");
        Files.writeString(root.resolve("js/order/api.js"), "export default {}");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CodeGeneratorTemplateService.zipDirectoryContent(out, root.toFile());

        Map<String, String> entries = readZip(out.toByteArray());

        // 解压出来直接是 java/、js/，不带 9f2c 这层随机目录名
        assertEquals(3, entries.size(), () -> "实际条目：" + entries.keySet());
        assertTrue(entries.containsKey("java/order/OrderEntity.java"), () -> "" + entries.keySet());
        assertTrue(entries.containsKey("java/order/constant/OrderStatusEnum.java"), () -> "" + entries.keySet());
        assertTrue(entries.containsKey("js/order/api.js"), () -> "" + entries.keySet());
        entries.keySet().forEach(name ->
                assertFalse(name.contains("\\"), () -> "条目名出现反斜杠，Windows 下生成的是畸形包：" + name));
        entries.keySet().forEach(name ->
                assertFalse(name.startsWith("9f2c"), () -> "不应包含最外层临时目录：" + name));

        assertEquals("class OrderEntity {}", entries.get("java/order/OrderEntity.java"));
        // 中文内容按 UTF-8 存取，不能变成问号
        assertEquals("enum 状态 {}", entries.get("java/order/constant/OrderStatusEnum.java"));
    }

    @Test
    public void zip_必须写出中央目录否则是个打不开的包(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("only");
        Files.createDirectories(root);
        Files.writeString(root.resolve("x.txt"), "x");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CodeGeneratorTemplateService.zipDirectoryContent(out, root.toFile());

        byte[] bytes = out.toByteArray();
        // PK\003\004 开头 + 能被完整读出条目，才说明 ZipOutputStream 真的 close 过
        assertEquals(0x50, bytes[0] & 0xFF);
        assertEquals(0x4B, bytes[1] & 0xFF);
        assertEquals(Map.of("x.txt", "x"), readZip(bytes));
    }

    @Test
    public void deleteRecursively_非空目录也要删干净(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("trash");
        Files.createDirectories(root.resolve("a/b/c"));
        Files.writeString(root.resolve("a/b/c/deep.txt"), "deep");
        Files.writeString(root.resolve("a/top.txt"), "top");

        CodeGeneratorTemplateService.deleteRecursively(root.toFile());
        assertFalse(Files.exists(root), "临时目录应被整个删掉，否则每次下载代码都在工作目录留一坨");

        // 不存在时静默返回，不抛异常（finally 里调用，抛了会盖掉真正的异常）
        CodeGeneratorTemplateService.deleteRecursively(root.toFile());
    }

    private Map<String, String> readZip(byte[] bytes) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                result.put(entry.getName(), new String(zin.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}
