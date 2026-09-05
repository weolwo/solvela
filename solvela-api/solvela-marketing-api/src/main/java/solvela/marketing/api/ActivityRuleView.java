package solvela.marketing.api;

import solvela.enums.ActivityStatusEnum;

import java.time.LocalDateTime;

/**
 * C 端看到的活动：基础信息 + 时间 + 状态 + 展示配置 + 规则正文。
 *
 * <h3>为什么不直接复用 ActivityConfigDTO</h3>
 * 那是<b>后台视角</b>的对象，带着 createBy / updateBy / 主键 id。下发给 C 端有两个后果：
 * 一是白送出运营的账号名，二是这些字段一旦被前端用上就成了事实契约，后台想改都改不动。
 * 这十几行转换正是网关这一层存在的理由。
 *
 * <h3>⚠️ ruleContent 可能很大</h3>
 * {@code t_activity_display.rule_content} 是 mediumtext，运营能写到 200KB，
 * 而它跟着每次打开详情页走一遍。目前按「一次调用拿全」的约定放在这里；
 * 如果哪天线上看到这个接口的响应体异常大，第一个该拆出去的就是它。
 *
 * @param activityCode 活动编码。<b>对外一律用它寻址，不用 id</b> —— 整个活动域都是这个约定
 * @param activityName 活动名称
 * @param activityType 玩法类型 BASIC/DRAW/TASK/LOTTERY
 * @param status       活动状态。<b>如实下发</b> —— 未开始和已下线要给用户看不同的话
 * @param startTime    开始时间
 * @param dataEndTime  <b>数据截止时间</b>：此刻起不再受理参与，但已中的奖仍可领到 endTime。
 *                     为空表示与 endTime 相同。判据见类注释下方的时间窗
 * @param endTime      活动结束时间
 * @param subTitle     副标题
 * @param themeColor   主题色
 * @param mainImageId  主图 file_id。<b>给的是 id 不是 URL</b>：URL 带签名、有有效期，
 *                     该由取图那一刻现算，缓存住一个过期 URL 是很难查的一类问题
 * @param bgImageId    背景图 file_id
 * @param shareImageId 分享图 file_id
 * @param shareTitle   分享标题
 * @param shareDesc    分享描述
 * @param extraConfig  扩展配置 JSON 原文，前端自己解析
 * @param ruleContent  规则正文（富文本 HTML），没配为 null
 */
public record ActivityRuleView(
        String activityCode,
        String activityName,
        String activityType,
        ActivityStatusEnum status,
        LocalDateTime startTime,
        LocalDateTime dataEndTime,
        LocalDateTime endTime,
        String subTitle,
        String themeColor,
        Long mainImageId,
        Long bgImageId,
        Long shareImageId,
        String shareTitle,
        String shareDesc,
        String extraConfig,
        String ruleContent) {

    /**
     * 此刻还能不能<b>参与</b>（抽奖、累计任务进度）。
     *
     * <p>判据是 {@link #dataEndTime} 而不是 {@link #endTime} —— 这正是加那一列的原因：
     * 数据截止之后活动还在，只是不再受理新的参与。
     *
     * <p>⚠️ 这是给<b>展示</b>用的（按钮要不要置灰）。真正的准入判定在服务端做，
     * 不能靠客户端这个方法 —— 它算的是客户端的时钟。
     */
    public boolean joinable(LocalDateTime now) {
        // 判据抽到 ActivityWindow：列表页的 ActivityBriefView 用的是同一份，不许各判一遍
        return ActivityWindow.joinable(status, startTime, dataEndTime, endTime, now);
    }

    /**
     * 此刻还能不能<b>领奖</b>。数据截止之后到活动结束之前，这里仍然是 true。
     */
    public boolean claimable(LocalDateTime now) {
        return ActivityWindow.claimable(status, startTime, endTime, now);
    }
}
