package net.lab1024.sa.lottery.constant;

/**
 * 彩票模块常量（前端 constants/business/lottery/lottery-const.js 同源）
 *
 * @Author alaric
 * @Date 2026-07-27
 */
public class LotteryConst {

    private LotteryConst() {
    }

    /**
     * 号码长度下限。号码空间 = 10^numberLength，取 4 即空间不小于 1 万。
     *
     * 三个理由叠加：
     * ① 域太小时 FPE 的「看起来随机」会打折（不重复是代数性质，多小都成立，但统计分布会变差）；
     * ② 3 位时 cycle-walking 的期望加密次数是 1.024，4 位起最差只有 1.005；
     * ③ 3 位只有 1000 个号，可被穷举 —— 而本系统的号码是可反解验真的，
     *    空间太小会让「反解验真」失去意义，真号码被穷举出来后验真也挡不住冒领。
     */
    public static final int MIN_NUMBER_LENGTH = 4;

    /**
     * 号码长度上限。10^9 已经远超单期发行量的现实需求，再长只是徒增用户记忆成本。
     */
    public static final int MAX_NUMBER_LENGTH = 9;

    /**
     * 号码字符集：固定十进制，不可配。
     *
     * 字母对彩票没有意义，且 0/O、1/I、2/Z、5/S、8/B 形近，
     * 会人为制造一类「我明明中了、你们说号码不对」的无法自证的纠纷。
     * t_lottery_config.number_charset 列保留作预留扩展点，但服务端只接受本值。
     */
    public static final String NUMBER_CHARSET = "0-9";

    /**
     * 未中奖 / 未开奖的奖级占位值。
     *
     * 不用 null：C 端「我的号码」要按奖级排序（一等奖在前、未中奖沉底），
     * 而 MySQL 的 ORDER BY prize_level ASC 会把 NULL 排在最前面，正好排反。
     * 落 99 之后排序退化成朴素的 ORDER BY prize_level ASC, id DESC。
     *
     * 配套约束：奖级规则里禁止配置 prize_level = 99，否则 C 端无法区分「未中奖」与「99 等奖」。
     */
    public static final int PRIZE_LEVEL_NONE = 99;
}
