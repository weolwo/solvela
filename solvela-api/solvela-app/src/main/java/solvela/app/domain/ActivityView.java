package solvela.app.domain;

import solvela.enums.ActivityStatusEnum;

import java.time.LocalDateTime;

/**
 * C 端看到的活动。
 *
 * <h3>为什么不直接把 {@code ActivityRuleView} 下发</h3>
 * 那是<b>服务间</b>的契约。直接透传有两个后果：
 * <ul>
 *   <li>营销服务往契约里加一个内部字段，就会自动出现在 C 端响应里 —— 没有任何人做决定；</li>
 *   <li>前端一旦用上某个字段，它就成了事实契约，营销侧再想改就动不了了。</li>
 * </ul>
 * 多这一层转换，换来的是「哪些字段能出公网」有一个明确的地方可以看。
 *
 * <p>顺带在这里把「能不能参与」算好下发：客户端不该自己拿三个时间去判 ——
 * 那等于把判据抄一份到前端，而它算的是<b>客户端的时钟</b>。
 *
 * @param activityCode 活动编码
 * @param activityName 活动名称
 * @param activityType 玩法类型
 * @param status       活动状态
 * @param startTime    开始时间
 * @param endTime      结束时间。<b>不下发 dataEndTime</b> —— 那是运营的内部口径，
 *                     用户只需要知道「现在能不能参与」，那已经算在 joinable 里了
 * @param subTitle     副标题
 * @param themeColor   主题色
 * @param mainImageId  主图 file_id
 * @param bgImageId    背景图 file_id
 * @param shareImageId 分享图 file_id
 * @param shareTitle   分享标题
 * @param shareDesc    分享描述
 * @param extraConfig  扩展配置 JSON 原文
 * @param prizes       奖品盘面。<b>来源是奖池</b>（抽奖引擎真正抽的那张表），
 *                     不是运营手写的 extraConfig JSON。非抽奖玩法为空列表
 * @param ruleContent  规则正文
 * @param joinable     此刻能不能参与（服务端时钟算的）
 * @param claimable    此刻能不能领奖
 */
public record ActivityView(
        String activityCode,
        String activityName,
        String activityType,
        ActivityStatusEnum status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String subTitle,
        String themeColor,
        Long mainImageId,
        Long bgImageId,
        Long shareImageId,
        String shareTitle,
        String shareDesc,
        String extraConfig,
        String ruleContent,
        java.util.List<ActivityPrizeItem> prizes,
        boolean joinable,
        boolean claimable) {
}
