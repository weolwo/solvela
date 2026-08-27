package solvela.ledger.coupon.domain.dto;


import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 会员券统计的<b>结果模型</b>。管理端直接把它作为响应体返回，<b>没有再装配一层 VO</b>。
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
public class MemberCouponStatDTO {

    // ---------------- 本期发放（时间窗落在 create_time） ----------------

    /** 本期发放张数：create_time 落在时间范围内 */
    private Long issuedCount;

    /** 本期发放涉及的会员数（去重） */
    private Long issuedMemberCount;

    /**
     * 本期发放的这批券里已经被用掉的张数。
     * 分母是「同一批券」，所以这个比率是有意义的 —— 但刚发的券天然还没来得及用，
     * 时间范围选得越近，它越低。
     */
    private Long issuedUsedCount;

    /** 本期发放券的核销率 = 本期发放中已使用 / 本期发放 */
    private BigDecimal issuedUsedRate;

    // ---------------- 本期核销（时间窗落在 used_time，与上面不是同一批券） ----------------

    /**
     * 时间窗落在 {@code used_time} 上。今天核销的券可能是上个月发的 ——
     * 这个数和上面的「本期发放」没有包含关系，不要相减。
     */
    private Long usedCount;

    /** 本期核销涉及的会员数（去重） */
    private Long usedMemberCount;

    // ---------------- 券库存（全量，不受时间范围影响） ----------------

    /** 券总张数（全量） */
    private Long stockTotalCount;

    /**
     * ⚠️ 这个数是<b>虚高</b>的：没有过期扫描任务，过了有效期的券也还挂在这里。
     * 真正还能用的是 {@code stockUnusedCount - staleUnusedCount}。
     */
    private Long stockUnusedCount;

    /** 已使用张数（全量）：status=1 */
    private Long stockUsedCount;

    /** 已过期张数（全量）：status=2。全工程没有任何地方写这个状态 */
    private Long stockExpiredCount;

    /** 已作废张数（全量）：status=3 */
    private Long stockVoidCount;

    /**
     * 已过有效期却仍是「未使用」。没有过期扫描任务，这些券永远不会自己收口。
     */
    private Long staleUnusedCount;

    /**
     * 七天内到期且还没用的券。运营催一催还来得及 —— 过期之后就只能是一次失败的发放。
     */
    private Long expiringSoonCount;

    // ---------------- 分布 ----------------

    /** 券模维度分布（按本期发放量降序，TOP 10） */
    private List<CouponStatDTO> couponList;

    /** 来源维度分布（本期发放） */
    private List<SourceStatDTO> sourceList;

    /** 数据一致性体检告警 */
    private List<String> issueList;

    /**
     * 一种券模在本期的发放与核销。
     */
    @Data
    public static class CouponStatDTO {

        /** 券模编码 */
        private String couponCode;

        /** 券名称（取同组样本；改过名的历史行可能与最新配置不一致） */
        private String couponName;

        /** 本期发放张数 */
        private Long issuedCount;

        /** 涉及会员数 */
        private Long memberCount;

        /** 本期发放中已使用的张数 */
        private Long usedCount;

        /** 核销率 = 已使用 / 本期发放 */
        private BigDecimal usedRate;

        /**
         * 本期发放的这批券里，已经过了有效期还没被用掉的张数。
         */
        private Long staleCount;
    }

    /**
     * 一个来源的发放情况。
     *
     * <p>{@code source_type} 的 DDL 注释写的是 DRAW/TASK/MANUAL_SEND，
     * 但这一列同样没有枚举约束，所以原样回显取值，不做归类。
     */
    @Data
    public static class SourceStatDTO {

        /** 来源类型原值 */
        private String sourceType;

        /** 本期发放张数 */
        private Long issuedCount;

        /** 占本期发放的比例 */
        private BigDecimal issuedShare;
    }
}
