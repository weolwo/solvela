package net.lab1024.sa.lottery.engine;

import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FPE 发号器工厂：把「配置里的主密钥」与「纯算法的 FpeCipher」焊接起来。
 *
 * <p>这是 engine 包里<b>唯一带 Spring 注解的类</b>，其余全是纯内存可单测的。
 * 之所以要有它：主密钥必须从配置读，而 {@link FpeCipher} 若自己去读配置就不再是纯函数、没法单测；
 * 把「取密钥」这一件带副作用的事收在工厂里，算法本身保持干净。
 *
 * <p>不做缓存：{@link FpeCipher} 的构造只是一次 HMAC，微秒级，
 * 而缓存反而要处理「彩票配置被改了怎么失效」的问题，得不偿失。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Slf4j
@Component
public class FpeCipherFactory {

    /**
     * 配置态推演台专用的演示期号。
     *
     * 推演台在配置页（此时一期都还没建），而号码恰恰随期号变化，所以必须给一个固定的演示 tweak。
     * 不挑真实期号：那会让运营误以为「我看到的就是这一期要发的号」，任何一次期号变更都会让这个印象落空。
     * 也不能省掉 tweak：那等于让预览走一条与线上不同的密钥派生路径，就违背了「预览与线上同一段代码」的初衷。
     * 本值不符合期号格式、也不会入库，不存在与真实期号撞车的可能。
     */
    public static final String PREVIEW_ISSUE_NO = "__PREVIEW__";

    /**
     * 未配置时留空，等到真正发号/开奖时才报错。
     * 不用 @Value 的必填形式，是为了不让「没配彩票密钥」阻断整个后台启动 —— 彩票只是众多模块之一。
     */
    @Value("${lottery.fpe.master-secret:}")
    private String masterSecret;

    /**
     * 按「彩票 + 期号」拿一个发号器
     */
    public FpeCipher create(String lotteryCode, String issueNo, int numberLength) {
        if (masterSecret == null || masterSecret.isBlank()) {
            log.error("[彩票发号] lottery.fpe.master-secret 未配置，无法发号 lotteryCode={}", lotteryCode);
            throw new BusinessException("FPE 主密钥未配置，请联系管理员配置 lottery.fpe.master-secret");
        }
        try {
            return FpeCipher.of(masterSecret, lotteryCode, issueNo, numberLength);
        } catch (IllegalArgumentException e) {
            // 引擎抛的是 IllegalArgumentException（纯算法不该依赖业务异常体系），到这一层转成对运营友好的提示
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 配置态推演台专用：用固定演示期号算一个样例号码
     */
    public FpeCipher createForPreview(String lotteryCode, int numberLength) {
        return create(lotteryCode, PREVIEW_ISSUE_NO, numberLength);
    }
}
