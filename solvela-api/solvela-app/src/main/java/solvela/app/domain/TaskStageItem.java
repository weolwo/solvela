package solvela.app.domain;

/**
 * 任务的一个档位（C 端形状）。
 *
 * @param target     达标阈值，十进制字符串 —— 金额型任务的阈值是小数
 * @param rewardText 这一档发什么
 * @param reached    <b>本周期内</b>已达标。前端据此把已拿到的那档打上勾，
 *                   不然用户签到 1 天拿了 188 积分，界面上看不出任何变化
 */
public record TaskStageItem(
        String target,
        String rewardText,
        boolean reached) {
}
