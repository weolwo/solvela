package solvela.marketing.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/**
 * 积分商城的对外契约。实现在 {@code solvela-mall}。
 *
 * <h3>为什么在 marketing-api 而不是一个新的 mall-api</h3>
 * 本模块的 pom 写着「粒度对齐<b>服务</b>、不对齐今天的 maven 模块」——
 * 2026-09-05 商城的域层拆成了独立的 {@code solvela-mall}，但它和玩法将来还在同一个进程，
 * 所以契约留在这里。真要把商城独立成服务的那天，才是把这个接口拆出去的时候，
 * 那一步会连着一个进程壳。
 *
 * <h3>这是「兑换」，不是「购物」</h3>
 * <b>没有购物车</b>：一单一 SKU。DDL 开头那段列了刻意不做的东西 ——
 * 购物车、多商品合单、优惠券叠加、满减、运费模板、会员等级差异化定价。
 * 想往这里加之前先回去看那一段。
 *
 * <h3>路径前缀 /internal 是有意的</h3>
 * 公网入口是网关自己的 {@code /mall/**} 与 {@code /address/**}，
 * 鉴权、字段裁剪、措辞都在那一层。<b>会员号一律由调用方从登录态取</b>，
 * 这些方法收 memberId 是因为它们在服务间调用，不是让客户端传。
 */
@HttpExchange("/internal/mall")
public interface MallApi {

    /**
     * 商品分类。只出启用中的，按运营排的顺序。
     *
     * <p>⚠️ 分类<b>没有 categoryCode</b>，只有自增 id（DDL 里解释过：分类是纯运营数据，
     * 代码不引用它）。所以 C 端按 id 取。
     */
    @GetExchange("/category")
    List<MallCategoryView> listCategories();

    /**
     * 商品列表。<b>只出上架中且在上架有效期内的</b>。
     *
     * <p>分页从第一天就有 —— DDL 的 {@code idx_mall_cmd_cat_status_sort} 就是照这个查询建的。
     *
     * @param memberId 会员号。用来标记「我收藏过哪些」，<b>未登录传 null</b>，
     *                 那时 favorite 一律 false（不是报错：商品列表匿名可看）
     */
    @PostExchange("/commodity/page")
    MallCommodityPageView pageCommodity(@RequestBody MallCommodityPageCmd cmd);

    /**
     * 商品详情。商品不存在或已下架 → 返回 {@code null}，<b>不抛异常</b> ——
     * 用户拿一个过期链接来访问是完全正常的事，翻译成什么状态码由网关决定。
     */
    @GetExchange("/commodity/{commodityId}")
    MallCommodityDetailView getCommodity(@PathVariable Long commodityId,
                                         @RequestParam(required = false) Long memberId);

    /* ---------------- 收藏。商品粒度，不是 SKU 粒度 ---------------- */

    /**
     * 我收藏的商品，按收藏时间倒序。形状与列表项一致（同一张卡片渲染）。
     */
    @GetExchange("/favorite")
    List<MallCommodityBriefView> listFavorites(@RequestParam Long memberId);

    /**
     * 收藏 / 取消收藏。
     *
     * <p>幂等：重复收藏靠 {@code uk_mall_fav_mbr_cmd} 挡住（前端连点两次是常态）；
     * 取消是<b>物理删</b> —— 软删会在「收藏 → 取消 → 再收藏」时撞唯一键，
     * 而收藏没有任何历史价值。
     */
    @PutExchange("/favorite/{commodityId}")
    void addFavorite(@PathVariable Long commodityId, @RequestParam Long memberId);

    @DeleteExchange("/favorite/{commodityId}")
    void removeFavorite(@PathVariable Long commodityId, @RequestParam Long memberId);

    /* ---------------- 兑换 ---------------- */

    /**
     * 兑换一件商品。<b>一单一 SKU。</b>
     *
     * <p>服务端在<b>一个事务</b>里做完：锁库存 → 扣限兑 → 扣积分 → 落订单，
     * 任何一步失败整体回滚。调用方只调一次、只看结果 ——
     * 「先调 A 再调 B」的写法中间断网就是账不平，而且是用户看不见的那种不平。
     *
     * <p>🔴 <b>订单号由服务端生成</b>，它本身就是扣积分的幂等键
     *（落 {@code t_member_asset_transaction} 的 UNIQUE(biz_ref_id, asset_type)）。
     * 客户端那个 requestId 是另一件事：挡「连点两次提交」，挡在网关。
     *
     * <p>⚠️ 履约<b>不在</b>这个事务里。状态机是
     * {@code 0/10 →(投递履约)→ 20 →(履约回执)→ 30/60}，本方法到 10（或 0）为止 ——
     * 把写发货单/发券塞进来，等于让一次外部调用决定用户的积分扣不扣得成。
     */
    @PostExchange("/redeem")
    MallRedeemResult redeem(@RequestBody MallRedeemCmd cmd);

    /**
     * 我的兑换记录，按下单时间倒序。
     *
     * <p>只取 limit 条，<b>不分页</b> —— 与奖品记录、优惠记录同一个判断。
     * 真要分页时该配一个游标而不是 pageNum：按时间倒序的列表用 offset 分页，
     * 新数据进来会让第二页重复出现第一页的行。
     *
     * <p>🔴 <b>全部状态都出</b>，包括「已取消」和「履约失败」。
     * 只出成功的等于把「我兑的东西呢」这个问题藏起来 ——
     * 而那正是用户点进这一页最想知道的事。
     */
    @GetExchange("/order")
    List<MallOrderView> listMyOrders(@RequestParam Long memberId, @RequestParam int limit);

    /* ---------------- 收货地址簿 ---------------- */

    /**
     * 我的收货地址，<b>默认地址排最前</b>（兑换页取第 0 条作预选，这个顺序就是「默认」的含义）。
     *
     * <p>🔴 手机号下发的是<b>脱敏值</b>。收件人姓名、手机号、详细门牌在库里是密文
     *（与 {@code t_physical_delivery} 同一套 PiiTypeHandler、同一把密钥），
     * 解密后在应用层截，<b>不存第二份明文脱敏值</b>。
     */
    @GetExchange("/address")
    List<MallAddressView> listAddresses(@RequestParam Long memberId);

    @GetExchange("/address/{addressId}")
    MallAddressView getAddress(@PathVariable Long addressId, @RequestParam Long memberId);

    /** 新增。<b>第一条地址自动成为默认</b> —— 让用户为了用它还要多点一次是无谓的 */
    @PostExchange("/address")
    MallAddressView createAddress(@RequestBody MallAddressCmd cmd);

    @PutExchange("/address/{addressId}")
    MallAddressView updateAddress(@PathVariable Long addressId, @RequestBody MallAddressCmd cmd);

    /**
     * 删除。<b>删掉的如果是默认地址，服务端要自动把剩下的第一条置默认</b> ——
     * 不能让账号进入「一条地址都不默认」的状态，那会让兑换页每次都要用户重选。
     */
    @DeleteExchange("/address/{addressId}")
    void deleteAddress(@PathVariable Long addressId, @RequestParam Long memberId);

    /**
     * 设为默认。<b>一个接口做完两件事</b>（旧的取消、新的置上），不是调用方调两次 ——
     * 中间断网会留下两个默认地址，或者一个都没有。
     */
    @PutExchange("/address/{addressId}/default")
    void setDefaultAddress(@PathVariable Long addressId, @RequestParam Long memberId);
}
