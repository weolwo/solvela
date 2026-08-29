package solvela.admin.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.ProposalStatusEnum;
import solvela.risk.ProposalRecord;
import solvela.risk.proposal.dao.ProposalRecordDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提案状态机枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>t_proposal_record 有 1130 行，是这一批数据量最大的表。
 * 取值只有 50(成功) 与 80(风控拦截) 两种 —— 中间态（10/11/30/40）是<b>瞬时</b>的，
 * 正常情况下不会在库里静止，这本身就是状态机健康的信号。
 *
 * <p>本测试<b>只读</b>：不造数、不改库。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ProposalEnumMappingTest {

    @Autowired
    private ProposalRecordDao proposalRecordDao;

    @Test
    @DisplayName("提案：不连号的状态值（0/10/11/20/30/40/50/60/70/80）都能装配")
    void 提案装配() {
        List<ProposalRecord> list = proposalRecordDao.selectList(null);
        assertFalse(list.isEmpty(), "t_proposal_record 没有数据，这条用例失去意义");
        for (ProposalRecord e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null —— 不连号的取值多半没落进枚举");
        }
    }

    @Test
    @DisplayName("提案：成功远多于风控拦截，口径反了这条会立刻红")
    void 终态分布() {
        List<ProposalRecord> list = proposalRecordDao.selectList(null);
        assertFalse(list.isEmpty());

        long success = list.stream().filter(e -> e.getStatus() == ProposalStatusEnum.SUCCESS).count();
        long blocked = list.stream().filter(e -> e.getStatus() == ProposalStatusEnum.RISK_BLOCKED).count();

        assertTrue(success > 0, "一条成功的提案都没有");
        assertTrue(blocked > 0, "一条风控拦截都没有 —— 那条记录是合规审计和客诉排查的唯一证据");
        assertTrue(success > blocked, "成功(" + success + ") 不比风控拦截(" + blocked + ") 多，取值口径多半反了");
    }

    @Test
    @DisplayName("提案：按枚举分状态查询的总数要等于总量")
    void 按状态过滤() {
        List<ProposalRecord> all = proposalRecordDao.selectList(null);
        assertFalse(all.isEmpty());

        int sum = 0;
        for (ProposalStatusEnum status : ProposalStatusEnum.values()) {
            List<ProposalRecord> hit = proposalRecordDao.selectList(
                    new LambdaQueryWrapper<ProposalRecord>().eq(ProposalRecord::getStatus, status));
            for (ProposalRecord e : hit) {
                assertEquals(status, e.getStatus(), "按 " + status + " 查询却查出了 " + e.getStatus());
            }
            sum += hit.size();
        }
        assertEquals(all.size(), sum, "分状态查询的总数与总量对不上，说明有行的 status 落在枚举之外");
    }

    @Test
    @DisplayName("风控拦截的记录必须带 risk_code，其它状态不该有")
    void 风控码只在拦截态出现() {
        List<ProposalRecord> list = proposalRecordDao.selectList(null);
        assertFalse(list.isEmpty());

        for (ProposalRecord e : list) {
            if (e.getStatus() == ProposalStatusEnum.RISK_BLOCKED) {
                assertNotNull(e.getRiskCode(),
                        "提案 " + e.getId() + " 是风控拦截却没有 risk_code，审计链断了");
            } else {
                assertEquals(null, e.getRiskCode(),
                        "提案 " + e.getId() + " 状态是 " + e.getStatus() + " 却带着 risk_code");
            }
        }
    }
}
