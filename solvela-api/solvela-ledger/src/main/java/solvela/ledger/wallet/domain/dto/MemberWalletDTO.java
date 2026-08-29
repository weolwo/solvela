package solvela.ledger.wallet.domain.dto;

import solvela.enums.WalletStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <h3>为什么 SQL 不再直接映射进 VO</h3>
 * 原先 {@code MemberWalletMapper.xml} 的 resultMap 直接 type 到 {@code MemberWalletVO}，
 * 等于把「HTTP 响应体的形状」写进了 SQL 映射。这两个东西的<b>变更频率与变更原因完全不同</b>：
 * 前端调整一个字段就要动 mapper，而 mapper 是共享层的东西 —— 于是 C 端也被这个形状绑住了。
 *
 * <p>更实际的问题是<b>字段可见性</b>。本 DTO 里的 {@code version} 是乐观锁版本号，
 * {@code createBy} / {@code updateBy} 是<b>后台运营人员</b>的账号。管理端列表要看这些，
 * C 端的「我的钱包」一个都不该看到。共用一个 VO 时，C 端复用它是 IDE 一按就补全的默认选项，
 * 没人会停下来想 —— 而漏出去的是内部字段和运营身份。
 *
 * <p>所以分工是：<b>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分</b>。
 * 装配在端上做（见 admin 的 {@code MemberWalletController}）。
 */
@Data
public class MemberWalletDTO {

    private Long id;

    private Long memberId;

    /**
     * 会员账号（取自会员主表的当前值，JOIN 出来的）
     */
    private String memberName;

    /**
     * 资产类型：SCORE-积分, BALANCE-现金
     */
    private String assetType;

    private BigDecimal balance;

    /**
     * 状态：0-冻结, 1-正常
     */
    private WalletStatusEnum status;

    /**
     * 乐观锁版本号。🔴 内部字段，端上要显式决定给不给
     */
    private Integer version;

    /**
     * 创建人。🔴 后台运营账号，同上
     */
    private String createBy;

    private LocalDateTime createTime;

    /**
     * 更新人。🔴 后台运营账号，同上
     */
    private String updateBy;

    private LocalDateTime updateTime;
}
