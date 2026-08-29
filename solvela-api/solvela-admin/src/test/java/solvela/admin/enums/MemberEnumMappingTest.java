package solvela.admin.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.GenderEnum;
import solvela.enums.MemberStatusEnum;
import solvela.enums.MemberVerifyStatusEnum;
import solvela.member.Member;
import solvela.member.MemberVerify;
import solvela.member.dao.MemberDao;
import solvela.member.verify.dao.MemberVerifyDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会员状态与实名认证状态枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>{@code t_member} 有 3392 行，是 C 端登录链路上的第一道闸门。
 *
 * <p>⚠️ {@code t_member_verify} 在对账时是<b>零行</b>，本类里对它只能验到「查询不炸」，
 * 验不到真实数据的装配 —— 这一点在对账报告的「零行，风险未覆盖」一节里也标着。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class MemberEnumMappingTest {

    @Autowired
    private MemberDao memberDao;

    @Autowired
    private MemberVerifyDao memberVerifyDao;

    @Test
    @DisplayName("会员状态：3392 行全部能装配")
    void 会员状态装配() {
        List<Member> list = memberDao.selectList(null);
        assertFalse(list.isEmpty(), "t_member 没有数据，这条用例失去意义");
        for (Member e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
        // 库里全是 1-正常
        assertTrue(list.stream().anyMatch(e -> e.getStatus() == MemberStatusEnum.NORMAL));
    }

    @Test
    @DisplayName("会员状态：分状态计数之和等于总量")
    void 按状态过滤会员() {
        Long total = memberDao.selectCount(new LambdaQueryWrapper<>());
        assertNotNull(total);

        long sum = 0;
        for (MemberStatusEnum status : MemberStatusEnum.values()) {
            Long n = memberDao.selectCount(
                    new LambdaQueryWrapper<Member>().eq(Member::getStatus, status));
            assertNotNull(n);
            sum += n;
        }
        assertEquals(total.longValue(), sum,
                "分状态计数之和与总量对不上，说明有行的 status 落在枚举之外");
    }

    @Test
    @DisplayName("性别：3392 行全部能装配，且分性别计数之和等于总量")
    void 会员性别() {
        // gender 从来没进过对账报告 —— 那份报告是按「列名像不像 status」筛的，
        // gender 不像，于是躲过了整轮改造，直到最后回查实体字段类型才翻出来。
        List<Member> list = memberDao.selectList(null);
        assertFalse(list.isEmpty(), "t_member 没有数据，这条用例失去意义");
        for (Member e : list) {
            assertNotNull(e.getGender(), "gender 装配成了 null");
        }

        Long total = memberDao.selectCount(new LambdaQueryWrapper<>());
        assertNotNull(total);
        long sum = 0;
        for (GenderEnum gender : GenderEnum.values()) {
            Long n = memberDao.selectCount(new LambdaQueryWrapper<Member>().eq(Member::getGender, gender));
            assertNotNull(n);
            sum += n;
        }
        assertEquals(total.longValue(), sum,
                "分性别计数之和与总量对不上，说明有行的 gender 落在枚举之外");
    }

    @Test
    @DisplayName("实名认证：零行，只能验到查询链路不炸")
    void 实名认证查询不炸() {
        // 这张表当前没有数据。断言写成「查得动、且返回的每一行都能装配」，
        // 而不是断言某个具体取值 —— 那样等有数据了才会开始有意义，现在不会假绿。
        List<MemberVerify> list = memberVerifyDao.selectList(null);
        for (MemberVerify e : list) {
            assertNotNull(e.getVerifyStatus(), "verifyStatus 装配成了 null");
        }

        // 按枚举过滤也要能下推（这条即使零行也能证明 SQL 没报错）
        for (MemberVerifyStatusEnum status : MemberVerifyStatusEnum.values()) {
            Long n = memberVerifyDao.selectCount(
                    new LambdaQueryWrapper<MemberVerify>().eq(MemberVerify::getVerifyStatus, status));
            assertNotNull(n, "按 " + status + " 过滤时 SQL 出错");
        }
    }
}
