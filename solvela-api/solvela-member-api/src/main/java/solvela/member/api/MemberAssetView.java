package solvela.member.api;

import java.math.BigDecimal;

/**
 * 一种资产的余额。{@code t_member_wallet} 的对外投影。
 *
 * <h3>刻意只有四个字段</h3>
 * {@code MemberWalletDTO} 里还有 {@code version}（乐观锁）、{@code createBy} /
 * {@code updateBy}（<b>后台运营人员的账号</b>）、{@code memberName}。
 * 那些是「领域能查出来的全部」，而本 record 是「服务对外决定给出去的那一部分」——
 * 那个 DTO 的类注释把这条分工讲得很清楚，这里照做。
 *
 * <p>🔴 尤其是 {@code createBy}：C 端把它渲染出来就是运营账号泄露，
 * 而复用 DTO 时它是 IDE 一按就补全的默认选项，没人会停下来想。
 *
 * @param assetType 资产类型编码，如 {@code SCORE} / {@code BALANCE}。
 *                  <b>下发编码而不是中文名</b> —— 叫「积分」还是「星币」是展示决策，
 *                  由网关那一层决定，第二个端想换个叫法不必改域。
 * @param balance   余额。BigDecimal 出去经 {@code ToStringSerializer} 变成十进制字符串，
 *                  前端必须走 Decimal 封装 —— {@code 0.1 + 0.2} 那类误差在余额上就是事故。
 *                  ⚠️ 积分虽然通常是整数，但这一列本身是 decimal，别在任何一层当整数用。
 * @param frozen    钱包是否被冻结。由 {@code WalletStatusEnum} 翻成一个布尔 ——
 *                  端上不该认识域内部的枚举值域，它多一个状态时端不必跟着改。
 */
public record MemberAssetView(
        String assetType,
        BigDecimal balance,
        boolean frozen) {
}
