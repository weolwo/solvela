package solvela.admin.enums;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.enums.EnableStatusEnum;
import solvela.enums.TaskConfigStatusEnum;
import solvela.enums.TaskFlowTypeEnum;
import solvela.enums.TaskRecordStatusEnum;
import solvela.task.TaskConfig;
import solvela.task.TaskEvent;
import solvela.task.TaskRecord;
import solvela.task.TaskRecordFlow;
import solvela.task.TaskTemplate;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.recordflow.dao.TaskRecordFlowDao;
import solvela.task.taskconfig.dao.TaskConfigDao;
import solvela.task.taskevent.dao.TaskEventDao;
import solvela.task.tasktemplate.dao.TaskTemplateDao;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务中台五个状态列枚举化之后的真实验收（连数据库，只读）。
 *
 * <p>{@code t_task_record_flow} 有 5369 行（推进 4693 / 丢弃 676），
 * 是全库数据量最大的一张状态表 —— 这一条最有说服力。流水表只做 count，不整表拉进内存。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class TaskEnumMappingTest {

    @Autowired
    private TaskConfigDao taskConfigDao;

    @Autowired
    private TaskEventDao taskEventDao;

    @Autowired
    private TaskTemplateDao taskTemplateDao;

    @Autowired
    private TaskRecordDao taskRecordDao;

    @Autowired
    private TaskRecordFlowDao taskRecordFlowDao;

    @Test
    @DisplayName("任务配置：status 能装配；运行态判据是 != OFFLINE 而不是 == ACTIVE")
    void 任务配置装配() {
        List<TaskConfig> list = taskConfigDao.selectList(null);
        assertFalse(list.isEmpty(), "t_task_config 没有数据，这条用例失去意义");
        for (TaskConfig e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }

        // 全工程没有任何地方把 status 从 PENDING 改成 ACTIVE。这条断言把这个事实钉住：
        // 一旦哪天真出现 ACTIVE 数据，说明有人加了「启用」接口，
        // 那 TaskEventService 的 != OFFLINE 订阅判据就该一起复审。
        assertTrue(list.stream().noneMatch(e -> e.getStatus() == TaskConfigStatusEnum.ACTIVE),
                "出现了 ACTIVE 的任务配置 —— 请复审 TaskEventService 的订阅判据是否还成立");
    }

    @Test
    @DisplayName("事件定义与模板：共用 EnableStatusEnum")
    void 事件与模板装配() {
        List<TaskEvent> events = taskEventDao.selectList(null);
        assertFalse(events.isEmpty(), "t_task_event 没有数据");
        for (TaskEvent e : events) {
            assertNotNull(e.getStatus(), "TaskEvent.status 装配成了 null");
        }
        assertTrue(events.stream().anyMatch(e -> e.getStatus() == EnableStatusEnum.ENABLED));

        List<TaskTemplate> templates = taskTemplateDao.selectList(null);
        assertFalse(templates.isEmpty(), "t_task_template 没有数据");
        for (TaskTemplate e : templates) {
            assertNotNull(e.getStatus(), "TaskTemplate.status 装配成了 null");
        }
        assertTrue(templates.stream().anyMatch(e -> e.getStatus() == EnableStatusEnum.ENABLED));
    }

    @Test
    @DisplayName("任务记录：有序状态与 atLeast 语义")
    void 任务记录装配() {
        List<TaskRecord> list = taskRecordDao.selectList(null);
        assertFalse(list.isEmpty(), "t_task_record 没有数据，这条用例失去意义");
        for (TaskRecord e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }

        // atLeast 是有序语义的唯一入口，顺带钉住它的行为
        assertTrue(TaskRecordStatusEnum.DISPATCHED.atLeast(TaskRecordStatusEnum.COMPLETED));
        assertTrue(TaskRecordStatusEnum.COMPLETED.atLeast(TaskRecordStatusEnum.COMPLETED));
        assertFalse(TaskRecordStatusEnum.RUNNING.atLeast(TaskRecordStatusEnum.COMPLETED));

        // 库里是 0×2257 / 2×840
        long reached = list.stream()
                .filter(e -> e.getStatus().atLeast(TaskRecordStatusEnum.COMPLETED)).count();
        assertTrue(reached > 0, "一条达标的任务记录都没有");
    }

    @Test
    @DisplayName("流水类型：推进要远多于丢弃，且分类型计数之和等于总量")
    void 流水类型装配() {
        long advance = countFlow(TaskFlowTypeEnum.ADVANCE);
        long discard = countFlow(TaskFlowTypeEnum.DISCARD);

        assertTrue(advance > 0, "一条进度推进流水都没有");
        assertTrue(discard > 0, "一条丢弃流水都没有 —— 丢弃也要落库，否则排查只能翻日志");
        assertTrue(advance > discard,
                "推进(" + advance + ") 不比丢弃(" + discard + ") 多，取值口径多半反了");

        Long total = taskRecordFlowDao.selectCount(new LambdaQueryWrapper<>());
        assertNotNull(total);
        assertEquals(total.longValue(), advance + discard,
                "分类型计数之和与总量对不上，说明有行的 flow_type 落在枚举之外");
    }

    private long countFlow(TaskFlowTypeEnum flowType) {
        Long n = taskRecordFlowDao.selectCount(
                new LambdaQueryWrapper<TaskRecordFlow>().eq(TaskRecordFlow::getFlowType, flowType));
        assertNotNull(n);
        return n;
    }
}
