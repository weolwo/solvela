package solvela.member;

import solvela.enums.MemberOperationLimitStatusEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;
import solvela.enums.MemberOperationTypeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会员操作限制（功能级、带到期、可解冻） 实体类
 *
 * <h3>这张表是「锁」，不是「计数器」</h3>
 * 连续失败的<b>计数放在 Redis</b>，只有真正触发限制时才往这里 insert 一行。
 * 反过来把计数也塞进表里的话，每一次输错密码都要 upsert 一行 —— 会员量级下这是写热点，
 * 而且拿别人手机号狂刷就能直接制造数据库压力，等于把风控功能本身变成攻击面。
 *
 * <h3>为什么是 append-only 而不是一人一行</h3>
 * 表里带了 {@code unlock_time / unlock_type / unlock_operator}，说明<b>解冻过程本身要留痕</b>。
 * 若做成 (member_id, operation_type) 唯一键、解冻就地改状态，那么同一个人第二次被限制时
 * 会覆盖掉上一次的解冻记录 —— 客服恰恰需要「这个人这半年被限过几次、每次谁解的」。
 * 所以每次限制都是新的一行，历史保留；「当前是否受限」靠
 * {@code status = 0 AND expire_time > now()} 查，索引走 idx_member_operation_status。
 *
 * @Date 2026-08-26
 */

@Data
@TableName("t_member_operation_limit")
public class MemberOperationLimit {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号
     */
    private Long memberId;

    /**
     * 受限操作类型，见 MemberOperationTypeEnum
     */
    private MemberOperationTypeEnum operationType;

    /**
     * 冻结开始时间
     */
    private LocalDateTime lockTime;

    /**
     * 自动到期时间：到点即视为解除，不依赖任何回写动作
     */
    private LocalDateTime expireTime;

    /**
     * 实际解冻时间。自动到期的行由回写补上，人工/重置密码解冻时当场写
     */
    private LocalDateTime unlockTime;

    /**
     * 解冻方式，见 MemberOperationUnlockTypeEnum；status=0 时为 null
     */
    private MemberOperationUnlockTypeEnum unlockType;

    /**
     * 人工解冻的操作人。unlock_type=3 时必填，其余为 null —— 追溯用
     */
    private String operator;

    /**
     * 0 冻结中，1 已解冻
     *
     * <p>🔴 没有公开 setter：初始状态只能经 {@link #lockUntil} 定，之后只走 {@code MemberOperationLimitDao}
     * 带「仍是冻结中」条件的 UPDATE（状态机见 {@link MemberOperationLimitStatusEnum#canTransitTo}）。
     * MyBatis 读库时直接写字段，不需要 setter。
     */
    @Setter(AccessLevel.NONE)
    private MemberOperationLimitStatusEnum status;

    /**
     * 触发原因，给客服看的人话，如「连续登录失败5次」
     */
    private String reason;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 落为<b>冻结中</b>，连同冻结时间与到期时间一起定：冻结中的行一定有这两个时间。
     *
     * @param expireTime 到期时间。靠改密解除的（如 {@code ASSET_OUT}）给一个很远的日期 —— 那一列是 NOT NULL
     */
    public void lockUntil(LocalDateTime lockTime, LocalDateTime expireTime) {
        if (status != null) {
            throw new IllegalStateException("操作限制 " + id + " 已是 " + status + "，不能再定初始状态");
        }
        this.status = MemberOperationLimitStatusEnum.LOCKED;
        this.lockTime = lockTime;
        this.expireTime = expireTime;
    }
}
