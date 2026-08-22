package sa.ledger.wallet.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import sa.base.common.code.UserErrorCode;
import sa.base.common.exception.BusinessException;

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
     * 会员号：全链路关联键（v3.71.0 换键）。
     *
     * <p>🔴 这里<b>刻意没有 memberName</b>。钱包是状态表（余额被反复 UPDATE），
     * 存一份账号快照只会和 {@code t_member} 长期不一致 —— 用户改了名，
     * 钱包里永远是老名字，反而更难认。要显示名字就 join 会员表取<b>当前值</b>，
     * 见 {@code MemberWalletMapper.queryPage}。
     *
     * <p>对应的 {@code member_name} 列由 v3.72.0 删除；v3.71.1 已先把它放开为可空，
     * 否则这里少写一个字段就会撞上「NOT NULL 且无默认值」，整表插不进去。
     */
    private Long memberId;

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
