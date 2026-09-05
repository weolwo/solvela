package solvela.app.domain;

/**
 * 转盘上的一格（C 端形状）。
 *
 * <p>🔴 只有三样东西出公网：编码（用来和抽奖结果对上）、名字、以及它是不是
 * 「谢谢参与」那一格。库存、限领次数、白名单、奖品面值一律不下发 ——
 * 前三样能反推中奖概率，白名单里装的是<b>会员号</b>。
 *
 * @param prizeCode 与抽奖结果的 prizeCode 对应。端上靠它决定转盘停在哪一格
 * @param thanks    是不是「谢谢参与」。域里它是 MARKER 类型的正常奖品，
 *                  但端上要把它画得和真奖不一样，所以翻成一个布尔 ——
 *                  <b>前端不该认识 PrizeTypeEnum 的取值</b>
 * @param featured  要不要画成大奖那一格。由运营配的 prize_level 决定
 */
public record ActivityPrizeItem(
        String prizeCode,
        String prizeName,
        boolean thanks,
        boolean featured) {
}
