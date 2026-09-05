package solvela.app.domain;

/**
 * C 端活动列表里的一条。首页焦点位与活动中心共用这一个形状。
 *
 * <h3>没有 status 字段，只有 joinable</h3>
 * 域里下发的是 {@code ActivityStatusEnum}，那是<b>内部状态机</b>（ONLINE/OFFLINE/DRAFT…）。
 * C 端要回答的只有一个问题：<b>现在能不能点</b>。把内部状态原样透出去，
 * 前端就得自己维护一份「哪些状态算可参与」的映射 —— 那是第二份状态机，
 * 而且域里加一个状态时它会静默变错。
 *
 * @param activityCode 活动编码，路由用它寻址
 * @param activityName 活动名称
 * @param subTitle     副标题，没配为 null
 * @param themeColor   主题色，没配为 null
 * @param startTime    开始时间。给「即将开始」那类文案用
 * @param endTime      结束时间。给「活动截止 X 月 X 日」用
 * @param mainImageId  主图 file_id。⚠️ 网关还没暴露文件下载，前端现在拿它没用，
 *                     先如实下发 —— 接通那天前端不用改契约
 * @param joinable     此刻能不能参与。<b>由服务端时钟算好下发</b>，
 *                     不让客户端拿本地时间自己判：改一下系统时间就能点开一个没开始的活动
 */
public record PromoView(
        String activityCode,
        String activityName,
        String subTitle,
        String themeColor,
        String startTime,
        String endTime,
        Long mainImageId,
        boolean joinable) {
}
