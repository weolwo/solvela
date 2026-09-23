package solvela.mall.commodity.service;

import solvela.enums.MallPayTypeEnum;
import solvela.enums.MallCommodityStatusEnum;
import solvela.enums.EnableStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.domain.PageResult;
import solvela.exception.BusinessException;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.module.file.service.FileAssetService;
import solvela.base.module.file.service.RichTextImageExtractor;
import solvela.enums.PrizeTypeEnum;
import solvela.mall.MallCategory;
import solvela.mall.category.manager.MallCategoryManager;
import solvela.mall.clientapi.MallClientFacade;
import solvela.marketing.api.MallCommodityDetailView;
import solvela.mall.commodity.dao.MallCommodityDao;
import solvela.mall.MallCommodity;
import solvela.mall.commodity.domain.query.MallCommodityQuery;
import solvela.mall.commodity.domain.command.MallCommoditySaveCommand;
import solvela.mall.commodity.domain.command.MallCommoditySkuCommand;
import solvela.mall.MallGradePrice;
import solvela.mall.commodity.dao.MallGradePriceDao;
import solvela.mall.commodity.domain.dto.MallCommodityDetailDTO;
import solvela.mall.commodity.domain.dto.MallCommoditySkuDTO;
import solvela.mall.commodity.domain.dto.MallCommodityDTO;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.constant.MallConst;
import solvela.mall.constant.MallSkuAttrs;
import solvela.mall.MallOrder;
import solvela.mall.order.manager.MallOrderManager;
import solvela.mall.MallSku;
import solvela.mall.sku.manager.MallSkuManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商城-商品 Service
 *
 * <p>写操作只有一个入口 {@link #save}：主表 + SKU + 图片引用同事务落库。
 * 生成器留下的 add/update 已删掉 —— 它们只能写主表，用它们建出来的商品没有 SKU，
 * 而「没有 SKU 的商品」在下单链路里是不存在的形态（DDL：无规格商品也必须有一行 {@code {}}）。
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MallCommodityService {

    private final MallCommodityDao mallCommodityDao;
    /*
     * ⚠️ 管理端服务注入了 C 端 facade，只为了【预览】——
     *    目的正是让预览走 C 端那一份渲染，而不是在别处再写一份。
     *    别拿它做别的事：保存/查询走的是本类自己的路。
     */
    private final MallClientFacade mallClientFacade;
    private final MallGradePriceDao mallGradePriceDao;
    private final MallCommodityManager mallCommodityManager;
    private final MallSkuManager mallSkuManager;
    private final MallCategoryManager mallCategoryManager;
    /** 删除守卫要看这个商品有没有产生过订单 —— 订单在，商品就不能删 */
    private final MallOrderManager mallOrderManager;
    private final FileAssetService fileAssetService;

    /** 商品类型允许的取值：对齐 PrizeTypeEnum，但不含 SCORE（积分换积分无意义）与 LOTTERY / CUSTOM */
    private static final Set<String> COMMODITY_TYPES = Set.of(
            PrizeTypeEnum.PHYSICAL.name(), PrizeTypeEnum.COUPON.name(), PrizeTypeEnum.BALANCE.name());

    private static final Set<String> LIMIT_PERIODS = Set.of(
            MallConst.LIMIT_PERIOD_LIFETIME, MallConst.LIMIT_PERIOD_DAILY,
            MallConst.LIMIT_PERIOD_WEEKLY, MallConst.LIMIT_PERIOD_MONTHLY);

    // ================================================================== 查询

    /**
     * 分页查询
     */
    public PageResult<MallCommodityDTO> queryPage(MallCommodityQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MallCommodityDTO> list = mallCommodityDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 商品编辑页的聚合回显：主表 + SKU + 轮播图，一次查完。
     *
     * <p>拆成三个接口的话，编辑页打开要发三个请求，而且「主表回来了、SKU 还没回来」
     * 这一帧里表单是可编辑的 —— 手快的人能在空 SKU 状态下点保存。
     */
    public MallCommodityDetailDTO detail(Long id) {
        MallCommodity commodity = mallCommodityManager.getById(id);
        if (commodity == null) {
            throw new BusinessException("商品不存在");
        }

        MallCommodityDetailDTO vo = copyOwnColumns(commodity);

        MallCategory category = mallCategoryManager.getById(commodity.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getCategoryName());

        // 轮播图存在 t_file_relation 里（顺序即 sort），不是商品表的列
        vo.setBannerFileIds(new ArrayList<>(fileAssetService.listBizFileIds(MallConst.BIZ_TYPE_BANNER, id)));
        vo.setSkuList(listSkuVO(id));
        return vo;
    }

    /**
     * 逐列搬 t_mall_commodity 自己的字段。
     *
     * <p>刻意<b>不</b>用 BeanUtil 反射拷贝：这张表的列会随业务加，而反射拷贝在加了新列
     * 却忘了加 DTO 字段时是<b>静默漏掉</b>的 —— 表现是编辑页少一个输入框，
     * 保存一次就把那一列清空了。手写的话新列不出现在这里一眼就能看出来。
     */
    private static MallCommodityDetailDTO copyOwnColumns(MallCommodity commodity) {
        MallCommodityDetailDTO vo = new MallCommodityDetailDTO();
        vo.setId(commodity.getId());
        vo.setCommodityCode(commodity.getCommodityCode());
        vo.setCategoryId(commodity.getCategoryId());
        vo.setCommodityType(commodity.getCommodityType());
        vo.setAssetRef(commodity.getAssetRef());
        vo.setCommodityName(commodity.getCommodityName());
        vo.setCommodityIntro(commodity.getCommodityIntro());
        vo.setCoverFileId(commodity.getCoverFileId());
        vo.setDetailContent(commodity.getDetailContent());
        vo.setExchangeNotice(commodity.getExchangeNotice());
        vo.setPayType(commodity.getPayType());
        vo.setOriginalPrice(commodity.getOriginalPrice());
        vo.setPointsPrice(commodity.getPointsPrice());
        vo.setCashPrice(commodity.getCashPrice());
        vo.setLimitPeriod(commodity.getLimitPeriod());
        vo.setLimitCount(commodity.getLimitCount());
        vo.setMinGrade(commodity.getMinGrade());
        vo.setGradePriceFlag(commodity.getGradePriceFlag());
        vo.setStartTime(commodity.getStartTime());
        vo.setEndTime(commodity.getEndTime());
        vo.setStatus(commodity.getStatus());
        vo.setIsHome(commodity.getIsHome());
        vo.setSort(commodity.getSort());
        vo.setSoldCount(commodity.getSoldCount());
        vo.setCreateBy(commodity.getCreateBy());
        vo.setCreateTime(commodity.getCreateTime());
        vo.setUpdateBy(commodity.getUpdateBy());
        vo.setUpdateTime(commodity.getUpdateTime());
        return vo;
    }

    /**
     * 生成一个库里不存在的商品编码，供编辑页展示。
     *
     * <p>只是<b>候选值</b>：运营也可以不点这个按钮、自己手输一个（铁律 8 明说两条路径都要支持）。
     * 真正的判重在 {@link #save} 里做 —— 中间隔着填表单的几分钟，
     * 这里判过不代表保存时还没被占走。
     */
    public String generateCommodityCode() {
        return SolvelaCodeUtil.generateUniqueBizCode(
                SolvelaCodeUtil.BizCodePrefix.MALL_COMMODITY, this::existsByCommodityCode);
    }

    /**
     * 批量生成互不重复的 SKU 编码，供前端「生成/刷新 SKU 表」一次性取走。
     *
     * <p>为什么不让前端自己随机：铁律 8 的唯一真源是 {@link SolvelaCodeUtil}，
     * 前端自造的话前缀规则、字符集、长度会各写一套，迟早和后端漂开。
     * 一次要 N 个也只发一个请求，不比前端本地生成慢。
     *
     * <p>同样只是<b>给运营看的候选值</b>：真正的判重在保存时做，中间隔着填表单的几分钟。
     */
    public List<String> generateSkuCodes(int count) {
        if (count <= 0 || count > MallConst.MAX_SKU_COUNT) {
            throw new BusinessException("一次最多生成 " + MallConst.MAX_SKU_COUNT + " 个编码");
        }
        Set<String> codes = new LinkedHashSet<>();
        // 本批内部也要互不重复：SolvelaCodeUtil 只保证「库里没有」，不知道这一批已经发过什么
        while (codes.size() < count) {
            codes.add(SolvelaCodeUtil.generateUniqueBizCode(
                    SolvelaCodeUtil.BizCodePrefix.MALL_SKU, this::existsBySkuCode));
        }
        return new ArrayList<>(codes);
    }

    public boolean existsByCommodityCode(String commodityCode) {
        return mallCommodityManager.lambdaQuery()
                .eq(MallCommodity::getCommodityCode, commodityCode).exists();
    }

    private boolean existsBySkuCode(String skuCode) {
        return mallSkuManager.lambdaQuery().eq(MallSku::getSkuCode, skuCode).exists();
    }

    private List<MallCommoditySkuDTO> listSkuVO(Long commodityId) {
        return listDbSku(commodityId).stream().map(sku -> {
            MallCommoditySkuDTO vo = new MallCommoditySkuDTO();
            vo.setId(sku.getId());
            vo.setSkuCode(sku.getSkuCode());
            vo.setSkuAttrs(parseSkuAttrs(sku.getSkuAttrs()));
            vo.setSkuCoverFileId(sku.getSkuCoverFileId());
            vo.setSkuPointsPrice(sku.getSkuPointsPrice());
            vo.setSkuCashPrice(sku.getSkuCashPrice());
            vo.setTotalStock(sku.getTotalStock());
            vo.setLockedStock(sku.getLockedStock());
            vo.setSoldCount(sku.getSoldCount());
            vo.setAvailableStock(sku.getAvailableStock());
            vo.setSkuStatus(sku.getSkuStatus());
            vo.setSort(sku.getSort());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<MallSku> listDbSku(Long commodityId) {
        if (commodityId == null) {
            return List.of();
        }
        return mallSkuManager.lambdaQuery()
                .eq(MallSku::getCommodityId, commodityId)
                .orderByAsc(MallSku::getSort)
                .orderByAsc(MallSku::getId)
                .list();
    }

    /** 解析实现搬到了 {@link MallSkuAttrs}：C 端门面要用同一份，各写一遍会漂 */
    private Map<String, String> parseSkuAttrs(String json) {
        return MallSkuAttrs.parse(json);
    }

    // ================================================================== 保存

    /**
     * 商品聚合保存：主表 + SKU（整表 diff）+ 图片引用，同一事务。
     *
     * <p><b>校验全部在写库之前做完</b>，所以那些提前抛出不依赖回滚 ——
     * 这条顺序不要打乱：一旦「写一半再校验」，报错信息里的商品 id 就成了一个
     * 事务结束后并不存在的号码，排查时对不上任何东西。
     *
     * @return 商品 id。<b>新建时前端要靠它把地址栏换成编辑态</b>，
     * 否则运营保存后又点一次保存，会建出第二个商品。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(MallCommoditySaveCommand form, String operator) {
        MallCategory category = requireCategory(form.getCategoryId());
        checkCommodityFields(form);
        Shelf shelf = resolveShelf(form);

        MallCommodity existing = loadExisting(form.getId());
        String commodityCode = resolveCommodityCode(form, existing);
        SkuPlan skuPlan = planSkus(form);
        if (shelf.status() == MallCommodityStatusEnum.ON) {
            checkReadyForShelf(category, skuPlan.forms());
        }

        Long commodityId = saveCommodity(form, existing, commodityCode, shelf, operator);
        saveSkus(commodityId, form, skuPlan, operator);
        confirmFileRelations(commodityId, form, skuPlan.forms());
        return commodityId;
    }

    private MallCategory requireCategory(Long categoryId) {
        MallCategory category = mallCategoryManager.getById(categoryId);
        if (category == null) {
            throw new BusinessException("商品分类不存在");
        }
        return category;
    }

    private MallCommodity loadExisting(Long id) {
        if (id == null) {
            return null;
        }
        MallCommodity existing = mallCommodityManager.getById(id);
        if (existing == null) {
            throw new BusinessException("商品不存在，可能已被删除");
        }
        return existing;
    }

    /**
     * 主表字段校验。
     */
    private void checkCommodityFields(MallCommoditySaveCommand form) {
        if (!COMMODITY_TYPES.contains(form.getCommodityType())) {
            throw new BusinessException("商品类型不合法：" + form.getCommodityType());
        }
        // COUPON 必须说清楚发哪张券。不卡这一条的话，兑换会在履约阶段失败 ——
        // 而那时用户的积分已经扣掉了
        if (PrizeTypeEnum.COUPON.name().equals(form.getCommodityType()) && StringUtils.isBlank(form.getAssetRef())) {
            throw new BusinessException("优惠券商品必须填写券模编码");
        }
        if (form.getPayType() == null) {
            throw new BusinessException("支付方式不合法");
        }
        checkDetailContent(form.getDetailContent());
    }

    /**
     * 上架窗口与限兑周期：这四个字段都<b>有服务端默认值</b>，不是简单地把表单值搬过去。
     *
     * @param startTime 留空 = 不限，落哨兵值。列是 NOT NULL 的，理由见 {@code MallConst.SHELF_START_SENTINEL}
     */
    private record Shelf(MallCommodityStatusEnum status, String limitPeriod,
                         LocalDateTime startTime, LocalDateTime endTime) {
    }

    private Shelf resolveShelf(MallCommoditySaveCommand form) {
        String limitPeriod = StringUtils.defaultIfBlank(form.getLimitPeriod(), MallConst.LIMIT_PERIOD_LIFETIME);
        if (!LIMIT_PERIODS.contains(limitPeriod)) {
            throw new BusinessException("限兑周期不合法：" + limitPeriod);
        }
        LocalDateTime startTime = form.getStartTime() == null ? MallConst.SHELF_START_SENTINEL : form.getStartTime();
        LocalDateTime endTime = form.getEndTime() == null ? MallConst.SHELF_END_SENTINEL : form.getEndTime();
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("上架结束时间必须晚于开始时间");
        }
        return new Shelf(form.getStatus() == null ? MallCommodityStatusEnum.DRAFT : form.getStatus(),
                limitPeriod, startTime, endTime);
    }

    /**
     * 商品编码：新建时校验并判重，编辑时<b>一律沿用库里的</b>。
     *
     * <p>创建后不可改（DDL）：它一旦被 C 端楼层配置或履约单引用就锁死了，改一次等于把那些引用
     * 全指向空。前端禁用输入框只是防呆，这里才是约束。
     *
     * <p>新建这一路运营手输或点「生成」都会走到，两条路径服务端都要校验并判重（铁律 8）——
     * 唯一索引只是兜底，让它抛 SQL 异常的话，运营看到的是一串英文堆栈。
     */
    private String resolveCommodityCode(MallCommoditySaveCommand form, MallCommodity existing) {
        if (existing != null) {
            return existing.getCommodityCode();
        }
        String commodityCode = StringUtils.upperCase(StringUtils.trimToEmpty(form.getCommodityCode()));
        if (!SolvelaCodeUtil.isValidBizCode(commodityCode)) {
            throw new BusinessException("商品" + SolvelaCodeUtil.BIZ_CODE_MESSAGE);
        }
        if (existsByCommodityCode(commodityCode)) {
            throw new BusinessException("商品编码「" + commodityCode + "」已被占用，请换一个或点「生成」");
        }
        return commodityCode;
    }

    /**
     * SKU 整表 diff 的计划：这次<b>要落的</b>规格行，与<b>要删的</b>库内行。
     *
     * <p>先算完再落库，是因为「哪些要删」必须在插入之前就定下来 ——
     * 边插边判的话，这一次新建的行会被当成「库里有、表单没有」而删掉。
     *
     * @param forms 表单提交的规格；无规格商品在这里已被补上一条默认规格
     */
    private record SkuPlan(List<MallCommoditySkuCommand> forms, List<MallSku> toDelete) {
    }

    private SkuPlan planSkus(MallCommoditySaveCommand form) {
        List<MallSku> dbSkuList = listDbSku(form.getId());
        List<MallCommoditySkuCommand> skuFormList = resolveSkuForms(form, dbSkuList);
        Map<Long, MallSku> dbSkuMap = dbSkuList.stream()
                .collect(Collectors.toMap(MallSku::getId, Function.identity()));

        Set<Long> keepSkuIds = checkSkuForms(skuFormList, dbSkuMap);
        List<MallSku> toDelete = dbSkuList.stream()
                .filter(sku -> !keepSkuIds.contains(sku.getId())).toList();
        checkDeletable(toDelete);
        return new SkuPlan(skuFormList, toDelete);
    }

    /**
     * 表单提交的规格行，补上两种服务端才知道的情况。
     */
    private List<MallCommoditySkuCommand> resolveSkuForms(MallCommoditySaveCommand form, List<MallSku> dbSkuList) {
        List<MallCommoditySkuCommand> skuFormList = form.getSkuList() == null
                ? new ArrayList<>() : new ArrayList<>(form.getSkuList());
        if (skuFormList.isEmpty()) {
            if (!dbSkuList.isEmpty()) {
                // 空列表在「整表提交」语义下等于删光。这几乎总是前端出了问题，
                // 而代价是把库存、已售、SKU 编码一起抹掉，所以宁可拒绝也不执行
                throw new BusinessException("商品至少要保留一个规格；不再售卖请把规格停用，或把商品下架");
            }
            // 无规格商品：由服务端补一条 {} 的默认规格，而不是让运营手工建「没有规格的规格」
            skuFormList.add(defaultSkuForm());
        }
        if (skuFormList.size() > MallConst.MAX_SKU_COUNT) {
            throw new BusinessException("单个商品最多 " + MallConst.MAX_SKU_COUNT + " 个规格");
        }
        return skuFormList;
    }

    /**
     * 逐行校验规格，顺便把 skuCode 归一化回表单对象。
     *
     * @return 这次要保留的库内 SKU id —— 没出现在里面的就是要删的
     */
    private Set<Long> checkSkuForms(List<MallCommoditySkuCommand> skuFormList, Map<Long, MallSku> dbSkuMap) {
        Set<String> attrsKeys = new HashSet<>();
        Set<String> skuCodes = new HashSet<>();
        Set<Long> keepSkuIds = new HashSet<>();
        for (MallCommoditySkuCommand skuForm : skuFormList) {
            MallSku dbSku = skuForm.getId() == null ? null : dbSkuMap.get(skuForm.getId());
            skuForm.setSkuCode(resolveSkuCode(skuForm, dbSku, skuCodes));

            // JSON 列做不了唯一索引，规格组合的去重只能在这里做（DDL 里写明了）。
            // 不做的话，表单重复提交会生成一堆规格完全相同的 SKU，
            // C 端下拉里出现两个「星空灰 XL」
            if (!attrsKeys.add(normalizedAttrsKey(skuForm.getSkuAttrs()))) {
                throw new BusinessException("规格组合重复：" + displayAttrs(skuForm.getSkuAttrs()));
            }

            if (skuForm.getId() == null) {
                continue;
            }
            if (dbSku == null) {
                throw new BusinessException("规格不存在或不属于本商品");
            }
            keepSkuIds.add(dbSku.getId());
            checkStockNotBelowOccupied(skuForm, dbSku);
        }
        return keepSkuIds;
    }

    /**
     * 新增行的编码由运营填 / 前端生成，老行沿用库里的（同样创建后不可改）。
     *
     * @param skuCodes 本次提交已经用掉的编码。同一次提交内部先查一遍，是因为两行填了同一个编码时，
     *                 只靠唯一索引会在插到第二行才炸，而那时报出来的是一句 SQL 层的英文
     */
    private String resolveSkuCode(MallCommoditySkuCommand skuForm, MallSku dbSku, Set<String> skuCodes) {
        String skuCode = dbSku != null
                ? dbSku.getSkuCode()
                : StringUtils.upperCase(StringUtils.trimToEmpty(skuForm.getSkuCode()));
        if (!SolvelaCodeUtil.isValidBizCode(skuCode)) {
            throw new BusinessException("规格「" + displayAttrs(skuForm.getSkuAttrs()) + "」的 SKU"
                    + SolvelaCodeUtil.BIZ_CODE_MESSAGE);
        }
        if (!skuCodes.add(skuCode)) {
            throw new BusinessException("SKU 编码重复：" + skuCode);
        }
        if (dbSku == null && existsBySkuCode(skuCode)) {
            throw new BusinessException("SKU 编码「" + skuCode + "」已被占用，请换一个或点「生成」");
        }
        return skuCode;
    }

    /**
     * 总库存是「运营投放量」，而卖掉和锁定的那部分<b>已经发生了，改不回去</b>。
     *
     * <p>允许改小，但不能小到低于 locked + sold —— 那会让虚拟列 available_stock 变成负数。
     */
    private void checkStockNotBelowOccupied(MallCommoditySkuCommand skuForm, MallSku dbSku) {
        int occupied = nullToZero(dbSku.getLockedStock()) + nullToZero(dbSku.getSoldCount());
        if (skuForm.getTotalStock() < occupied) {
            throw new BusinessException("规格「" + displayAttrs(skuForm.getSkuAttrs())
                    + "」的总库存不能小于已锁定+已售的 " + occupied + " 件");
        }
    }

    /** 库里有、这次没传 = 要删。有流水的不许删，只能停用 */
    private void checkDeletable(List<MallSku> toDeleteList) {
        for (MallSku sku : toDeleteList) {
            if (nullToZero(sku.getLockedStock()) > 0 || nullToZero(sku.getSoldCount()) > 0) {
                throw new BusinessException("规格「" + displayAttrs(parseSkuAttrs(sku.getSkuAttrs()))
                        + "」已产生兑换记录，不能删除；请改为停用");
            }
        }
    }

    /**
     * 上架校验：<b>只在真的要上架时收紧</b>。
     *
     * <p>存草稿时不拦，是因为运营配商品是分几次做的（先把名字图片存下来，规格晚点补）；
     * 而一旦要放到 C 端，「没有可买的规格」「分类是停用的」都会让用户点进去看到一个死页面。
     */
    private void checkReadyForShelf(MallCategory category, List<MallCommoditySkuCommand> skuFormList) {
        boolean hasEnabledSku = skuFormList.stream()
                .anyMatch(sku -> sku.getSkuStatus() == null || sku.getSkuStatus() == EnableStatusEnum.ENABLED);
        if (!hasEnabledSku) {
            throw new BusinessException("上架的商品至少要有一个启用的规格");
        }
        if (category.getStatus() == EnableStatusEnum.DISABLED) {
            throw new BusinessException("分类「" + category.getCategoryName() + "」已停用，不能在该分类下上架商品");
        }
    }

    /**
     * 落库：主表。
     *
     * @return 商品 id。新建时是刚拿到的自增值，SKU 与图片引用都要挂到它下面
     */
    private Long saveCommodity(MallCommoditySaveCommand form, MallCommodity existing,
                               String commodityCode, Shelf shelf, String operator) {
        MallCommodity entity = toEntity(form, commodityCode, shelf);
        /*
         * ⚠️ 关掉等级折扣开关会让这件商品已配的覆盖价【一行都不生效】——
         *    它们不会被删（运营可能只是临时关一下），但也不会有任何界面提示。
         *    留一条 WARN 说清影响面，和 GradeEntitlementAdminService 停用配置那条同一个做法。
         *
         *    这一段留在保存里、没有跟着 toEntity 走：它是【写操作的副作用】，
         *    而 toEntity 是纯映射，预览也要调 —— 预览一次就打一条 WARN 是噪音。
         */
        if (entity.getGradePriceFlag() == 0 && entity.getId() != null) {
            long overrides = mallGradePriceDao.selectCount(new LambdaQueryWrapper<MallGradePrice>()
                    .eq(MallGradePrice::getCommodityId, entity.getId()));
            if (overrides > 0) {
                log.warn("【商城商品】{} 关闭了等级折扣开关，但它还有 {} 行单品覆盖价 —— "
                                + "那些价从现在起【不生效】，商品按挂牌价卖。要用它们请重新打开开关",
                        entity.getCommodityCode(), overrides);
            }
        }
        // create_time / update_time 一律不设：铁律 9，只认数据库时钟
        entity.setUpdateBy(operator);
        if (existing == null) {
            entity.setSoldCount(0);
            entity.setCreateBy(operator);
            mallCommodityDao.insert(entity);
        } else {
            mallCommodityDao.updateById(entity);
        }
        return entity.getId();
    }

    /**
     * 管理端「C 端预览」：把<b>编辑页当前的表单</b>渲染成 C 端详情页的样子。
     *
     * <h3>🔴 它不落库，也不自己算价</h3>
     * 表单走的是保存那条路上<b>同一份映射</b>（{@link #toEntity} / {@link #toSkuEntity}），
     * 渲染走的是 C 端详情页<b>同一个方法</b>（{@code MallClientFacade.renderPreview}
     * 最终调的就是 {@code toDetailView}）。
     *
     * <p>在此之前预览是 admin-web 里的一段 JS，自己算最低价、自己拼对价文案，
     * 结果和真实 C 端漂过一次 —— 而且是<b>预览对、C 端错</b>
     *（C 端发商品基准价，某件商品因此显示成真实价格的 100 倍）。
     * 两份实现里哪一份对都无所谓，问题是没有任何机制会发现它们不一样。
     *
     * <h3>⚠️ 库存要自己补</h3>
     * {@code available_stock} 是数据库的虚拟列，草稿实体上是 null，
     * 而渲染那边拿 null 当 0 —— 不补的话<b>每次预览都显示「已兑完」</b>。
     * 已有的行按 {@code 投放 - 锁定 - 已售} 算，新加的行还没有任何占用，就是投放量。
     *
     * @param gradeCode 假装是几级；{@code 0} = 未登录 / 普通会员
     */
    public MallCommodityDetailView preview(MallCommoditySaveCommand form, int gradeCode) {
        Shelf shelf = resolveShelf(form);
        MallCommodity draft = toEntity(form, form.getCommodityCode(), shelf);

        boolean pointsOnly = form.getPayType() == MallPayTypeEnum.POINTS;
        List<MallCommoditySkuCommand> skuForms = form.getSkuList() == null
                ? List.of() : form.getSkuList();
        // 已有行的锁定/已售只能从库里拿：表单刻意不带这两个字段（见 toSkuEntity）
        Map<Long, MallSku> existingById = draft.getId() == null ? Map.of()
                : listDbSku(draft.getId()).stream()
                        .collect(Collectors.toMap(MallSku::getId, java.util.function.Function.identity(),
                                (a, b) -> a));

        List<MallSku> draftSkus = new ArrayList<>();
        for (int i = 0; i < skuForms.size(); i++) {
            MallCommoditySkuCommand skuForm = skuForms.get(i);
            MallSku sku = toSkuEntity(skuForm, draft.getId(), pointsOnly, i);
            sku.setId(skuForm.getId());
            MallSku existing = skuForm.getId() == null ? null : existingById.get(skuForm.getId());
            int occupied = existing == null ? 0
                    : nullToZero(existing.getLockedStock()) + nullToZero(existing.getSoldCount());
            sku.setAvailableStock(Math.max(0, nullToZero(sku.getTotalStock()) - occupied));
            draftSkus.add(sku);
        }

        List<Long> bannerIds = form.getBannerFileIds() == null ? List.of() : form.getBannerFileIds();
        return mallClientFacade.renderPreview(draft, draftSkus, bannerIds, gradeCode);
    }

    /**
     * 表单 → 主表实体的<b>纯映射</b>，不碰数据库。
     *
     * <h3>🔴 抽出来是为了让「预览」和「保存」用同一份映射</h3>
     * C 端预览（{@link #preview}）渲染的是一个<b>还没落库</b>的草稿，
     * 它必须和保存后的样子一致 —— 而「一致」唯一可靠的保证方式，
     * 就是两边走同一段代码。
     *
     * <p>下面每一条兜底（现金价清零、minGrade 归 0、gradePriceFlag 默认 1…）
     * 都会直接改变用户看到的价格与标签。各写一份的话，预览里是一个样、
     * 存进去是另一个样，而<b>两边都不报错</b>。
     */
    private MallCommodity toEntity(MallCommoditySaveCommand form, String commodityCode, Shelf shelf) {
        MallCommodity entity = new MallCommodity();
        entity.setId(form.getId());
        entity.setCommodityCode(commodityCode);
        entity.setCategoryId(form.getCategoryId());
        entity.setCommodityType(form.getCommodityType());
        // 实物没有资产引用。类型从 COUPON 改回 PHYSICAL 时必须真的清掉，
        // 留着的话履约会拿残留的券模编码去发券
        entity.setAssetRef(PrizeTypeEnum.PHYSICAL.name().equals(form.getCommodityType())
                ? null : StringUtils.trimToNull(form.getAssetRef()));
        entity.setCommodityName(form.getCommodityName());
        entity.setCommodityIntro(StringUtils.trimToNull(form.getCommodityIntro()));
        entity.setCoverFileId(form.getCoverFileId());
        entity.setDetailContent(form.getDetailContent());
        entity.setExchangeNotice(StringUtils.trimToNull(form.getExchangeNotice()));
        entity.setPayType(form.getPayType());
        entity.setOriginalPrice(nullToZero(form.getOriginalPrice()));
        entity.setPointsPrice(form.getPointsPrice());
        // pay_type=1 时现金价恒为 0（DDL）。信任前端传值的话，运营从「积分+现金」改回
        // 「纯积分」时那个现金价会留在库里，下单扣款读到它就是白扣用户的钱
        entity.setCashPrice(form.getPayType() == MallPayTypeEnum.POINTS
                ? BigDecimal.ZERO : nullToZero(form.getCashPrice()));
        entity.setLimitPeriod(shelf.limitPeriod());
        entity.setLimitCount(form.getLimitCount() == null ? 0 : form.getLimitCount());
        /*
         * ⚠️ 不填按 0（不限）。负数也归 0 —— 那一列是 NOT NULL DEFAULT 0，
         * 而「负的最低等级」没有任何语义，落库只会让 MallGradeGate 那边多一个分支去猜。
         */
        entity.setMinGrade(form.getMinGrade() == null || form.getMinGrade() < 0
                ? 0 : form.getMinGrade());
        /*
         * 🔴 不填按【参与】(1)，不是按 0。
         * 与 minGrade 那一行的方向刚好相反，因为两列的「空」含义不同：
         * 没填最低等级 = 不限（对用户最宽松）；没填这一列 = 跟着全场走（DDL 默认值就是 1）。
         * 按 0 存的话，新建的商品全都悄悄不参与等级折扣，
         * 而运营配了折扣率之后会发现一件都没便宜。
         */
        entity.setGradePriceFlag(form.getGradePriceFlag() == null || form.getGradePriceFlag() != 0
                ? 1 : 0);
        entity.setStartTime(shelf.startTime());
        entity.setEndTime(shelf.endTime());
        entity.setStatus(shelf.status());
        entity.setIsHome(Boolean.TRUE.equals(form.getIsHome()));
        entity.setSort(form.getSort() == null ? 0 : form.getSort());
        return entity;
    }

    /**
     * 落库：SKU 整表 diff。
     *
     * <p>🔴 {@code locked_stock} / {@code sold_count} 只由下单与履约链路改，这里新建归零、
     * 更新时压根不带 —— 让编辑页能写它们，等于让运营的一次保存把在途订单的锁库存抹掉。
     * {@code available_stock} 是虚拟列，任何时候都不写。
     */
    private void saveSkus(Long commodityId, MallCommoditySaveCommand form, SkuPlan skuPlan, String operator) {
        // 纯积分商品不存在现金覆盖价，理由同主表
        boolean pointsOnly = form.getPayType() == MallPayTypeEnum.POINTS;
        List<MallCommoditySkuCommand> skuFormList = skuPlan.forms();
        for (int i = 0; i < skuFormList.size(); i++) {
            MallCommoditySkuCommand skuForm = skuFormList.get(i);
            MallSku sku = toSkuEntity(skuForm, commodityId, pointsOnly, i);
            sku.setUpdateBy(operator);
            if (skuForm.getId() == null) {
                sku.setSkuCode(skuForm.getSkuCode());
                sku.setLockedStock(0);
                sku.setSoldCount(0);
                sku.setCreateBy(operator);
                mallSkuManager.save(sku);
            } else {
                sku.setId(skuForm.getId());
                mallSkuManager.updateById(sku);
            }
        }
        if (!skuPlan.toDelete().isEmpty()) {
            mallSkuManager.removeByIds(skuPlan.toDelete().stream().map(MallSku::getId).toList());
        }
    }

    /**
     * SKU 表单 → 实体的<b>纯映射</b>，不碰数据库。理由同 {@link #toEntity}：
     * 预览与保存必须走同一段代码，否则「预览里是一个价、存进去是另一个价」，
     * 而两边都不报错。
     *
     * <p>⚠️ <b>不设</b> {@code lockedStock} / {@code soldCount} / {@code availableStock}：
     * 前两个只由下单与履约链路改（让编辑页写它们，等于让一次保存把在途订单的锁库存抹掉），
     * 后者是虚拟列，写它会直接报错。
     */
    private MallSku toSkuEntity(MallCommoditySkuCommand skuForm, Long commodityId,
                                boolean pointsOnly, int index) {
        MallSku sku = new MallSku();
        sku.setCommodityId(commodityId);
        sku.setSkuAttrs(JsonUtils.toJson(skuForm.getSkuAttrs() == null
                ? new LinkedHashMap<String, String>() : skuForm.getSkuAttrs()));
        sku.setSkuCoverFileId(skuForm.getSkuCoverFileId());
        sku.setSkuPointsPrice(skuForm.getSkuPointsPrice());
        sku.setSkuCashPrice(pointsOnly ? null : skuForm.getSkuCashPrice());
        sku.setTotalStock(skuForm.getTotalStock());
        sku.setSkuStatus(skuForm.getSkuStatus() == null ? EnableStatusEnum.ENABLED : skuForm.getSkuStatus());
        // 顺序取表单里的行序 —— 运营拖动排序后期望 C 端就是那个顺序
        sku.setSort(skuForm.getSort() == null ? index : skuForm.getSort());
        return sku;
    }

    /**
     * 把这个商品引用到的<b>全部</b>图片登记进 {@code t_file_relation}。
     *
     * <p>不登记的话，孤儿清理任务会把它们当成「上传了但没人引用」的垃圾删掉，
     * 素材库的删除守卫也会以为没人在用、放行运营的删除。两种情况下商品图都会静默变成死链
     * （{@code img} 加载失败不报错，没人会来投诉）。
     *
     * <p>四个来源一个都不能漏，其中<b>各 SKU 的专属图是最容易忘的一处</b>。
     */
    private void confirmFileRelations(Long commodityId, MallCommoditySaveCommand form, List<MallCommoditySkuCommand> skuFormList) {
        // 轮播图单独一组：它的顺序是业务数据，回显要按 sort 读回来
        List<Long> bannerIds = new ArrayList<>(new LinkedHashSet<>(
                form.getBannerFileIds() == null ? List.<Long>of() : form.getBannerFileIds()));
        bannerIds.removeIf(Objects::isNull);
        fileAssetService.confirm(bannerIds, MallConst.BIZ_TYPE_BANNER, commodityId);

        // 其余引用：封面 + 各 SKU 专属图 + 富文本内嵌图。顺序无意义，去重即可
        Set<Long> otherIds = new LinkedHashSet<>();
        if (form.getCoverFileId() != null) {
            otherIds.add(form.getCoverFileId());
        }
        for (MallCommoditySkuCommand skuForm : skuFormList) {
            if (skuForm.getSkuCoverFileId() != null) {
                otherIds.add(skuForm.getSkuCoverFileId());
            }
        }
        Set<String> sources = RichTextImageExtractor.extractImageSources(form.getDetailContent());
        if (!sources.isEmpty()) {
            List<Long> resolved = fileAssetService.resolveFileIds(sources);
            otherIds.addAll(resolved);
            if (resolved.size() < sources.size()) {
                // 外链图、已删除的文件都会走到这里，属正常；但数量对不上时留一条线索，
                // 否则「详情里的图后来不见了」完全无从查起
                log.info("[商城] 商品 {} 的图文详情有 {} 张图，其中 {} 张能对应到素材库",
                        commodityId, sources.size(), resolved.size());
            }
        }
        // 轮播图已经登记在另一组里，这里去掉，避免同一个文件出现两行关系
        // （不致命，但删除守卫的「正被 N 处引用」会虚高，运营看着莫名其妙）
        bannerIds.forEach(otherIds::remove);
        fileAssetService.confirm(new ArrayList<>(otherIds), MallConst.BIZ_TYPE, commodityId);
    }

    /**
     * 上架 / 下架的快捷开关：列表页直接点，不用打开编辑页。
     *
     * <p>上架仍然要过 {@link #save} 里的那组校验 —— 快捷开关绕过校验的话，
     * 运营就有了一条「先存草稿、再从列表点上架」的路，把没有规格的商品放到 C 端。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, MallCommodityStatusEnum status, String operator) {
        if (status == null) {
            throw new BusinessException("商品状态不合法");
        }
        MallCommodity commodity = mallCommodityManager.getById(id);
        if (commodity == null) {
            throw new BusinessException("商品不存在");
        }
        if (status == MallCommodityStatusEnum.ON) {
            boolean hasEnabledSku = listDbSku(id).stream()
                    .anyMatch(sku -> sku.getSkuStatus() == EnableStatusEnum.ENABLED);
            if (!hasEnabledSku) {
                throw new BusinessException("该商品没有启用的规格，不能上架");
            }
            MallCategory category = mallCategoryManager.getById(commodity.getCategoryId());
            if (category == null || category.getStatus() == EnableStatusEnum.DISABLED) {
                throw new BusinessException("商品分类不存在或已停用，不能上架");
            }
        }
        MallCommodity update = new MallCommodity();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateBy(operator);
        mallCommodityDao.updateById(update);
    }

    // ================================================================== 删除

    /**
     * 批量删除。有一个不满足删除条件就整批停下并把原因返回 ——
     * 「删了 3 个、第 4 个失败」这种半截结果，运营根本不知道该怎么收拾。
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        // delete 自己抛 BusinessException，异常穿透本方法即触发回滚，
        // 「要么全删、要么一个没删」由事务保证，这里不需要再判返回值
        idList.forEach(this::delete);
    }

    /**
     * 单个删除：主表 + 其 SKU + 图片引用一起清掉。
     *
     * <p><b>产生过订单的商品不允许删除</b>。订单里的商品信息虽然是快照（删了也不会显示成空），
     * 但售后要回查商品、对账要回查 SKU，删掉之后这些路径只剩一个 id 指向空气。
     * 「不卖了」的正确动作是把状态改成下架，不是删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            return;
        }
        MallCommodity commodity = mallCommodityManager.getById(id);
        if (commodity == null) {
            return;
        }
        boolean hasOrder = mallOrderManager.lambdaQuery().eq(MallOrder::getCommodityId, id).exists();
        if (hasOrder) {
            throw new BusinessException("商品「" + commodity.getCommodityName() + "」已产生兑换订单，不能删除；请改为下架");
        }

        List<MallSku> dbSkuList = listDbSku(id);
        if (!dbSkuList.isEmpty()) {
            mallSkuManager.removeByIds(dbSkuList.stream().map(MallSku::getId).toList());
        }
        // 两组引用都要解除。漏一组，那些图会永远显示「正被 1 处业务引用」而删不掉
        fileAssetService.releaseRelation(MallConst.BIZ_TYPE, id);
        fileAssetService.releaseRelation(MallConst.BIZ_TYPE_BANNER, id);
        /*
         * 🔴 单品覆盖价跟着商品一起删。
         *
         * t_mall_grade_price 刻意不建外键（理由见那张表的注释：商品不会被物理删除，
         * 加外键换来的只是每次改价一次跨表锁）—— 但「不会被物理删除」在这一行是假的，
         * 这个方法就是在物理删除。不删的话它们就是一堆 commodity_id 指向空气的孤儿：
         * 查不到、显示不出来，直到某天有人用同一个自增 id 建了新商品，
         * 那件新商品会凭空带上前任的特价。
         */
        mallGradePriceDao.delete(new LambdaQueryWrapper<MallGradePrice>()
                .eq(MallGradePrice::getCommodityId, id));
        mallCommodityDao.deleteById(id);
    }

    // ================================================================== 内部工具

    /**
     * 无规格商品的默认 SKU：规格 {}，价格继承主表，库存 0（运营随后在规格区补货）。
     */
    private MallCommoditySkuCommand defaultSkuForm() {
        MallCommoditySkuCommand skuForm = new MallCommoditySkuCommand();
        // 这一行不是运营填的（表单一个规格都没提交），编码只能服务端补
        skuForm.setSkuCode(SolvelaCodeUtil.generateUniqueBizCode(
                SolvelaCodeUtil.BizCodePrefix.MALL_SKU, this::existsBySkuCode));
        skuForm.setSkuAttrs(new LinkedHashMap<>());
        skuForm.setTotalStock(0);
        skuForm.setSkuStatus(EnableStatusEnum.ENABLED);
        skuForm.setSort(0);
        return skuForm;
    }

    /**
     * 图文详情的两条硬约束。
     *
     * <p>编辑器那边也关掉了 base64，但前端配置是会被人改回来的，<b>后端这道才是真的</b>：
     * 一张图几百 KB 的 base64 塞进 mediumtext，几十个商品就把表撑爆。
     */
    private void checkDetailContent(String detailContent) {
        if (StringUtils.isBlank(detailContent)) {
            return;
        }
        if (RichTextImageExtractor.containsBase64Image(detailContent)) {
            throw new BusinessException("图文详情不允许内嵌 base64 图片，请用编辑器的上传按钮插入");
        }
        if (detailContent.length() > MallConst.MAX_DETAIL_CONTENT_LENGTH) {
            throw new BusinessException("图文详情过长（" + detailContent.length()
                    + " 字符），上限 " + MallConst.MAX_DETAIL_CONTENT_LENGTH);
        }
    }

    /**
     * 规格组合的比较键：<b>按属性名排序后拼接</b>。
     *
     * <p>不排序的话，{@code 颜色=黑,尺码=XL} 与 {@code 尺码=XL,颜色=黑} 会被当成两个不同的规格 ——
     * 而它们在 C 端就是同一个东西，用户点进去会看到两个一模一样的选项。
     */
    private String normalizedAttrsKey(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return "";
        }
        Map<String, String> sorted = new TreeMap<>();
        attrs.forEach((k, v) -> sorted.put(StringUtils.trimToEmpty(k), StringUtils.trimToEmpty(v)));
        return sorted.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("/"));
    }

    private String displayAttrs(Map<String, String> attrs) {
        String key = normalizedAttrsKey(attrs);
        return key.isEmpty() ? "无规格" : key;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
