package solvela.admin.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.activity.ActivityConfig;
import solvela.activity.dao.ActivityConfigDao;
import solvela.activity.domain.command.ActivityConfigAddCommand;
import solvela.activity.domain.command.ActivityConfigUpdateCommand;
import solvela.activity.service.ActivityConfigService;
import solvela.base.util.SolvelaCodeUtil;
import solvela.enums.ActivityStatusEnum;
import solvela.enums.ActivityTypeEnum;
import solvela.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 活动状态在真库上走一遍：新建 → 上下线 → 改资料 → 删除，经 Service 入口。
 *
 * <p>{@code ActivityConfigStatusSqlTest} 只渲染 SQL、不连库；这里验的是 {@code ${...}} 在 Spring 装配的
 * MyBatis-Plus 配置下能求值并真的命中行，以及两条以前能被绕过的规则：新建一律未开始、上过线的活动删不掉。
 * 用 BASIC 类型：它不挂玩法，上线前完备度检查天然通过。数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class ActivityConfigStatusFlowTest {

    @Autowired
    private ActivityConfigService service;
    @Autowired
    private ActivityConfigDao dao;

    @Test
    @DisplayName("🔴 新建一律未开始；上线后改资料不动状态；上过线的活动删不掉")
    void 新建上线删除() {
        ActivityConfig activity = add();
        assertEquals(ActivityStatusEnum.NOT_START, activity.getStatus(), "新建应由服务端定为未开始");

        service.updateStatus(List.of(activity.getId()), ActivityStatusEnum.ONLINE);
        service.updateStatus(List.of(activity.getId()), ActivityStatusEnum.ONLINE);
        assertEquals(ActivityStatusEnum.ONLINE, statusOf(activity), "重复上线应当跳过而不是报错");

        ActivityConfigUpdateCommand update = new ActivityConfigUpdateCommand();
        update.setId(activity.getId());
        update.setActivityName("改了名字");
        update.setStartTime(activity.getStartTime());
        update.setEndTime(activity.getEndTime());
        service.update(update);
        assertEquals(ActivityStatusEnum.ONLINE, statusOf(activity), "改资料不该动状态");

        assertThrows(BusinessException.class, () -> service.delete(activity.getId()));
        service.updateStatus(List.of(activity.getId()), ActivityStatusEnum.OFFLINE);
        assertThrows(BusinessException.class, () -> service.delete(activity.getId()), "下线的活动也上过线，不能删");
        assertNotNull(dao.selectById(activity.getId()));
    }

    @Test
    @DisplayName("闸门：未开始可直接下线；任何状态都回不到未开始；删除只删未开始")
    void 闸门() {
        ActivityConfig neverOnline = add();
        assertEquals(1, dao.markOffline(neverOnline.getId()), "未开始应当可以直接下线");
        assertEquals(0, dao.markOffline(neverOnline.getId()));
        assertEquals(1, dao.markOnline(neverOnline.getId()));
        assertEquals(0, dao.deleteNotStarted(neverOnline.getId()), "上过线的活动被删了");

        ActivityConfig fresh = add();
        service.delete(fresh.getId());
        assertEquals(null, dao.selectById(fresh.getId()));
    }

    private ActivityStatusEnum statusOf(ActivityConfig activity) {
        return dao.selectById(activity.getId()).getStatus();
    }

    private ActivityConfig add() {
        String code = SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.ACTIVITY,
                c -> service.getByActivityCode(c) != null);
        ActivityConfigAddCommand command = new ActivityConfigAddCommand();
        command.setActivityCode(code);
        command.setActivityName("状态机验收");
        command.setActivityType(ActivityTypeEnum.BASIC);
        command.setStartTime(LocalDateTime.now().minusDays(1));
        command.setEndTime(LocalDateTime.now().plusDays(1));
        service.add(command);
        return service.getByActivityCode(code);
    }
}
