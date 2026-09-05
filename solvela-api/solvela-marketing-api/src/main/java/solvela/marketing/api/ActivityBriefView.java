package solvela.marketing.api;

import solvela.enums.ActivityStatusEnum;

import java.time.LocalDateTime;

/**
 * 活动列表里的一条。C 端首页焦点位与活动中心用。
 *
 * <h3>为什么不直接复用 {@link ActivityRuleView}</h3>
 * 那个 record 带 {@code ruleContent}（富文本 HTML）与 {@code extraConfig}（配置 JSON 原文），
 * 单条详情要，<b>列表不要</b>：十条活动就是十份富文本，白白撑大响应体，
 * 而列表页一个字都不会渲染它们。
 *
 * <p>背景图 / 分享图也不给：那三张图只有专题页与分享卡片用得上。
 *
 * @param activityCode 活动编码。<b>对外一律用它寻址，不用 id</b>
 * @param activityName 活动名称
 * @param activityType 玩法类型 BASIC/DRAW/TASK/LOTTERY
 * @param status       活动状态。<b>如实下发</b> —— 未开始和已下线要给用户看不同的话
 * @param startTime    开始时间
 * @param dataEndTime  数据截止时间，为空表示与 endTime 相同。见 {@link ActivityWindow}
 * @param endTime      活动结束时间
 * @param subTitle     副标题
 * @param themeColor   主题色
 * @param mainImageId  主图 file_id。<b>给的是 id 不是 URL</b>：URL 带签名、有有效期，
 *                     该由取图那一刻现算，缓存住一个过期 URL 是很难查的一类问题
 */
public record ActivityBriefView(
        String activityCode,
        String activityName,
        String activityType,
        ActivityStatusEnum status,
        LocalDateTime startTime,
        LocalDateTime dataEndTime,
        LocalDateTime endTime,
        String subTitle,
        String themeColor,
        Long mainImageId) {

    /**
     * 此刻还能不能参与。判据与 {@link ActivityRuleView#joinable} <b>是同一份实现</b>
     * （{@link ActivityWindow}）—— 列表说「进行中」、点进去说「没开始」是最难查的一类不一致。
     *
     * <p>⚠️ 展示用。真正的准入判定在服务端，见 {@link ActivityWindow} 的类注释。
     */
    public boolean joinable(LocalDateTime now) {
        return ActivityWindow.joinable(status, startTime, dataEndTime, endTime, now);
    }
}
