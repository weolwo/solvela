package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.draw.runtime.ActivityDrawFacade;
import solvela.marketing.api.DrawApi;
import solvela.marketing.api.DrawCmd;
import solvela.marketing.api.DrawResultView;

/**
 * {@link DrawApi} 的 HTTP 薄壳 —— <b>直调抽奖引擎</b>，收 poolCode。
 *
 * <h3>🔴 这条路由不能对公网开放</h3>
 * 它绕过玩法编排脚本，由调用方直接指定奖池。C 端要走的是
 * {@code ActivityApi.draw}（不含 poolCode，奖池由脚本算）。
 * 本端点留给内部工具与联调，入口层必须把 {@code /internal/**} 整体挡在外面。
 *
 * <p>同 {@link ActivityInternalController}：进程内有两个 {@code DrawApi} 类型的 bean，
 * 要注入就注入 {@link ActivityDrawFacade}。
 */
@RestController
@RequiredArgsConstructor
public class DrawInternalController implements DrawApi {

    private final ActivityDrawFacade activityDrawFacade;

    @Override
    public DrawResultView draw(DrawCmd cmd) {
        return activityDrawFacade.draw(cmd);
    }
}
