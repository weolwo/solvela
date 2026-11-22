package solvela.task;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.TaskRecordStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRecordTest {

    @Test
    @DisplayName("接取为进行中；不能再定一次")
    void 接取() {
        TaskRecord record = new TaskRecord();
        record.start();
        assertEquals(TaskRecordStatusEnum.RUNNING, record.getStatus());
        assertThrows(IllegalStateException.class, record::start);
    }

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        TaskRecord record = new TaskRecord();
        MetaObject meta = SystemMetaObject.forObject(record);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", TaskRecordStatusEnum.DISPATCHED);
        assertEquals(TaskRecordStatusEnum.DISPATCHED, record.getStatus());
    }
}
