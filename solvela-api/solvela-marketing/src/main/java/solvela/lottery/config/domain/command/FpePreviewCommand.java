package solvela.lottery.config.domain.command;

import lombok.Data;

/**
 * FPE 算号推演台入参
 *
 * ⚠️ lotteryCode 必传：密钥是 HMAC(masterSecret, lotteryCode + "|" + issueNo)，
 * 缺了它服务端根本算不出正确的 key，推演结果会和线上真实发出的号码对不上 ——
 * 一个「看起来能用、实际全错」的预览比没有预览更糟。
 *
 * ⚠️ 刻意不含 totalCount：它不是密码学输入，只用于前端把游标输入框的 :max 限住，
 * 放进请求体会让人误以为它参与算号。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Data
public class FpePreviewCommand {

    /** 彩票编码（参与密钥派生，必传） */
    private String lotteryCode;

    /** 号码长度，取值 4~9 */
    private Integer numberLength;

    /** 推演的游标（1-indexed，与运营心智一致：第 1 个领号的人） */
    private Long sequenceNo;
}
