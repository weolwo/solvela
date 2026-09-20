package solvela.member.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import solvela.exception.BusinessException;
import solvela.member.Member;
import solvela.member.grade.service.MemberGrowthService;
import solvela.member.manager.MemberManager;
import solvela.scriptengine.spi.EngineContext;
import solvela.scriptengine.spi.ScriptIdentity;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 脚本里的会员等级 —— 「等级专享活动/奖池」在脚本侧的入口（阶段 3）。
 *
 * <h3>🔴 这里守的两条都只会静默出错</h3>
 * ① <b>{@code level} 必须是 long</b>：脚本里的数字字面量是 long，
 *    QLExpress 下 {@code m.grade >= 3} 两边类型不一致时的行为不值得赌；
 * ② <b>一次执行只查一次库</b>：脚本里 {@code member_info()} 写三遍是常态，
 *    缓存破了不会报错，只会让一次抽奖多打几次库。
 *
 * @Date 2026-09-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberScriptGradeTest {

    private static final Long MEMBER_ID = 5731678932L;

    @Mock
    private MemberManager memberManager;

    @Mock
    private MemberGrowthService memberGrowthService;

    private MemberScriptFunctions functions;

    @BeforeEach
    void setUp() {
        functions = new MemberScriptFunctions(memberManager, memberGrowthService);
        Member member = new Member();
        member.setMemberId(MEMBER_ID);
        member.setMemberName("sv" + MEMBER_ID);
        member.setNickname("测试会员");
        member.setCreateTime(LocalDateTime.now().minusDays(30));
        when(memberManager.getById(MEMBER_ID)).thenReturn(member);
    }

    private EngineContext contextOf(int grade) {
        when(memberGrowthService.currentGrade(MEMBER_ID)).thenReturn(grade);
        return EngineContext.create(Map.of()).bindInternal(ScriptIdentity.MEMBER_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("member_info() 带上 level")
    void 资料里有等级() {
        Map<String, Object> info = functions.info(contextOf(3));

        assertEquals(3L, info.get("grade"));
    }

    @Test
    @DisplayName("🔴 level 是 long，不是 Integer —— 脚本里的数字字面量是 long")
    void 等级必须是long() {
        /*
         * 装成 Integer 的话，QLExpress 里 m.grade >= 3 的两边是 Integer 和 Long。
         * 那条比较在不同版本里的行为不一样，而错了是静默的：判据整条失效，
         * 表现为「等级专享奖池谁都抽不到」或者「谁都能抽到」。
         */
        Object level = functions.info(contextOf(3)).get("grade");

        assertInstanceOf(Long.class, level, "实际类型：" + level.getClass().getSimpleName());
    }

    @Test
    @DisplayName("member_gradeAtLeast：达标、超标都算通过，不足不通过")
    void 等级门槛判定() {
        assertTrue(functions.gradeAtLeast(contextOf(3), 3), "正好达到门槛应当通过");
        assertTrue(functions.gradeAtLeast(contextOf(4), 3), "高于门槛应当通过");
        assertFalse(functions.gradeAtLeast(contextOf(2), 3), "低于门槛不该通过");
    }

    @Test
    @DisplayName("没攒过成长值的会员是 0 级，不是 null —— 脚本里 null >= 3 会炸")
    void 零级会员() {
        when(memberGrowthService.currentGrade(MEMBER_ID)).thenReturn(null);
        EngineContext context = EngineContext.create(Map.of())
                .bindInternal(ScriptIdentity.MEMBER_ID, MEMBER_ID);

        assertEquals(0L, functions.grade(context));
        assertFalse(functions.gradeAtLeast(context, 1));
    }

    @Test
    @DisplayName("🔴 负数门槛直接报错 —— 那样写谁都满足，一定是参数写错了")
    void 负数门槛报错() {
        EngineContext context = contextOf(0);

        BusinessException e = assertThrows(BusinessException.class, () -> functions.gradeAtLeast(context, -1));
        assertTrue(e.getMessage().contains("等级从 0 起"), e.getMessage());
    }

    @Test
    @DisplayName("🔴 一次执行只查一次库：info + level + gradeAtLeast 连着调，等级只取一次")
    void 一次执行只查一次() {
        EngineContext context = contextOf(2);

        functions.info(context);
        functions.grade(context);
        functions.gradeAtLeast(context, 1);
        functions.info(context);

        // 缓存破了不会报错，只会让一次抽奖多打几次库 —— 这种事只能靠断言钉住
        verify(memberManager, times(1)).getById(anyLong());
        verify(memberGrowthService, times(1)).currentGrade(anyLong());
    }
}
