package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.Anonymous;
import solvela.app.auth.CurrentMember;
import solvela.app.domain.RedeemRequest;
import solvela.app.domain.RedeemResultView;
import solvela.app.service.RedeemService;
import solvela.app.web.ApiErrors;
import solvela.app.web.ApiException;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallCategoryView;
import solvela.marketing.api.MallCommodityBriefView;
import solvela.marketing.api.MallCommodityDetailView;
import solvela.marketing.api.MallCommodityPageCmd;
import solvela.marketing.api.MallCommodityPageView;

import java.util.List;

/**
 * 积分商城。
 *
 * <h3>为什么商品可以匿名看，收藏不行</h3>
 * 商品列表与详情是<b>分享入口</b> —— 要求先登录才能看一眼等于把新用户挡在门外，
 * 和活动页同一个理由。而收藏是「我的」东西，没有登录态就没有内容。
 *
 * <p>匿名访问时 memberId 传 null，商品上的 {@code favorite} 一律 false ——
 * 那不是错误，是「还不知道你是谁」。
 *
 * <h3>会员号一律从登录态取</h3>
 * 接口上<b>没有 memberId 参数</b>。有了就等于开放「查/改任意会员的收藏」。
 */
@Tag(name = "积分商城")
@RestController
@RequestMapping("/mall")
@RequiredArgsConstructor
public class MallController {

    private final MallApi mallApi;
    private final RedeemService redeemService;

    @Anonymous
    @GetMapping("/category")
    public List<MallCategoryView> listCategories() {
        return mallApi.listCategories();
    }

    /**
     * 商品列表。只出上架中且在上架有效期内的。
     *
     * <p>分页从第一天就有 —— 商品表会一直长，而 C 端首屏只需要前几十条。
     * 筛选与排序都在服务端做：本地过滤在分页之后立刻就是错的（只能搜到当前页）。
     *
     * @param sortBy SORT(默认，运营权重) / SOLD(热销) / POINTS_ASC(积分从低到高)
     */
    @Anonymous
    @GetMapping("/commodity")
    public MallCommodityPageView pageCommodity(@RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String sortBy,
                                               @RequestParam(required = false) Integer pageNum,
                                               @RequestParam(required = false) Integer pageSize) {
        return mallApi.pageCommodity(new MallCommodityPageCmd(
                categoryId, keyword, sortBy, CurrentMember.memberIdOrNull(), pageNum, pageSize));
    }

    /**
     * 商品详情。
     *
     * <p>商品不存在<b>或已下架</b> → 404。两种情况给同一个响应是刻意的：
     * 「这个商品下架了」对用户没有可操作性，而区分开来等于告诉爬虫哪些 id 是有效的。
     */
    @Anonymous
    @GetMapping("/commodity/{commodityId}")
    public MallCommodityDetailView getCommodity(@PathVariable Long commodityId) {
        MallCommodityDetailView view = mallApi.getCommodity(
                commodityId, CurrentMember.memberIdOrNull());
        if (view == null) {
            throw new ApiException(ApiErrors.NOT_FOUND, "商品不存在或已下架");
        }
        return view;
    }

    /* ---------------- 兑换。要登录 ---------------- */

    /**
     * 兑换一件商品。<b>一单一 SKU，没有购物车。</b>
     *
     * <p>服务端在一个事务里做完锁库存 → 扣限兑 → 扣积分 → 落订单。
     * 库存没了、超限兑、积分不足都会回 4xx 并带一句人话 —— 那几种都是<b>预期内</b>的。
     *
     * <p>🔴 会员号从登录态取。订单号由服务端生成，客户端给不了 ——
     * 请求体里那个 requestId 只挡「连点两次提交」。
     */
    @PostMapping("/redeem")
    public RedeemResultView redeem(@RequestBody @Valid RedeemRequest request) {
        return redeemService.redeem(CurrentMember.require().memberId(), request);
    }

    /* ---------------- 收藏。要登录 ---------------- */

    /** 我收藏的商品。<b>已下架的不出现</b> —— 收藏行还在，但不展示一个点不进去的卡片 */
    @GetMapping("/favorite")
    public List<MallCommodityBriefView> listFavorites() {
        return mallApi.listFavorites(CurrentMember.require().memberId());
    }

    /** 收藏。重复收藏不报错 —— 前端连点两次是常态 */
    @PutMapping("/favorite/{commodityId}")
    public void addFavorite(@PathVariable Long commodityId) {
        mallApi.addFavorite(commodityId, CurrentMember.require().memberId());
    }

    /** 取消收藏。已经不在了也不报错，重复点是常态 */
    @DeleteMapping("/favorite/{commodityId}")
    public void removeFavorite(@PathVariable Long commodityId) {
        mallApi.removeFavorite(commodityId, CurrentMember.require().memberId());
    }
}
