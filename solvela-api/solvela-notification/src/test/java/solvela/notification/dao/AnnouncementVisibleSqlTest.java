package solvela.notification.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.AnnouncementStatusEnum;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面向 C 端的四条公告查询，都必须带齐「已发布、到了发布时间、没过期」三个条件。
 *
 * <p>少一个的表现分别是「下架的公告还在显示」「预发布的提前泄露」「过期的永远占着红点」，都不报错。
 * 其中 {@code selectPendingAck} 带 LEFT JOIN，没法 include {@code visible_conditions} 片段，是另写的一份 ——
 * 两份都在这里逐条渲染断言。状态值写成 {@code ${@...@PUBLISHED.value}}，常量改名会在渲染这一步抛异常。
 */
class AnnouncementVisibleSqlTest {

    private static final List<String> MEMBER_QUERIES = List.of("selectVisible", "countUnread", "selectMaxVisibleId", "selectPendingAck");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/notification/AnnouncementMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 四条 C 端查询都带齐：已发布、publish_time <= now、expire_time > now")
    void C端查询带齐三个条件() {
        Pattern published = Pattern.compile("\\bstatus\\s*=\\s*" + AnnouncementStatusEnum.PUBLISHED.getValue() + "\\b");
        for (String id : MEMBER_QUERIES) {
            String sql = sqlOf(id);
            assertTrue(published.matcher(sql).find(), id + " 没有「已发布」条件，下架的公告会照样显示：" + sql);
            assertTrue(sql.contains("publish_time <= ?"), id + " 没有发布时间条件，预发布的会提前泄露：" + sql);
            assertTrue(sql.contains("expire_time > ?"), id + " 没有过期条件，过期的会永远占着红点：" + sql);
        }
    }

    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        param.put("now", LocalDateTime.now());
        param.put("memberCreateTime", LocalDateTime.now());
        param.put("memberId", 1L);
        param.put("limit", 20);
        param.put("lastId", null);
        param.put("lastReadId", null);
        String sql = configuration.getMappedStatement(AnnouncementDao.class.getName() + "." + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
