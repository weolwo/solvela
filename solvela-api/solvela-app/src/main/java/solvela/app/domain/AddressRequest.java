package solvela.app.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑收货地址的请求体。
 *
 * <h3>🔴 刻意没有 memberId</h3>
 * 有了它，一个改一下 body 的请求就能往别人的地址簿里塞地址、或者改掉别人的收货地址。
 * 会员号一律由控制器从登录态取。
 *
 * @param receiverPhone 明文手机号。<b>编辑时可以留空 = 不修改</b>，所以不能标
 *                      {@code @NotBlank}；空串由 pattern 的 {@code ^$} 分支放行。
 *                      <p>只卡「1 开头的 11 位数字」—— 号段会变，卡太死会误伤真实用户。
 * @param detailAddress 门牌号。<b>长度上限按密文列宽反推</b>：
 *                      {@code detail_address varchar(512)} 装的是 AES-GCM+Base64 后的密文，
 *                      对应明文约 100 字。让明文填满 512 会在写入时被 MySQL 静默截断，
 *                      表现是「存进去了，读出来解密失败」，而且那一行救不回来。
 */
public record AddressRequest(
        @NotBlank(message = "请填写收件人")
        @Size(max = 40, message = "收件人姓名过长")
        String receiverName,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不对")
        String receiverPhone,

        @NotBlank(message = "请选择省")
        @Size(max = 32, message = "省名过长")
        String province,

        @NotBlank(message = "请选择市")
        @Size(max = 32, message = "市名过长")
        String city,

        @NotBlank(message = "请选择区/县")
        @Size(max = 32, message = "区县名过长")
        String district,

        @NotBlank(message = "请填写详细地址")
        @Size(max = 100, message = "详细地址过长")
        String detailAddress) {
}
