package solvela.ledger.logistic.domain.dto;


import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 实物履约统计的<b>结果模型</b>。管理端直接把它作为响应体返回，<b>没有再装配一层 VO</b>。
 *
 * <h3>为什么这里和列表查询的处理不一样</h3>
 * 列表的 VO 是「某个端决定给出去哪些字段」——{@code MemberWalletDTO} 里有乐观锁版本号和
 * 运营账号，C 端一个都不该看到，所以必须在端上投影一层。
 * 而统计的产物是<b>算出来的结果</b>（含 {@code issueList} 这种给运营看的体检人话），
 * 它本来就不是任何一个端的响应体形状；C 端将来要看自己的资产汇总，会是另一套指标，
 * 不是这份的子集。为它复制一份字段完全相同的 VO，是纯仪式。
 *
 * <h3>而且那层装配会引入一个静默故障</h3>
 * 本类含嵌套 List（如 {@code assetList}）。{@code SolvelaBeanUtil.copy} 底层是
 * Spring 的 {@code BeanUtils.copyProperties}，它会解析泛型：发现
 * {@code List<AssetFlowDTO>} 与 {@code List<AssetFlowVO>} 不兼容后<b>直接跳过该属性</b>，
 * 既不报错也不转换 —— 目标字段留在 {@code null}。
 * 编译通过、接口照常返回，只是那一段数据<b>凭空消失了</b>，
 * 而前端拿到 null 往往只是少显示一块，没人会立刻发现。
 * 要做这层装配就必须用 {@code SolvelaBeanUtil.deepCopy} 或逐层 {@code copyList}；
 * 为一层没有收益的装配去冒这个险不值得。
 *
 * <p>本类<b>直接</b>作为管理端响应体返回，但身上没有 {@code @Schema} ——
 * 字段说明由 javadoc 提供：therapi 在编译期把这些注释编进 class 旁的资源，
 * 运行期 springdoc 的 {@code SpringDocJavadocProvider} 读出来当接口文档描述。
 * 一份注释同时服务读代码的人和看文档的人，不会出现两份说明各自过期。
 */
@Data
public class PhysicalDeliveryStatDTO {

    // ---------------- 本期新增（时间窗落在 create_time） ----------------

    /** 本期新增履约单数 */
    private Long newCount;

    /** 本期新增涉及的会员数（去重） */
    private Long newMemberCount;

    // ---------------- 履约状态（全量，不受时间范围影响） ----------------

    /** 履约单总数（全量） */
    private Long totalCount;

    /** 待发货（全量）：status=0 */
    private Long pendingCount;

    /**
     * 待发货里<b>收件信息还没补全</b>的单数。想发也发不了 —— 要去催用户填地址。
     */
    private Long pendingNoAddressCount;

    /**
     * 待发货里<b>地址齐了、就等发货</b>的单数。这才是运营今天真正能干的活。
     */
    private Long pendingReadyCount;

    /**
     * 最久的一单待发货已经等了多少分钟。只看条数看不出「压了一周」。
     */
    private Long pendingOldestMinutes;

    /** 已发货（全量）：status=1 */
    private Long deliveredCount;

    /** 已签收（全量）：status=2 */
    private Long signedCount;

    /** 异常退回（全量）：status=3，终态，需人工跟进 */
    private Long returnedCount;

    /**
     * 已作废（软删除）。页面上的「删除」按钮走的是
     * {@code UPDATE ... SET status=-1 WHERE status=0}，数据并没有真的删掉。
     *
     * <p>⚠️ 这个取值<b>既不在 DDL 的列注释里，也不在 {@code DeliveryStatusEnum} 里</b>，
     * 只有前端字典认得它。不单独给它一个桶的话，四个状态桶之和会小于总数，
     * 看的人只会以为统计算错了。
     */
    private Long cancelledCount;

    /**
     * 有效履约单数 = 总数 - 已作废。发货率的分母用它 ——
     * 作废掉的单子是被主动撤回的，不该算成「没发出去」拉低发货率。
     */
    private Long validCount;

    /** 发货率 = (已发货 + 已签收) / 有效履约单数 */
    private BigDecimal deliveredRate;

    /** 来源维度分布（全量），按履约单数降序 */
    private List<SourceStatDTO> sourceList;

    /** 数据一致性体检告警 */
    private List<String> issueList;

    /**
     * 一个来源的履约情况。
     *
     * <p>{@code source_type} 这一列同样没有枚举约束，原样回显取值，不做归类。
     */
    @Data
    public static class SourceStatDTO {

        /** 来源类型原值 */
        private String sourceType;

        /** 履约单数（全量） */
        private Long deliveryCount;

        /** 其中待发货的单数 */
        private Long pendingCount;

        /** 其中已作废的单数：status=-1 */
        private Long cancelledCount;

        /** 发货率 = (已发货 + 已签收) / (履约单数 - 已作废) */
        private BigDecimal deliveredRate;
    }
}
