package solvela.admin.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.MemberOperationLimitStatusEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;
import solvela.member.MemberOperationLimit;
import solvela.member.operationlimit.dao.MemberOperationLimitDao;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code t_member_operation_limit} 三列枚举化的验收。
 *
 * <p><b>这个类和其他 *EnumMappingTest 不一样：它会写库。</b>
 * 那几个都是只读的，靠「分状态计数之和 == 总量」在存量数据上验装配；
 * 这张表是<b>零行</b>的，只读什么也验不到 —— 查询恒返回空集，
 * 就算 0/1 装反了用例照样绿。所以这里自己插一行再读回来，
 * {@code @Transactional + @Rollback} 保证跑完不留痕。
 *
 * <p>要验的东西有两层：
 * <ol>
 *   <li>实体的三个枚举字段能写进去、再原样读回来；</li>
 *   <li>枚举当作 {@code @Param} 传进<b>手写 SQL</b> 的 {@code #{operationType}}
 *       时走的是 MybatisEnumTypeHandler（按 value），不是 toString。
 *       这条尤其要验 —— 前面几轮改的都是 LambdaQueryWrapper 那条路径，
 *       手写 @Select/@Update 是另一条，没验过。</li>
 * </ol>
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class OperationLimitEnumMappingTest {

    /** 用一个不可能撞上真实会员的 id，即使回滚失效也不会污染业务数据 */
    private static final Long FAKE_MEMBER_ID = -99999L;

    @Autowired
    private MemberOperationLimitDao dao;

    @Test
    @Transactional
    @Rollback
    @DisplayName("写进去再读回来：三个枚举字段原样往返")
    void 往返() {
        LocalDateTime now = LocalDateTime.now();
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.setMemberId(FAKE_MEMBER_ID);
        limit.setOperationType(MemberOperationTypeEnum.CHANGE_PASSWORD);
        limit.setLockTime(now);
        limit.setExpireTime(now.plusHours(1));
        limit.setStatus(MemberOperationLimitStatusEnum.LOCKED);
        limit.setReason("枚举往返用例");
        limit.setCreateTime(now);
        limit.setUpdateTime(now);
        dao.insert(limit);
        assertNotNull(limit.getId(), "插入后没回填主键");

        MemberOperationLimit read = dao.selectById(limit.getId());
        assertNotNull(read);
        // 取值分别是 2 和 0。如果 TypeHandler 落成了按枚举名，这里读出来会直接抛
        assertEquals(MemberOperationTypeEnum.CHANGE_PASSWORD, read.getOperationType());
        assertEquals(MemberOperationLimitStatusEnum.LOCKED, read.getStatus());
        // status=0 时 unlock_type 为 NULL，是 DDL 定的
        assertNull(read.getUnlockType(), "还没解冻，unlock_type 就该是 null");
    }

    @Test
    @Transactional
    @Rollback
    @DisplayName("枚举当 @Param 传进手写 SQL：selectActive 与 unlock 都按 value 匹配")
    void 手写SQL的枚举形参() {
        LocalDateTime now = LocalDateTime.now();
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.setMemberId(FAKE_MEMBER_ID);
        limit.setOperationType(MemberOperationTypeEnum.LOGIN);
        limit.setLockTime(now);
        limit.setExpireTime(now.plusHours(1));
        limit.setStatus(MemberOperationLimitStatusEnum.LOCKED);
        limit.setReason("枚举形参用例");
        limit.setCreateTime(now);
        limit.setUpdateTime(now);
        dao.insert(limit);

        // ① selectActive：operation_type = #{operationType} 得按 1 去匹配，不是按 "LOGIN"
        MemberOperationLimit active = dao.selectActive(
                FAKE_MEMBER_ID, MemberOperationTypeEnum.LOGIN, now);
        assertNotNull(active, "按枚举查生效中的限制没查到 —— #{operationType} 多半没走 value");
        assertEquals(limit.getId(), active.getId());

        // ② 换一个操作类型就该查不到，证明上面那条不是「恰好每行都返回」
        assertNull(dao.selectActive(FAKE_MEMBER_ID, MemberOperationTypeEnum.CHANGE_PASSWORD, now),
                "换了操作类型还能查到，说明 operation_type 这个条件根本没生效");

        // ③ unlock：写入 unlock_type，同样是手写 SQL 的枚举形参
        int rows = dao.unlock(FAKE_MEMBER_ID, MemberOperationTypeEnum.LOGIN,
                now, MemberOperationUnlockTypeEnum.MANUAL, "测试", "枚举形参用例");
        assertEquals(1, rows);

        MemberOperationLimit unlocked = dao.selectById(limit.getId());
        assertEquals(MemberOperationLimitStatusEnum.UNLOCKED, unlocked.getStatus());
        assertEquals(MemberOperationUnlockTypeEnum.MANUAL, unlocked.getUnlockType());

        // ④ 解冻后就不该再是「生效中」—— unlock 的 status=0 条件也一并验了
        assertNull(dao.selectActive(FAKE_MEMBER_ID, MemberOperationTypeEnum.LOGIN, now));
    }
}
