package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.AddressRequest;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.marketing.api.MallAddressCmd;
import solvela.marketing.api.MallAddressView;
import solvela.marketing.api.MallApi;

import java.util.List;

/**
 * 收货地址簿。
 *
 * <h3>全部要登录，没有 @Anonymous</h3>
 * 地址是<b>个人信息</b>，而且是能定位到自然人的那一类（收件人 + 电话 + 门牌）。
 *
 * <h3>🔴 会员号从登录态取，请求体里那个不算</h3>
 * {@link AddressRequest} 里刻意没有 memberId —— 有了它，一个改一下 body 的请求
 * 就能往别人的地址簿里塞地址，或者改掉别人的收货地址。
 *
 * <h3>「不存在」与「不是你的」给同一个响应</h3>
 * 地址 id 是自增的、可枚举。区分开来等于确认某个 id 存在，
 * 而这条信息对攻击者是有用的、对用户是无用的。
 */
@Tag(name = "收货地址")
@RestController
@RequestMapping("/address")
@RequiredArgsConstructor
public class AddressController {

    private final MallApi mallApi;

    /** 我的地址，<b>默认那条排最前</b>。手机号是脱敏值（138****8000） */
    @GetMapping
    public List<MallAddressView> list() {
        return mallApi.listAddresses(CurrentMember.require().memberId());
    }

    @GetMapping("/{addressId}")
    public MallAddressView get(@PathVariable Long addressId) {
        MallAddressView view = mallApi.getAddress(addressId, CurrentMember.require().memberId());
        if (view == null) {
            throw new ApiException(ApiErrors.NOT_FOUND, "收货地址不存在");
        }
        return view;
    }

    /** 新增。<b>第一条自动成为默认</b> */
    @PostMapping
    public MallAddressView create(@RequestBody @Valid AddressRequest request) {
        return mallApi.createAddress(toCmd(request));
    }

    /**
     * 编辑。
     *
     * <p>⚠️ {@code receiverPhone} 留空表示<b>不修改手机号</b> —— 列表下发的是脱敏值，
     * 端上不该把 {@code 138****8000} 回填给用户改，一提交就把星号存进库了。
     */
    @PutMapping("/{addressId}")
    public MallAddressView update(@PathVariable Long addressId,
                                  @RequestBody @Valid AddressRequest request) {
        return mallApi.updateAddress(addressId, toCmd(request));
    }

    /** 删除。删掉默认那条时服务端会把剩下的第一条置默认 */
    @DeleteMapping("/{addressId}")
    public void delete(@PathVariable Long addressId) {
        mallApi.deleteAddress(addressId, CurrentMember.require().memberId());
    }

    /** 设为默认。服务端一个事务做完「旧的取消 + 新的置上」 */
    @PutMapping("/{addressId}/default")
    public void setDefault(@PathVariable Long addressId) {
        mallApi.setDefaultAddress(addressId, CurrentMember.require().memberId());
    }

    private static MallAddressCmd toCmd(AddressRequest request) {
        return new MallAddressCmd(
                // 🔴 会员号只从登录态来，见类注释
                CurrentMember.require().memberId(),
                request.receiverName(), request.receiverPhone(),
                request.province(), request.city(), request.district(),
                request.detailAddress());
    }
}
