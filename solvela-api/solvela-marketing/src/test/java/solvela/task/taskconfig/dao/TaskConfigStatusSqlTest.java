package solvela.task.taskconfig.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.TaskConfigStatusEnum;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务配置上下线的两条闸门，与 {@link TaskConfigStatusEnum#canTransitTo} 一条不多、一条不少。
 * 做法与 {@code solvela.mall.order.dao.MallOrderStatusSqlTest} 相同。
 */
class TaskConfigStatusSqlTest {

    private static final Pattern SET_STATUS = Pattern.compile("set\\s+status\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_STATUS = Pattern.compile(
            "where\\b.*?\\bstatus\\s*(?:=\\s*(\\d+)|in\\s*\\(([\\d,\\s]+)\\))", Pattern.CASE_INSENSITIVE);

    private static final List<String> GATES = List.of("markPending", "markOffline");

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/marketing/TaskConfigMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("🔴 上下线两条闸门与枚举状态机一条不多、一条不少；生效中不是任何闸门的目标")
    void 闸门与状态机一一对应() {
        Set<String> fromSql = new HashSet<>();
        for (String id : GATES) {
            String sql = configuration.getMappedStatement(TaskConfigDao.class.getName() + "." + id)
                    .getBoundSql(new HashMap<>()).getSql().replaceAll("\\s+", " ");
            assertFalse(sql.contains("${") || sql.contains("@solvela"), id + " 里有没求值的占位：" + sql);
            Matcher set = SET_STATUS.matcher(sql);
            assertTrue(set.find(), id + " 找不到 set status：" + sql);
            TaskConfigStatusEnum to = byValue(Integer.parseInt(set.group(1)));
            Matcher where = WHERE_STATUS.matcher(sql);
            assertTrue(where.find(), id + " 找不到前置状态条件：" + sql);
            String values = where.group(1) != null ? where.group(1) : where.group(2);
            Arrays.stream(values.split(",")).map(v -> byValue(Integer.parseInt(v.trim())))
                    .forEach(from -> fromSql.add(from + "->" + to));
        }
        Set<String> fromEnum = new HashSet<>();
        for (TaskConfigStatusEnum from : TaskConfigStatusEnum.values()) {
            for (TaskConfigStatusEnum to : TaskConfigStatusEnum.values()) {
                if (from.canTransitTo(to)) {
                    fromEnum.add(from + "->" + to);
                }
            }
        }
        assertFalse(fromEnum.isEmpty(), "canTransitTo 一条边都没有");
        assertEquals(fromEnum, fromSql, "TaskConfigDao 的闸门与 TaskConfigStatusEnum#canTransitTo 对不上");
        for (TaskConfigStatusEnum from : TaskConfigStatusEnum.values()) {
            assertFalse(from.canTransitTo(TaskConfigStatusEnum.ACTIVE), "生效中没有任何代码认它，不该有迁入的边");
        }
    }

    private static TaskConfigStatusEnum byValue(int value) {
        return Arrays.stream(TaskConfigStatusEnum.values()).filter(s -> s.getValue() == value).findFirst()
                .orElseThrow(() -> new AssertionError("状态值 " + value + " 不是任何一个 TaskConfigStatusEnum"));
    }
}
