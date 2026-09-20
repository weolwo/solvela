package solvela.member.grade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.MemberGrade;
import solvela.member.grade.dao.MemberGrowthDao;
import solvela.member.grade.dao.MemberGradeDao;
import solvela.member.grade.service.MemberGradeConfigService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 等级配置的业务约束。
 *
 * <h3>🔴 这里拦的都是「页面上看不出错」的配置</h3>
 * 两行数字而已，运营填完看着很正常，保存也成功 ——
 * 错误只会在下一次判级时表现为「所有人白升了一级」，而且不报错。
 * 表单校验看不见别的行，所以只能在这一层拦。
 *
 * @Date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberGradeConfigServiceTest {

    @Mock
    private MemberGradeDao memberGradeDao;

    @Mock
    private MemberGrowthDao memberGrowthDao;

    @InjectMocks
    private MemberGradeConfigService service;

    private static MemberGrade grade(Long id, int grade, String name, long threshold) {
        MemberGrade row = new MemberGrade();
        row.setId(id);
        row.setGradeCode(grade);
        row.setGradeName(name);
        row.setThreshold(threshold);
        row.setStatus(EnableStatusEnum.ENABLED);
        return row;
    }

    private void givenExistingLevels() {
        when(memberGradeDao.selectList(any())).thenReturn(List.of(
                grade(1L, 0, "普通会员", 0L),
                grade(2L, 1, "银卡", 1000L),
                grade(3L, 2, "金卡", 5000L)));
    }

    @Test
    @DisplayName("🔴 门槛必须随等级递增 —— 高等级门槛更低会让所有人白升一级")
    void 门槛必须递增() {
        /*
         * gradeOf 取的是「门槛不超过成长值的最高那一档」。
         * 等级 3 的门槛设成 3000（低于等级 2 的 5000）之后，
         * 一个刚过 3000 的人会被直接判成等级 3 —— 不报错、不异常。
         */
        givenExistingLevels();
        MemberGrade form = grade(null, 3, "白金", 3000L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.save(form, "huke"));
        assertTrue(e.getMessage().contains("门槛必须随等级递增"), e.getMessage());
        verify(memberGradeDao, never()).insert(any(MemberGrade.class));
    }

    @Test
    @DisplayName("🔴 反方向也要拦：低等级的门槛不能比高等级还高")
    void 低等级门槛不能更高() {
        givenExistingLevels();
        // 把银卡（等级 1）的门槛改到 9999，比金卡（等级 2，5000）还高
        MemberGrade form = grade(2L, 1, "银卡", 9999L);

        assertThrows(BusinessException.class, () -> service.save(form, "huke"));
    }

    @Test
    @DisplayName("🔴 等级 0 的门槛必须是 0 —— 否则新会员一进来就没过线")
    void 零档门槛必须为零() {
        givenExistingLevels();
        MemberGrade form = grade(1L, 0, "普通会员", 100L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.save(form, "huke"));
        assertTrue(e.getMessage().contains("门槛必须是 0"), e.getMessage());
    }

    @Test
    @DisplayName("🔴 等级 0 不能停用 —— 它是新会员的落点，也是判级函数的兜底")
    void 零档不能停用() {
        when(memberGradeDao.selectById(1L)).thenReturn(grade(1L, 0, "普通会员", 0L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, EnableStatusEnum.DISABLED, "huke"));
        assertTrue(e.getMessage().contains("不能停用"), e.getMessage());
        verify(memberGradeDao, never()).updateById(any(MemberGrade.class));
    }

    @Test
    @DisplayName("等级 0 可以改名，只是不能停用")
    void 零档可以启用() {
        when(memberGradeDao.selectById(1L)).thenReturn(grade(1L, 0, "普通会员", 0L));

        assertDoesNotThrow(() -> service.updateStatus(1L, EnableStatusEnum.ENABLED, "huke"));
        verify(memberGradeDao).updateById(any(MemberGrade.class));
    }

    @Test
    @DisplayName("等级号不能重复：同一个 grade 已经有别的行了")
    void 等级号不能重复() {
        when(memberGradeDao.selectOne(any())).thenReturn(grade(3L, 2, "金卡", 5000L));

        MemberGrade form = grade(null, 2, "金卡PLUS", 6000L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.save(form, "huke"));
        assertTrue(e.getMessage().contains("已经存在"), e.getMessage());
    }

    @Test
    @DisplayName("合法的新一档：门槛在最高档之上，能存进去")
    void 正常新增() {
        givenExistingLevels();
        MemberGrade form = grade(null, 3, "白金", 20000L);

        assertDoesNotThrow(() -> service.save(form, "huke"));
        verify(memberGradeDao).insert(form);
        assertAll(
                () -> assertTrue("huke".equals(form.getCreateBy()), "新增要落操作人"));
    }
}
