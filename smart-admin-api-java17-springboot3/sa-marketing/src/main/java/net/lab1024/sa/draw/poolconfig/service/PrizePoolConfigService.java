package net.lab1024.sa.draw.poolconfig.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.activity.domain.entity.ActivityConfig;
import net.lab1024.sa.activity.service.ActivityConfigService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.JsonUtils;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.draw.poolconfig.dao.PrizePoolConfigDao;
import net.lab1024.sa.draw.poolconfig.domain.entity.PrizePoolConfig;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchMappingForm;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchPoolForm;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchPoolItemForm;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchSaveForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigAddForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigQueryForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigUpdateForm;
import net.lab1024.sa.draw.poolconfig.domain.vo.PrizePoolConfigVO;
import net.lab1024.sa.draw.poolconfig.manager.PrizePoolConfigManager;
import net.lab1024.sa.draw.poolitem.dao.PrizePoolItemDao;
import net.lab1024.sa.draw.poolitem.domain.entity.PrizePoolItem;
import net.lab1024.sa.draw.poolitem.manager.PrizePoolItemManager;
import net.lab1024.sa.draw.prizemapping.dao.PoolPrizeMappingDao;
import net.lab1024.sa.draw.prizemapping.domain.entity.PoolPrizeMapping;
import net.lab1024.sa.draw.prizemapping.manager.PoolPrizeMappingManager;
import net.lab1024.sa.draw.runtime.DrawStockService;
import net.lab1024.sa.prize.prizeconfig.service.PrizeConfigService;
import org.apache.commons.collections4.CollectionUtils;
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
            if (prizeConfigService.getByPrizeCode(item.getPrizeCode()) == null) {
                return ResponseDTO.userErrorParam("奖品不存在于资产大库：" + item.getPrizeCode());
            }
        }

        // 3. 逐池校验：概率闭环（BigDecimal + 容差）、池编码唯一、坑位引用的奖品必须在物资列表内
        Set<String> poolCodes = new HashSet<>();
        for (DrawWorkbenchPoolForm pool : form.getPoolList()) {
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

        // 7. 落库：奖池 upsert（工作台只管理 poolName，costAssetType 等门票配置由奖池 CRUD 页维护，不覆盖）
        for (DrawWorkbenchPoolForm pool : form.getPoolList()) {
            PrizePoolConfig existed = dbPoolMap.get(pool.getPoolCode());
            if (existed == null) {
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
                entity.setPoolCode(pool.getPoolCode());
                entity.setPrizeItemId(dbItemMap.get(mapping.getPrizeCode()).getId());
                entity.setProbability(mapping.getProbability());
                entity.setIsFallback(Boolean.TRUE.equals(mapping.getIsFallback()) ? 1 : 0);
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
     */
    public ResponseDTO<String> add(PrizePoolConfigAddForm addForm) {
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
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
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

        prizePoolConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
