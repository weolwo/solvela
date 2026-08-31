package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.activity.runtime.ActivityFacade;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.ActivityDrawCmd;
import solvela.marketing.api.ActivityRuleView;
import solvela.marketing.api.DrawResultView;

/**
 * {@link ActivityApi} 的 HTTP 薄壳。
 *
 * <h3>为什么 implements 接口，而不是自己写 @PostMapping</h3>
 * Spring MVC 认得接口上的 {@code @HttpExchange}，所以<b>路径与方法只在契约里定义一次</b>。
 * 自己写一遍映射的话，网关侧的客户端代理和这里的服务端映射就是两份，
 * 改一处忘另一处 —— 表现是 404，而且要等到联调才发现。
 *
 * <p>方法体全部是一行转发。<b>这里不许出现任何业务判断</b>：
 * 一旦开始写 if，就等于让「跑在哪个进程里」影响了业务行为，
 * 而同一段逻辑在 admin 进程里是直接调 {@link ActivityFacade} 的。
 *
 * <h3>⚠️ 本进程里有两个 ActivityApi 类型的 bean</h3>
 * 本类和 {@link ActivityFacade} 都实现了它。所以<b>进程内不要按 ActivityApi 类型注入</b>，
 * 要注入就注入 {@code ActivityFacade}（像本类这样）。
 * 按接口注入的是<b>网关</b>，那边只有 HTTP 代理一个实现，不存在歧义。
 */
@RestController
@RequiredArgsConstructor
public class ActivityInternalController implements ActivityApi {

    private final ActivityFacade activityFacade;

    @Override
    public ActivityRuleView getActivityRule(String activityCode) {
        return activityFacade.getActivityRule(activityCode);
    }

    @Override
    public DrawResultView draw(ActivityDrawCmd cmd) {
        return activityFacade.draw(cmd);
    }
}
