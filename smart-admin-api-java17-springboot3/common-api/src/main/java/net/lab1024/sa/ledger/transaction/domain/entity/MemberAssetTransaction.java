package net.lab1024.sa.ledger.transaction.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 交易明细表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */

@Data
@TableName("t_member_asset_transaction")
public class MemberAssetTransaction {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 会员名
     */
    private String memberName;

    /**
     * 资产类型：SCORE, BALANCE
     */
    private String assetType;

    /**
     * 资金流向：1-收入, 2-支出
     */
    private Integer transactionType;

    /**
     * 变动绝对值
     */
    private BigDecimal changeAmount;

    /**
     * 变动后最新余额
     */
    private BigDecimal balanceAfter;

    /**
     * 业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST
     */
    private String bizType;

    /**
     * 关联外部业务ID(如 prize_code)
     */
    private String bizRefId;

    /**
     * C端展示摘要
     */
    private String remark;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
