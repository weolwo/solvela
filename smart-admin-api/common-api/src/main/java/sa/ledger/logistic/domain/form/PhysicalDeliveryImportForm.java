package sa.ledger.logistic.domain.form;

import sa.base.sonicexcel.annotation.SonicTitle;

/**
 * 发货物流表 —— <b>新增模式</b>导入表单。
 *
 * <p>整行新建履约单，等价于页面上的「新建」，只是批量。
 * 撞 {@code uk_t_biz_phy_dlv_prop_pool (proposal_id, source_type)} 的行会被逐行报错退回，
 * 不会静默覆盖已有履约单 —— 覆盖是「回填模式」({@link PhysicalDeliveryShipImportForm}) 的事。
 *
 * <p>做成 record 且全部用包装类型：基本类型在缺列时会被静默赋 0，
 * 和 null（"这一列没填"）在业务上不是一回事。理由同 {@code GoodsImportForm}。
 *
 * @Author alaric
 * @Date 2026-08-14
 */
public record PhysicalDeliveryImportForm(

        @SonicTitle("会员名") String memberName,

        @SonicTitle("发奖提案ID") Long proposalId,

        @SonicTitle("来源类型") String sourceType,

        @SonicTitle("收件人姓名") String receiverName,

        // 手机号是"看起来像数字但不是数值"的典型，不强制文本会被 Excel 吃掉前导 0 或转成科学计数法
        @SonicTitle(value = "收件人电话", forceText = true) String receiverPhone,

        @SonicTitle("收件详细地址") String receiverAddress,

        @SonicTitle("物流公司") String logisticsCompany,

        @SonicTitle(value = "物流单号", forceText = true) String logisticsNo
) {
}
