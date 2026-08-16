package net.lab1024.sa.draw.poolconfig.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.activity.domain.entity.ActivityConfig;
import net.lab1024.sa.activity.service.ActivityConfigService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.JsonUtils;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCodeUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.draw.drawlog.domain.entity.DrawPrizeLog;
import net.lab1024.sa.draw.drawlog.manager.DrawPrizeLogManager;
import net.lab1024.sa.draw.poolconfig.dao.PrizePoolConfigDao;
import net.lab1024.sa.draw.poolconfig.domain.entity.PrizePoolConfig;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchMappingForm;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchPoolForm;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchPoolItemForm;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchSaveForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigAddForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigQueryForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigUpdateForm;
import net.lab1024.sa.draw.poolconfig.domain.vo.DrawWorkbenchItemVO;
import net.lab1024.sa.draw.poolconfig.domain.vo.DrawWorkbenchMappingVO;
import net.lab1024.sa.draw.poolconfig.domain.vo.DrawWorkbenchPoolVO;
import net.lab1024.sa.draw.poolconfig.domain.vo.DrawWorkbenchVO;
import net.lab1024.sa.draw.poolconfig.domain.vo.PrizePoolConfigVO;
import net.lab1024.sa.draw.poolconfig.manager.PrizePoolConfigManager;
import net.lab1024.sa.draw.poolitem.dao.PrizePoolItemDao;
import net.lab1024.sa.draw.poolitem.domain.entity.PrizePoolItem;
import net.lab1024.sa.draw.poolitem.manager.PrizePoolItemManager;
import net.lab1024.sa.draw.prizemapping.dao.PoolPrizeMappingDao;
import net.lab1024.sa.draw.prizemapping.domain.entity.PoolPrizeMapping;
import net.lab1024.sa.draw.prizemapping.manager.PoolPrizeMappingManager;
import net.lab1024.sa.draw.runtime.DrawStockService;
import net.lab1024.sa.prize.prizeconfig.domain.entity.PrizeConfig;
import net.lab1024.sa.prize.prizeconfig.service.PrizeConfigService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 奖池配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PrizePoolConfigService {

    private final PrizePoolConfigDao prizePoolConfigDao;
    private final PrizePoolConfigManager prizePoolConfigManager;
    private final PrizePoolItemDao prizePoolItemDao;
    private final PrizePoolItemManager prizePoolItemManager;
    private final PoolPrizeMappingDao poolPrizeMappingDao;
    private final PoolPrizeMappingManager poolPrizeMappingManager;
    private final ActivityConfigService activityConfigService;
    private final PrizeConfigService prizeConfigService;
    private final DrawStockService drawStockService;
    // 删除守卫要看这个池有没有产生过抽奖流水 —— 流水在，池就不能删
    private final DrawPrizeLogManager drawPrizeLogManager;

    /**
     * 活动状态：1-上线（上线后启用结构锁：库存只增不减、禁止删奖项/删池/池内增删坑位）
     */
    private static final Integer ACTIVITY_STATUS_ONLINE = 1;

    /**
     * 库存/限领「不限量」哨兵值
     */
    private static final Integer UNLIMITED = -1;

    /**
     * 概率累加容差：防御浮点边界，前后端同语义
     */
    private static final BigDecimal PROBABILITY_EPSILON = new BigDecimal("0.0001");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 坑位兜底标记：1-兜底（库存不足时降级命中），每池最多一个
     */
    private static final Integer FALLBACK_YES = 1;

    private static final Integer FALLBACK_NO = 0;

    /**
     * 生成一个未被占用的奖池编码（10 位大写字母+数字），供工作台「新建奖池」调用
     */
    public ResponseDTO<String> generatePoolCode() {
        return ResponseDTO.ok(SmartCodeUtil.generateUniqueBizCode(SmartCodeUtil.BizCodePrefix.POOL, this::existsByPoolCode));
    }

    /**
     * 奖池编码是否已被占用（全局唯一，不分活动，对齐 uk_pool_code）
     */
    public boolean existsByPoolCode(String poolCode) {
        return prizePoolConfigManager.lambdaQuery().eq(PrizePoolConfig::getPoolCode, poolCode).exists();
    }

    /**
     * 抽奖工作台聚合回显：与 workbenchSave 的入参同构，前端拿到即可直接填回两个 Tab
     * 空配置（活动刚建、还没配过奖池）返回空列表而非报错，前端据此进入「从零配置」态
     */
    public ResponseDTO<DrawWorkbenchVO> workbenchDetail(String activityCode) {
        ActivityConfig activity = activityConfigService.getByActivityCode(activityCode);
        if (activity == null) {
            return ResponseDTO.userErrorParam("活动不存在：" + activityCode);
        }
        boolean online = ACTIVITY_STATUS_ONLINE.equals(activity.getStatus());

        // Tab1 物资：SKU 化后名称/价值等展示信息需回查资产大库补齐
        List<PrizePoolItem> dbItems = prizePoolItemManager.lambdaQuery()
                .eq(PrizePoolItem::getActivityCode, activityCode)
                .orderByAsc(PrizePoolItem::getId).list();
        Map<String, PrizeConfig> prizeConfigMap = prizeConfigService.queryListByActivityCode(activityCode).stream()
                .collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a));
        List<DrawWorkbenchItemVO> itemVOList = dbItems.stream().map(item -> {
            PrizeConfig prize = prizeConfigMap.get(item.getPrizeCode());
            return new DrawWorkbenchItemVO(
                    item.getPrizeCode(),
                    prize == null ? item.getPrizeCode() : prize.getPrizeName(),
                    prize == null ? null : prize.getPrizeType(),
                    prize == null ? BigDecimal.ZERO : prize.getPrizeValue(),
                    item.getTotalStock(),
                    item.getUserMaxCount(),
                    item.getUsedStock(),
                    parseWhiteList(item.getWhiteList()));
        }).collect(Collectors.toList());

        // Tab2 奖池：存储层用 prize_item_id 关联，对外统一翻译回 prizeCode
        List<PrizePoolConfig> dbPools = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getActivityCode, activityCode)
                .orderByAsc(PrizePoolConfig::getId).list();
        Map<Long, String> itemIdToCodeMap = dbItems.stream()
                .collect(Collectors.toMap(PrizePoolItem::getId, PrizePoolItem::getPrizeCode));
        List<String> poolCodeList = dbPools.stream().map(PrizePoolConfig::getPoolCode).collect(Collectors.toList());
        Map<String, List<PoolPrizeMapping>> mappingGroup = poolCodeList.isEmpty()
                ? Map.of()
                : poolPrizeMappingManager.lambdaQuery()
                        .in(PoolPrizeMapping::getPoolCode, poolCodeList)
                        .orderByAsc(PoolPrizeMapping::getSortWeight).list().stream()
                        .collect(Collectors.groupingBy(PoolPrizeMapping::getPoolCode));

        List<DrawWorkbenchPoolVO> poolVOList = dbPools.stream().map(pool -> {
            List<DrawWorkbenchMappingVO> mappingVOList = mappingGroup.getOrDefault(pool.getPoolCode(), List.of())
                    .stream()
                    // 物资被删导致的悬空坑位直接过滤，避免前端渲染出无法编辑的幽灵行
                    .filter(mapping -> itemIdToCodeMap.containsKey(mapping.getPrizeItemId()))
                    .map(mapping -> new DrawWorkbenchMappingVO(
                            itemIdToCodeMap.get(mapping.getPrizeItemId()),
                            mapping.getProbability(),
                            FALLBACK_YES.equals(mapping.getIsFallback())))
                    .collect(Collectors.toList());
            return new DrawWorkbenchPoolVO(pool.getPoolCode(), pool.getPoolName(), mappingVOList);
        }).collect(Collectors.toList());

        return ResponseDTO.ok(new DrawWorkbenchVO(activity.getActivityCode(), activity.getActivityName(),
                activity.getStatus(), online, itemVOList, poolVOList));
    }

    /**
     * white_list 列存的是 JSON 数组字符串。JsonUtils.parseList 遇到脏数据会抛异常，
     * 单个奖项的脏名单不该让整个工作台打不开，故此处兜住并降级为空名单
     */
    private List<String> parseWhiteList(String whiteListJson) {
        if (StringUtils.isBlank(whiteListJson)) {
            return List.of();
        }
        try {
            List<String> whiteList = JsonUtils.parseList(whiteListJson, String.class);
            return whiteList == null ? List.of() : whiteList;
        } catch (RuntimeException e) {
            log.warn("[抽奖工作台回显] 白名单 JSON 解析失败，已降级为空名单: {}", whiteListJson, e);
            return List.of();
        }
    }

    /**
     * 抽奖工作台聚合保存：物资(t_prize_pool_item) + 奖池(t_prize_pool_config) + 坑位映射(t_pool_prize_mapping)
     * 同一事务落库。前端的概率闭环/上线锁只是 UI 防呆，此处全部服务端重算
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> workbenchSave(DrawWorkbenchSaveForm form) {
        // 1. 活动必须存在，并据其状态判定是否启用结构锁
        ActivityConfig activity = activityConfigService.getByActivityCode(form.getActivityCode());
        if (activity == null) {
            return ResponseDTO.userErrorParam("活动不存在：" + form.getActivityCode());
        }
        boolean online = ACTIVITY_STATUS_ONLINE.equals(activity.getStatus());

        // 2. 物资编码不允许重复，且必须真实存在于资产大库 t_prize_config
        Set<String> itemCodes = new HashSet<>();
        for (DrawWorkbenchPoolItemForm item : form.getPrizeItemList()) {
            if (!itemCodes.add(item.getPrizeCode())) {
                return ResponseDTO.userErrorParam("奖品编码重复：" + item.getPrizeCode());
            }
            if (prizeConfigService.getByActivityCodeAndPrizeCode(form.getActivityCode(), item.getPrizeCode()) == null) {
                return ResponseDTO.userErrorParam("奖品不存在于本活动的资产大库：" + item.getPrizeCode());
            }
        }

        // 3. 逐池校验：概率闭环（BigDecimal + 容差）、池编码唯一、坑位引用的奖品必须在物资列表内
        Set<String> poolCodes = new HashSet<>();
        for (DrawWorkbenchPoolForm pool : form.getPoolList()) {
            // 奖池编码由前端生成/手输，服务端重校验格式（对齐活动编码、奖品编码的统一约定）
            if (!SmartCodeUtil.isValidBizCode(pool.getPoolCode())) {
                return ResponseDTO.userErrorParam("奖池「" + pool.getPoolName() + "」" + SmartCodeUtil.BIZ_CODE_MESSAGE);
            }
            if (!poolCodes.add(pool.getPoolCode())) {
                return ResponseDTO.userErrorParam("奖池编码重复：" + pool.getPoolCode());
            }
            Set<String> mappingCodes = new HashSet<>();
            BigDecimal total = BigDecimal.ZERO;
            int fallbackCount = 0;
            for (DrawWorkbenchMappingForm mapping : pool.getPrizeMappingList()) {
                if (!mappingCodes.add(mapping.getPrizeCode())) {
                    return ResponseDTO.userErrorParam("奖池「" + pool.getPoolName() + "」坑位奖品重复：" + mapping.getPrizeCode());
                }
                if (!itemCodes.contains(mapping.getPrizeCode())) {
                    return ResponseDTO.userErrorParam("奖池「" + pool.getPoolName() + "」引用了物资列表外的奖品：" + mapping.getPrizeCode());
                }
                if (Boolean.TRUE.equals(mapping.getIsFallback())) {
                    fallbackCount++;
                }
                total = total.add(mapping.getProbability());
            }
            if (fallbackCount > 1) {
                return ResponseDTO.userErrorParam("奖池「" + pool.getPoolName() + "」兜底奖项最多只能有一个");
            }
            if (total.subtract(HUNDRED).abs().compareTo(PROBABILITY_EPSILON) > 0) {
                return ResponseDTO.userErrorParam("奖池「" + pool.getPoolName() + "」概率总和为 " + total.toPlainString() + "%，必须等于100%");
            }
        }

        // 4. 加载 DB 现状
        List<PrizePoolItem> dbItems = prizePoolItemManager.lambdaQuery()
                .eq(PrizePoolItem::getActivityCode, form.getActivityCode()).list();
        Map<String, PrizePoolItem> dbItemMap = dbItems.stream()
                .collect(Collectors.toMap(PrizePoolItem::getPrizeCode, Function.identity()));
        List<PrizePoolConfig> dbPools = prizePoolConfigManager.lambdaQuery()
                .eq(PrizePoolConfig::getActivityCode, form.getActivityCode()).list();
        Map<String, PrizePoolConfig> dbPoolMap = dbPools.stream()
                .collect(Collectors.toMap(PrizePoolConfig::getPoolCode, Function.identity()));

        // 5. 上线结构锁：绕过前端直接调接口同样拦截
        if (online) {
            String lockError = checkOnlineStructureLock(form, itemCodes, poolCodes, dbItemMap, dbPools);
            if (lockError != null) {
                return ResponseDTO.userErrorParam(lockError);
            }
        }

        // 6. 落库：物资 upsert（used_stock/version 永不接受前端值）
        for (DrawWorkbenchPoolItemForm item : form.getPrizeItemList()) {
            PrizePoolItem existed = dbItemMap.get(item.getPrizeCode());
            if (existed == null) {
                PrizePoolItem entity = new PrizePoolItem();
                entity.setActivityCode(form.getActivityCode());
                entity.setPrizeCode(item.getPrizeCode());
                entity.setTotalStock(item.getTotalStock());
                entity.setUserMaxCount(item.getUserMaxCount());
                entity.setWhiteList(item.getWhiteList() == null ? null : JsonUtils.toJson(item.getWhiteList()));
                entity.setUsedStock(0);
                entity.setVersion(0);
                prizePoolItemDao.insert(entity);
                dbItemMap.put(item.getPrizeCode(), entity);
            } else {
                PrizePoolItem update = new PrizePoolItem();
                update.setId(existed.getId());
                update.setTotalStock(item.getTotalStock());
                update.setUserMaxCount(item.getUserMaxCount());
                update.setWhiteList(item.getWhiteList() == null ? null : JsonUtils.toJson(item.getWhiteList()));
                prizePoolItemDao.updateById(update);
            }
        }
        // 未提交的存量物资：未上线时删除（上线时已被结构锁拦截）
        List<Long> removeItemIdList = dbItems.stream()
                .filter(db -> !itemCodes.contains(db.getPrizeCode()))
                .map(PrizePoolItem::getId)
                .collect(Collectors.toList());
        if (!removeItemIdList.isEmpty()) {
            prizePoolItemDao.deleteBatchIds(removeItemIdList);
        }

        // 7. 落库：奖池 upsert（工作台只管理 poolName，resetPeriod 等奖池自身配置由奖池 CRUD 页维护，不覆盖）
        for (DrawWorkbenchPoolForm pool : form.getPoolList()) {
            PrizePoolConfig existed = dbPoolMap.get(pool.getPoolCode());
            if (existed == null) {
                // uk_pool_code 是全局唯一：手输的编码可能撞上别的活动的奖池，提前给出人话提示而不是抛 SQL 异常
                if (existsByPoolCode(pool.getPoolCode())) {
                    return ResponseDTO.userErrorParam("奖池编码已被其他活动占用：" + pool.getPoolCode());
                }
                PrizePoolConfig entity = new PrizePoolConfig();
                entity.setActivityCode(form.getActivityCode());
                entity.setPoolCode(pool.getPoolCode());
                entity.setPoolName(pool.getPoolName());
                prizePoolConfigDao.insert(entity);
            } else if (!Objects.equals(existed.getPoolName(), pool.getPoolName())) {
                PrizePoolConfig update = new PrizePoolConfig();
                update.setId(existed.getId());
                update.setPoolName(pool.getPoolName());
                prizePoolConfigDao.updateById(update);
            }
        }
        // 未提交的存量奖池：连同其坑位映射一并删除
        for (PrizePoolConfig dbPool : dbPools) {
            if (!poolCodes.contains(dbPool.getPoolCode())) {
                prizePoolConfigDao.deleteById(dbPool.getId());
                poolPrizeMappingManager.lambdaUpdate()
                        .eq(PoolPrizeMapping::getPoolCode, dbPool.getPoolCode()).remove();
            }
        }

        // 8. 落库：坑位映射整池重建（prizeCode -> prize_item_id 由服务端解析）
        for (DrawWorkbenchPoolForm pool : form.getPoolList()) {
            poolPrizeMappingManager.lambdaUpdate()
                    .eq(PoolPrizeMapping::getPoolCode, pool.getPoolCode()).remove();
            List<PoolPrizeMapping> mappingList = new ArrayList<>();
            for (int i = 0; i < pool.getPrizeMappingList().size(); i++) {
                DrawWorkbenchMappingForm mapping = pool.getPrizeMappingList().get(i);
                PoolPrizeMapping entity = new PoolPrizeMapping();
                entity.setTenantId("0");
                entity.setPoolCode(pool.getPoolCode());
                entity.setPrizeItemId(dbItemMap.get(mapping.getPrizeCode()).getId());
                entity.setProbability(mapping.getProbability());
                entity.setIsFallback(Boolean.TRUE.equals(mapping.getIsFallback()) ? FALLBACK_YES : FALLBACK_NO);
                entity.setSortWeight(i);
                mappingList.add(entity);
            }
            poolPrizeMappingDao.insertBatch(mappingList);
        }

        // 9. 预热库存缓存：抽奖运行态的 Lua 预扣依赖这些 key（dbItemMap 此时已含新插入项的 id）
        drawStockService.warmStock(form.getActivityCode(), new ArrayList<>(dbItemMap.values()));

        return ResponseDTO.ok();
    }

    /**
     * 上线结构锁校验：库存只增不减、禁止删物资/删池/新建池、池内坑位集合不可增删（概率可调）
     * 返回 null 表示通过
     */
    private String checkOnlineStructureLock(DrawWorkbenchSaveForm form, Set<String> itemCodes, Set<String> poolCodes,
                                            Map<String, PrizePoolItem> dbItemMap, List<PrizePoolConfig> dbPools) {
        // 物资：存量不可删除，库存不可缩减（-1 不限量视为最大，不可改回限量）
        for (PrizePoolItem db : dbItemMap.values()) {
            if (!itemCodes.contains(db.getPrizeCode())) {
                return "活动已上线，禁止移除奖项：" + db.getPrizeCode();
            }
        }
        for (DrawWorkbenchPoolItemForm item : form.getPrizeItemList()) {
            PrizePoolItem db = dbItemMap.get(item.getPrizeCode());
            if (db == null) {
                return "活动已上线，禁止引入新奖项：" + item.getPrizeCode();
            }
            boolean dbUnlimited = UNLIMITED.equals(db.getTotalStock());
            boolean submitUnlimited = UNLIMITED.equals(item.getTotalStock());
            boolean shrink = (dbUnlimited && !submitUnlimited)
                    || (!dbUnlimited && !submitUnlimited && item.getTotalStock() < db.getTotalStock());
            if (shrink) {
                return "活动已上线，奖项「" + item.getPrizeCode() + "」库存只允许追加，不允许缩减";
            }
        }
        // 奖池：不可删、不可新建
        Set<String> dbPoolCodes = dbPools.stream().map(PrizePoolConfig::getPoolCode).collect(Collectors.toSet());
        for (String dbPoolCode : dbPoolCodes) {
            if (!poolCodes.contains(dbPoolCode)) {
                return "活动已上线，禁止删除奖池：" + dbPoolCode;
            }
        }
        for (String poolCode : poolCodes) {
            if (!dbPoolCodes.contains(poolCode)) {
                return "活动已上线，禁止新建奖池：" + poolCode;
            }
        }
        // 池内坑位集合不可增删（对比 prize_item_id 集合；概率调整不受限）
        List<PoolPrizeMapping> dbMappings = dbPoolCodes.isEmpty()
                ? List.of()
                : poolPrizeMappingManager.lambdaQuery().in(PoolPrizeMapping::getPoolCode, dbPoolCodes).list();
        Map<String, Set<Long>> dbPoolItemIds = dbMappings.stream().collect(
                Collectors.groupingBy(PoolPrizeMapping::getPoolCode,
                        Collectors.mapping(PoolPrizeMapping::getPrizeItemId, Collectors.toSet())));
        for (DrawWorkbenchPoolForm pool : form.getPoolList()) {
            Set<Long> submitIds = pool.getPrizeMappingList().stream()
                    .map(m -> dbItemMap.get(m.getPrizeCode()))
                    .filter(Objects::nonNull)
                    .map(PrizePoolItem::getId)
                    .collect(Collectors.toSet());
            Set<Long> dbIds = dbPoolItemIds.getOrDefault(pool.getPoolCode(), Set.of());
            if (!submitIds.equals(dbIds)) {
                return "活动已上线，奖池「" + pool.getPoolName() + "」禁止增删奖项（概率可调整）";
            }
        }
        return null;
    }

    /**
     * 分页查询
     */
    public PageResult<PrizePoolConfigVO> queryPage(PrizePoolConfigQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrizePoolConfigVO> list = prizePoolConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     * 奖池编码允许手工输入，故服务端必须重校验唯一性（格式由 AddForm 的 @Pattern 拦）
     */
    public ResponseDTO<String> add(PrizePoolConfigAddForm addForm) {
        if (existsByPoolCode(addForm.getPoolCode())) {
            return ResponseDTO.userErrorParam("奖池编码已存在：" + addForm.getPoolCode());
        }
        PrizePoolConfig prizePoolConfig = SmartBeanUtil.copy(addForm, PrizePoolConfig.class);
        prizePoolConfigDao.insert(prizePoolConfig);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PrizePoolConfigUpdateForm updateForm) {
        PrizePoolConfig prizePoolConfig = SmartBeanUtil.copy(updateForm, PrizePoolConfig.class);
        prizePoolConfigDao.updateById(prizePoolConfig);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除。逐个走同一套守卫，任一条不可删就整批拒绝并说明原因 ——
     * 删一半留一半会让运营以为「删掉了」，而实际有几个池还在。
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }
        for (Long id : idList) {
            String error = checkDeletable(id);
            if (error != null) {
                return ResponseDTO.userErrorParam(error);
            }
        }
        prizePoolConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }
        String error = checkDeletable(id);
        if (error != null) {
            return ResponseDTO.userErrorParam(error);
        }
        prizePoolConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }

    /**
     * 奖池可删性守卫，返回 null 表示可删。
     *
     * <p>生成器产出的 delete 是裸的 {@code deleteById}，会留下两类烂摊子：
     * <ul>
     *   <li><b>孤儿坑位映射。</b>{@code t_pool_prize_mapping} 按 pool_code 关联，
     *       池没了映射还在，既不会被抽奖用到、也无处修改 ——
     *       只有把奖池筛选清空才能在概率分析页看到它们；</li>
     *   <li><b>断掉的抽奖流水。</b>{@code t_draw_prize_log} 里存着 pool_code，
     *       那是发奖凭证与对账依据，指向一个不存在的奖池之后客诉自证就断了。</li>
     * </ul>
     *
     * <p>已上线的活动一律不许删池：与工作台的上线结构锁同一个判据
     * （{@code checkOnlineStructureLock} 里「活动已上线，禁止删除奖池」），
     * 两边必须一致，否则从这个入口就能绕过结构锁。
     */
    private String checkDeletable(Long id) {
        PrizePoolConfig pool = prizePoolConfigDao.selectById(id);
        if (pool == null) {
            return null;
        }
        ActivityConfig activity = activityConfigService.getByActivityCode(pool.getActivityCode());
        if (activity != null && ACTIVITY_STATUS_ONLINE.equals(activity.getStatus())) {
            return "奖池「" + pool.getPoolName() + "」所属活动已上线，禁止删除；请先下线活动";
        }
        long mappingCount = poolPrizeMappingManager.lambdaQuery()
                .eq(PoolPrizeMapping::getPoolCode, pool.getPoolCode()).count();
        if (mappingCount > 0) {
            return "奖池「" + pool.getPoolName() + "」下还有 " + mappingCount
                    + " 个奖项坑位，直接删会留下孤儿映射。请先在抽奖工作台清空该池的坑位";
        }
        long logCount = drawPrizeLogManager.lambdaQuery()
                .eq(DrawPrizeLog::getPoolCode, pool.getPoolCode()).count();
        if (logCount > 0) {
            return "奖池「" + pool.getPoolName() + "」已产生 " + logCount
                    + " 条抽奖流水，不能删除：流水是发奖凭证与对账依据，指向一个不存在的奖池会让客诉无法自证。"
                    + "如需停用请把状态改为「关闭」";
        }
        return null;
    }
}
