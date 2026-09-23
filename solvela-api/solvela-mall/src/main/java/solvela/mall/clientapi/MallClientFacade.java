package solvela.mall.clientapi;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.member.api.DeliveryReceiverCmd;
import solvela.member.api.DeliveryFillResult;
import solvela.member.api.DeliveryApi;
import solvela.marketing.api.MallDeliveryFillResult;
import solvela.enums.EnableStatusEnum;
import solvela.base.module.file.service.FileAssetService;
import solvela.enums.MallCommodityStatusEnum;
import solvela.member.MemberGrade;
import solvela.member.grade.service.MemberGradeResolver;
import solvela.mall.commodity.MallGradeGate;
import solvela.mall.commodity.GradeDiscount;
import solvela.mall.commodity.MallGradeDiscountResolver;
import solvela.mall.commodity.MallPricing;
import solvela.mall.MallAddress;
import solvela.mall.MallCategory;
import solvela.mall.MallCommodity;
import solvela.mall.MallOrder;
import solvela.mall.MallFavorite;
import solvela.mall.MallSku;
import solvela.mall.address.service.MallAddressService;
import solvela.mall.order.service.MallRedeemService;
import solvela.mall.pay.MallPayService;
import solvela.marketing.api.MallPayResult;
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
    private final MallGradeGate mallGradeGate;
    private final MemberGradeResolver memberGradeResolver;
    private final MallGradeDiscountResolver mallGradeDiscountResolver;
    private final MallSkuManager mallSkuManager;
    private final MallFavoriteManager mallFavoriteManager;
    private final MallExchangeLimitManager mallExchangeLimitManager;
    private final MallAddressService mallAddressService;
    private final MallRedeemService mallRedeemService;
    private final MallPayService mallPayService;
    /**
     * 资产域的履约单契约。
     *
     * <p>🔴 这<b>不是</b> solvela-ledger —— 商城的 pom 里刻意没有它
     * （{@code MallLedgerBoundaryTest} 守着）。这里注入的是 member-api 里的接口，
     * 今天解析成同进程的 bean，资产域独立出去之后解析成 HTTP 代理，本类一行不改。
     */
    private final DeliveryApi deliveryApi;

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
        Map<Long, List<MallSku>> skus = onSaleSkusOf(list.stream().map(MallCommodity::getId).toList());
        Set<Long> favorites = favoriteIds(cmd.memberId(),
                list.stream().map(MallCommodity::getId).toList());

        // 封面 URL 一次批量换完 —— 逐行调 urlOf 就是 N+1，而列表页每次进都会打
        Map<Long, String> covers = urlsOf(list.stream().map(MallCommodity::getCoverFileId).toList());

        // 等级视角同理：整页共用一份，不按商品逐个查（覆盖价也一次 IN 查完）
        GradeLens lens = gradeLens(cmd.memberId(), list.stream().map(MallCommodity::getId).toList());

        return new MallCommodityPageView(
                list.stream().map(c -> toBrief(c, lens, skus, favorites, covers)).toList(),
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
        List<MallSku> skus = listOnSaleSkus(commodityId);

        /*
         * 🔴 轮播图的 biz_type 是 MALL_COMMODITY_BANNER，<b>不是</b> MALL_COMMODITY。
         * mall.sql 里那句「复用 t_file_relation(biz_type='MALL_COMMODITY')」写漏了后缀：
         * 后台保存时把封面登记成 MALL_COMMODITY、把轮播图登记成 MALL_COMMODITY_BANNER
         * （见 MallCommoditySaveCommand），拿前者查只会查到封面自己。
         */
        List<Long> bannerIds = fileAssetService.listBizFileIds(
                MallConst.BIZ_TYPE_BANNER, commodity.getId());

        return toDetailView(commodity, skus, bannerIds,
                loadAllImages(commodity, skus, bannerIds),
                !favoriteIds(memberId, List.of(commodityId)).isEmpty(),
                remainingCount(commodity, memberId), gradeLens(memberId, List.of(commodityId)));
    }

    /**
     * 一次请求里的「等级视角」：这个人是几级、各档叫什么名字。
     *
     * <h3>🔴 每次请求只查一次，不是每件商品查一次</h3>
     * 商品列表一页 20 条，按商品查的话就是 20 次会员等级查询 + 20 次等级配置查询 ——
     * 而那两个答案在同一次请求里<b>完全不会变</b>。
     * 这类「循环里查同一个东西」的写法不报错，只是列表页慢，
     * 而慢到被发现时通常已经在生产上了。
     */
    private record GradeLens(int memberGrade, Map<Integer, String> gradeNames,
                             GradeDiscount discount) {

        boolean locked(MallCommodity commodity) {
            return MallGradeGate.requiredGrade(commodity) > memberGrade;
        }

        /** 专享商品的等级名；不限时为 null，端上据此决定要不要出「专享」标 */
        String nameOf(MallCommodity commodity) {
            int required = MallGradeGate.requiredGrade(commodity);
            return required == 0 ? null : gradeNames.get(required);
        }
    }

    /**
     * 装配等级视角。
     *
     * <p>⚠️ 等级名查不到时返回 null 而不是编一个（比如「等级 3」）：
     * 运营删掉一档之后，端上显示「等级 3 专享」只会让人困惑，
     * 而 null 会让专享标整个不出现 —— 商品仍然兑不了（服务端照拦），
     * 只是不再宣传一个已经不存在的等级。
     */
    private GradeLens gradeLens(Long memberId, java.util.Collection<Long> commodityIds) {
        return gradeLensOf(mallGradeGate.gradeOf(memberId), commodityIds);
    }

    /**
     * 直接按等级号装配视角。
     *
     * <p>⚠️ 与 {@link #gradeLens} 的区别只有一处：那个要先把 memberId 翻成等级
     *（含保级缓冲期那套判断），这个是<b>已经知道等级</b>。
     * 管理端的 C 端预览走这条 —— 它没有会员，只有运营选的一个「假装是几级」。
     */
    private GradeLens gradeLensOf(int grade, java.util.Collection<Long> commodityIds) {
        List<MemberGrade> grades = memberGradeResolver.enabledGrades();
        Map<Integer, String> names = grades.stream()
                .filter(g -> g.getGradeCode() != null && g.getGradeName() != null)
                .collect(Collectors.toMap(MemberGrade::getGradeCode, MemberGrade::getGradeName,
                        (a, b) -> a));
        /*
         * ⚠️ 折扣走【已经在手上的】grades，不再查一次库 —— 这两个答案本来就是同一份数据。
         *    列表页一页 20 件，这里多查一次就是多 20 次（如果哪天有人把它挪进循环）。
         */
        return new GradeLens(grade, names, mallGradeDiscountResolver.of(grade, grades, commodityIds));
    }

    /**
     * 管理端「C 端预览」：拿一个<b>还没落库的草稿</b>渲染出 C 端详情页的样子。
     *
     * <h3>🔴 它存在的全部理由是「不要有第二份实现」</h3>
     * 在此之前预览是 admin-web 里的一段 JS，自己算最低价、自己拼对价文案。
     * 那段代码和真实 C 端漂过一次：2026-09-23 发现<b>预览是对的、C 端是错的</b>
     *（C 端发商品基准价，某件商品因此显示成真实价格的 100 倍），
     * 两边对不上而没有任何机制会发现。
     *
     * <p>现在预览走的就是 {@link #toDetailView} —— 和 {@link #getCommodity} 同一个方法。
     * 价格规则、角标、划线、「起」、专享锁，全都<b>不可能</b>再各算一份。
     *
     * <h3>⚠️ 三个和真实 C 端刻意不同的地方</h3>
     * <ol>
     *   <li><b>草稿没落库</b>，所以传进来的是表单映射出的瞬时实体，不是查出来的；</li>
     *   <li><b>没有会员</b>，等级由运营在预览里选。收藏恒为 false，
     *       「本周期还可兑几件」按<b>一个从没兑过的人</b>算 —— 那是新用户看到的样子；</li>
     *   <li><b>新建商品还没有 id</b>，所以查不到单品覆盖价（它按 commodity_id 配）。
     *       这时预览只反映折扣率。保存一次之后就准了。</li>
     * </ol>
     *
     * @param gradeCode 假装是几级；{@code 0} = 未登录 / 普通会员
     */
    public MallCommodityDetailView renderPreview(MallCommodity draft, List<MallSku> draftSkus,
                                                 List<Long> bannerIds, int gradeCode) {
        /*
         * 🔴 只留在售规格，和 listOnSaleSkus 同一个判据。
         * 不过滤的话，一个停用的便宜规格会把预览价拉低 —— 而用户根本看不到它。
         */
        List<MallSku> onSale = draftSkus.stream()
                .filter(sku -> EnableStatusEnum.ENABLED == sku.getSkuStatus())
                .toList();
        List<Long> ids = draft.getId() == null ? List.of() : List.of(draft.getId());
        Integer remaining = draft.getLimitCount() == null || draft.getLimitCount() <= 0
                ? null : draft.getLimitCount();
        return toDetailView(draft, onSale, bannerIds,
                loadAllImages(draft, onSale, bannerIds),
                false, remaining, gradeLensOf(Math.max(0, gradeCode), ids));
    }

    /** 在售 SKU，按运营配的 sort 排；sort 相同按 id 兜底，保证两次请求顺序一致 */
    private List<MallSku> listOnSaleSkus(Long commodityId) {
        return mallSkuManager.lambdaQuery()
                .eq(MallSku::getCommodityId, commodityId)
                .eq(MallSku::getSkuStatus, EnableStatusEnum.ENABLED)
                .orderByAsc(MallSku::getSort)
                .orderByAsc(MallSku::getId)
                .list();
    }

    /**
     * 组装详情视图。
     *
     * <p>总库存是<b>各在售 SKU 之和</b>，不读商品表 —— 商品表上没有这个数，
     * 而「有货没货」这个判断只有 SKU 说了算。
     */
    private MallCommodityDetailView toDetailView(MallCommodity commodity, List<MallSku> skus,
                                                 List<Long> bannerIds, Map<Long, String> images,
                                                 boolean favorite, Integer remaining, GradeLens lens) {
        int stock = stockOf(skus);
        /*
         * ⚠️ 详情页顶上那个价也是「最便宜那个规格」，和卡片同一条规则。
         *    用户还没选规格时总要显示一个数，显示商品基准价的话，
         *    从卡片点进来价格会当场变一次 —— 而他还什么都没做。
         */
        MallPricing.CardPrice price = MallPricing.cheapest(commodity, skus, lens.discount());
        return new MallCommodityDetailView(
                commodity.getId(), commodity.getCommodityCode(), commodity.getCategoryId(),
                commodity.getCommodityType(), commodity.getCommodityName(),
                commodity.getCommodityIntro(), urlFor(images, commodity.getCoverFileId()),
                commodity.getPayType(),
                price.points(), price.listPoints(),
                // 同 toBrief：发的是这件商品实际打了几折
                MallPricing.effectivePercent(price.listPoints(), price.points()),
                price.cash(), price.varies(),
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
                remaining,
                skus.stream().map(sku -> toSkuView(sku, commodity, lens.discount(), images)).toList(),
                MallGradeGate.requiredGrade(commodity), lens.nameOf(commodity),
                lens.locked(commodity));
    }

    /**
     * 主图、轮播图、各 SKU 的图<b>一次全换成 URL</b>。
     *
     * <p>详情页的 SKU 常有十几个，加上几张轮播图，一个一个换就是二十次查询 ——
     * 而它们本来可以是一次。这个页面是 C 端点击最密集的地方之一。
     */
    private Map<Long, String> loadAllImages(MallCommodity commodity, List<MallSku> skus, List<Long> bannerIds) {
        List<Long> imageIds = new java.util.ArrayList<>(bannerIds);
        imageIds.add(commodity.getCoverFileId());
        skus.forEach(sku -> imageIds.add(sku.getSkuCoverFileId()));
        return urlsOf(imageIds);
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
        Map<Long, List<MallSku>> skus = onSaleSkusOf(ids);
        Set<Long> favorites = Set.copyOf(ids);
        // 按收藏时间倒序还原顺序 —— IN 查出来的顺序是不确定的
        Map<Long, MallCommodity> byId = list.stream()
                .collect(Collectors.toMap(MallCommodity::getId, Function.identity()));
        Map<Long, String> covers = urlsOf(list.stream().map(MallCommodity::getCoverFileId).toList());
        GradeLens lens = gradeLens(memberId, ids);
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(c -> toBrief(c, lens, skus, favorites, covers))
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
    public MallPayResult pay(String orderNo, Long memberId) {
        return mallPayService.pay(orderNo, memberId);
    }

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

    /**
     * 用地址簿里的一个地址补填履约单。<b>本类唯一一处跨到资产域的调用。</b>
     *
     * <h3>为什么是商城来做这件事</h3>
     * 只有商城解析得了 {@code addressId} —— 地址簿是它的表。
     * 资产域收的是<b>明文三件套</b>，不认识地址 id。
     * 分工与 {@code MallFulfillService.buildCmd} 完全一致，那条路已经跑通很久了。
     */
    @Override
    public MallDeliveryFillResult fillDeliveryAddress(Long deliveryId, Long memberId, Long addressId) {
        /*
         * 🔴 getOwned 带 memberId —— 少了它，这个接口就成了
         *    「拿别人的 addressId 去猜别人住哪」：填进去之后再查一次履约单就读出来了。
         */
        MallAddress address = mallAddressService.getOwned(addressId, memberId);
        if (address == null) {
            // 不存在和不是你的给同一句话，否则能拿它探测别人的地址 id
            return new MallDeliveryFillResult(false, "收货地址不存在");
        }
        /*
         * ⚠️ 这里传的是【解密后的明文】—— address 实体上的三列挂了 PiiTypeHandler，
         *    读出来就是明文。资产域落库时自己再加密一次。
         *
         *    别顺手在这里脱敏：脱敏值是给【看】的，寄快递要的是真号码。
         *    下发给 C 端的那条路才脱敏（toAddressView），两者刻意不共用。
         */
        DeliveryFillResult result = deliveryApi.fillReceiver(new DeliveryReceiverCmd(
                deliveryId, memberId,
                address.getReceiverName(),
                address.getReceiverPhone(),
                fullAddress(address)));
        // 措辞原样透传：同一件事不该有两种说法
        return new MallDeliveryFillResult(result.accepted(), result.message());
    }

    /**
     * 省市区 + 详细门牌拼成一条。
     *
     * <p>⚠️ 与 {@code MallFulfillService.fullAddress} 是同一段逻辑的第二份。
     * 抽不掉的原因很土：那个类在 {@code order} 包、本类在 {@code clientapi} 包，
     * 而它只有四行。真要合并该往下沉成一个 {@code MallAddressUtil}，
     * <b>连同 MallFulfillService 一起改</b> —— 只改一边就是制造漂移。
     */
    private static String fullAddress(MallAddress address) {
        String joined = org.apache.commons.lang3.StringUtils.defaultString(address.getProvince())
                + org.apache.commons.lang3.StringUtils.defaultString(address.getCity())
                + org.apache.commons.lang3.StringUtils.defaultString(address.getDistrict())
                + org.apache.commons.lang3.StringUtils.defaultString(address.getDetailAddress());
        return org.apache.commons.lang3.StringUtils.trimToNull(joined);
    }

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
    /**
     * 一页商品的在售 SKU，按商品分组。<b>一次查完。</b>
     *
     * <p>🔴 库存和价格都从这一份里算，<b>不要为价格再查一次</b>。
     * 2026-09-23 之前这个方法叫 stockOf，查回全部 SKU 之后
     * 只用来求了个库存和就把它们扔了 —— 而卡片的价当时直接发商品基准价，
     * 于是一台实际要 ¥5000 的手机在列表上写着「¥0.00」。
     * 数据本来就在手上，只是没人用。
     */
    private Map<Long, List<MallSku>> onSaleSkusOf(Collection<Long> commodityIds) {
        if (commodityIds.isEmpty()) {
            return Map.of();
        }
        return mallSkuManager.lambdaQuery()
                .in(MallSku::getCommodityId, commodityIds)
                .eq(MallSku::getSkuStatus, EnableStatusEnum.ENABLED)
                .list().stream()
                .collect(Collectors.groupingBy(MallSku::getCommodityId));
    }

    /** 可用库存之和。空列表算 0 —— 那件商品已经兑完了 */
    private static int stockOf(List<MallSku> skus) {
        return skus == null ? 0 : skus.stream().mapToInt(s -> nullToZero(s.getAvailableStock())).sum();
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

    private static MallCommodityBriefView toBrief(MallCommodity c, GradeLens lens,
                                                  Map<Long, List<MallSku>> skus,
                                                  Set<Long> favorites, Map<Long, String> covers) {
        List<MallSku> onSale = skus.getOrDefault(c.getId(), List.of());
        /*
         * 🔴 卡片的价是【最便宜那个在售规格的实际价】，不是商品表上那两列。
         *
         * 商品表上的基准价只是 SKU 的继承来源，不保证有人按它卖 ——
         * 直接发它，一台实际要 ¥5000 的手机会在列表上写着「¥0.00」（库里真有这样的数据）。
         * 而卡片是用户决定要不要点进去的唯一依据。
         *
         * 加等级价那次这里也踩过一半：原先发 c.getPointsPrice()，
         * 于是「列表按原价、详情和下单按折后价」，用户看到列表 10000、点进去 8800。
         */
        MallPricing.CardPrice price = MallPricing.cheapest(c, onSale, lens.discount());
        return new MallCommodityBriefView(
                c.getId(), c.getCommodityCode(), c.getCategoryId(), c.getCommodityType(),
                c.getCommodityName(), c.getCommodityIntro(), urlFor(covers, c.getCoverFileId()),
                c.getPayType(),
                price.points(), price.listPoints(),
                /*
                 * 🔴 发出去的是【这件商品实际打了几折】，不是这个人的折扣率。
                 * 配了覆盖价的商品，折扣率还是 92 而实际价可能是 888 —— 按折扣率
                 * 挂「9.2折」就是在一件打了 0.9 折的商品上说假话；反过来，
                 * 退出等级折扣的商品折扣率仍是 92 而价格一分没少，同样是假话。
                 */
                MallPricing.effectivePercent(price.listPoints(), price.points()),
                price.cash(), price.varies(), c.getOriginalPrice(),
                favorites.contains(c.getId()), stockOf(onSale),
                MallGradeGate.requiredGrade(c), lens.nameOf(c), lens.locked(c));
    }

    /**
     * SKU 的价格：为空则继承商品基准价。
     *
     * <p>DDL 刻意允许 NULL 而非默认 0 —— 0 是「免费兑换」的合法取值，
     * 用 0 当「未设置」就分不清「没填」和「真免费」了。所以继承逻辑必须在这里做，
     * <b>端上拿到的一定是算好的值</b>。
     *
     * <h3>🔴 2026-09-22 订正：上面这句话此前是假的</h3>
     * 这个方法原来把 {@code sku.getSkuPointsPrice()} <b>原样传出去</b>，nullable 照旧。
     * 于是「继承」这件事跑到了端上，而两个页面只有一个做了：
     * <ul>
     *   <li>{@code ProductView.vue} 写了 {@code sku?.pointsPrice ?? 商品基准价}，蒙对了；</li>
     *   <li>{@code RedeemView.vue} 写的是 {@code (sku?.pointsPrice ?? 0) * 数量} ——
     *       <b>SKU 没填价时兑换页显示 0 分，而服务端照基准价扣</b>。</li>
     * </ul>
     * 库里现在就有这样一条（SKU 8「HUAWEI WATCH GT 7 Pro（46mm）」，基准价 19990），
     * 只是它库存为 0 才没被点到。
     *
     * <p>⚠️ 判据是「价格由谁算」，不是「哪个页面写错了」：只要契约里还传 null，
     * 每一个新页面都得自己记得继承一次，而忘记的那一次不报错，
     * 只是<b>价格显示成 0</b> —— 这是最不该让端上自由发挥的那类值。
     *
     * <p>规则本体在 {@link MallPricing}，与下单扣减共用同一份。
     */
    private static MallCommoditySkuView toSkuView(MallSku sku, MallCommodity commodity,
                                                  GradeDiscount discount, Map<Long, String> images) {
        return new MallCommoditySkuView(
                sku.getId(), sku.getSkuCode(), MallSkuAttrs.parse(sku.getSkuAttrs()),
                urlFor(images, sku.getSkuCoverFileId()),
                MallPricing.points(sku, commodity, discount),
                MallPricing.listPoints(sku, commodity),
                MallPricing.cash(sku, commodity),
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
