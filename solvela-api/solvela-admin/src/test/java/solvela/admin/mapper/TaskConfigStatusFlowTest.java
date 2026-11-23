package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.TaskConfigStatusEnum;
import solvela.exception.BusinessException;
import solvela.task.TaskConfig;
import solvela.task.taskconfig.dao.TaskConfigDao;
import solvela.task.taskconfig.service.TaskConfigService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 任务配置上下线在真库上走一遍，经 Service 入口：下线、重复下线跳过、再上线、拒绝写「生效中」。
 * 数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class TaskConfigStatusFlowTest {

    @Autowired
    private TaskConfigService service;
    @Autowired
    private TaskConfigDao dao;

    @Test
    @DisplayName("🔴 下线 → 重复下线跳过 → 再上线；目标是生效中直接拒绝")
    void 上下线() {
        TaskConfig config = insert();
        assertEquals(TaskConfigStatusEnum.PENDING, statusOf(config), "落库后读回的状态不对，或没装配上");

        service.updateStatus(List.of(config.getId()), TaskConfigStatusEnum.OFFLINE);
        service.updateStatus(List.of(config.getId()), TaskConfigStatusEnum.OFFLINE);
        assertEquals(TaskConfigStatusEnum.OFFLINE, statusOf(config));
        assertEquals(0, dao.markOffline(config.getId()), "已下线的又被下线了一次");

        service.updateStatus(List.of(config.getId()), TaskConfigStatusEnum.PENDING);
        assertEquals(TaskConfigStatusEnum.PENDING, statusOf(config));

        assertThrows(BusinessException.class, () -> service.updateStatus(List.of(config.getId()), TaskConfigStatusEnum.ACTIVE));
        assertEquals(TaskConfigStatusEnum.PENDING, statusOf(config));
    }

    private TaskConfigStatusEnum statusOf(TaskConfig config) {
        return dao.selectById(config.getId()).getStatus();
    }

    private TaskConfig insert() {
        TaskConfig config = new TaskConfig();
        config.setActivityCode("ZZTESTACT");
        config.setTaskName("状态机验收");
        config.setTemplateCode("ZZTPL");
        config.setTriggerEvent("ZZ_EVENT");
        config.setRuleConfig("{}");
        config.startPending();
        dao.insert(config);
        return config;
    }
}
