package net.lab1024.sa.base.storage;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * storageKey 生成器：{@code {categoryCode}/{yyyyMM}/{dd}/{id}.{ext}}
 *
 * <pre>
 * banner/202608/10/0mkq3z7x1a9f4c2.png
 * </pre>
 *
 * <p>四条硬规则（设计文档 §7.1）：
 * <ol>
 *   <li><b>key 里不含任何用户输入</b>，一个字符都不行</li>
 *   <li><b>扩展名从嗅探出的 MIME 反推</b>，不从用户文件名取 —— 用户传 {@code evil.html}
 *       而内容是 PNG 时存成 {@code .png}，这一条直接掐死存储型 XSS</li>
 *   <li><b>id 时间有序</b>，不用 UUIDv4 —— 随机分布会打散对象存储的前缀分片，
 *       而且不可排序，排查问题时没法按时间扫</li>
 *   <li><b>日期分片是给运维的</b>：让"清理 2024 年之前的孤儿"变成一次前缀列举而不是全桶扫描</li>
 * </ol>
 *
 * <p><b>为什么不用项目现成的 {@code SnowflakeIdGenerator}</b>（设计文档 §7.1 原先是这么写的，
 * 那句话是错的）：它在 {@code common-api} 模块，而 {@code common-api} 反过来依赖 {@code sa-base}
 * （见 common-api/pom.xml），sa-base 引它会成循环依赖。所以这里自带一个不需要任何协调的实现。
 *
 * <p><b>ID 编码</b>：9 位 base36 的毫秒时间戳（<b>定宽零填充</b>）+ 7 位 base36 随机。
 * 定宽是关键 —— 变长的 base36 在位数进位时会破坏字典序与时间序的一致性；
 * 9 位可用到公元 5138 年。随机部分 36^7 ≈ 7.8e10，同毫秒内碰撞概率可忽略。
 *
 * @Date 2026-08-10
 */
public final class StorageKeyGenerator {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd");

    private static final int TIME_CHARS = 9;
    private static final int RANDOM_CHARS = 7;
    private static final int RADIX = 36;

    /**
     * 与 {@code t_file.extension varchar(16)} 对齐。
     */
    private static final int MAX_EXTENSION_LENGTH = 16;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public StorageKeyGenerator() {
        this(Clock.systemDefaultZone());
    }

    /**
     * 注入 Clock 只为可测：断言"生成的 key 落在预期日期目录下"需要一个可控的时间。
     */
    public StorageKeyGenerator(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param categoryCode 分类 code。<b>它来自可配置的 {@code t_file_category} 表，不是常量</b>，
     *                     所以必须校验 —— 运营在后台建一个叫「活动素材」的分类，
     *                     直接拼进 key 就会生成一个非法 key，而那时才报错就太晚了
     * @param extension    扩展名，<b>调用方必须传嗅探 MIME 反推出来的值</b>，不能传用户文件名里的。
     *                     可为 null / 空（无扩展名的文件）
     */
    public StorageKey generate(String categoryCode, String extension) {
        String prefix = normalizeCategoryCode(categoryCode);
        LocalDate today = LocalDate.now(clock);
        StringBuilder sb = new StringBuilder(64)
                .append(prefix).append('/')
                .append(today.format(MONTH)).append('/')
                .append(today.format(DAY)).append('/')
                .append(nextId());
        String ext = normalizeExtension(extension);
        if (!ext.isEmpty()) {
            sb.append('.').append(ext);
        }
        // 交给 StorageKey 做最后一道校验：任何拼错都在这里炸，而不是等到落盘
        return new StorageKey(sb.toString());
    }

    // ------------------------------------------------------------------

    private String nextId() {
        String time = Long.toString(clock.millis(), RADIX);
        if (time.length() > TIME_CHARS) {
            // 公元 5138 年之后才可能发生。真到那天，定宽假设失效，字典序不再等于时间序
            throw new IllegalStateException("时间戳超出 " + TIME_CHARS + " 位 base36 编码范围：" + time);
        }
        StringBuilder sb = new StringBuilder(TIME_CHARS + RANDOM_CHARS);
        sb.append("0".repeat(TIME_CHARS - time.length())).append(time);
        for (int i = 0; i < RANDOM_CHARS; i++) {
            sb.append(Character.forDigit(random.nextInt(RADIX), RADIX));
        }
        return sb.toString();
    }

    /**
     * 分类 code 只允许 {@code [A-Za-z0-9_-]}，且不含斜杠 —— 允许斜杠就等于允许运营
     * 从后台构造任意目录层级，那是把 key 的形状交给了配置数据。
     */
    private static String normalizeCategoryCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new IllegalArgumentException("分类 code 不能为空");
        }
        for (int i = 0; i < categoryCode.length(); i++) {
            char c = categoryCode.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                throw new IllegalArgumentException(
                        "分类 code 只允许 [A-Za-z0-9_-]，实际为：" + categoryCode);
            }
        }
        return categoryCode.toLowerCase();
    }

    /**
     * 扩展名归一化：小写、去点、非法字符直接丢弃整个扩展名而不是"尽力修复"。
     *
     * <p>修复式处理在这里是有害的 —— 一个修不干净的扩展名比没有扩展名危险得多。
     */
    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        ext = ext.toLowerCase();
        if (ext.isEmpty() || ext.length() > MAX_EXTENSION_LENGTH) {
            return "";
        }
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return "";
            }
        }
        return ext;
    }
}
