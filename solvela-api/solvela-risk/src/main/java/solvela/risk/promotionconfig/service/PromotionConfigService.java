package solvela.risk.promotionconfig.service;

import solvela.enums.EnableStatusEnum;
import solvela.enums.ReviewLevelEnum;
import solvela.exception.BusinessException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.risk.promotionconfig.dao.PromotionConfigDao;
import solvela.risk.PromotionConfig;
import solvela.risk.PromotionGroup;
import solvela.risk.promotiongroup.manager.PromotionGroupManager;
import solvela.risk.promotionconfig.domain.command.PromotionConfigAddCommand;
import solvela.risk.promotionconfig.domain.query.PromotionConfigQuery;
import solvela.risk.promotionconfig.domain.command.PromotionConfigUpdateCommand;
import solvela.risk.promotionconfig.domain.dto.PromotionConfigOptionDTO;
import solvela.risk.promotionconfig.domain.dto.PromotionConfigDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 优惠配置表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PromotionConfigService {

    private final PromotionConfigDao promotionConfigDao;
    private final PromotionGroupManager promotionGroupManager;

    /**
     * 分页查询
     */
    public PageResult<PromotionConfigDTO> queryPage(PromotionConfigQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PromotionConfigDTO> list = promotionConfigDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    public PromotionConfig getById(Long id) {
        return promotionConfigDao.selectById(id);
    }
    /**
     * 下拉选项：返回启用中的全部优惠配置，按资产类型排序
     * <p>
     * 刻意不做服务端按类型过滤：配置总量本来就不大，前端一次拉全量、按 prizeType 分组缓存，
     * 运营切换奖品类型时本地过滤即可，省掉来回打接口。
     */
    public List<PromotionConfigOptionDTO> queryOptionList() {
        List<PromotionConfig> list = promotionConfigDao.selectList(
                Wrappers.<PromotionConfig>lambdaQuery()
                        .eq(PromotionConfig::getStatus, EnableStatusEnum.ENABLED)
                        .orderByAsc(PromotionConfig::getPrizeType)
                        .orderByAsc(PromotionConfig::getId));

        // 分组名一次查完做成 map，不在循环里逐条查 —— 配置总量不大，但 N+1 是习惯问题。
        // 本模块直接读 t_promotion_group 是合法的：两张表同属 solvela-risk。
        Map<Long, String> groupNameMap = loadGroupNames(list);

        return list.stream()
                .map(item -> new PromotionConfigOptionDTO(
                        item.getId(),
                        item.getPromoName(),
                        item.getPrizeType(),
                        item.getTotalAmount(),
                        item.getUsedAmount(),
                        item.getTotalQuota(),
                        item.getUsedQuota(),
                        item.getReviewLevel(),
                        item.getGroupId(),
                        item.getGroupId() == null ? null : groupNameMap.get(item.getGroupId())))
                .collect(Collectors.toList());
    }

    /**
     * 批量取分组名。没有任何配置属于分组时直接返回空 map ——
     * {@code IN ()} 是非法 SQL，空集合必须提前短路。
     */
    private Map<Long, String> loadGroupNames(List<PromotionConfig> list) {
        Set<Long> groupIds = list.stream()
                .map(PromotionConfig::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return promotionGroupManager.lambdaQuery()
                .in(PromotionGroup::getId, groupIds)
                .list().stream()
                .collect(Collectors.toMap(PromotionGroup::getId, PromotionGroup::getGroupName, (a, b) -> a));
    }

    /**
     * 添加
     */
    public void add(PromotionConfigAddCommand addForm) {
        PromotionConfig promotionConfig = SolvelaBeanUtil.copy(addForm, PromotionConfig.class);
        normalizeForSave(promotionConfig);
        promotionConfigDao.insert(promotionConfig);
    }

    /**
     * 更新
     *
     */
    public void update(PromotionConfigUpdateCommand updateForm) {
        PromotionConfig promotionConfig = SolvelaBeanUtil.copy(updateForm, PromotionConfig.class);
        normalizeForSave(promotionConfig);
        promotionConfigDao.updateById(promotionConfig);
    }

    /**
     * 新建与编辑共用的落库前归一：审核阈值按层级归零 + 自定义窗口校验。
     *
     * <p>两个入口都必须走一遍。只在 add 里做的话，「新建时选双层、编辑时改成免审」
     * 会把一对早已失效的阈值留在库里，下次有人打开表单看到的是自相矛盾的配置。
     *
     * <p>放开为 public 供 {@code PromotionGroupService} 的工作台聚合保存复用 ——
     * 那条路径绕过了本类的 add/update，归一规则必须是同一份，
     * 否则从工作台存和从单条表单存会得到两种结果。
     */
    public void normalizeForSave(PromotionConfig config) {
        normalizeReviewThreshold(config);
        validateLimitWindow(config);
    }

    /**
     * 审核阈值按层级归一。
     *
     * <p>这两个字段原先在 Form 上挂 {@code @NotNull}，而页面在层级不适用时把输入框置灰 ——
     * 一个填不了的框却是必填项，于是「选无需审核 / 单层审批」根本保存不了，
     * 表现为点保存只弹一句「参数验证错误」。必填与否取决于 reviewLevel，
     * bean validation 看不到字段间的关系，所以规则收到这里：
     * <b>不适用的档位一律归零</b>，而不是要求调用方传值。
     *
     * <p>归零而不是留 null：DDL 上这两列是 {@code NOT NULL DEFAULT 0.0000}，
     * 且 MyBatis-Plus 的 NOT_NULL 更新策略会把 null 字段整个排除在 SET 之外 ——
     * 那样「双层改单层」时旧的二审阈值会原样留在库里，清不掉。
     */
    private void normalizeReviewThreshold(PromotionConfig config) {
        ReviewLevelEnum level = config.getReviewLevel();
        if (level == null) {
            return;
        }
        if (level == ReviewLevelEnum.NONE) {
            // 免审：两个阈值都不起作用
            config.setFirstReviewThreshold(BigDecimal.ZERO);
            config.setSecondReviewThreshold(BigDecimal.ZERO);
            return;
        }
        if (config.getFirstReviewThreshold() == null) {
            // 需要审批却没给一审阈值：0 的语义是「笔笔一审」，是这里最安全的默认
            config.setFirstReviewThreshold(BigDecimal.ZERO);
        }
        if (level != ReviewLevelEnum.DOUBLE || config.getSecondReviewThreshold() == null) {
            config.setSecondReviewThreshold(BigDecimal.ZERO);
        }
    }

    /**
     * 自定义限制周期的窗口校验。
     *
     * <p>CUSTOM 之外的周期一律把两个时间清空 —— 留着上一次填的值，
     * 表单再打开时会显示一个「每日」却带着起止时间的配置，没人说得清它到底按哪个算。
     *
     * <p>窗口决定 {@code FrequencyRiskFilter} 计数键的 TTL。结束时间缺失或早于开始时间时，
     * 那里会回退成一天并打 WARN —— 但那是运行态的兜底，配置阶段就该拦住。
     */
    private void validateLimitWindow(PromotionConfig config) {
        if (!LIMIT_PERIOD_CUSTOM.equals(config.getLimitPeriod())) {
            config.setLimitStartTime(null);
            config.setLimitEndTime(null);
            return;
        }
        if (config.getLimitStartTime() == null || config.getLimitEndTime() == null) {
            throw new BusinessException("限制周期选择「自定义」时，必须填写窗口的开始与结束时间");
        }
        if (!config.getLimitEndTime().isAfter(config.getLimitStartTime())) {
            throw new BusinessException("限制周期的结束时间必须晚于开始时间");
        }
    }

    /** 限制周期取值：自定义窗口。其余取值（LIFETIME/DAILY/WEEKLY/MONTHLY）由 FrequencyRiskFilter 换算 TTL */
    private static final String LIMIT_PERIOD_CUSTOM = "CUSTOM";

    /**
     * 批量删除
     */
    public void batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }

        promotionConfigDao.deleteBatchIds(idList);
    }

    /**
     * 单个删除
     */
    public void delete(Long id) {
        if (null == id) {
            return;
        }

        promotionConfigDao.deleteById(id);
    }
}
