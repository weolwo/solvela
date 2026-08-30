package solvela.enums;

import lombok.Getter;

/**
 * 资产/奖品类型，对齐 {@code t_prize_config.prize_type}、{@code t_prize_log.prize_type}
 * 与 {@code t_proposal_record.asset_type} —— 这三列是同一个字典。
 *
 * <p>⚠️ 枚举里有取值 ≠ 这个类型发得出去。派发要两层策略都注册齐：
 * consumer 的 {@code @PrizeStrategy}（生成提案/直接下发）与 ledger 的 {@code @AssetStrategy}（履约入账）。
 * 少任何一层，中奖后都会在 {@code AFTER_COMMIT} 里静默失败，只在 {@code t_prize_log.fail_reason} 留痕。
 * 前端 {@code prize-config-const.js} 的 dispatchable 标记就是这份能力的镜像，改这里记得同步改那里。
 */
@Getter
public enum PrizeTypeEnum {

    /** 积分：值类资产，走提案 -> 钱包入账 */
    SCORE,

    /** 现金：值类资产，走提案 -> 钱包入账 */
    BALANCE,

    /** 优惠券：实例类资产，走提案 -> 发券 */
    COUPON,

    /** 实物：实例类资产，走提案 -> 生成履约单 */
    PHYSICAL,

    /**
     * 标记（标识）：<b>纯占位奖项，不产生任何资产变动</b>。
     *
     * <p>「谢谢参与」「差一点点」这类兜底奖项用它。此前只能拿 {@code SCORE} + {@code prize_value=0}
     * 硬凑（{@code ScoreHandler} 里那段「0 分无需入账」就是为此写的），但那样在奖励漏斗里
     * 会被算进积分口径，运营看到的「发出积分 0 元 N 笔」全是噪声。
     *
     * <p>不动账 = 不进提案链路，所以 ledger 侧<b>刻意没有</b>对应的 {@code @AssetStrategy}：
     * {@code MarkerHandler} 直接判成功，永远走不到资产层。
     */
    MARKER,

    /** 彩票：给会员发一张号码（t_lottery_record）。派发策略尚未实现，见 prize-config-const.js */
    LOTTERY,

    /** 自定义：预留，派发策略尚未实现 */
    CUSTOM;

}
