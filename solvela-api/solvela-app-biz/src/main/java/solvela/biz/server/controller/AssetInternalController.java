package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.enums.WalletStatusEnum;
import solvela.ledger.wallet.service.MemberWalletService;
import solvela.member.api.AssetApi;
import solvela.member.api.MemberAssetView;

import java.util.List;

/**
 * {@link AssetApi} 的 HTTP 薄壳。
 *
 * <h3>为什么 implements 接口，而不是自己写 @GetMapping</h3>
 * Spring MVC 认得接口上的 {@code @HttpExchange}，所以<b>路径与方法只在契约里定义一次</b>。
 * 自己再写一遍映射的话，网关侧的客户端代理和这里的服务端映射就是两份，
 * 改一处忘另一处 —— 表现是 404，而且要等到联调才发现。
 * 与 {@link MemberAuthInternalController} 同一个做法。
 *
 * <h3>裁剪在这里做，不在 ledger 里做</h3>
 * {@code MemberWalletService.listByMember} 返回的是实体（含 {@code version} 乐观锁、
 * {@code createBy} 运营账号）。往 {@link MemberAssetView} 搬的这几行就是那道裁剪 ——
 * 域给出「能查到的全部」，服务决定「对外给哪几个」。
 *
 * <p>🔴 别图省事把实体直接返回：{@code createBy} 是后台运营人员的账号，
 * 一旦顺着这条链路到了 C 端就是身份泄露，而复用实体是 IDE 一按就补全的默认选项。
 */
@RestController
@RequiredArgsConstructor
public class AssetInternalController implements AssetApi {

    private final MemberWalletService memberWalletService;

    @Override
    public List<MemberAssetView> listAssets(Long memberId) {
        return memberWalletService.listByMember(memberId).stream()
                .map(wallet -> new MemberAssetView(
                        wallet.getAssetType(),
                        wallet.getBalance(),
                        // 端上不认识 WalletStatusEnum：域里多一个状态值时，这里翻译，端不改
                        WalletStatusEnum.FROZEN == wallet.getStatus()))
                .toList();
    }
}
