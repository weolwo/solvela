package solvela.ledger.logistic.domain.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 实物履约单列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <h3>🔴 本 DTO 含解密后的个人信息</h3>
 * {@code receiverName} / {@code receiverPhone} / {@code receiverAddress} 在库里是密文，
 * 经 {@code PiiTypeHandler} 解密后落到这里 —— 这是<b>全项目字段可见性最敏感的一个读模型</b>。
 * 端上装配 VO 时必须显式决定给不给、给到什么粒度（是否掩码），
 * 绝不能因为「DTO 里有」就顺手 copy 出去。
 *
 * <p>另外 {@code createBy} / {@code updateBy} 是后台运营账号。
 * 分层说明见 {@code MemberWalletDTO}。
 */

@Data
public class PhysicalDeliveryDTO {


    private Long id;

    /**
     * 会员号
     */
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    private String memberName;

    /**
     * 来源单号：PROPOSAL 存提案ID / MALL 存订单号
     */
    private String sourceBizId;

    /**
     * 来源类型：PROPOSAL / MALL
     */
    private String sourceType;

    /*
     * 收件三项库里是密文，这里是<b>解密后的明文</b>（resultMap 上挂了 PiiTypeHandler）。
     *
     * ⚠️ 刻意<b>不脱敏</b>：运营台这个页面的用途就是发货 —— 看不到完整地址就没法干活，
     * 也没法把单子导给物流商。加密防的是<b>静态泄露</b>（库被脱、备份被拷、DBA 直接 select），
     * 不是防有应用权限的人。
     * 「谁能看到完整收件信息」是权限点的问题，不是加密能解决的 —— 那件事还没做，
     * 见交接文档 §13.8。
     */
    private String receiverName;

    /**
     * 收件人电话（库中密文，接口返回明文）
     */
    private String receiverPhone;

    /**
     * 收件详细地址（库中密文，接口返回明文）
     */
    private String receiverAddress;

    /**
     * 物流公司
     */
    private String logisticsCompany;

    /**
     * 物流单号
     */
    private String logisticsNo;

    /**
     * 状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回
     */
    private Integer status;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
