package solvela.ledger.stat.domain.query;


import java.time.LocalDate;

import lombok.Data;

/**
 * 账务统计的<b>时间窗口参数</b>，钱包 / 券 / 流水 / 履约四个子域共用。
 *
 * <p>取代原先直接传进 service 的 {@code LedgerStatQuery}。四个 Dao 都拿它当 MyBatis 参数，
 * 属性名不变所以 SQL 一行不用改 —— 变的只是「共享层的入参不再是一个 HTTP 表单」。
 *
 * <p>分层说明见 {@code MemberWalletQuery}。
 */
@Data
public class LedgerStatQuery {
    /**
     * 统计开始日期（含）。不传则与结束日期一起落到数据库当天
     */
    private LocalDate statDateBegin;

    /**
     * 结束日期是<b>含当天</b>的。SQL 里一律写成 {@code < 次日零点} 而不是 {@code <= 当天}：
     * 这些列都带时分秒，写 {@code <=} 会把结束日当天的数据整天筛掉。
     */
    /**
     * 统计结束日期（含）。不传则与开始日期一起落到数据库当天
     */
    private LocalDate statDateEnd;
}
