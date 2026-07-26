package net.lab1024.sa.ledger.wallet.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 让实体类“活”起来（充血模型）
 * 把计算和状态校验还给 MemberWallet 自己，Service 不要越俎代庖
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */

@Data
@TableName("t_member_wallet")
public class MemberWallet {

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
     * 资产类型：SCORE-积分, BALANCE-现金，取值对齐 PrizeTypeEnum，与流水表 asset_type 同一字典
     */
    private String assetType;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 状态：0-冻结, 1-正常
     */
    private Integer status;

    /**
     * 乐观锁版本号
     */
    private Integer version;

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


    /**
     * 实体自带业务校验逻辑
     */
    public void checkAvailable() {
        if (this.status != 1) { // 最好用枚举 StatusEnum.NORMAL.getCode()
            throw new BusinessException(UserErrorCode.ACCOUNT_FROZEN);
        }
    }

    /**
     * 实体自带计算逻辑
     */
    public BigDecimal calculateAfterBalance(BigDecimal addAmount) {
        BigDecimal current = this.balance == null ? BigDecimal.ZERO : this.balance;
        return current.add(addAmount);
    }
}
