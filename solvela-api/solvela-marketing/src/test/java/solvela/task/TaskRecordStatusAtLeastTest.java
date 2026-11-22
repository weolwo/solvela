package solvela.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.TaskRecordStatusEnum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TaskRecordStatusEnum#atLeast} 的语义：进行中 &lt; 已完成 &lt; 已发奖，已过期不在链上。
 *
 * <p>放在营销模块而不是 contract：contract 没有测试依赖，任务记录的使用方在这边。
 */
class TaskRecordStatusAtLeastTest {

    @Test
    @DisplayName("达标链：已发奖、已完成算达标，进行中不算")
    void 达标链() {
        assertTrue(TaskRecordStatusEnum.DISPATCHED.atLeast(TaskRecordStatusEnum.COMPLETED));
        assertTrue(TaskRecordStatusEnum.COMPLETED.atLeast(TaskRecordStatusEnum.COMPLETED));
        assertFalse(TaskRecordStatusEnum.RUNNING.atLeast(TaskRecordStatusEnum.COMPLETED));
        assertTrue(TaskRecordStatusEnum.RUNNING.atLeast(TaskRecordStatusEnum.RUNNING));
    }

    @Test
    @DisplayName("🔴 已过期不算达到任何一档 —— 只按数值比的话它比已完成大")
    void 已过期不算达标() {
        assertFalse(TaskRecordStatusEnum.EXPIRED.atLeast(TaskRecordStatusEnum.COMPLETED));
        assertFalse(TaskRecordStatusEnum.EXPIRED.atLeast(TaskRecordStatusEnum.DISPATCHED));
        assertFalse(TaskRecordStatusEnum.EXPIRED.atLeast(TaskRecordStatusEnum.RUNNING));
    }

    @Test
    @DisplayName("拿已过期当门槛是调用方写错了，直接抛错")
    void 已过期不能当门槛() {
        assertThrows(IllegalArgumentException.class,
                () -> TaskRecordStatusEnum.DISPATCHED.atLeast(TaskRecordStatusEnum.EXPIRED));
    }
}
