package net.lab1024.sa.consumer.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.EventRoute;
import net.lab1024.sa.consumer.strategy.PrizeStrategyFactory;
import net.lab1024.sa.domain.event.UserPrizeEvent;
import net.lab1024.sa.enums.EventCategoryEnum;
import net.lab1024.sa.prize.prizeconfig.domain.entity.PrizeConfig;
import net.lab1024.sa.prize.prizeconfig.service.PrizeConfigService;
import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.prize.prizelog.service.PrizeLogService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EventRoute(EventCategoryEnum.PRIZE)
public class PrizeDispatchHandler implements BizEventHandler<UserPrizeEvent> {

    @Resource
    private PrizeLogService prizeLogService;
    @Resource
    private PrizeConfigService prizeConfigService; // 【新增】用于自己查配置
    @Resource
    private PrizeStrategyFactory strategyFactory;

    @Override
    public void handle(UserPrizeEvent event) {
        log.info(">>>> [重型处理器] 收到派奖事件，单号: {}", event.getSourceBizId());

        // ==========================================
        // 1. 【核心变动】自己拿 prizeCode 去查最新配置
        // ==========================================
        PrizeConfig config = prizeConfigService.getByPrizeCode(event.getPrizeCode());

        // 极致防御：配置被删了，或者被停用了怎么办？
        if (config == null || config.getStatus() == 0) {
            log.error("【重大异常】奖品配置不存在或已停用，发奖终止！prizeCode: {}", event.getPrizeCode());
            // 建议这里记录一张异常表，或者直接发钉钉告警让运营补救
            return;
        }

        // 2. 构造 PrizeLog (结合 Event 的数据 和 Config 的数据)
        PrizeLog prizeLog = buildPrizeLog(event, config);

        try {
            prizeLogService.save(prizeLog);
        } catch (DuplicateKeyException e) {
            return; // 命中防重，丢弃
        }

        // 3. 审批拦截阀门 (此时用的就是自己查出来的 config.getApproveMode)
        if (config.getApproveMode() == 1) {
            log.info("【风控拦截】命中人工审批...");
            // 注意：这里可能需要计算 expire_time
            // prizeLog.setExpireTime(LocalDateTime.now().plusHours(config.getExpireHours()));
            return;
        }

        // 4. 自动免审的，直接放行
        //doDispatch(prizeLog);
    }

    private PrizeLog buildPrizeLog(UserPrizeEvent event, PrizeConfig config) {
        PrizeLog log = new PrizeLog();
        // 来自事件 (谁，什么单号)
        log.setMemberName(event.getMemberName());
        log.setExternalBizNo(event.getSourceBizId());

        // 来自自己查的配置 (怎么审，发什么)
        log.setPrizeType(config.getPrizeType());
        log.setApproveMode(config.getApproveMode());
        log.setApproveStatus(config.getApproveMode() == 1 ? 1 : 0);
        // ... 其他设值
        return log;
    }
}