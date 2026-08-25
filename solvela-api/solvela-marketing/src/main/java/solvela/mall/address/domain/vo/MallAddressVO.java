package solvela.mall.address.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public class MallAddressVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号：关联键")
    private Long memberId;

    @Schema(description = "收件人姓名【密文】")
    private String receiverName;

    @Schema(description = "收件人电话【密文】")
    private String receiverPhone;

    @Schema(description = "详细门牌地址【密文】")
    private String detailAddress;

    @Schema(description = "省【明文，可统计】")
    private String province;

    @Schema(description = "市【明文，可统计】")
    private String city;

    @Schema(description = "区/县【明文，可统计】")
    private String district;

    @Schema(description = "是否默认地址：0-否, 1-是。设默认时先把该会员其余行置0")
    private Integer isDefault;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
