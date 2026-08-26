package solvela.risk.engine;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class RiskChainEngine implements SmartInitializingSingleton {

    @Resource
    private List<IRiskFilter> filters;

    public RiskResult execute(RiskContext context) {
        String bizId = context.getRequest().getSourceBizId();

        for (IRiskFilter filter : filters) {
            RiskResult result = filter.doFilter(context);
            if (!result.isPassed()) {
                log.warn("【风控引擎拦截】单号: {}, 拦截规则: [{}], 原因: {}",
                        bizId, result.getRuleCode(), result.getReason());
                // 工业级精髓：一旦失败，立刻阻断（Fast-Fail），后续规则不再执行！
                return result;
            }
        }

        return RiskResult.pass();
    }

    @Override
    public void afterSingletonsInstantiated() {
        // 项目启动时，根据 getOrder() 对拦截器进行升序排序
        filters.sort(Comparator.comparingInt(IRiskFilter::getOrder));
        log.info("【风控引擎】已加载 {} 个风控规则节点", filters.size());
    }
}
