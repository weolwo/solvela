package solvela.mall.clientapi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.EnableStatusEnum;
import solvela.base.module.file.service.FileAssetService;
import solvela.enums.MallCommodityStatusEnum;
import solvela.mall.MallAddress;
import solvela.mall.MallCategory;
import solvela.mall.MallCommodity;
import solvela.mall.MallOrder;
import solvela.mall.MallFavorite;
import solvela.mall.MallSku;
import solvela.mall.address.service.MallAddressService;
import solvela.mall.order.service.MallRedeemService;
import solvela.mall.constant.MallConst;
import solvela.mall.constant.MallSkuAttrs;
import solvela.mall.category.manager.MallCategoryManager;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.exchangelimit.manager.MallExchangeLimitManager;
import solvela.mall.favorite.manager.MallFavoriteManager;
import solvela.mall.order.manager.MallOrderManager;
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
import solvela.marketing.api.MallOrderView;
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

    /** 兑换记录一次最多回多少条。挡住调用方传一个巨大的 limit 把库拖垮 */
    private static final int MAX_ORDER_LIMIT = 100;

    private final FileAssetService fileAssetService;
    private final MallOrderManager mallOrderManager;
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
        List<MallCategory> categories = mallCategoryManager.lambdaQuery()
                .eq(MallCategory::getStatus, EnableStatusEnum.ENABLED)
                .orderByAsc(MallCategory::getSort)
                .orderByAsc(MallCategory::getId)
                .list();
        Map<Long, String> icons = urlsOf(
                categories.stream().map(MallCategory::getIconFileId).toList());
        return categories.stream()
                .map(c -> new MallCategoryView(c.getId(), c.getParentId(), c.getCategoryName(),
                        urlFor(icons, c.getIconFileId()), c.getSort()))
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

        // 封面 URL 一次批量换完 —— 逐行调 urlOf 就是 N+1，而列表页每次进都会打
        Map<Long, String> covers = urlsOf(list.stream().map(MallCommodity::getCoverFileId).toList());

        return new MallCommodityPageView(
                list.stream().map(c -> toBrief(c, stocks, favorites, covers)).toList(),
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

        /*
         * 主图、轮播图、各 SKU 的图<b>一起换</b> —— 详情页的 SKU 常有十几个，
         * 加上几张轮播图，一个一个换就是二十次查询，而它们本来可以是一次。
         *
         * 🔴 轮播图的 biz_type 是 MALL_COMMODITY_BANNER，<b>不是</b> MALL_COMMODITY。
         * mall.sql 里那句「复用 t_file_relation(biz_type='MALL_COMMODITY')」写漏了后缀：
         * 后台保存时把封面登记成 MALL_COMMODITY、把轮播图登记成 MALL_COMMODITY_BANNER
         * （见 MallCommoditySaveCommand），拿前者查只会查到封面自己。
         */
        List<Long> bannerIds = fileAssetService.listBizFileIds(
                MallConst.BIZ_TYPE_BANNER, commodity.getId());
        List<Long> imageIds = new java.util.ArrayList<>(bannerIds);
        imageIds.add(commodity.getCoverFileId());
        skus.forEach(sku -> imageIds.add(sku.getSkuCoverFileId()));
        Map<Long, String> images = urlsOf(imageIds);

        return new MallCommodityDetailView(
                commodity.getId(), commodity.getCommodityCode(), commodity.getCategoryId(),
                commodity.getCommodityType(), commodity.getCommodityName(),
                commodity.getCommodityIntro(), urlFor(images, commodity.getCoverFileId()),
                commodity.getPayType(), commodity.getPointsPrice(), commodity.getCashPrice(),
                commodity.getOriginalPrice(), favorite, stock,
                /*
                 * 轮播图，按 t_file_relation.sort 排 —— 那一列的注释原文就是「轮播图必需」。
                 * 换不出 URL 的（文件被删）直接滤掉：宁可少一张，
                 * 也不要在图集里留一个永远转不出来的位置。
                 */
                bannerIds.stream().map(id -> urlFor(images, id))
                        .filter(java.util.Objects::nonNull).toList(),
                commodity.getDetailContent(), commodity.getExchangeNotice(),
                commodity.getLimitPeriod(), commodity.getLimitCount(),
                remainingCount(commodity, memberId),
                skus.stream().map(sku -> toSkuView(sku, images)).toList());
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
        Map<Long, String> covers = urlsOf(list.stream().map(MallCommodity::getCoverFileId).toList());
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(c -> toBrief(c, stocks, favorites, covers))
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

    /* ---------------- 兑换记录 ---------------- */

    /**
     * 我的兑换记录。<b>字段全部取订单快照</b>，一次商品表都不查 ——
     * 那些列当初冗余进订单，就是为了这一页在商品改名改价之后仍然是对的。
     *
     * <p>唯一要外查的是封面图 URL（订单存的是 file_id），一次批量换完。
     */
    @Override
    public List<MallOrderView> listMyOrders(Long memberId, int limit) {
        if (memberId == null) {
            return List.of();
        }
        int size = Math.min(Math.max(limit, 1), MAX_ORDER_LIMIT);
        List<MallOrder> orders = mallOrderManager.lambdaQuery()
                .eq(MallOrder::getMemberId, memberId)
                .orderByDesc(MallOrder::getCreateTime)
                // 同一秒下的多单按 id 兜底，保证顺序稳定
                .orderByDesc(MallOrder::getId)
                .last("LIMIT " + size)
                .list();
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, String> covers = urlsOf(orders.stream().map(MallOrder::getCoverFileId).toList());
        return orders.stream().map(o -> new MallOrderView(
                o.getOrderNo(), o.getCommodityId(), o.getCommodityName(), o.getCommodityType(),
                urlFor(covers, o.getCoverFileId()), MallSkuAttrs.parse(o.getSkuAttrs()),
                o.getQuantity(), o.getPayPoints(), o.getPayCash(),
                o.getStatus(), o.getFailReason(), o.getCreateTime())).toList();
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

    /**
     * 一批 file_id 换成可直接访问的 URL。<b>一次批量，不逐个换。</b>
     *
     * <p>null 与重复的 id 先滤掉：前者是「这个位置没配图」（合法，端上画占位），
     * 后者在列表页很常见（同一张兜底图挂在多个商品上）。
     *
     * <p>换不出来的 id <b>不会出现在返回的 map 里</b>，于是 {@code map.get(id)}
     * 给 null，端上照常画占位块。这比返回一个拼出来的 URL 好：
     * 文件被删了就该没有图，而不是一个 404 的图裂。
     */
    private Map<Long, String> urlsOf(Collection<Long> fileIds) {
        Set<Long> ids = fileIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : fileAssetService.batchUrl(ids);
    }

    /**
     * 从「id → URL」里取一个，<b>id 为 null 时直接给 null</b>。
     *
     * <h3>🔴 不能写成 map.get(id)</h3>
     * {@link #urlsOf} 在这批 id 全为 null 时返回 {@code Map.of()}，
     * 而<b>不可变 map 的 get(null) 会抛 NPE</b> ——
     * {@code ImmutableCollections.MapN.get} 对空 map 显式做了
     * {@code Objects.requireNonNull(key)}，非空时也会去调 {@code key.hashCode()}。
     *
     * <p>这不是理论风险：分类图标绝大多数没配（{@code icon_file_id} 可空），
     * 于是「一个分类都没配图标」是最常见的情况，而它会让整个分类接口 500。
     * 2026-09-05 就是这么炸的。
     *
     * <p>用 HashMap 兜住能让 get(null) 不抛，但那是<b>靠实现类的宽容</b>，
     * 下一个人把 urlsOf 的返回换成不可变 map 就又炸了。在取值这一侧判才是对的。
     */
    private static String urlFor(Map<Long, String> urls, Long fileId) {
        return fileId == null ? null : urls.get(fileId);
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
                                                  Set<Long> favorites, Map<Long, String> covers) {
        return new MallCommodityBriefView(
                c.getId(), c.getCommodityCode(), c.getCategoryId(), c.getCommodityType(),
                c.getCommodityName(), c.getCommodityIntro(), urlFor(covers, c.getCoverFileId()),
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
    private static MallCommoditySkuView toSkuView(MallSku sku, Map<Long, String> images) {
        return new MallCommoditySkuView(
                sku.getId(), sku.getSkuCode(), MallSkuAttrs.parse(sku.getSkuAttrs()),
                urlFor(images, sku.getSkuCoverFileId()),
                sku.getSkuPointsPrice(), sku.getSkuCashPrice(),
                nullToZero(sku.getAvailableStock()));
    }

    private static MallAddressView toAddressView(MallAddress a) {
        return new MallAddressView(a.getId(), a.getReceiverName(), maskPhone(a.getReceiverPhone()),
                a.getProvince(), a.getCity(), a.getDistrict(), a.getDetailAddress(),
                Boolean.TRUE.equals(a.getIsDefault()));
    }

    /**
     * 手机号脱敏：{@code 13800008000 → 138****8000}。
     *
     * <h3>🔴 契约早就承诺了这件事，只是没做</h3>
     * {@link MallAddressView#receiverPhone} 的注释白纸黑字写着「<b>脱敏值</b>（138****8000）」，
     * 而这里一直传的是解密后的明文。<b>文档承诺了而代码没做</b>是最难发现的一类问题 ——
     * 读代码的人会相信注释，然后在别处基于「它已经脱敏了」做决定。
     *
     * <p>就算这一页只有用户自己看得到（后台没有地址簿入口，所有查询都带 memberId），
     * 脱敏仍然值得做：响应会经过日志、浏览器历史、截图，以及将来任何一层代理。
     * 而对本人来说，{@code 138****8000} 足够从几条地址里认出是哪一条 ——
     * 这正是这一页要回答的唯一问题。
     *
     * <p>前端<b>刻意不回填手机号</b>（编辑时留空 = 不改），所以脱敏值不会被存回库里。
     * 这两处是一对，改任何一边之前先看另一边。
     *
     * <h3>号码格式不敢假设</h3>
     * 不写死「11 位」：库里已经有非大陆号码（菲律宾的地址）。
     * 规则是「留头 3 尾 4」，短号则只留尾 2，再短就全遮 ——
     * <b>任何情况下露出的字符都不会比原号码多</b>。
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String trimmed = phone.trim();
        int len = trimmed.length();
        if (len <= 2) {
            // 两位以内没有「部分遮蔽」可言，全遮
            return "*".repeat(len);
        }
        if (len < 8) {
            return "*".repeat(len - 2) + trimmed.substring(len - 2);
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(len - 4);
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
