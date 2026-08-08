package net.lab1024.sa.admin.module.business.goods.domain.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.lab1024.sa.admin.module.business.goods.constant.GoodsStatusEnum;
import net.lab1024.sa.admin.module.business.goods.excel.GoodsCategoryConverter;
import net.lab1024.sa.base.common.excel.SonicEnum;
import net.lab1024.sa.base.common.excel.SonicEnumConverter;
import net.lab1024.sa.base.module.support.dict.excel.SonicDict;
import net.lab1024.sa.base.module.support.dict.excel.SonicDictConverter;
import net.lab1024.sa.base.sonicexcel.annotation.SonicTitle;

import java.math.BigDecimal;

/**
 * excel商品
 *
 * <p>字段是实体的<b>原始值</b>，类目名 / 状态描述 / 产地字典全部交给转换器 ——
 * 这三处翻译原先是手写在 {@code GoodsService#getAllGoods} 里拼 VO 的。
 * 表头文本与列序保持不变，用户手里的旧模板照常可用。
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

    @SonicTitle(value = "商品分类", converter = GoodsCategoryConverter.class)
    private Long categoryId;

    @SonicTitle("商品名称")
    private String goodsName;

    // 表头这个错别字是历史遗留：改字会让用户手里所有旧模板对不上，保持原样
    @SonicTitle(value = "商品状态错误", converter = SonicEnumConverter.class)
    @SonicEnum(GoodsStatusEnum.class)
    private Integer goodsStatus;

    @SonicTitle(value = "产地", converter = SonicDictConverter.class)
    @SonicDict("GOODS_PLACE")
    private String place;

    @SonicTitle("商品价格")
    private BigDecimal price;

    @SonicTitle("备注")
    private String remark;
}
