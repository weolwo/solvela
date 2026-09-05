package solvela.mall;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import solvela.crypto.PiiTypeHandler;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 商城-会员收货地址簿 实体类
 *
 * @Author weolwo
 * @Date 2026-08-22 19:25:03
 * @Copyright weolwo
 */

@Data
/**
 * 🔴 {@code autoResultMap = true} 不能删：收件三列挂了 {@link PiiTypeHandler}，
 * 而 MyBatis-Plus <b>只在写的时候用 typeHandler，读的时候要靠 autoResultMap 才会用</b>。
 * 少了它的表现是「存进去是密文，查出来还是密文」，且不报任何错。
 *
 * <p>与 {@code t_physical_delivery} 用的是<b>同一套 PiiTypeHandler、同一把密钥</b> ——
 * mall.sql「附」第 ① 条写着：两边各写一套加密，将来「同一个地址在两张表里对不上」
 * 会非常难查。
 */
@TableName(value = "t_mall_address", autoResultMap = true)
public class MallAddress {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号：关联键
     */
    private Long memberId;

    /**
     * 收件人姓名【密文】
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String receiverName;

    /**
     * 收件人电话【密文】
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String receiverPhone;

    /**
     * 详细门牌地址【密文】
     */
    @TableField(typeHandler = PiiTypeHandler.class)
    private String detailAddress;

    /**
     * 省【明文，可统计】
     */
    private String province;

    /**
     * 市【明文，可统计】
     */
    private String city;

    /**
     * 区/县【明文，可统计】
     */
    private String district;

    /**
     * 是否默认地址：0-否, 1-是。设默认时先把该会员其余行置0
     */
    private Boolean isDefault;

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
