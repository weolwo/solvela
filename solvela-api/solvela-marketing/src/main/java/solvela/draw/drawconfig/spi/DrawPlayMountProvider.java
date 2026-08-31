package solvela.draw.drawconfig.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.activity.spi.ActivityPlayMountProvider;
import solvela.draw.DrawConfig;
import solvela.draw.drawconfig.service.DrawConfigService;
import solvela.enums.ActivityTypeEnum;
import solvela.enums.EnableStatusEnum;
import solvela.scriptengine.spi.ScriptRefPoint;

/**
 * 抽奖活动的玩法编排脚本挂在<b>抽奖配置</b>上。
 *
 * <p>这是 {@link ActivityPlayMountProvider} 的第一个实现，也是它存在的理由：
 * 活动模块在上游，引不到抽奖配置，所以由这里注册回去。
 */
@Component
@RequiredArgsConstructor
public class DrawPlayMountProvider implements ActivityPlayMountProvider {

    private final DrawConfigService drawConfigService;

    @Override
    public ActivityTypeEnum supportType() {
        return ActivityTypeEnum.DRAW;
    }

    /**
     * <p>🔴 <b>抽奖配置被关闭时也返回 null</b>，与「还没建」走同一个拒绝。
     * 对用户是同一件事（现在抽不了），而对运营，「关掉了」本来就是他自己关的。
     * 让一个被关闭的配置照样把脚本跑起来，才是真正的意外。
     */
    @Override
    public PlayMount resolve(String activityCode) {
        DrawConfig config = drawConfigService.getByActivityCode(activityCode);
        if (config == null || config.getStatus() != EnableStatusEnum.ENABLED) {
            return null;
        }
        return new PlayMount(ScriptRefPoint.DRAW_PLAY, config.getDrawCode());
    }
}
