package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.AssetView;
import solvela.app.service.AssetService;

import java.util.List;

/**
 * 我的资产。
 *
 * <h3>没有 @Anonymous，这是刻意的</h3>
 * 默认需要登录，公开页才显式开口子。反过来写的话，新加端点忘了标记就是默默裸奔，
 * 而那个方向的错误在 C 端是数据泄露 —— 余额尤其。
 *
 * <h3>会员号从登录态取，客户端传的一律不认</h3>
 * 接口上<b>没有 memberId 参数</b>，也不该有。有了它就等于开放「查任意会员余额」，
 * 而这种接口一旦上线，删掉它要等所有调用方改完。与抽奖那条链路同一个规矩。
 */
@Tag(name = "我的资产")
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /**
     * 我的全部资产。没有任何钱包记录时返回<b>空数组</b> ——
     * 新注册、还没拿过奖励的用户就是这个状态，它是正常的，不是 404。
     */
    @GetMapping
    public List<AssetView> listAssets() {
        return assetService.listAssets(CurrentMember.require().memberId());
    }
}
