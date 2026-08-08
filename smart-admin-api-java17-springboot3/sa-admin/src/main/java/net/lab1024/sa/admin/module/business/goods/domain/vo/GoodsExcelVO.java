package net.lab1024.sa.admin.module.business.goods.domain.vo;


import net.lab1024.sa.base.sonicexcel.annotation.SonicTitle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * excel商品
 *
 * @Author 1024创新实验室: 胡克
 * @Date 2021-10-25 20:26:54
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsExcelVO {

    @SonicTitle("商品分类")
    private String categoryName;

    @SonicTitle("商品名称")
    private String goodsName;

    @SonicTitle("商品状态错误")
    private String goodsStatus;

    @SonicTitle("产地")
    private String place;

    @SonicTitle("商品价格")
    private BigDecimal price;

    @SonicTitle("备注")
    private String remark;
}
