package solvela.admin.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.dao.PrizePoolConfigDao;
import solvela.draw.poolconfig.service.PrizePoolConfigService;
import solvela.draw.poolconfig.spi.DrawActivityRefProvider;
import solvela.enums.PrizePoolStatusEnum;
import solvela.exception.BusinessException;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 奖池开关在真库上走一遍：禁用 / 启用经 Service 入口，复制活动时开关照抄源奖池。
 *
 * <p>复制那条要紧：status 没有 setter，{@code SolvelaBeanUtil.copy} 会跳过它，漏写的话按 DDL 默认值 1 落成开启 ——
 * 被运营关掉的池（比如配错了）复制过去又开了，而且不报错。数据由用例自己造，结束时回滚。
 */
@SpringBootTest
@Transactional
class PrizePoolStatusFlowTest {

    private static final String SOURCE = "ZZPOOLSRC1";
    private static final String TARGET = "ZZPOOLDST1";

    @Autowired
    private PrizePoolConfigService service;
    @Autowired
    private PrizePoolConfigDao dao;
    @Autowired
    private DrawActivityRefProvider provider;

    @Test
    @DisplayName("禁用 → 重复禁用被拒 → 启用；闸门重复执行影响 0 行")
    void 开关() {
        PrizePoolConfig pool = insert(SOURCE, "ZZP" + System.nanoTime() % 100000000L);
        assertEquals(PrizePoolStatusEnum.OPEN, dao.selectById(pool.getId()).getStatus(), "落库后读回的状态不对，或没装配上");

        service.offline(pool.getId());
        assertThrows(BusinessException.class, () -> service.offline(pool.getId()));
        assertEquals(0, dao.markClosed(pool.getId()));
        service.online(pool.getId());
        assertEquals(PrizePoolStatusEnum.OPEN, dao.selectById(pool.getId()).getStatus());
    }

    @Test
    @DisplayName("🔴 复制活动：关着的源奖池复制过去仍是关的，开着的仍是开的")
    void 复制照抄开关() {
        PrizePoolConfig open = insert(SOURCE, "ZZO" + System.nanoTime() % 100000000L);
        PrizePoolConfig closed = insert(SOURCE, "ZZC" + System.nanoTime() % 100000000L);
        assertEquals(1, dao.markClosed(closed.getId()));

        provider.copyTo(SOURCE, TARGET, Map.of());

        Map<String, PrizePoolStatusEnum> byName = dao.selectList(Wrappers.<PrizePoolConfig>lambdaQuery()
                        .eq(PrizePoolConfig::getActivityCode, TARGET)).stream()
                .collect(Collectors.toMap(PrizePoolConfig::getPoolName, PrizePoolConfig::getStatus));
        assertEquals(PrizePoolStatusEnum.OPEN, byName.get(open.getPoolName()));
        assertEquals(PrizePoolStatusEnum.CLOSED, byName.get(closed.getPoolName()), "关着的源奖池复制过去被打开了");
    }

    private PrizePoolConfig insert(String activityCode, String poolCode) {
        PrizePoolConfig pool = new PrizePoolConfig();
        pool.setActivityCode(activityCode);
        pool.setPoolCode(poolCode);
        pool.setPoolName("验收-" + poolCode);
        pool.startOpen();
        dao.insert(pool);
        return pool;
    }
}
