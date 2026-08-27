package solvela.ledger.logistic.domain.excel;

import solvela.base.sonicexcel.annotation.SonicEnum;
import solvela.base.sonicexcel.converter.SonicEnumConverter;
import solvela.base.sonicexcel.option.SonicEnumOptionProvider;
import solvela.base.sonicexcel.annotation.SonicOptions;
import solvela.base.sonicexcel.annotation.SonicTitle;
import solvela.enums.DeliveryStatusEnum;

/**
 * 回填物流导入的<b>Excel 行模型</b>（非 HTTP 表单）。
 *
 * <p>它被两处使用，所以必须留在共享层：共享层的 service 用 SonicExcel 从上传的文件
 * 反射构造它；admin 用它身上的 {@code @SonicTitle} 生成下载模板的表头。
 * 表头与解析共用同一个类是刻意的 —— 拆成两份，字段一改就会漂。
 *
 * <p>原名以 Form 结尾会让人以为它和其它写入表单一样该移交端，故改名为 Row。
 * @param sourceBizId 来源单号。原先是「发奖提案ID」(Long)，随 t_physical_delivery 泛化成字符串单号 —— 商城兑换的履约单没有提案 ID，填的是订单号。 ⚠️ forceText：订单号是「看起来像数字但不是数值」的典型，不强制文本会被 Excel 吃掉前导 0 或转成科学计数法，回填时就匹配不上了。
 */
public record PhysicalDeliveryShipImportRow(

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
