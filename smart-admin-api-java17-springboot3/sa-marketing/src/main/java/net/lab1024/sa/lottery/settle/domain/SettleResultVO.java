package net.lab1024.sa.lottery.settle.domain;

/**
 * 开奖核销结果
 *
 * @param issueNo       期号
 * @param winningNumber 开奖号码
 * @param winCount      本次认领到的中奖张数
 * @param loseCount     本次判定为未中奖的张数
 * @param totalCount    本期号码总数（三者应满足 winCount + loseCount &lt;= totalCount，
 *                      断点续跑时前两者只统计本次增量）
 * @param waitDispatch  待派奖张数：核销只判定中奖，发奖是独立的下一步
 *
 * @Author alaric
 * @Date 2026-07-28
 */
public record SettleResultVO(String issueNo,
                             String winningNumber,
                             int winCount,
                             int loseCount,
                             long totalCount,
                             long waitDispatch) {
}
