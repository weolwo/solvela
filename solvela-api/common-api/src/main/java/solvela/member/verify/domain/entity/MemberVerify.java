package solvela.member.verify.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.base.common.crypto.PiiTypeHandler;

import java.time.LocalDateTime;

/**
 * 会员实名信息（敏感，与主表分离） 实体类
 *
 * <p>🔴 {@code autoResultMap = true} 不能删：姓名与身份证两列挂了 {@link PiiTypeHandler}，
 * 而 MyBatis-Plus <b>只在写的时候用 typeHandler，读的时候要靠 autoResultMap 才会用</b>。
 * 少了它的表现是「存进去是密文，查出来还是密文」，且不报任何错
 * （同样的坑 {@code PhysicalDelivery} 上已经踩过一次）。
 *
 * <p>走自定义 resultMap 的查询（本模块的 queryPage / queryList）不吃 autoResultMap，
 * 那边要在 XML 的 &lt;result&gt; 上单独挂 typeHandler。
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */

@Data
@TableName(value = "t_member_verify", autoResultMap = true)
public class MemberVerify {

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
     * 真实姓名【<b>密文落库</b>，见 {@link PiiTypeHandler}】
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String realName;

    /**
     * 身份证号【<b>密文落库</b>，见 {@link PiiTypeHandler}】
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String idCard;

    /**
     * 身份证HMAC-SHA256原始字节(32B)：查重与唯一约束走它。
     *
     * <p><b>内部字段，绝不出现在任何 VO 里</b> —— 它是 binary(32)，映射成 String 是一串乱码字节，
     * 而且下发它等于把「同一个身份证有没有注册过」这个判断能力交给了前端。
     */
    private String idCardHash;

    /**
     * 认证状态：0-未认证, 1-认证中, 2-已认证, 3-认证失败
     */
    private Integer verifyStatus;

    /**
     * 认证通过时间
     */
    private LocalDateTime verifyTime;

    /**
     * 认证失败原因
     *
     * <p>{@code ALWAYS}：审核通过时要把上一次的驳回原因清掉，否则界面上会出现
     * 「已认证」旁边还挂着一条失败理由。默认的 NOT_NULL 策略会把这条 set null 静默丢掉。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failReason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
