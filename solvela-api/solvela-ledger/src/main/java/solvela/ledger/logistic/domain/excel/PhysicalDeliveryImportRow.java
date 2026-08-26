package solvela.ledger.logistic.domain.excel;

import solvela.base.sonicexcel.annotation.SonicTitle;

/**
 * 新增履约单导入的<b>Excel 行模型</b>（非 HTTP 表单）。
 *
 * <p>它被两处使用，所以必须留在共享层：共享层的 service 用 SonicExcel 从上传的文件
 * 反射构造它；admin 用它身上的 {@code @SonicTitle} 生成下载模板的表头。
 * 表头与解析共用同一个类是刻意的 —— 拆成两份，字段一改就会漂。
 *
 * <p>原名以 Form 结尾会让人以为它和其它写入表单一样该移交端，故改名为 Row。
 */
public record PhysicalDeliveryImportRow(

        /**
         * 会员账号（不是会员号）。运营记得住的是账号，所以表头收账号，
         * 由 {@code PhysicalDeliveryService.importAdd} 批量换成关联键 {@code member_id}；
         * 换不到的账号<b>逐行报错退回</b>，不会静默生成一张无主履约单。
         */
        @SonicTitle("会员账号") String memberName,

        /**
         * 来源单号。原先是「发奖提案ID」(Long)，随 t_physical_delivery 泛化成字符串单号。
         * ⚠️ forceText 理由同电话：单号不强制文本会被 Excel 吃掉前导 0。
         */
        @SonicTitle(value = "来源单号", forceText = true) String sourceBizId,

        @SonicTitle("来源类型") String sourceType,

        // ⚠️ 收件三项落库前会被加密（PiiTypeHandler），长度上限由密文列宽反推：
        //    姓名 40 / 电话 30 / 地址 100。超了在 Service 里逐行报错，不靠 MySQL 静默截断。
        @SonicTitle("收件人姓名") String receiverName,

        // 手机号是"看起来像数字但不是数值"的典型，不强制文本会被 Excel 吃掉前导 0 或转成科学计数法
        @SonicTitle(value = "收件人电话", forceText = true) String receiverPhone,

        @SonicTitle("收件详细地址") String receiverAddress,

        @SonicTitle("物流公司") String logisticsCompany,

        @SonicTitle(value = "物流单号", forceText = true) String logisticsNo
) {
}
