package solvela.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.app.domain.AssetView;
import solvela.member.api.AssetApi;
import solvela.member.api.MemberAssetView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 资产的接入层：<b>翻译 + 组装</b>，没有业务逻辑。
 *
 * <p>余额是多少、冻没冻结，全部由资产域说了算 —— 本类一行都不判、一分都不算。
 * 它只做两件网关该做的事：
 * <ol>
 *   <li>把资产编码翻成<b>给用户看的名字</b>，并决定它按金额还是按次数展示；</li>
 *   <li>把域的 view 组装成 C 端的形状。</li>
 * </ol>
 * 与活动那条链路（{@link ActivityService}）是同一套分工。
 *
 * <h3>为什么展示口径在这一层</h3>
 * 同一个 {@code SCORE}，C 端叫「积分」，将来的合作方渠道可能叫「星币」，
 * 内部工具要看到的是编码本身。域只陈述「这个会员的 SCORE 余额是 12345.67」，
 * 叫什么、怎么排、要不要带小数，由这里决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    /**
     * 资产编码 → 给用户看的名字与展示方式。
     *
     * <h3>🔴 为什么用 {@code Map<String, ...>} 而不是枚举</h3>
     * 资产类型是<b>运营会加的东西</b>（今天 SCORE / BALANCE，明天可能有 TICKET）。
     * 写成枚举的话，域里加一种、网关不发版，C 端就什么都看不到；
     * 而写成 map 加兜底，最坏情况只是名字不好看（回显编码），余额照常显示。
     *
     * <p>这和「reject reason 要用 switch 表达式让编译期报错」不冲突 ——
     * 那边是<b>封闭</b>的值域（域里穷举了拒绝原因，多一个就必须有人决定怎么说），
     * 这边是<b>开放</b>的值域（多一种资产不需要网关做任何决定）。
     * 判据是「新增一个值时，这一层必须做决定吗」。
     */
    private static final Map<String, Display> DISPLAY = Map.of(
            "SCORE", new Display("积分", true),
            "BALANCE", new Display("余额", true));

    private final AssetApi assetApi;

    /**
     * 我的资产。没有钱包记录时返回空列表 —— 新注册用户就是这个状态，不是错误。
     *
     * @param memberId 会员号，<b>由控制器从登录态取</b>。客户端传的一律不认，
     *                 否则就是「查别人的余额」。
     */
    public List<AssetView> listAssets(Long memberId) {
        List<MemberAssetView> assets = assetApi.listAssets(memberId);
        return assets.stream().map(AssetService::toView).toList();
    }

    private static AssetView toView(MemberAssetView asset) {
        Display display = DISPLAY.get(asset.assetType());
        if (display == null) {
            /*
             * 认不出的资产类型：回显编码，仍然把余额给出去。
             * 这一行日志是为了让人知道该来这里加一条 —— 但它不该拦住用户看自己的钱。
             */
            log.warn("【资产类型未配展示】{} 没有对应的中文名，C 端会直接显示编码", asset.assetType());
            display = new Display(asset.assetType(), true);
        }
        // 余额可能为 null（历史数据/刚建的钱包），当 0 处理，不让 C 端拿到 null 去算
        BigDecimal balance = asset.balance() == null ? BigDecimal.ZERO : asset.balance();
        return new AssetView(asset.assetType(), display.label(), balance.toPlainString(),
                display.currency(), asset.frozen());
    }

    /**
     * @param label    给用户看的名字
     * @param currency 是否按金额展示（带千分位与小数）。次数类资产用整数展示
     */
    private record Display(String label, boolean currency) {
    }
}
