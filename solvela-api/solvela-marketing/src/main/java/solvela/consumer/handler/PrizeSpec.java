package solvela.consumer.handler;

import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;

import java.util.function.Function;

/**
 * 一种奖品在派发链路上的<b>全部差异点</b>。
 *
 * <p>积分、现金、优惠券、实物四种奖走的是同一条路：校验价值 -> 查奖品配置 -> 建提案 -> 看提案结果。
 * 改造前这条路在四个 handler 里各抄了一遍，于是抄出了三处不一致：
 * 现金那份多包了一层 {@code catch (Exception) { log; throw; }}（除了多一行日志没有任何作用）、
 * 报错文案四种写法、而「价值为 0」的处理更是三种语义混在一起。
 * 这个 record 就是把那些差异<b>列出来</b>，让它们变成四行声明而不是四份复制。
 *
 * @param assetType     资产类型，提案与账务侧据此选发放策略
 * @param valueLabel    这个奖的「价值」在人话里叫什么（积分数值 / 券面额 / 金额 / 实物价值）。
 *                      只出现在日志与 fail_reason 里，但它决定了运营能不能读懂那条失败流水
 * @param zeroPolicy    价值为 0 时怎么办，见 {@link ZeroPolicy}
 * @param instanceAsset 是否<b>实例类资产</b>：光有金额发不出来，还得说清发哪一张券 / 哪一件货。
 *                      为 true 时提案会带上 assetRef 与 assetName —— 账务侧不能回头查营销域，
 *                      不传的话发出去的券全叫「提案生成成功」
 * @param remark        提案备注，运营在提案列表里看到的就是这句
 * @Author alaric
 * @Date 2026-09-04
 */
public record PrizeSpec(PrizeTypeEnum assetType,
                        String valueLabel,
                        ZeroPolicy zeroPolicy,
                        boolean instanceAsset,
                        Function<PrizeLog, String> remark) {

    /**
     * 价值为 0 时的处理方式 —— 这是四种奖之间<b>唯一一处语义分歧</b>，值得单独命名。
     */
    public enum ZeroPolicy {

        /**
         * 0 是正常取值，直接判成功不入账。
         *
         * <p>「谢谢参与」这类占位奖品曾经只能配成 0 积分（{@code MARKER} 类型是后来才有的）。
         * 若把 0 当异常，抽奖的兜底奖项会刷出满屏失败流水，把真正的故障淹掉。
         */
        SKIP,

        /**
         * 0 与负数一律拒绝。
         *
         * <p>现金/券/实物的价值都要进风控预算口径，0 元的提案会让预算统计失真；
         * 而负数在任何一种奖上都是改包的信号。
         */
        REJECT
    }

    /** 值类资产：金额即全部信息，不需要 assetRef */
    public static PrizeSpec value(PrizeTypeEnum assetType, String valueLabel, ZeroPolicy zeroPolicy,
                                  Function<PrizeLog, String> remark) {
        return new PrizeSpec(assetType, valueLabel, zeroPolicy, false, remark);
    }

    /** 实例类资产：还要告诉账务域发哪一个 */
    public static PrizeSpec instance(PrizeTypeEnum assetType, String valueLabel,
                                     Function<PrizeLog, String> remark) {
        return new PrizeSpec(assetType, valueLabel, ZeroPolicy.REJECT, true, remark);
    }
}
