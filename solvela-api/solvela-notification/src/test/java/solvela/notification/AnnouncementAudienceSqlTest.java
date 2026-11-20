package solvela.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.AnnouncementAudienceEnum;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告的人群类型：枚举里的每个值，C 端可见性判定的 SQL 都要认。
 *
 * <p>🔴 AnnouncementMapper.xml 里判「这条公告谁能看到」的是两段手写 SQL，按 {@code audience_type = '...'} 字面量比对。
 * 枚举加了一种人群而 SQL 没跟上，那种公告<b>对所有人都不可见，而且不报错</b> —— 运营发出去了，后台显示已发布，
 * 用户就是收不到。这条用例把「改枚举必须改 SQL」从注释变成编译后就能抓到的失败。
 */
@DisplayName("公告人群类型：枚举与可见性 SQL 一致")
class AnnouncementAudienceSqlTest {

    private static final Path MAPPER = Path.of("src/main/resources/mapper/notification/AnnouncementMapper.xml");

    /** 两处可见性判定：未读列表 / 计数用的 audience_conditions 片段，以及强制确认弹窗那条查询里内联的一份 */
    private static final Pattern AUDIENCE_LITERAL = Pattern.compile("audience_type\\s*=\\s*'([A-Z_]+)'");

    @Test
    @DisplayName("🔴 每个枚举值在 SQL 里都出现两次（两处判定各一次），SQL 里也没有枚举外的值")
    void 枚举与SQL一一对应() throws IOException {
        String xml = Files.readString(MAPPER, StandardCharsets.UTF_8);
        Matcher m = AUDIENCE_LITERAL.matcher(xml);
        Map<String, Integer> counts = new TreeMap<>();
        while (m.find()) {
            counts.merge(m.group(1), 1, Integer::sum);
        }
        // 空扫通过比没有测试更糟：文件搬走或写法变了，这里要先红
        assertTrue(counts.size() > 0, "在 " + MAPPER + " 里一个 audience_type 字面量都没找到，扫描规则多半过期了");

        for (AnnouncementAudienceEnum e : AnnouncementAudienceEnum.values()) {
            assertEquals(2, counts.getOrDefault(e.name(), 0),
                    e.name() + " 在可见性 SQL 里应当出现两次（两处判定各一次）：少了的那一处，这类公告谁都看不到");
        }
        for (String literal : counts.keySet()) {
            assertTrue(contains(literal), "SQL 里的 '" + literal + "' 不是 AnnouncementAudienceEnum 的值，存不进来也就永远匹配不上");
        }
    }

    private static boolean contains(String name) {
        for (AnnouncementAudienceEnum e : AnnouncementAudienceEnum.values()) {
            if (e.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
