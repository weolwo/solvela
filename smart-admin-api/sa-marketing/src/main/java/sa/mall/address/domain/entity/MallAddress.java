package sa.mall.address.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
@TableName("t_mall_address")
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
    private String receiverName;

    /**
     * 收件人电话【密文】
     */
    private String receiverPhone;

    /**
     * 详细门牌地址【密文】
     */
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
    private Integer isDefault;

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
