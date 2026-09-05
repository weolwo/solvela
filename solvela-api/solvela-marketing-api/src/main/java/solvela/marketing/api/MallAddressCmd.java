package solvela.marketing.api;

/**
 * 新增 / 编辑收货地址。
 *
 * @param memberId      会员号，<b>由调用方从登录态取</b>。客户端传的一律不认，
 *                      否则就是「往别人的地址簿里塞地址」
 * @param receiverPhone 提交的是<b>明文</b>手机号。列表回来的是脱敏值，两者不是同一个东西。
 *                      <p>编辑时<b>为空表示不修改手机号</b> —— 端上不该把
 *                      {@code 138****8000} 回填给用户改，一提交就把星号存进库了
 */
public record MallAddressCmd(
        Long memberId,
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress) {
}
