package solvela.ledger.wallet.domain.query;

import solvela.enums.WalletStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;

import java.time.LocalDate;

/**
 * 钱包分页查询的<b>领域参数</b>。
 *
 * <h3>它和 admin 的 MemberWalletQueryForm 有什么区别</h3>
 * 形状目前几乎一样，但<b>变更的理由不同</b>，这才是它们必须分开的原因：
 * <ul>
 *   <li>Form 跟着<b>某个端的页面</b>走 —— 后台加一个筛选框、换一种日期控件、
 *       调整校验提示，改的是 Form；</li>
 *   <li>Query 跟着<b>领域能力</b>走 —— 「钱包能按什么维度查」变了才改它。</li>
 * </ul>
 *
 * <p>合成一个的代价不是理论上的：C 端要做「我的钱包」时，会发现 service 的入参是一个
 * 带 {@code @Schema} 注解、按后台列表页设计的表单，里面有 {@code memberId} 这种
 * 「C 端根本不该由前端传」的字段。那时只有两条路 —— 要么让 C 端构造一个管理端的表单，
 * 要么复制一份 service。后者就是「一份代码到处复制」的起点。
 *
 * <p>⚠️ 这里<b>刻意没有</b> {@code @Schema} 与校验注解：接口文档和参数校验都是端的职责。
 * 共享层不认识 HTTP，自然也不该描述 HTTP 的请求体长什么样。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberWalletQuery extends PageParam {

    private Long memberId;

    /**
     * 资产类型：SCORE-积分, BALANCE-现金
     */
    private String assetType;

    /**
     * 状态：0-冻结, 1-正常
     */
    private WalletStatusEnum status;

    private LocalDate createTimeBegin;

    private LocalDate createTimeEnd;
}
