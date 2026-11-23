package solvela.task;

import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.TaskConfigStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskConfigTest {

    @Test
    @DisplayName("落为待生效；不能再定一次；没有 setter 时 MyBatis 仍能写入 status")
    void 初始状态与装配() {
        TaskConfig config = new TaskConfig();
        config.startPending();
        assertEquals(TaskConfigStatusEnum.PENDING, config.getStatus());
        assertThrows(IllegalStateException.class, config::startPending);
        assertTrue(SystemMetaObject.forObject(new TaskConfig()).hasSetter("status"),
                "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
    }
}
