package sa.ledger.logistic.domain.form;

import sa.base.sonicexcel.annotation.SonicTitle;

/**
 * 发货物流表 —— <b>新增模式</b>导入表单。
 *
 * <p>整行新建履约单，等价于页面上的「新建」，只是批量。
 * 撞 {@code uk_t_biz_phy_dlv_src (source_biz_id, source_type)} 的行会被逐行报错退回，
 * 不会静默覆盖已有履约单 —— 覆盖是「回填模式」({@link PhysicalDeliveryShipImportForm}) 的事。
 *
 * <p>做成 record 且全部用包装类型：基本类型在缺列时会被静默赋 0，
 * 和 null（"这一列没填"）在业务上不是一回事。理由同 {@code GoodsImportForm}。
 *
 * @Author alaric
 * @Date 2026-08-14
 */
public record PhysicalDeliveryImportForm(

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
