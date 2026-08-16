package sa.admin.mapper;

import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import sa.risk.proposal.dao.ProposalRecordDao;
import sa.risk.proposal.domain.form.ProposalRecordQueryForm;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mapper 的 {@code <resultMap>} 属性 与 目标类 setter 的一致性守卫。
 *
 * <p>要防的是这样一类线上事故：SQL 和 resultMap 里加了新列（如 {@code asset_name -> assetName}），
 * 实体和 Form 都同步了，<b>唯独 VO 漏了</b>。这种错误在编译期完全静默，
 * MyBatis 也不会在启动时校验 —— 它是在<b>真的查出一行、且该列非空</b>时才抛：
 * <pre>ReflectionException: There is no setter for property named 'assetName' in '...VO'</pre>
 * 也就是说：库里没数据时一切正常，等有数据了列表页直接 500。
 * （2026-08 实际发生过一次，ProposalRecordVO 缺 assetName。）
 *
 * <p>所以这里<b>不依赖库里有没有数据</b>：直接拿 MyBatis 解析完的 Configuration
 * 逐个 resultMap 与目标类对账，全表为空也照样能抓出来。
 *
 * <p>本测试只读：不造数、不改库，可以随时重复跑。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ResultMapPropertyTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private ProposalRecordDao proposalRecordDao;

    @Test
    @DisplayName("所有 resultMap 的 property 都能在目标类上找到 setter")
    void everyResultMapPropertyHasSetter() {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        List<String> problems = new ArrayList<>();

        for (ResultMap resultMap : configuration.getResultMaps()) {
            Class<?> type = resultMap.getType();
            // 只管本项目自己的类型。MyBatis / MyBatis-Plus 会为分页、嵌套查询等
            // 生成一批内部 resultMap，那些不归我们管。
            if (type == null || !type.getName().startsWith("sa.")) {
                continue;
            }
            MetaClass metaClass = MetaClass.forClass(type, configuration.getReflectorFactory());

            for (ResultMapping mapping : resultMap.getResultMappings()) {
                String property = mapping.getProperty();
                if (property == null
                        // 构造器注入的列不走 setter
                        || resultMap.getConstructorResultMappings().contains(mapping)
                        // 嵌套结果映射由子 resultMap 自己负责，会被外层循环单独扫到
                        || mapping.getNestedResultMapId() != null) {
                    continue;
                }
                if (!metaClass.hasSetter(property)) {
                    problems.add(String.format(
                            "%s 缺少属性 '%s'（resultMap: %s，列: %s）",
                            type.getName(), property, resultMap.getId(), mapping.getColumn()));
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "以下 resultMap 的属性在目标类上没有 setter，查出数据时会抛 ReflectionException：\n  "
                        + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("ProposalRecordDao.queryPage 能完整映射（真跑一次查询）")
    void proposalQueryPageMapsCleanly() {
        // 上面那个测试是静态对账，这里补一次真实链路：真查库、真做结果映射。
        // 库里没数据时这个测试不会失败也不会误报，它的价值在有数据的环境（本地/测试环境）。
        assertDoesNotThrow(() -> proposalRecordDao.queryPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10),
                new ProposalRecordQueryForm()));
    }
}
