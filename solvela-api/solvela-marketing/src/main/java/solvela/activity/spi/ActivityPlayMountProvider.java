package solvela.activity.spi;

import solvela.enums.ActivityTypeEnum;
import solvela.scriptengine.spi.ScriptRefPoint;

/**
 * 「这个活动的玩法编排脚本挂在哪」——由各玩法自己回答。
 *
 * <h3>为什么要这个 SPI</h3>
 * 玩法编排脚本原本一律挂在 {@code ACTIVITY_PLAY} / 活动编码上。那对抽奖不合适：
 * 抽奖有自己的玩法配置（{@code t_draw_config}），脚本该挂在<b>抽奖配置</b>上，
 * 就像彩票脚本将来该挂在彩票配置上一样。
 *
 * <p>但玩法配置都在 {@code solvela-marketing}，而本模块（{@code solvela-activity}）
 * 是它的<b>上游</b>，反过来引不到。所以接口声明在这里、实现放在玩法那边，
 * 由 Spring 注入回来 —— 与 {@link ActivityRefProvider} 同一套依赖倒置。
 *
 * <p><b>没有实现的活动类型退回 {@code ACTIVITY_PLAY} / 活动编码</b>，
 * 也就是这个 SPI 出现之前的行为。BASIC 这种没有玩法配置的活动一直走这条路。
 */
public interface ActivityPlayMountProvider {

    /**
     * 本实现负责哪种活动类型
     */
    ActivityTypeEnum supportType();

    /**
     * 解析这个活动的玩法编排脚本挂在哪。
     *
     * @return 玩法配置还没建时返回 {@code null} —— 调用方会把它翻译成一个
     * 「配置没做完」的拒绝，而不是当成「没挂脚本」。两者的排查方向完全不同
     */
    PlayMount resolve(String activityCode);

    /**
     * 一个挂载位置：挂载点 + 业务对象编码。
     *
     * @param point 挂载点，如 {@code DRAW_PLAY}
     * @param refId 业务对象编码，如 {@code draw_code}
     */
    record PlayMount(ScriptRefPoint point, String refId) {
    }
}
