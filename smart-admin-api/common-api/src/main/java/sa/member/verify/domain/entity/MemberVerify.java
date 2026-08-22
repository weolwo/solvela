package sa.member.verify.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员实名信息（敏感，与主表分离） 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 21:00:09
 * @Copyright weolwo
 */

@Data
@TableName("t_member_verify")
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
     * 真实姓名密文
     */
    private String realName;

    /**
     * 身份证号密文
     */
    private String idCard;

    /**
     * 身份证HMAC-SHA256原始字节(32B)：查重与唯一约束走它
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
     */
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
