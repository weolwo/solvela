package solvela.risk.promotiongroup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.enums.EnableStatusEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.exception.BusinessException;
import solvela.risk.PromotionConfig;
import solvela.risk.PromotionGroup;
import solvela.risk.promotionconfig.manager.PromotionConfigManager;
import solvela.risk.promotionconfig.service.PromotionConfigService;
import solvela.risk.promotiongroup.dao.PromotionGroupDao;
import solvela.risk.promotiongroup.domain.command.PromotionGroupWorkbenchSaveCommand;
import solvela.risk.promotiongroup.domain.dto.PromotionGroupDTO;
import solvela.risk.promotiongroup.domain.dto.PromotionGroupWorkbenchDTO;
import solvela.risk.promotiongroup.domain.query.PromotionGroupQuery;
import solvela.risk.promotiongroup.manager.PromotionGroupManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 优惠配置分组 Service —— 工作台的聚合读写。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PromotionGroupService {

    private final PromotionGroupDao promotionGroupDao;
    private final PromotionGroupManager promotionGroupManager;
    private final PromotionConfigManager promotionConfigManager;
    private final PromotionConfigService promotionConfigService;

    /**
     * 能进分组的资产类型。
     *
     * <p>与前端 {@code prize-config-const.js} 的 dispatchable 是同一份能力的两面，
     * 但这里必须服务端<b>再判一次</b>：前端的过滤只是防呆，绕过页面直接 POST 就穿了（铁律 2）。
     *
     * <p>{@code MARKER} 刻意不在内：标记类奖品不动账、不进提案，
     * 预算/库存/风控/审批四样它一个都用不上，{@code t_prize_config.promotion_config_id}
     * 对它已经是可空的。让它进组只会产生一条永远不会被消耗的空池子。
     */
    private static final Set<String> GROUPABLE_PRIZE_TYPES = Set.of(
            PrizeTypeEnum.SCORE.name(),
            PrizeTypeEnum.BALANCE.name(),
            PrizeTypeEnum.COUPON.name(),
            PrizeTypeEnum.PHYSICAL.name());

    // ------------------------------------------------------------------ 查询

    public PageResult<PromotionGroupDTO> queryPage(PromotionGroupQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PromotionGroupDTO> list = promotionGroupDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 工作台聚合回显：分组 + 组内各类型的配置。
     *
     * <p>{@code groupId} 为空表示「新建」，返回一个只有默认值的空壳，
     * 让前端不必为新建与编辑写两套初始化逻辑。
     */
    public PromotionGroupWorkbenchDTO workbenchDetail(Long groupId) {
        PromotionGroupWorkbenchDTO dto = new PromotionGroupWorkbenchDTO();
        if (groupId == null) {
            dto.setStatus(EnableStatusEnum.ENABLED);
            dto.setItemList(new ArrayList<>());
            return dto;
        }
        PromotionGroup group = promotionGroupDao.selectById(groupId);
        if (group == null) {
            throw new BusinessException("优惠配置分组不存在：" + groupId);
        }
        SolvelaBeanUtil.copyProperties(group, dto);
        dto.setItemList(SolvelaBeanUtil.copyList(
                listConfigsByGroup(groupId), PromotionGroupWorkbenchDTO.PromotionGroupItemDTO.class));
        return dto;
    }

    // ------------------------------------------------------------------ 保存

    /**
     * 工作台聚合保存：{@code t_promotion_group} + {@code t_promotion_config} 同一事务。
     *
     * <h3>🔴 子表是 upsert，不是整表重建</h3>
     * 彩票工作台那套「先 remove 再逐条 insert」在这里是资损操作：
     * {@code used_quota} / {@code used_amount} 会被清零（预算闸门重置到满水位），
     * 且 {@code t_prize_config} 与 {@code t_proposal_record} 上指向配置的 ID 会全部变悬挂。
     * 所以这里按 {@code (group_id, prize_type)} 定位后更新，缺的才插入。
     *
     * <p>同理，「这次没提交的类型」走<b>停用</b>而不是删除 ——
     * 那条配置可能已经发过奖，历史提案还指着它。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long workbenchSave(PromotionGroupWorkbenchSaveCommand form) {
        if (StringUtils.isBlank(form.getGroupName())) {
            throw new BusinessException("分组名称不能为空");
        }
        List<PromotionGroupWorkbenchSaveCommand.PromotionGroupItemCommand> itemList =
                form.getItemList() == null ? List.of() : form.getItemList();
        validateItems(itemList);

        PromotionGroup group = upsertGroup(form);
        upsertItems(group, itemList);
        return group.getId();
    }

    /**
     * 组内类型的合法性：全量先验完再落库。
     *
     * <p>验完再插，插入阶段就只剩真正的 DB 异常 —— 那种异常抛出去正好由 {@code @Transactional} 回滚。
     * 边验边插的话，一旦插到一半才发现类型非法，就只能靠 setRollbackOnly，
     * 而那会让外层提交抛 UnexpectedRollbackException，把给运营看的人话提示变成一个 500
     * （本项目在提案域踩过这个坑）。
     */
    private void validateItems(List<PromotionGroupWorkbenchSaveCommand.PromotionGroupItemCommand> itemList) {
        Set<String> seen = new HashSet<>();
        for (PromotionGroupWorkbenchSaveCommand.PromotionGroupItemCommand item : itemList) {
            String prizeType = item.getPrizeType();
            if (StringUtils.isBlank(prizeType)) {
                throw new BusinessException("组内存在没有指定资产类型的配置");
            }
            if (PrizeTypeEnum.MARKER.name().equals(prizeType)) {
                throw new BusinessException("标记类奖品不需要优惠配置，不能加入分组");
            }
            if (!GROUPABLE_PRIZE_TYPES.contains(prizeType)) {
                throw new BusinessException("资产类型 " + prizeType + " 还没有派发策略，配了也发不出去，暂不能加入分组");
            }
            // 唯一索引 uk_group_prize_type 也会挡，但那会抛一个 SQL 异常。
            // 同一次提交里出现两条同类型是前端状态出了问题，得给人话
            if (!seen.add(prizeType)) {
                throw new BusinessException("组内资产类型重复：" + prizeType + "，一种类型只能配一条");
            }
        }
    }

    private PromotionGroup upsertGroup(PromotionGroupWorkbenchSaveCommand form) {
        if (form.getId() == null) {
            PromotionGroup group = new PromotionGroup();
            // 编码由服务端发，不采信前端传值：它是这个组对外的标识，唯一性必须由发号方保证（铁律 8）
            group.setGroupCode(SolvelaCodeUtil.generateUniqueBizCode(this::existsByGroupCode));
            group.setGroupName(form.getGroupName());
            group.setRemark(form.getRemark());
            group.setStatus(form.getStatus() == null ? EnableStatusEnum.ENABLED : form.getStatus());
            promotionGroupDao.insert(group);
            return group;
        }
        PromotionGroup existed = promotionGroupDao.selectById(form.getId());
        if (existed == null) {
            throw new BusinessException("优惠配置分组不存在：" + form.getId());
        }
        // 没传状态就沿用库里的：这个方法的返回值会被 resolveItemStatus 读，
        // 拿一个 null 状态去判「组是不是停用」会让子项一律按启用落库
        EnableStatusEnum status = form.getStatus() == null ? existed.getStatus() : form.getStatus();

        PromotionGroup update = new PromotionGroup();
        update.setId(existed.getId());
        update.setGroupName(form.getGroupName());
        update.setRemark(form.getRemark());
        update.setStatus(status);
        // group_code 不进 SET：编码创建后不可改，传了也不算数
        promotionGroupDao.updateById(update);

        // 🔴 返回值必须带上本次提交的名字与状态，不能直接返回库里查出来的那份：
        // 下面 resolveItemStatus 读的是 group.getStatus()，
        // 用旧状态判断会让「把分组改成停用并保存」这一步漏掉子配置
        existed.setGroupName(form.getGroupName());
        existed.setStatus(status);
        return existed;
    }

    /**
     * 组内配置逐条 upsert，并把本次未提交的类型停用。
     */
    private void upsertItems(PromotionGroup group,
                             List<PromotionGroupWorkbenchSaveCommand.PromotionGroupItemCommand> itemList) {
        // 定位一律按 (groupId, prizeType) 重查，不采信前端传的配置 ID ——
        // 传错一个就会把别的组的配置改掉，而且改得悄无声息
        Map<String, PromotionConfig> existedMap = listConfigsByGroup(group.getId()).stream()
                .collect(Collectors.toMap(PromotionConfig::getPrizeType, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));

        Set<String> submitted = new HashSet<>();
        for (PromotionGroupWorkbenchSaveCommand.PromotionGroupItemCommand item : itemList) {
            submitted.add(item.getPrizeType());
            PromotionConfig config = SolvelaBeanUtil.copy(item, PromotionConfig.class);
            config.setGroupId(group.getId());
            config.setPromoName(resolvePromoName(group, item));
            config.setStatus(resolveItemStatus(group, item));
            // 阈值归零与自定义窗口校验复用单条表单那一份，两条路径的口径必须一致
            promotionConfigService.normalizeForSave(config);

            PromotionConfig existed = existedMap.get(item.getPrizeType());
            if (existed == null) {
                // 新增：id 必须显式清掉。前端可能带着一个别处的 ID 过来，
                // 留着会让 MyBatis-Plus 拿它当主键插入
                config.setId(null);
                // used_quota / used_amount 不设，走 DDL 默认值 0
                promotionConfigManager.save(config);
            } else {
                config.setId(existed.getId());
                // 🔴 usedQuota / usedAmount 一个字都不能带：它们不在 Command 里，
                // copy 出来就是 null，而 MyBatis-Plus 的 NOT_NULL 策略会把 null 排除在 SET 之外 ——
                // 这正是我们要的，水位由发放链路的原子 SQL 独占维护
                promotionConfigManager.updateById(config);
            }
        }

        // 本次没提交的类型：停用，不删。那条配置可能已经发过奖，
        // 历史提案与奖品配置还指着它的 ID，删掉就是断链
        for (Map.Entry<String, PromotionConfig> entry : existedMap.entrySet()) {
            if (submitted.contains(entry.getKey())) {
                continue;
            }
            PromotionConfig off = new PromotionConfig();
            off.setId(entry.getValue().getId());
            off.setStatus(EnableStatusEnum.DISABLED);
            promotionConfigManager.updateById(off);
            log.info("【优惠配置分组】类型 {} 已从分组 {} 移除，配置 {} 转为停用（不删除，历史引用还在）",
                    entry.getKey(), group.getId(), entry.getValue().getId());
        }
    }

    /**
     * 子配置的启停：<b>组停用时一律落停用</b>，不管前端那张卡上的开关是什么状态。
     *
     * <p>维持「组停用 ⇒ 组内全停用」这条不变量。少了这一步就会出现
     * 「组是关的、但某个类型还在发奖」—— 而运营看着列表上那个关掉的开关，
     * 完全不会想到还要去查子配置。
     */
    private EnableStatusEnum resolveItemStatus(PromotionGroup group,
                                               PromotionGroupWorkbenchSaveCommand.PromotionGroupItemCommand item) {
        if (group.getStatus() == EnableStatusEnum.DISABLED) {
            return EnableStatusEnum.DISABLED;
        }
        return item.getStatus() == null ? EnableStatusEnum.ENABLED : item.getStatus();
    }

    /**
     * 子配置的名称：运营不该为组里每一条都单独起名，留空时按「分组名-类型」生成。
     * {@code promo_name} 是 NOT NULL，且提案与预算告警都会打印它，不能是空串。
     */
    private String resolvePromoName(PromotionGroup group,
                                    PromotionGroupWorkbenchSaveCommand.PromotionGroupItemCommand item) {
        if (StringUtils.isNotBlank(item.getPromoName())) {
            return item.getPromoName();
        }
        return group.getGroupName() + "-" + item.getPrizeType();
    }

    /**
     * 复制一个分组：连同组内每种类型的配置一起复制成新的一份。
     *
     * <p>一个活动的几套风控参数通常和上一个活动只差一点点，复制省掉的就是那部分重复劳动 ——
     * 与优惠配置列表页那个「复制」是同一个诉求，只是这里一次复制一整组。
     *
     * <h3>🔴 三样东西必须重置，不能照抄</h3>
     * <ul>
     *   <li>{@code group_code}：重新发一个。编码是这个组对外的标识，抄过来就撞唯一索引了；</li>
     *   <li>配置的 {@code id}：必须置空。留着会让 MyBatis-Plus 拿它当主键插入，
     *       撞主键还算好的，更坏的情况是把源组的配置改掉；</li>
     *   <li>{@code used_quota} / {@code used_amount}：<b>必须归零</b>。
     *       它们是运行态水位，抄过来等于新池子开局就把预算闸门拨到了半途 ——
     *       源组已经发掉 8 成预算的话，复制出来的新组一上线就几乎发不出东西，
     *       而且这件事在页面上完全看不出来。</li>
     * </ul>
     *
     * @param groupName 新分组名称，留空则在原名后加「副本」
     * @return 新分组ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long sourceId, String groupName) {
        PromotionGroup source = promotionGroupDao.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("优惠配置分组不存在：" + sourceId);
        }

        PromotionGroup target = new PromotionGroup();
        target.setGroupCode(SolvelaCodeUtil.generateUniqueBizCode(this::existsByGroupCode));
        target.setGroupName(StringUtils.isBlank(groupName) ? source.getGroupName() + " 副本" : groupName);
        target.setRemark(source.getRemark());
        // 状态照抄：复制的语义就是「一样的一份」。新组还没有任何奖品配置指向它，
        // 即便是启用状态也不会有任何预算被消耗
        target.setStatus(source.getStatus());
        promotionGroupDao.insert(target);

        for (PromotionConfig config : listConfigsByGroup(sourceId)) {
            PromotionConfig copy = SolvelaBeanUtil.copy(config, PromotionConfig.class);
            copy.setId(null);
            copy.setGroupId(target.getId());
            // 名字按新组名重生成：组内配置的名字一律是「组名-类型」自动生成的
            // （工作台压根没暴露这个字段给人改），照抄会让新组的每条配置都挂着旧组的名字，
            // 而奖品配置的下拉里正是靠这个名字分辨「这条是哪个活动的」
            copy.setPromoName(target.getGroupName() + "-" + config.getPrizeType());
            // 运行态水位归零 —— 见方法注释
            copy.setUsedQuota(0);
            copy.setUsedAmount(BigDecimal.ZERO);
            // 审计列交给 MyBatis-Plus 的自动填充与 DDL 默认值，不抄源记录的
            copy.setCreateBy(null);
            copy.setCreateTime(null);
            copy.setUpdateBy(null);
            copy.setUpdateTime(null);
            promotionConfigManager.save(copy);
        }
        log.info("【优惠配置分组】分组 {} 已复制为 {}（{}）", sourceId, target.getId(), target.getGroupName());
        return target.getId();
    }

    // ------------------------------------------------------------------ 状态与删除

    /**
     * 分组主开关。
     *
     * <h3>关：一键熔断</h3>
     * 组内每一条配置一起停用。出事时能一次停掉整个活动的发放 ——
     * 这正是这个开关存在的理由，所以它<b>不接受</b>「只关组不关配置」这种半截状态。
     *
     * <h3>开：必须显式选要开哪几种</h3>
     * 关的那一刻，「原来哪些类型是开的」这个信息就被覆盖掉了。
     * 猜一个默认（比如全开）的代价是把本来就该停发的类型重新放出去，
     * 而那是静默的资损方向。所以开启必须带上 {@code enablePrizeTypes}，
     * 名单里的启用、其余保持停用。
     *
     * @param enablePrizeTypes 要启用的资产类型；仅在 status=ENABLED 时有意义
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, EnableStatusEnum status, List<String> enablePrizeTypes) {
        PromotionGroup group = promotionGroupDao.selectById(id);
        if (group == null) {
            throw new BusinessException("优惠配置分组不存在：" + id);
        }
        List<PromotionConfig> configList = listConfigsByGroup(id);

        if (status == EnableStatusEnum.DISABLED) {
            switchConfigs(configList, config -> EnableStatusEnum.DISABLED);
        } else {
            Set<String> toEnable = enablePrizeTypes == null ? Set.of() : new HashSet<>(enablePrizeTypes);
            if (!configList.isEmpty() && toEnable.isEmpty()) {
                throw new BusinessException("启用分组时至少要选择一种要启用的资产类型");
            }
            Set<String> owned = configList.stream().map(PromotionConfig::getPrizeType).collect(Collectors.toSet());
            for (String prizeType : toEnable) {
                if (!owned.contains(prizeType)) {
                    throw new BusinessException("资产类型 " + prizeType + " 不在本分组内，无法启用");
                }
            }
            switchConfigs(configList,
                    config -> toEnable.contains(config.getPrizeType()) ? EnableStatusEnum.ENABLED : EnableStatusEnum.DISABLED);
        }

        PromotionGroup update = new PromotionGroup();
        update.setId(id);
        update.setStatus(status);
        promotionGroupDao.updateById(update);
        log.info("【优惠配置分组】分组 {} 切换为 {}，同步处理组内 {} 条配置", id, status, configList.size());
    }

    /**
     * 单条配置的发放开关 —— 供分组列表展开行里直接拨，不用进工作台。
     *
     * <p>🔴 <b>组停用时不允许单独启用某个类型</b>：那会破掉「组停用 ⇒ 组内全停用」
     * 这条不变量，而列表上那个关着的分组开关会让人以为整个组都停了。
     * 反方向（组是启用的，单独关掉一个类型）完全合法 —— 那正是这个开关的用途。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateItemStatus(Long configId, EnableStatusEnum status) {
        PromotionConfig config = promotionConfigManager.getById(configId);
        if (config == null) {
            throw new BusinessException("优惠配置不存在：" + configId);
        }
        if (status == EnableStatusEnum.ENABLED && config.getGroupId() != null) {
            PromotionGroup group = promotionGroupDao.selectById(config.getGroupId());
            if (group != null && group.getStatus() == EnableStatusEnum.DISABLED) {
                throw new BusinessException("分组「" + group.getGroupName() + "」已停用，请先启用分组再单独开启类型");
            }
        }
        PromotionConfig update = new PromotionConfig();
        update.setId(configId);
        update.setStatus(status);
        promotionConfigManager.updateById(update);
    }

    /**
     * 逐条切状态。只 set id + status，其余列一个都不碰 ——
     * 尤其是 used_quota / used_amount，那是发放链路的原子 SQL 独占的水位。
     */
    private void switchConfigs(List<PromotionConfig> configList,
                               Function<PromotionConfig, EnableStatusEnum> statusResolver) {
        for (PromotionConfig config : configList) {
            EnableStatusEnum target = statusResolver.apply(config);
            if (config.getStatus() == target) {
                continue;
            }
            PromotionConfig update = new PromotionConfig();
            update.setId(config.getId());
            update.setStatus(target);
            promotionConfigManager.updateById(update);
        }
    }

    /**
     * 删除分组 = <b>解散</b>：组没了，组内配置还在，只是变回未分组的独立配置。
     *
     * <p>不连带删配置，理由与「移除类型走停用」是同一条：
     * {@code t_prize_config} 与 {@code t_proposal_record} 上都有指向配置的 ID，
     * 删掉就是悬挂引用，而那只会在发奖时以「优惠配置不存在或已停用」的形式暴露出来。
     *
     * <p>本模块<b>看不到</b> {@code t_prize_config}（solvela-prize 依赖 solvela-risk，
     * 依赖方向是单向的），所以这里做不了引用检查 ——
     * 解散而不是删除，正好把这个检查变成不需要的。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) {
            return;
        }
        promotionConfigManager.lambdaUpdate()
                .eq(PromotionConfig::getGroupId, id)
                .set(PromotionConfig::getGroupId, null)
                .update();
        promotionGroupDao.deleteById(id);
    }

    // ------------------------------------------------------------------ 内部

    private List<PromotionConfig> listConfigsByGroup(Long groupId) {
        return promotionConfigManager.lambdaQuery()
                .eq(PromotionConfig::getGroupId, groupId)
                .orderByAsc(PromotionConfig::getPrizeType)
                .list();
    }

    public boolean existsByGroupCode(String groupCode) {
        return promotionGroupManager.lambdaQuery().eq(PromotionGroup::getGroupCode, groupCode).exists();
    }
}
