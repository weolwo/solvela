package solvela.prize.prizeconfig.service;

import solvela.enums.EnableStatusEnum;
import solvela.enums.PrizeTypeEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.prize.prizeconfig.dao.PrizeConfigDao;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.domain.command.PrizeConfigAddCommand;
import solvela.prize.prizeconfig.domain.query.PrizeConfigQuery;
import solvela.prize.prizeconfig.domain.command.PrizeConfigUpdateCommand;
import solvela.prize.prizeconfig.domain.dto.PrizeConfigDTO;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;
import solvela.risk.PromotionConfig;
import solvela.risk.promotionconfig.service.PromotionConfigService;
import solvela.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 奖品配置表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizeConfigService {

    private final PrizeConfigDao prizeConfigDao;
    private final PrizeConfigManager prizeConfigManager;
    private final PromotionConfigService promotionConfigService;

    /**
     * 查询活动下的全部奖品（含停用）：抽奖工作台回显时按 prizeCode 补名称/价值等展示信息
     */
    public List<PrizeConfig> queryListByActivityCode(String activityCode) {
        return prizeConfigManager.lambdaQuery().eq(PrizeConfig::getActivityCode, activityCode).list();
    }

    /**
     * 查询活动下启用中的奖品：供抽奖工作台「从资产大库引入奖项」抽屉选择
     */
    public List<PrizeConfigDTO> queryEnabledList(String activityCode) {
        List<PrizeConfig> list = prizeConfigManager.lambdaQuery()
                .eq(PrizeConfig::getActivityCode, activityCode)
                .eq(PrizeConfig::getStatus, EnableStatusEnum.ENABLED)
                .orderByAsc(PrizeConfig::getSortWeight)
                .list();
        return SolvelaBeanUtil.copyList(list, PrizeConfigDTO.class);
    }

    /**
     * 分页查询
     */
    public PageResult<PrizeConfigDTO> queryPage(PrizeConfigQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PrizeConfigDTO> list = prizeConfigDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 生成一个未被占用的奖品编码（10 位大写字母+数字），供前端「生成」按钮调用
     */
    public String generatePrizeCode() {
        return SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.PRIZE, this::existsByPrizeCode);
    }

    /**
     * 奖品编码是否已被占用（全局唯一，不分活动）
     */
    public boolean existsByPrizeCode(String prizeCode) {
        return prizeConfigManager.lambdaQuery().eq(PrizeConfig::getPrizeCode, prizeCode).exists();
    }

    /**
     * 校验优惠配置与奖品的资产类型是否一致
     * <p>
     * 为什么必须服务端重算：AssetDispatchEngine 是按**优惠配置的 prizeType** 选发货策略的，
     * 一旦挂错（比如积分奖品指向了优惠券的配置），用户实际会收到一张券。
     * 前端级联下拉只是防呆，绕过页面直接 POST 就穿了，这里必须堵死。
     * <p>
     * 放开为 public 供活动创建向导的聚合接口在<b>落库前</b>做全量预校验 ——
     * 那里必须先把所有奖品验一遍再开始插入，否则一旦插到一半才发现类型不匹配，
     * 就只能靠 setRollbackOnly 回滚，而那会让外层提交抛 UnexpectedRollbackException、
     * 把本来给运营看的人话提示变成一个 500（本项目在提案域踩过这个坑）。
     *
     * @return null 表示通过
     */
    public String checkPromotionConfigMatch(Long promotionConfigId, String prizeType) {
        // 标记类奖品不挂优惠配置，这是它的正常形态而不是漏配。
        // 优惠配置承载的是预算、库存、风控频次与审批阈值 —— 这四样对标记全都不适用：
        // 它不动账（没有预算可扣）、不进提案（没有审批可走），MarkerHandler 直接判成功。
        // 强行要求挂一个，只会让运营为每个「谢谢参与」建一条永远不会被消耗的空配置。
        if (PrizeTypeEnum.MARKER.name().equals(prizeType)) {
            return null;
        }
        // 其余类型必须挂：这条「必填」刻意放在领域校验里而不是 Form 的 @NotNull 上 ——
        // 是否必填取决于 prizeType，而 bean validation 看不到字段之间的关系。
        if (promotionConfigId == null) {
            return "该资产类型的奖品必须挂优惠配置：预算、风控与审批阈值都由它承载";
        }
        PromotionConfig promotionConfig = promotionConfigService.getById(promotionConfigId);
        if (promotionConfig == null) {
            return "优惠配置不存在：" + promotionConfigId;
        }
        if (!StringUtils.equals(promotionConfig.getPrizeType(), prizeType)) {
            return "优惠配置「" + promotionConfig.getPromoName() + "」的资产类型是 "
                    + promotionConfig.getPrizeType() + "，与奖品的 " + prizeType + " 不一致";
        }
        return null;
    }

    /**
     * 添加
     * 奖品编码允许手工输入，故服务端必须重校验格式与唯一性
     */
    public void add(PrizeConfigAddCommand addForm) {
        if (!SolvelaCodeUtil.isValidBizCode(addForm.getPrizeCode())) {
            throw new BusinessException("奖品" + SolvelaCodeUtil.BIZ_CODE_MESSAGE);
        }
        if (existsByPrizeCode(addForm.getPrizeCode())) {
            throw new BusinessException("奖品编码已存在：" + addForm.getPrizeCode());
        }
        String matchError = checkPromotionConfigMatch(addForm.getPromotionConfigId(), addForm.getPrizeType());
        if (matchError != null) {
            throw new BusinessException(matchError);
        }
        PrizeConfig prizeConfig = SolvelaBeanUtil.copy(addForm, PrizeConfig.class);
        // 标记类即便调用方传了 id 也不落库：这一列对它没有语义，留着只会让人以为「这个池子会扣」
        if (PrizeTypeEnum.MARKER.name().equals(addForm.getPrizeType())) {
            prizeConfig.setPromotionConfigId(null);
        }
        prizeConfigDao.insert(prizeConfig);
    }

    /**
     * 奖品启用 / 禁用（单个开关与批量禁用共用）。
     *
     * <p>禁用不做任何校验 —— 出问题时能立刻停掉一个奖品是运营最需要的能力；
     * 已被奖池/奖级引用的奖品禁用后不再发放，但历史流水不受影响。
     */
    public void updateStatus(List<Long> idList, EnableStatusEnum status) {
        // 原先这里有一段「目标状态只能是 1 或 0」的运行期校验。入参换成枚举之后它没有意义了：
        // 非法值在 Jackson 反序列化阶段就被拒了（400 / INVALID_ARGUMENT），走不到这一行。
        for (Long id : idList) {
            PrizeConfig update = new PrizeConfig();
            update.setId(id);
            update.setStatus(status);
            prizeConfigDao.updateById(update);
        }
    }

    /**
     * 更新
     *
     */
    public void update(PrizeConfigUpdateCommand updateForm) {
        // 编辑同样要校验：改类型不改配置（或反过来）都会造成错配。
        // 🔴 判据不能再是「promotionConfigId != null」：标记类奖品本来就传 null，
        // 那样写会让「SCORE 改成 MARKER」这种编辑完全跳过校验。改成看 prizeType 有没有传，
        // 空值该不该放行交给 checkPromotionConfigMatch 按类型决定。
        if (StringUtils.isNotBlank(updateForm.getPrizeType())) {
            String matchError = checkPromotionConfigMatch(updateForm.getPromotionConfigId(), updateForm.getPrizeType());
            if (matchError != null) {
                throw new BusinessException(matchError);
            }
        }
        PrizeConfig prizeConfig = SolvelaBeanUtil.copy(updateForm, PrizeConfig.class);
        prizeConfigDao.updateById(prizeConfig);

        // 改成标记类之后，旧的 promotion_config_id 必须再补一条 UPDATE 显式清掉。
        // MyBatis-Plus 默认的 NOT_NULL 更新策略会把值为 null 的字段整个排除在 SET 之外，
        // 所以上面那条 updateById 无论如何都清不掉它，会留下一个指向别的资产类型的悬挂 ID。
        // （不能图省事把该列改成 FieldStrategy.ALWAYS：updateStatus 那种「只 set id + status」
        //   的局部更新会因此把其余列一并写成 null。）
        if (PrizeTypeEnum.MARKER.name().equals(updateForm.getPrizeType())) {
            prizeConfigManager.lambdaUpdate()
                    .eq(PrizeConfig::getId, updateForm.getId())
                    .set(PrizeConfig::getPromotionConfigId, null)
                    .update();
        }
    }

    /**
     * 把一个活动下的全部奖品配置复制到新活动。
     *
     * <p>返回 <b>旧奖品编码 -&gt; 新奖品编码</b> 的映射：奖池物资、任务奖励、彩票奖级
     * 三处都按 prize_code 引用奖品，玩法侧复制时全靠这份映射重指向，
     * 否则新活动的奖池会指向老活动的奖品 —— 而那是能跑通的，只是发出去的是别人的奖。
     *
     * <p>{@code promotion_config_id} <b>照抄不动</b>：优惠配置本来就是可跨活动复用的预算池，
     * 指向原来那条正是对的。真要给新活动单独一套预算，去优惠配置分组那边复制一份再改挂。
     *
     * @return 旧奖品编码 -> 新奖品编码；源活动没有奖品时返回空 map
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> copyForActivity(String sourceActivityCode, String targetActivityCode) {
        List<PrizeConfig> sourceList = queryListByActivityCode(sourceActivityCode);
        Map<String, String> prizeCodeMap = new LinkedHashMap<>();
        for (PrizeConfig source : sourceList) {
            PrizeConfig copy = SolvelaBeanUtil.copy(source, PrizeConfig.class);
            copy.setId(null);
            copy.setActivityCode(targetActivityCode);
            // 奖品编码全局唯一，必须重新发；判重走库，不是只在本次批次内去重
            copy.setPrizeCode(SolvelaCodeUtil.generateUniqueBizCode(
                    SolvelaCodeUtil.BizCodePrefix.PRIZE, this::existsByPrizeCode));
            copy.setCreateBy(null);
            copy.setCreateTime(null);
            copy.setUpdateBy(null);
            copy.setUpdateTime(null);
            prizeConfigDao.insert(copy);
            prizeCodeMap.put(source.getPrizeCode(), copy.getPrizeCode());
        }
        return prizeCodeMap;
    }

    public PrizeConfig getByPrizeCode(String prizeCode) {
        return prizeConfigManager.lambdaQuery().eq(PrizeConfig::getPrizeCode, prizeCode).one();
    }

    /**
     * 按「活动 + 奖品编码」精确查询
     * prize_code 只在活动内唯一，跨活动复用同一编码时 getByPrizeCode 会命中多行并抛 TooManyResultsException，
     * 凡是拿得到 activityCode 的调用方都应该走这个方法
     */
    public PrizeConfig getByActivityCodeAndPrizeCode(String activityCode, String prizeCode) {
        return prizeConfigManager.lambdaQuery()
                .eq(PrizeConfig::getActivityCode, activityCode)
                .eq(PrizeConfig::getPrizeCode, prizeCode)
                .one();
    }

    /**
     * 批量删除
     */
    public void batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }

        prizeConfigDao.deleteBatchIds(idList);
    }

    /**
     * 单个删除
     */
    public void delete(Long id) {
        if (null == id) {
            return;
        }

        prizeConfigDao.deleteById(id);
    }
}
