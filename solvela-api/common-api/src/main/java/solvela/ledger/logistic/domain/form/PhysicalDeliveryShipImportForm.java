package solvela.ledger.logistic.domain.form;

import solvela.base.common.excel.SonicEnum;
import solvela.base.common.excel.SonicEnumConverter;
import solvela.base.common.excel.SonicEnumOptionProvider;
import solvela.base.sonicexcel.annotation.SonicOptions;
import solvela.base.sonicexcel.annotation.SonicTitle;
import solvela.enums.DeliveryStatusEnum;

/**
 * 发货物流表 —— <b>回填模式</b>导入表单。
 *
 * <p>履约单本来就是发奖链路生成的，运营的日常动作是「线下发完货，把单号批量填回来」，
 * 所以这条路径按唯一键 {@code (source_biz_id, source_type)} 匹配<b>已存在</b>的单子做更新，
 * 匹配不到的行直接报错退回，绝不顺手新建 —— 凭一张 Excel 凭空造出履约单，
 * 意味着绕过了提案与预算，属于资损口子。
 *
 * <p>状态列在模板里是下拉，选项由 {@link DeliveryStatusEnum} 自动生成，
 * 读回来时由 {@link SonicEnumConverter} 把「已发货」翻译成 1，用户填不出非法值。
 *
 * @Author alaric
 * @Date 2026-08-14
 */
public record PhysicalDeliveryShipImportForm(

        /**
         * 来源单号。原先是「发奖提案ID」(Long)，随 t_physical_delivery 泛化成字符串单号 ——
         * 商城兑换的履约单没有提案 ID，填的是订单号。
         * ⚠️ forceText：订单号是「看起来像数字但不是数值」的典型，不强制文本会被 Excel
         * 吃掉前导 0 或转成科学计数法，回填时就匹配不上了。
         */
        @SonicTitle(value = "来源单号", forceText = true) String sourceBizId,

        @SonicTitle("来源类型") String sourceType,

        @SonicTitle("物流公司") String logisticsCompany,

        @SonicTitle(value = "物流单号", forceText = true) String logisticsNo,

        @SonicTitle(value = "状态", converter = SonicEnumConverter.class)
        @SonicEnum(DeliveryStatusEnum.class)
        @SonicOptions(provider = SonicEnumOptionProvider.class)
        Integer status
) {
}
