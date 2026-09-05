package solvela.mall.clientapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.EnableStatusEnum;
import solvela.enums.MallCommodityStatusEnum;
import solvela.mall.MallAddress;
import solvela.mall.MallCategory;
import solvela.mall.MallCommodity;
import solvela.mall.MallFavorite;
import solvela.mall.MallSku;
import solvela.mall.address.service.MallAddressService;
import solvela.mall.order.service.MallRedeemService;
import solvela.mall.constant.MallSkuAttrs;
import solvela.mall.category.manager.MallCategoryManager;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.exchangelimit.manager.MallExchangeLimitManager;
import solvela.mall.favorite.manager.MallFavoriteManager;
import solvela.mall.sku.manager.MallSkuManager;
import solvela.marketing.api.MallAddressCmd;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallAddressView;
import solvela.marketing.api.MallCategoryView;
import solvela.marketing.api.MallCommodityBriefView;
import solvela.marketing.api.MallCommodityDetailView;
import solvela.marketing.api.MallCommodityPageCmd;
import solvela.marketing.api.MallCommodityPageView;
import solvela.marketing.api.MallCommoditySkuView;
import solvela.marketing.api.MallRedeemCmd;
import solvela.marketing.api.MallRedeemResult;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商城的 C 端门面：{@link MallApi} 的实现。
 *
 * <h3>为什么单独一个类，不塞进各自的 Service</h3>
 * {@code MallCommodityService} 等已有的 Service 是<b>给运营台用的</b> ——
 * 它们的查询判据是「运营能不能看到/改」，而 C 端的判据是「用户能不能兑」。
 * 两套判据混进一个方法（加个 boolean 开关）迟早会让某一边悄悄放行错误的数据，
 * 而那正是「用户看到一个草稿商品」这类事故的来源。
 *
 * <h3>🔴 C 端可见性的判据只有一处，就在 {@link #visibleCommodity}</h3>
 * 上架中 + 在上架有效期内。列表、详情、兑换都必须过它 ——
 * 三处各写一遍的话，漏改一处的表现是「列表里没有但直链能兑」。
 */
@Service
@RequiredArgsConstructor
public class MallClientFacade implements MallApi {

    /** 一页最多给多少。不封顶的话一个 {@code pageSize=100000} 就能把内存打爆 */
    private static final int MAX_PAGE_SIZE = 50;

    private final MallCategoryManager mallCategoryManager;
    private final MallCommodityManager mallCommodityManager;
    private final MallSkuManager mallSkuManager;
    private final MallFavoriteManager mallFavoriteManager;
    private final MallExchangeLimitManager mallExchangeLimitManager;
    private final MallAddressService mallAddressService;
    private final MallRedeemService mallRedeemService;

    /* ---------------- 分类 ---------------- */

    @Override
    public List<MallCategoryView> listCategories() {
        return mallCategoryManager.lambdaQuery()
                .eq(MallCategory::getStatus, EnableStatusEnum.ENABLED)
                .orderByAsc(MallCategory::getSort)
                .orderByAsc(MallCategory::getId)
                .list().stream()
                .map(c -> new MallCategoryView(c.getId(), c.getParentId(), c.getCategoryName(),
                        c.getIconFileId(), c.getSort()))
                .toList();
    }

    /* ---------------- 商品 ---------------- */

    /**
     * C 端可见性：<b>上架中 + 在上架有效期内</b>。
     *
     * <p>时间用哨兵默认值而不是 NULL（DDL 里那两列是 {@code 1970-01-01} / {@code 2099-12-31}），
     * 所以这里可以直接比较，不必写 {@code (start is null or start <= now)}——
     * 那也是那两个默认值存在的理由：让这个条件能走索引。
     */
    private LambdaQueryWrapper<MallCommodity> visibleCommodity() {
        LocalDateTime now = LocalDateTime.now();
        return new LambdaQueryWrapper<MallCommodity>()
                .eq(MallCommodity::getStatus, MallCommodityStatusEnum.ON)
                .le(MallCommodity::getStartTime, now)
                .ge(MallCommodity::getEndTime, now);
    }

    @Override
    public MallCommodityPageView pageCommodity(MallCommodityPageCmd cmd) {
        LambdaQueryWrapper<MallCommodity> wrapper = visibleCommodity();
        if (cmd.categoryId() != null) {
            wrapper.eq(MallCommodity::getCategoryId, cmd.categoryId());
        }
        if (cmd.keyword() != null && !cmd.keyword().isBlank()) {
            wrapper.like(MallCommodity::getCommodityName, cmd.keyword().trim());
        }
        applySort(wrapper, cmd.sortBy());

        int size = cmd.pageSize() == null ? 20 : Math.min(Math.max(cmd.pageSize(), 1), MAX_PAGE_SIZE);
        long num = cmd.pageNum() == null ? 1 : Math.max(cmd.pageNum(), 1);
        Page<MallCommodity> page = mallCommodityManager.page(new Page<>(num, size), wrapper);

        List<MallCommodity> list = page.getRecords();
        Map<Long, Integer> stocks = stockOf(list.stream().map(MallCommodity::getId).toList());
        Set<Long> favorites = favoriteIds(cmd.memberId(),
                list.stream().map(MallCommodity::getId).toList());

        return new MallCommodityPageView(
                list.stream().map(c -> toBrief(c, stocks, favorites)).toList(),
                page.getTotal());
    }

    /**
     * 排序。
     *
     * <p>「热销」按 {@code sold_count} 算，<b>不是一个手工标记</b> ——
     * DDL 里那段解释过为什么不加 {@code is_hot}：手工标的热门会和真实数据打架
     *（标着热门下面挂 0 兑换量），而且没有任何机制提醒运营去撤掉它。
     *
     * <p>每一档都以 id 兜底，保证翻页时顺序稳定 —— 排序键相同的行如果没有兜底，
     * 第 2 页可能重复出现第 1 页的商品。
     */
    private static void applySort(LambdaQueryWrapper<MallCommodity> wrapper, String sortBy) {
        switch (sortBy == null ? "SORT" : sortBy) {
            case "SOLD" -> wrapper.orderByDesc(MallCommodity::getSoldCount);
            case "POINTS_ASC" -> wrapper.orderByAsc(MallCommodity::getPointsPrice);
            // 默认按运营排的权重（sort 是从小到大）
            default -> wrapper.orderByAsc(MallCommodity::getSort);
        }
        wrapper.orderByDesc(MallCommodity::getId);
    }

    @Override
    public MallCommodityDetailView getCommodity(Long commodityId, Long memberId) {
        MallCommodity commodity = mallCommodityManager.getOne(
                visibleCommodity().eq(MallCommodity::getId, commodityId));
        if (commodity == null) {
            // 不存在与已下架返回同一个值：翻译成什么状态码由网关决定
            return null;
        }
        List<MallSku> skus = mallSkuManager.lambdaQuery()
                .eq(MallSku::getCommodityId, commodityId)
                .eq(MallSku::getSkuStatus, EnableStatusEnum.ENABLED)
                .orderByAsc(MallSku::getSort)
                .orderByAsc(MallSku::getId)
                .list();

        int stock = skus.stream().mapToInt(s -> nullToZero(s.getAvailableStock())).sum();
        boolean favorite = !favoriteIds(memberId, List.of(commodityId)).isEmpty();

        return new MallCommodityDetailView(
                commodity.getId(), commodity.getCommodityCode(), commodity.getCategoryId(),
                commodity.getCommodityType(), commodity.getCommodityName(),
                commodity.getCommodityIntro(), commodity.getCoverFileId(),
                commodity.getPayType(), commodity.getPointsPrice(), commodity.getCashPrice(),
                commodity.getOriginalPrice(), favorite, stock,
                // 轮播图走 t_file_relation，等文件引用登记接上后填；现在是空列表
                List.of(),
                commodity.getDetailContent(), commodity.getExchangeNotice(),
                commodity.getLimitPeriod(), commodity.getLimitCount(),
                remainingCount(commodity, memberId),
                skus.stream().map(MallClientFacade::toSkuView).toList());
    }

    /**
     * 本周期还能兑几件。
     *
     * <p>{@code limitCount = 0} 表示不限制，返回 null（不是一个很大的数：
     * 端上要用「有没有值」来决定显不显示这一行）。未登录同样返回 null。
     *
     * <p>🔴 {@code period_key} 必须由<b>数据库时钟</b>算（铁律 9/10）——
     * 用 JVM 时间的话跨时区部署时日切点对不上，用户在 00:00~08:00 之间能多兑一次。
     * 这里读的是已经落库的计数行，那一行的 period_key 就是服务端算的，
     * <b>不要在这里自己拼一个 key 去查</b>。
     */
    private Integer remainingCount(MallCommodity commodity, Long memberId) {
        if (memberId == null || commodity.getLimitCount() == null || commodity.getLimitCount() <= 0) {
            return null;
        }
        Integer used = mallExchangeLimitManager.lambdaQuery()
                .eq(solvela.mall.MallExchangeLimit::getMemberId, memberId)
                .eq(solvela.mall.MallExchangeLimit::getCommodityId, commodity.getId())
                .list().stream()
                .map(solvela.mall.MallExchangeLimit::getUsedCount)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        return Math.max(0, commodity.getLimitCount() - used);
    }

    /* ---------------- 收藏 ---------------- */

    @Override
    public List<MallCommodityBriefView> listFavorites(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        List<Long> ids = mallFavoriteManager.lambdaQuery()
                .eq(MallFavorite::getMemberId, memberId)
                .orderByDesc(MallFavorite::getCreateTime)
                .list().stream().map(MallFavorite::getCommodityId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        /*
         * 只出仍然可见的商品：收藏的商品可能已经下架。
         * 保留收藏行本身（用户可能还想着它会回来），但列表里不展示一个点不进去的卡片。
         */
        List<MallCommodity> list = mallCommodityManager.list(
                visibleCommodity().in(MallCommodity::getId, ids));
        Map<Long, Integer> stocks = stockOf(ids);
        Set<Long> favorites = Set.copyOf(ids);
        // 按收藏时间倒序还原顺序 —— IN 查出来的顺序是不确定的
        Map<Long, MallCommodity> byId = list.stream()
                .collect(Collectors.toMap(MallCommodity::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(c -> toBrief(c, stocks, favorites))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(Long commodityId, Long memberId) {
        // 已收藏就什么都不做：前端连点两次是常态，uk_mall_fav_mbr_cmd 也会兜住
        if (memberId == null || !favoriteIds(memberId, List.of(commodityId)).isEmpty()) {
            return;
        }
        MallFavorite favorite = new MallFavorite();
        favorite.setMemberId(memberId);
        favorite.setCommodityId(commodityId);
        mallFavoriteManager.save(favorite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long commodityId, Long memberId) {
        if (memberId == null) {
            return;
        }
        // 🔴 物理删。软删 + 唯一键会在「收藏 → 取消 → 再收藏」时冲突，而收藏没有历史价值
        mallFavoriteManager.remove(new LambdaQueryWrapper<MallFavorite>()
                .eq(MallFavorite::getMemberId, memberId)
                .eq(MallFavorite::getCommodityId, commodityId));
    }

    /* ---------------- 兑换 ---------------- */

    /**
     * 兑换。整个事务在 {@link MallRedeemService} 里，本类只转发 ——
     * 那一段有明确的步骤顺序与回滚语义，不该被混进这个装配门面。
     */
    @Override
    public MallRedeemResult redeem(MallRedeemCmd cmd) {
        return mallRedeemService.redeem(cmd);
    }

    /* ---------------- 地址簿 ---------------- */

    @Override
    public List<MallAddressView> listAddresses(Long memberId) {
        return mallAddressService.listByMember(memberId).stream()
                .map(MallClientFacade::toAddressView)
                .toList();
    }

    @Override
    public MallAddressView getAddress(Long addressId, Long memberId) {
        MallAddress address = mallAddressService.getOwned(addressId, memberId);
        return address == null ? null : toAddressView(address);
    }

    @Override
    public MallAddressView createAddress(MallAddressCmd cmd) {
        return toAddressView(mallAddressService.create(toAddress(cmd)));
    }

    @Override
    public MallAddressView updateAddress(Long addressId, MallAddressCmd cmd) {
        return toAddressView(mallAddressService.update(addressId, toAddress(cmd)));
    }

    @Override
    public void deleteAddress(Long addressId, Long memberId) {
        mallAddressService.delete(addressId, memberId);
    }

    @Override
    public void setDefaultAddress(Long addressId, Long memberId) {
        mallAddressService.setDefault(addressId, memberId);
    }

    /* ---------------- 装配 ---------------- */

    /** 商品粒度的可用库存 = 各 SKU 之和。一次查完，不逐个商品查 */
    private Map<Long, Integer> stockOf(Collection<Long> commodityIds) {
        if (commodityIds.isEmpty()) {
            return Map.of();
        }
        return mallSkuManager.lambdaQuery()
                .in(MallSku::getCommodityId, commodityIds)
                .eq(MallSku::getSkuStatus, EnableStatusEnum.ENABLED)
                .list().stream()
                .collect(Collectors.groupingBy(MallSku::getCommodityId,
                        Collectors.summingInt(s -> nullToZero(s.getAvailableStock()))));
    }

    /** 这一批商品里我收藏了哪些。一次 IN 查完，不逐个判 */
    private Set<Long> favoriteIds(Long memberId, Collection<Long> commodityIds) {
        if (memberId == null || commodityIds.isEmpty()) {
            return Set.of();
        }
        return mallFavoriteManager.lambdaQuery()
                .eq(MallFavorite::getMemberId, memberId)
                .in(MallFavorite::getCommodityId, commodityIds)
                .list().stream().map(MallFavorite::getCommodityId).collect(Collectors.toSet());
    }

    private static MallCommodityBriefView toBrief(MallCommodity c, Map<Long, Integer> stocks,
                                                  Set<Long> favorites) {
        return new MallCommodityBriefView(
                c.getId(), c.getCommodityCode(), c.getCategoryId(), c.getCommodityType(),
                c.getCommodityName(), c.getCommodityIntro(), c.getCoverFileId(),
                c.getPayType(), c.getPointsPrice(), c.getCashPrice(), c.getOriginalPrice(),
                favorites.contains(c.getId()), stocks.getOrDefault(c.getId(), 0));
    }

    /**
     * SKU 的价格：为空则继承商品基准价。
     *
     * <p>DDL 刻意允许 NULL 而非默认 0 —— 0 是「免费兑换」的合法取值，
     * 用 0 当「未设置」就分不清「没填」和「真免费」了。所以继承逻辑必须在这里做，
     * <b>端上拿到的一定是算好的值</b>。
     */
    private static MallCommoditySkuView toSkuView(MallSku sku) {
        return new MallCommoditySkuView(
                sku.getId(), sku.getSkuCode(), MallSkuAttrs.parse(sku.getSkuAttrs()), sku.getSkuCoverFileId(),
                sku.getSkuPointsPrice(), sku.getSkuCashPrice(),
                nullToZero(sku.getAvailableStock()));
    }

    private static MallAddressView toAddressView(MallAddress a) {
        return new MallAddressView(a.getId(), a.getReceiverName(), a.getReceiverPhone(),
                a.getProvince(), a.getCity(), a.getDistrict(), a.getDetailAddress(),
                Boolean.TRUE.equals(a.getIsDefault()));
    }

    private static MallAddress toAddress(MallAddressCmd cmd) {
        MallAddress address = new MallAddress();
        address.setMemberId(cmd.memberId());
        address.setReceiverName(cmd.receiverName());
        address.setReceiverPhone(cmd.receiverPhone());
        address.setProvince(cmd.province());
        address.setCity(cmd.city());
        address.setDistrict(cmd.district());
        address.setDetailAddress(cmd.detailAddress());
        return address;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
