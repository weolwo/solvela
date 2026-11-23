package solvela.activity;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.ActivityStatusEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityConfigTest {

    @Test
    @DisplayName("落为未开始；不能再定一次")
    void 落未开始() {
        ActivityConfig activity = new ActivityConfig();
        activity.awaitLaunch();
        assertEquals(ActivityStatusEnum.NOT_START, activity.getStatus());
        assertThrows(IllegalStateException.class, activity::awaitLaunch);
    }

    /** 🔴 status 没有 setter，读库时 MyBatis 必须还能把它填进来；装配不上时是静默的 null */
    @Test
    @DisplayName("🔴 没有 setter，MyBatis 读库时仍能写入 status")
    void MyBatis仍能写入状态() {
        ActivityConfig activity = new ActivityConfig();
        MetaObject meta = SystemMetaObject.forObject(activity);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", ActivityStatusEnum.ONLINE);
        assertEquals(ActivityStatusEnum.ONLINE, activity.getStatus());
    }
}
