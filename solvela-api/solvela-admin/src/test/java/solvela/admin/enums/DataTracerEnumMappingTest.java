package solvela.admin.enums;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.admin.constant.UserTypeEnum;
import solvela.admin.module.system.datatracer.constant.DataTracerTypeEnum;
import solvela.admin.module.system.datatracer.dao.DataTracerDao;
import solvela.admin.module.system.datatracer.domain.entity.DataTracerEntity;
import solvela.admin.module.system.datatracer.domain.form.DataTracerQueryForm;
import solvela.admin.module.system.datatracer.domain.vo.DataTracerVO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 枚举字段落库/读库的真实验收（连数据库，只读）。
 *
 * <h3>为什么必须真跑库</h3>
 * 把 {@code Integer status} 换成枚举，编译期什么都发现不了，风险全在运行期，而且分三条路：
 *
 * <ol>
 *   <li><b>MyBatis-Plus 的 BaseMapper</b>：走 MP 自己的类型处理，实体字段是枚举时它认
 *       {@code IEnum}。这条通常没问题。</li>
 *   <li><b>手写 XML + {@code resultType}</b>：这条是真正的雷。resultType 走的是 MyBatis 的
 *       自动映射，int 列要落进枚举属性，取决于容器里到底注册了哪个 TypeHandler。
 *       没注册对的话，MyBatis 会退回按<b>枚举名</b>匹配，于是 int 值 10 找不到名叫 "10" 的常量。
 *       —— 而这个项目的 yaml 里<b>没有</b>配 {@code default-enum-type-handler}，
 *       所以这条路是靠 MP 的自动配置兜的，必须有测试钉住。</li>
 *   <li><b>枚举当查询参数</b>：{@code #{query.type}} 要能写成 int 而不是枚举名。</li>
 * </ol>
 *
 * <p>三条路任何一条断了，表现都是「查出来是 null」或「一条都查不到」，
 * 接口照样 200，页面上只是少了点东西 —— 这种故障在测试里比在线上便宜得多。
 *
 * <p>本测试<b>只读</b>：不造数、不改库，可以随时重复跑。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class DataTracerEnumMappingTest {

    @Autowired
    private DataTracerDao dataTracerDao;

    @Test
    @DisplayName("路径一：BaseMapper 读出来的实体，枚举字段不是 null")
    void 实体枚举字段能从int列装配() {
        List<DataTracerEntity> list = dataTracerDao.selectList(null);
        assertFalse(list.isEmpty(), "t_data_tracer 没有数据，这条用例失去意义");

        for (DataTracerEntity e : list) {
            assertNotNull(e.getType(), "type 装配成了 null —— int 列没能落进枚举属性");
            assertNotNull(e.getUserType(), "userType 装配成了 null");
        }
    }

    @Test
    @DisplayName("路径二：手写 XML 的 resultType 自动映射，同样能落进枚举属性")
    void XML的resultType能装配枚举() {
        DataTracerQueryForm form = new DataTracerQueryForm();
        IPage<DataTracerVO> page = new Page<>(1, 200);
        List<DataTracerVO> list = dataTracerDao.query((Page) page, form);
        assertFalse(list.isEmpty(), "查不到数据，这条用例失去意义");

        for (DataTracerVO vo : list) {
            assertNotNull(vo.getType(), "VO.type 是 null —— resultType 自动映射没走到枚举 TypeHandler");
            assertNotNull(vo.getUserType(), "VO.userType 是 null");
        }
    }

    @Test
    @DisplayName("路径三：枚举当查询条件，#{query.type} 要写成 int 而不是枚举名")
    void 枚举可以作为查询参数() {
        DataTracerQueryForm all = new DataTracerQueryForm();
        int total = dataTracerDao.query(new Page<>(1, 500), all).size();
        assertTrue(total > 0, "t_data_tracer 没有数据，这条用例失去意义");

        int sum = 0;
        boolean anyMatched = false;
        for (DataTracerTypeEnum type : DataTracerTypeEnum.values()) {
            DataTracerQueryForm form = new DataTracerQueryForm();
            form.setType(type);
            List<DataTracerVO> hit = dataTracerDao.query(new Page<>(1, 500), form);
            for (DataTracerVO vo : hit) {
                // 查 A 类型不能捞出 B 类型：证明条件真的按 value 下推到了 SQL
                assertTrue(type == vo.getType(),
                        "按 " + type + " 查询却查出了 " + vo.getType() + "，条件没有正确下推");
            }
            if (!hit.isEmpty()) {
                anyMatched = true;
            }
            sum += hit.size();
        }

        assertTrue(anyMatched, "按枚举查询一条都没查到 —— 参数多半被写成了枚举名而不是 value");
        assertTrue(sum == total,
                "按各枚举分别查询的总数(" + sum + ")与不带条件的总数(" + total + ")对不上，说明有行的 type 落在枚举之外");
    }

    @Test
    @DisplayName("UserTypeEnum 复用在多张表上，这里确认它在本表也是通的")
    void userType映射正确() {
        List<DataTracerEntity> list = dataTracerDao.selectList(null);
        assertFalse(list.isEmpty());
        for (DataTracerEntity e : list) {
            assertTrue(e.getUserType() == UserTypeEnum.ADMIN_EMPLOYEE || e.getUserType() == UserTypeEnum.MEMBER,
                    "userType 落在枚举之外：" + e.getUserType());
        }
    }
}
