package net.lab1024.sa.prize.prizeconfig.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCodeUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.prize.prizeconfig.dao.PrizeConfigDao;
import net.lab1024.sa.prize.prizeconfig.domain.entity.PrizeConfig;
import net.lab1024.sa.prize.prizeconfig.domain.form.PrizeConfigAddForm;
import net.lab1024.sa.prize.prizeconfig.domain.form.PrizeConfigQueryForm;
import net.lab1024.sa.prize.prizeconfig.domain.form.PrizeConfigUpdateForm;
import net.lab1024.sa.prize.prizeconfig.domain.vo.PrizeConfigVO;
import net.lab1024.sa.prize.prizeconfig.manager.PrizeConfigManager;
import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
import net.lab1024.sa.risk.promotionconfig.service.PromotionConfigService;
import org.apache.commons.collections4.CollectionUtils;
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
        return SmartBeanUtil.copyList(list, PrizeConfigVO.class);
    }

    /**
     * 分页查询
     */
    public PageResult<PrizeConfigVO> queryPage(PrizeConfigQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrizeConfigVO> list = prizeConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 生成一个未被占用的奖品编码（10 位大写字母+数字），供前端「生成」按钮调用
     */
    public ResponseDTO<String> generatePrizeCode() {
        return ResponseDTO.ok(SmartCodeUtil.generateUniqueBizCode(this::existsByPrizeCode));
    }

    /**
     * 奖品编码是否已被占用（全局唯一，不分活动）
     */
    public boolean existsByPrizeCode(String prizeCode) {
        return prizeConfigManager.lambdaQuery().eq(PrizeConfig::getPrizeCode, prizeCode).exists();
    }

    /**
     * 校验优惠配置与奖品的资产类型是否一致
     *
     * 为什么必须服务端重算：AssetDispatchEngine 是按**优惠配置的 prizeType** 选发货策略的，
     * 一旦挂错（比如积分奖品指向了优惠券的配置），用户实际会收到一张券。
     * 前端级联下拉只是防呆，绕过页面直接 POST 就穿了，这里必须堵死。
     *
     * @return null 表示通过
     */
    private String checkPromotionConfigMatch(Long promotionConfigId, String prizeType) {
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
        if (!SmartCodeUtil.isValidBizCode(addForm.getPrizeCode())) {
            return ResponseDTO.userErrorParam("奖品" + SmartCodeUtil.BIZ_CODE_MESSAGE);
        }
        if (existsByPrizeCode(addForm.getPrizeCode())) {
            return ResponseDTO.userErrorParam("奖品编码已存在：" + addForm.getPrizeCode());
        }
        String matchError = checkPromotionConfigMatch(addForm.getPromotionConfigId(), addForm.getPrizeType());
        if (matchError != null) {
            return ResponseDTO.userErrorParam(matchError);
        }
        PrizeConfig prizeConfig = SmartBeanUtil.copy(addForm, PrizeConfig.class);
        prizeConfigDao.insert(prizeConfig);
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
        PrizeConfig prizeConfig = SmartBeanUtil.copy(updateForm, PrizeConfig.class);
        prizeConfigDao.updateById(prizeConfig);
        return ResponseDTO.ok();
    }

    public PrizeConfig getByPrizeCode(String prizeCode){
        return prizeConfigManager.lambdaQuery().eq(PrizeConfig::getPrizeCode,prizeCode).one();
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
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        prizeConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        prizeConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
