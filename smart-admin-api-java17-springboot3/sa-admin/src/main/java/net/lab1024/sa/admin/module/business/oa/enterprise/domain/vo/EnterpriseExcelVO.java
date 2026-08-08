package net.lab1024.sa.admin.module.business.oa.enterprise.domain.vo;

import net.lab1024.sa.base.sonicexcel.annotation.SonicTitle;
import lombok.Data;

/**
 * 企业信息
 *
 * @Author 1024创新实验室: 开云
 * @Date 2022/7/28 20:37:15
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class EnterpriseExcelVO {

    @SonicTitle("企业名称")
    private String enterpriseName;

    @SonicTitle("统一社会信用代码")
    private String unifiedSocialCreditCode;

    @SonicTitle("企业类型")
    private String typeName;

    @SonicTitle("联系人")
    private String contact;

    @SonicTitle("联系人电话")
    private String contactPhone;

    @SonicTitle("邮箱")
    private String email;

    @SonicTitle("省份名称")
    private String provinceName;

    @SonicTitle("城市名称")
    private String cityName;

    @SonicTitle("区县名称")
    private String districtName;

    @SonicTitle("详细地址")
    private String address;

}
