package solvela.admin.module.ledger.stat.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

import lombok.Data;

/**
 * 财务中心统计面板的入参：四个页面（钱包 / 优惠券 / 交易明细 / 发货物流）共用一个。
 *
 * <h3>为什么只有一个时间范围，没有别的筛选项</h3>
 * 统计面板<b>刻意不吃页面上的列表筛选</b>（会员名、状态、资产类型…）：
 * 面板要回答的是「今天整体发生了什么」，跟着某个会员名筛之后它就不再是概览了。
 * 页面上会明说这一点 —— 筛了却不生效，比不筛更让人困惑。
 *
 * <h3>「默认当天」实现在 SQL 里，不在 Java 里</h3>
 * 两个字段都为 null 时，SQL 用 {@code COALESCE(#{...}, CURDATE())} 落到当天。
 * <b>刻意不在 Java 侧写 {@code LocalDate.now()}</b>：那是 JVM 时钟，
 * 而这张表里所有时间列都是数据库时钟写的（铁律 9：时间只认数据库一个钟）。
 * 两个钟不在同一时区时，「今天」会差出几个小时 —— 而这种错完全不报错，
 * 只是数字悄悄少一截。
 *
 * <p>⚠️ 正因为可以传 null，mapper 里必须写成 {@code #{form.statDateBegin,jdbcType=DATE}}：
 * MyBatis 拿到 null 又推断不出类型时会抛「JdbcType OTHER」，而这只在
 * 「用户不传日期」这一条路径上发生，日常点页面根本测不到。
 *
 * @Author alaric
 * @Date 2026-08-18
 */
@Data
public class LedgerStatForm {

    @Schema(description = "统计开始日期（含）。不传则与结束日期一起落到数据库当天")
    private LocalDate statDateBegin;

    /**
     * 结束日期是<b>含当天</b>的。SQL 里一律写成 {@code < 次日零点} 而不是 {@code <= 当天}：
     * 这些列都带时分秒，写 {@code <=} 会把结束日当天的数据整天筛掉。
     */
    @Schema(description = "统计结束日期（含）。不传则与开始日期一起落到数据库当天")
    private LocalDate statDateEnd;
}
