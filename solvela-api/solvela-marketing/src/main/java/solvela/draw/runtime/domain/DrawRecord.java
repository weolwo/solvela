package solvela.draw.runtime.domain;

import solvela.draw.engine.DrawResult;

/**
 * 一次抽奖的结果。连抽 N 次就是 N 条。
 *
 * <h3>这里没有 message</h3>
 * 「恭喜中奖」「手慢了，奖品已被抽完」是<b>展示文案</b>，由调用方决定 ——
 * C 端要一句人话、脚本引擎要的是一个能判断的值、后台联调要的是原始数据。
 * 上一版把文案放在域里返回，结果是域在替所有调用方决定用户看到什么，
 * 而同一个类的注释又写着「未受理时说什么由调用方定」，自相矛盾。
 *
 * <h3>source 是枚举不是字符串</h3>
 * 上一版返回 {@code source.name()}，调用方要分支只能比字符串，加一个来源不会编译报错。
 * 本项目在「枚举键当字符串用」上已经踩过三次（见 {@code ActivityRefProvider} 的类注释）。
 *
 * @param sourceBizId 本次抽奖的业务单号。落 {@code t_draw_prize_log.trace_id}，
 *                    并作为发奖事件的 {@code sourceBizId} —— {@code t_prize_log.uk_external_biz}
 *                    的跨系统防重就靠它，所以<b>每次抽奖必须各不相同</b>
 * @param hit         是否中奖
 * @param prizeItemId 中奖奖项 id；未中奖为 null。<b>奖池内部主键，不要下发给 C 端</b>
 * @param prizeCode   中奖奖品编码；未中奖为 null
 * @param source      命中来源；未中奖为 null。<b>给排查和风控看，不给用户看</b> ——
 *                    让用户知道自己是「白名单命中」的，等于告诉他这个活动内定了
 */
public record DrawRecord(
        String sourceBizId,
        boolean hit,
        Long prizeItemId,
        String prizeCode,
        DrawResult.HitSource source) {

    public static DrawRecord hit(String sourceBizId, long prizeItemId, String prizeCode,
                                 DrawResult.HitSource source) {
        return new DrawRecord(sourceBizId, true, prizeItemId, prizeCode, source);
    }

    /** 没中奖：抽奖真的发生了，机会已消耗，只是没中 */
    public static DrawRecord miss(String sourceBizId) {
        return new DrawRecord(sourceBizId, false, null, null, null);
    }
}
