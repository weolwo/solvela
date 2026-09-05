package solvela.mall.address.domain.dto;


import java.time.LocalDateTime;

public class MallAddressDTO {


    /** id */
    private Long id;

    /** 会员号：关联键 */
    private Long memberId;

    /** 收件人姓名【密文】 */
    private String receiverName;

    /** 收件人电话【密文】 */
    private String receiverPhone;

    /** 详细门牌地址【密文】 */
    private String detailAddress;

    /** 省【明文，可统计】 */
    private String province;

    /** 市【明文，可统计】 */
    private String city;

    /** 区/县【明文，可统计】 */
    private String district;

    /** 是否默认地址：0-否, 1-是。设默认时先把该会员其余行置0 */
    private Boolean isDefault;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
