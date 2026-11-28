package solvela.base.module.file.dao;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.base.module.file.constant.FileStatusEnum;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件确认引用、孤儿清理、手动删除三条闸门 SQL 的判据。
 *
 * <p>它们靠 t_file 的行锁串起来（见 FileMapper.xml 同一段注释）：确认要先锁行、只把临时改成已确认；
 * 清理与删除的「没有引用」必须压在 UPDATE 的 WHERE 里，读一次再无条件写的话，夹在中间登记的引用挡不住，
 * 刚配好的图会被删掉字节。状态值写成 {@code ${@...@X.value}}，常量改名会在渲染这一步抛异常。
 */
class FileStatusSqlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parse() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/support/FileMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private static final String TEMP = "status = " + FileStatusEnum.TEMP.getValue();

    @Test
    @DisplayName("🔴 确认：先 FOR UPDATE 锁住未删除的行，再只把临时改成已确认（状态机的那一条边）")
    void 确认先锁再改() {
        String lock = sqlOf("lockLiveFiles");
        assertTrue(lock.contains("FOR UPDATE") && lock.contains("deleted_flag = 0"), lock);

        String mark = sqlOf("markConfirmed");
        assertTrue(mark.contains("SET status = " + FileStatusEnum.CONFIRMED.getValue()), mark);
        assertTrue(mark.contains("WHERE " + TEMP) && mark.contains("deleted_flag = 0"), mark);
        assertTrue(FileStatusEnum.TEMP.canTransitTo(FileStatusEnum.CONFIRMED));
        assertFalse(FileStatusEnum.CONFIRMED.canTransitTo(FileStatusEnum.TEMP), "已确认不能回退成临时，否则会被清理掉");
    }

    @Test
    @DisplayName("🔴 清理只删「未删除、仍是临时、没有引用」的；手动删除只删「未删除、没有引用」的，判据都在 WHERE 里")
    void 清理与删除的判据在WHERE里() {
        String orphan = sqlOf("softDeleteOrphan");
        assertTrue(orphan.contains("f.deleted_flag = 0") && orphan.contains("f." + TEMP)
                && orphan.contains("NOT EXISTS (SELECT 1 FROM t_file_relation"), orphan);

        String manual = sqlOf("softDeleteUnreferenced");
        assertTrue(manual.contains("f.deleted_flag = 0") && manual.contains("NOT EXISTS (SELECT 1 FROM t_file_relation"), manual);
    }

    @Test
    @DisplayName("孤儿扫描与计数两条查询都只认临时")
    void 孤儿扫描只认临时() {
        for (String id : List.of("selectOrphanIds", "countOrphans")) {
            assertTrue(sqlOf(id).contains("f." + TEMP), id + " 应当只扫临时文件：" + sqlOf(id));
        }
    }

    private static String sqlOf(String id) {
        Map<String, Object> param = new HashMap<>();
        param.put("fileIds", List.of(1L, 2L));
        param.put("fileId", 1L);
        param.put("operator", "op");
        param.put("deadline", LocalDateTime.now());
        param.put("limit", 10);
        String sql = configuration.getMappedStatement(FileDao.class.getName() + "." + id).getBoundSql(param).getSql();
        assertFalse(sql.contains("${") || sql.contains("@solvela"), () -> id + " 里有没求值的占位：" + sql);
        return sql.replaceAll("\\s+", " ");
    }
}
