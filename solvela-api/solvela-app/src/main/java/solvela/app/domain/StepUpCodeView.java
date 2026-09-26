package solvela.app.domain;

/**
 * 二次验证码已发送。
 *
 * @param maskedEmail 码寄到了哪（打过码的）。客户端要展示「已发送到 a***@x.com」——
 *                    这是用户判断「这个码是不是发给我的」的唯一依据
 */
public record StepUpCodeView(String maskedEmail) {
}
