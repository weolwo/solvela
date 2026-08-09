package net.lab1024.sa.base.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StorageKeyGenerator} 的行为。
 *
 * @Date 2026-08-10
 */
class StorageKeyGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static StorageKeyGenerator at(String isoInstant) {
        return new StorageKeyGenerator(Clock.fixed(Instant.parse(isoInstant), ZONE));
    }

    @Test
    @DisplayName("格式：{code}/{yyyyMM}/{dd}/{id}.{ext}")
    void format() {
        StorageKey key = at("2026-08-10T03:04:05Z").generate("BANNER", "png");
        assertThat(key.value()).matches("banner/202608/10/[0-9a-z]{16}\\.png");
    }

    @Test
    @DisplayName("扩展名从入参来，且被归一化：带点、大写、都能吃")
    void extensionNormalized() {
        StorageKeyGenerator gen = at("2026-08-10T03:04:05Z");
        assertThat(gen.generate("c", ".PNG").extension()).isEqualTo("png");
        assertThat(gen.generate("c", "JPEG").extension()).isEqualTo("jpeg");
    }

    @Test
    @DisplayName("非法扩展名整个丢弃，而不是'尽力修复' —— 修不干净的扩展名比没有更危险")
    void illegalExtensionDropped() {
        StorageKeyGenerator gen = at("2026-08-10T03:04:05Z");
        assertThat(gen.generate("c", "p g").extension()).isEmpty();
        assertThat(gen.generate("c", "../sh").extension()).isEmpty();
        assertThat(gen.generate("c", "a".repeat(20)).extension()).isEmpty();
        assertThat(gen.generate("c", null).value()).doesNotContain(".");
    }

    @Test
    @DisplayName("分类 code 来自可配置的表，非法值必须在生成时就炸")
    void rejectsIllegalCategoryCode() {
        StorageKeyGenerator gen = at("2026-08-10T03:04:05Z");
        assertThatThrownBy(() -> gen.generate("活动素材", "png"))
                .isInstanceOf(IllegalArgumentException.class);
        // 允许斜杠等于把 key 的目录层级交给配置数据，必须拒
        assertThatThrownBy(() -> gen.generate("a/b", "png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gen.generate("", "png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同一毫秒内大量生成不重复")
    void noCollisionWithinSameMillis() {
        StorageKeyGenerator gen = at("2026-08-10T03:04:05Z");
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            keys.add(gen.generate("c", "png").value());
        }
        assertThat(keys).hasSize(20_000);
    }

    @Test
    @DisplayName("时间有序：时间靠后的 key 字典序也靠后 —— 定宽编码就是为了这个")
    void lexicographicOrderMatchesTimeOrder() {
        // 跨越 base36 位数进位的时刻也必须成立，这正是变长编码会出错的地方
        String earlier = at("2001-09-09T01:46:39Z").generate("c", "png").value();
        String later = at("2026-08-10T03:04:05Z").generate("c", "png").value();
        String muchLater = at("2400-01-01T00:00:00Z").generate("c", "png").value();

        String idEarlier = idOf(earlier);
        String idLater = idOf(later);
        String idMuchLater = idOf(muchLater);

        assertThat(idEarlier).isLessThan(idLater);
        assertThat(idLater).isLessThan(idMuchLater);
        assertThat(idEarlier).hasSameSizeAs(idMuchLater);
    }

    private static String idOf(String key) {
        String last = key.substring(key.lastIndexOf('/') + 1);
        int dot = last.lastIndexOf('.');
        return dot < 0 ? last : last.substring(0, dot);
    }

    @Test
    @DisplayName("生成的 key 一定能通过 StorageKey 的校验（否则等于把非法 key 造出来了）")
    void alwaysProducesValidKey() {
        StorageKeyGenerator gen = new StorageKeyGenerator();
        for (String code : new String[]{"COMMON", "help-doc", "a_b", "X9"}) {
            for (String ext : new String[]{"png", "", null, "tar"}) {
                assertThat(gen.generate(code, ext)).isNotNull();
            }
        }
    }
}
