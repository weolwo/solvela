package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.admin.module.task.record.domain.form.TaskRecordUpdateForm;
import solvela.enums.TaskRecordStatusEnum;
import tools.jackson.databind.json.JsonMapper;
import solvela.exception.BusinessException;
import solvela.task.TaskRecord;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.record.service.TaskRecordService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务记录状态机在真库上走一遍：接取 → 条件 UPDATE 迁移 → 读回实体，以及管理端禁用只认进行中。
 *
 * <p>{@code TaskRecordStatusSqlTest} 只渲染 SQL、不连库；这里验的是 {@code ${...}} 在 Spring 装配的
 * MyBatis-Plus 配置下能求值并真的命中行，去掉 setter 之后状态仍被装配进来，
 * 以及「禁用」不再把已发奖的记录改成已过期。数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class TaskRecordStatusFlowTest {

    @Autowired
    private TaskRecordDao taskRecordDao;

    @Autowired
    private TaskRecordService taskRecordService;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("🔴 进行中 → 已完成 → 已发奖，每个闸门只抢得到一次")
    void 达标后发奖() {
        TaskRecord record = insert();
        assertEquals(TaskRecordStatusEnum.RUNNING, statusOf(record), "接取后读回的状态不对，或没装配上");

        assertEquals(0, taskRecordDao.markDispatched(record.getId()), "没达标不该能发奖");
        assertEquals(1, taskRecordDao.markCompleted(record.getId()));
        assertEquals(0, taskRecordDao.markCompleted(record.getId()), "达标只能认领一次");
        assertEquals(0, taskRecordDao.advanceMetric(record.getId(), BigDecimal.ONE), "已达标的记录不该再接受事件");
        assertEquals(1, taskRecordDao.markDispatched(record.getId()));
        assertEquals(TaskRecordStatusEnum.DISPATCHED, statusOf(record));
    }

    @Test
    @DisplayName("🔴 管理端禁用：进行中的置为已过期，已发奖的不动，并告诉运营哪些没改")
    void 禁用只认进行中() {
        TaskRecord running = insert();
        TaskRecord dispatched = insert();
        taskRecordDao.markCompleted(dispatched.getId());
        taskRecordDao.markDispatched(dispatched.getId());

        BusinessException e = assertThrows(BusinessException.class, () -> taskRecordService.updateStatus(
                List.of(running.getId(), dispatched.getId()), TaskRecordStatusEnum.EXPIRED));
        assertTrue(e.getMessage().contains(String.valueOf(dispatched.getId())), "没改动的记录要列出来：" + e.getMessage());

        assertEquals(TaskRecordStatusEnum.EXPIRED, statusOf(running));
        assertEquals(TaskRecordStatusEnum.DISPATCHED, statusOf(dispatched), "已发奖的记录被禁用改成了已过期");
    }

    /**
     * 管理端表单还会把列表行里的 status 一并提交上来（表单对象是从行数据拷的），
     * 而 {@code TaskRecordUpdateForm} 已经没有这个字段。这里确认 Spring 装配的 JsonMapper 忽略未知字段，
     * 否则「修改」整个接口会 400 —— 那比「改不了状态」严重得多。
     */
    @Test
    @DisplayName("修改表单多带一个 status 也能反序列化，且不会改到状态")
    void 修改表单忽略状态字段() throws Exception {
        TaskRecordUpdateForm form = jsonMapper.readValue("{\"id\":1,\"status\":2,\"memberId\":1}", TaskRecordUpdateForm.class);
        assertEquals(1L, form.getId());
    }

    private TaskRecordStatusEnum statusOf(TaskRecord record) {
        return taskRecordDao.selectById(record.getId()).getStatus();
    }

    private TaskRecord insert() {
        TaskRecord record = new TaskRecord();
        record.setMemberId(930001L);
        record.setTaskConfigId(System.nanoTime());
        record.setActivityCode("ZZTESTACT");
        record.setPeriodKey("NONE");
        record.setValidStartTime(LocalDateTime.now().minusDays(1));
        record.setValidEndTime(LocalDateTime.now().plusDays(10));
        record.setCurrentMetric(BigDecimal.ZERO);
        record.setVersion(0);
        record.setProgressData("{}");
        record.setRuleSnapshot("{}");
        record.setPrizeSnapshot("[]");
        record.start();
        taskRecordDao.insert(record);
        return record;
    }
}
