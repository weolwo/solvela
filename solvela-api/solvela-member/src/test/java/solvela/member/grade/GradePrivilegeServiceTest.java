package solvela.member.grade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.GradePrivilege;
import solvela.member.grade.dao.GradePrivilegeDao;
import solvela.member.grade.dao.MemberGradeDao;
import solvela.member.grade.service.GradePrivilegeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 等级权益的后台维护。
 *
 * <h3>这个后台本身不危险，危险的是它造成的错觉</h3>
 * 这张表纯展示，配错了不会算错钱。但它有两种<b>配了却什么都没发生</b>的写法，
 * 而两种在页面上都长得一模一样（保存成功、列表里有那一行）：
 * <ol>
 *   <li>挂在一个<b>不存在的等级</b>上 —— C 端是拿 enabledGrades 去取权益的，
 *       挂错档的权益永远不会被任何人看到；</li>
 *   <li>同档<b>编码重复</b> —— 撞唯一键，而报出来的是一串 SQL 约束名。</li>
 * </ol>
 * 两条都只能在服务层拦，所以这里逐条钉住。
 *
 * @Date 2026-09-21
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradePrivilegeServiceTest {

    @Mock
    private GradePrivilegeDao gradePrivilegeDao;

    @Mock
    private MemberGradeDao memberGradeDao;

    @InjectMocks
    private GradePrivilegeService service;

    private static GradePrivilege form(Long id, int gradeCode, String code) {
        GradePrivilege p = new GradePrivilege();
        p.setId(id);
        p.setGradeCode(gradeCode);
        p.setPrivilegeCode(code);
        p.setPrivilegeName("生日礼");
        return p;
    }

    @BeforeEach
    void setUp() {
        // 默认：等级存在、同档没有重名
        when(memberGradeDao.selectCount(any())).thenReturn(1L);
        when(gradePrivilegeDao.selectOne(any())).thenReturn(null);
    }

    @Test
    @DisplayName("新增：落 createBy，sort 与 status 有默认值")
    void 新增填默认值() {
        GradePrivilege p = form(null, 3, "BIRTHDAY_GIFT");

        service.save(p, "alaric");

        ArgumentCaptor<GradePrivilege> saved = ArgumentCaptor.forClass(GradePrivilege.class);
        verify(gradePrivilegeDao).insert(saved.capture());
        assertEquals("alaric", saved.getValue().getCreateBy());
        // 不给默认值的话 sort 是 null，而列表按 sort 排 —— 新加的那条会飘到看不见的地方
        assertEquals(0, saved.getValue().getSort());
        assertEquals(EnableStatusEnum.ENABLED, saved.getValue().getStatus());
    }

    @Test
    @DisplayName("🔴 挂在不存在的等级上必须拦：那条权益永远不会被任何人看到")
    void 等级不存在时拒绝() {
        /*
         * C 端 MemberGradeQueryService 是拿 enabledGrades() 去 map 里取权益的。
         * grade_code 对不上任何一档时，这条权益不报错、不告警，就是不显示 ——
         * 运营只会觉得「我明明配了」。页面上完全看不出来，只能在这里拦。
         */
        when(memberGradeDao.selectCount(any())).thenReturn(0L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.save(form(null, 99, "GHOST"), "alaric"));

        assertTrue(e.getMessage().contains("99"), "错误信息要指出是哪一档：" + e.getMessage());
        verify(gradePrivilegeDao, never()).insert(any(GradePrivilege.class));
    }

    @Test
    @DisplayName("🔴 同档编码重复要说人话，不能让唯一键抛 SQL 约束名出去")
    void 同档编码重复时拒绝() {
        GradePrivilege existing = form(7L, 3, "BIRTHDAY_GIFT");
        existing.setPrivilegeName("生日双倍积分");
        when(gradePrivilegeDao.selectOne(any())).thenReturn(existing);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.save(form(null, 3, "BIRTHDAY_GIFT"), "alaric"));

        // 要把占位的那条叫什么也说出来，否则运营不知道该去改哪一行
        assertTrue(e.getMessage().contains("BIRTHDAY_GIFT"), e.getMessage());
        assertTrue(e.getMessage().contains("生日双倍积分"), e.getMessage());
        verify(gradePrivilegeDao, never()).insert(any(GradePrivilege.class));
    }

    @Test
    @DisplayName("改自己不算重复：编辑时查出来的就是自己那一行")
    void 编辑自己不算重复() {
        when(gradePrivilegeDao.selectOne(any())).thenReturn(form(7L, 3, "BIRTHDAY_GIFT"));
        when(gradePrivilegeDao.selectById(7L)).thenReturn(form(7L, 3, "BIRTHDAY_GIFT"));

        GradePrivilege p = form(7L, 3, "BIRTHDAY_GIFT");
        p.setPrivilegeName("生日礼（改过文案）");
        service.save(p, "alaric");

        verify(gradePrivilegeDao).updateById(any(GradePrivilege.class));
    }

    @Test
    @DisplayName("编辑一条已被别人删掉的：报错，不要变成静默新增")
    void 编辑已删除的报错() {
        when(gradePrivilegeDao.selectById(7L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.save(form(7L, 3, "BIRTHDAY_GIFT"), "alaric"));
        verify(gradePrivilegeDao, never()).insert(any(GradePrivilege.class));
    }

    @Test
    @DisplayName("🔴 删除是物理删：停用的行照样占着唯一键，只给停用就加不回同编码的")
    void 删除是物理删() {
        /*
         * uk(grade_code, privilege_code) 不看 status。停用白金的 BIRTHDAY_GIFT
         * 之后想重新加一条同编码的会撞键，而运营在页面上看到的是一行「已停用」，
         * 根本不会想到那就是挡住他的东西。
         */
        when(gradePrivilegeDao.selectById(7L)).thenReturn(form(7L, 3, "BIRTHDAY_GIFT"));

        service.delete(7L, "alaric");

        verify(gradePrivilegeDao).deleteById(7L);
    }

    @Test
    @DisplayName("删一条不存在的：静默返回，不报错吓人")
    void 重复删除幂等() {
        when(gradePrivilegeDao.selectById(anyLong())).thenReturn(null);

        service.delete(7L, "alaric");

        verify(gradePrivilegeDao, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("停用：只改 status 与 updateBy，不碰别的列")
    void 停用只改状态() {
        when(gradePrivilegeDao.selectById(7L)).thenReturn(form(7L, 3, "BIRTHDAY_GIFT"));

        service.updateStatus(7L, EnableStatusEnum.DISABLED, "alaric");

        ArgumentCaptor<GradePrivilege> saved = ArgumentCaptor.forClass(GradePrivilege.class);
        verify(gradePrivilegeDao).updateById(saved.capture());
        assertEquals(EnableStatusEnum.DISABLED, saved.getValue().getStatus());
        // 整条 copy 回去的话，页面没传的字段会被 null 覆盖掉
        assertEquals(null, saved.getValue().getPrivilegeName());
    }

    @Test
    @DisplayName("停用一条不存在的：报错（而不是静默成功）")
    void 停用不存在的报错() {
        when(gradePrivilegeDao.selectById(anyLong())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.updateStatus(7L, EnableStatusEnum.DISABLED, "alaric"));
    }
}
