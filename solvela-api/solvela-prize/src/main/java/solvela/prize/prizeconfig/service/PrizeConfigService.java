package solvela.prize.prizeconfig.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.domain.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.prize.prizeconfig.dao.PrizeConfigDao;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.domain.form.PrizeConfigAddForm;
import solvela.prize.prizeconfig.domain.form.PrizeConfigQueryForm;
import solvela.prize.prizeconfig.domain.form.PrizeConfigUpdateForm;
import solvela.prize.prizeconfig.domain.form.PrizeStatusUpdateForm;
import solvela.prize.prizeconfig.domain.vo.PrizeConfigVO;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;
import solvela.risk.PromotionConfig;
import solvela.risk.promotionconfig.service.PromotionConfigService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * 奖品状态：1-启用
     */
    private static final Integer STATUS_ENABLED = 1;

    /**
     * 状态：0-禁用
     */
    private static final Integer STATUS_DISABLED = 0;

    /**
     * 查询活动下的全部奖品（含停用）：抽奖工作台回显时按 prizeCode 补名称/价值等展示信息
     */
    public List<PrizeConfig> queryListByActivityCode(String activityCode) {
        return prizeConfigManager.lambdaQuery().eq(PrizeConfig::getActivityCode, activityCode).list();
    }

    /**
     * 查询活动下启用中的奖品：供抽奖工作台「从资产大库引入奖项」抽屉选择
     */
    public List<PrizeConfigVO> queryEnabledList(String activityCode) {
        List<PrizeConfig> list = prizeConfigManager.lambdaQuery()
                .eq(PrizeConfig::getActivityCode, activityCode)
                .eq(PrizeConfig::getStatus, STATUS_ENABLED)
                .orderByAsc(PrizeConfig::getSortWeight)
                .list();
        return SolvelaBeanUtil.copyList(list, PrizeConfigVO.class);
    }

    /**
     * 分页查询
     */
    public PageResult<PrizeConfigVO> queryPage(PrizeConfigQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PrizeConfigVO> list = prizeConfigDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 生成一个未被占用的奖品编码（10 位大写字母+数字），供前端「生成」按钮调用
     */
    public ResponseDTO<String> generatePrizeCode() {
        return ResponseDTO.ok(SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.PRIZE, this::existsByPrizeCode));
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
    public ResponseDTO<String> add(PrizeConfigAddForm addForm) {
        if (!SolvelaCodeUtil.isValidBizCode(addForm.getPrizeCode())) {
            return ResponseDTO.userErrorParam("奖品" + SolvelaCodeUtil.BIZ_CODE_MESSAGE);
        }
        if (existsByPrizeCode(addForm.getPrizeCode())) {
            return ResponseDTO.userErrorParam("奖品编码已存在：" + addForm.getPrizeCode());
        }
        String matchError = checkPromotionConfigMatch(addForm.getPromotionConfigId(), addForm.getPrizeType());
        if (matchError != null) {
            return ResponseDTO.userErrorParam(matchError);
        }
        PrizeConfig prizeConfig = SolvelaBeanUtil.copy(addForm, PrizeConfig.class);
        prizeConfigDao.insert(prizeConfig);
        return ResponseDTO.ok();
    }

    /**
     * 奖品启用 / 禁用（单个开关与批量禁用共用）。
     *
     * <p>禁用不做任何校验 —— 出问题时能立刻停掉一个奖品是运营最需要的能力；
     * 已被奖池/奖级引用的奖品禁用后不再发放，但历史流水不受影响。
     */
    public ResponseDTO<String> updateStatus(PrizeStatusUpdateForm form) {
        if (!STATUS_ENABLED.equals(form.getStatus()) && !STATUS_DISABLED.equals(form.getStatus())) {
            return ResponseDTO.userErrorParam("目标状态只能是 1-启用 或 0-禁用");
        }
        for (Long id : form.getIdList()) {
            PrizeConfig update = new PrizeConfig();
            update.setId(id);
            update.setStatus(form.getStatus());
            prizeConfigDao.updateById(update);
        }
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PrizeConfigUpdateForm updateForm) {
        // 编辑同样要校验：改类型不改配置（或反过来）都会造成错配
        if (updateForm.getPromotionConfigId() != null && StringUtils.isNotBlank(updateForm.getPrizeType())) {
            String matchError = checkPromotionConfigMatch(updateForm.getPromotionConfigId(), updateForm.getPrizeType());
            if (matchError != null) {
                return ResponseDTO.userErrorParam(matchError);
            }
        }
        PrizeConfig prizeConfig = SolvelaBeanUtil.copy(updateForm, PrizeConfig.class);
        prizeConfigDao.updateById(prizeConfig);
        return ResponseDTO.ok();
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
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        prizeConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id) {
            return ResponseDTO.ok();
        }

        prizeConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
