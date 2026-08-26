package solvela.risk.promotionconfig.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.domain.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.risk.promotionconfig.dao.PromotionConfigDao;
import solvela.risk.PromotionConfig;
import solvela.risk.promotionconfig.domain.form.PromotionConfigAddForm;
import solvela.risk.promotionconfig.domain.form.PromotionConfigQueryForm;
import solvela.risk.promotionconfig.domain.form.PromotionConfigUpdateForm;
import solvela.risk.promotionconfig.domain.vo.PromotionConfigOptionVO;
import solvela.risk.promotionconfig.domain.vo.PromotionConfigVO;
import org.springframework.stereotype.Service;

import java.util.List;
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

    /**
     * 分页查询
     */
    public PageResult<PromotionConfigVO> queryPage(PromotionConfigQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PromotionConfigVO> list = promotionConfigDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    public PromotionConfig getById(Long id) {
        return promotionConfigDao.selectById(id);
    }

    /**
     * 优惠配置状态：1-启用
     */
    private static final Integer STATUS_ENABLED = 1;

    /**
     * 下拉选项：返回启用中的全部优惠配置，按资产类型排序
     *
     * 刻意不做服务端按类型过滤：配置总量本来就不大，前端一次拉全量、按 prizeType 分组缓存，
     * 运营切换奖品类型时本地过滤即可，省掉来回打接口。
     */
    public ResponseDTO<List<PromotionConfigOptionVO>> queryOptionList() {
        List<PromotionConfig> list = promotionConfigDao.selectList(
                Wrappers.<PromotionConfig>lambdaQuery()
                        .eq(PromotionConfig::getStatus, STATUS_ENABLED)
                        .orderByAsc(PromotionConfig::getPrizeType)
                        .orderByAsc(PromotionConfig::getId));
        List<PromotionConfigOptionVO> optionList = list.stream()
                .map(item -> new PromotionConfigOptionVO(
                        item.getId(),
                        item.getPromoName(),
                        item.getPrizeType(),
                        item.getTotalAmount(),
                        item.getUsedAmount(),
                        item.getTotalQuota(),
                        item.getUsedQuota(),
                        item.getReviewLevel()))
                .collect(Collectors.toList());
        return ResponseDTO.ok(optionList);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PromotionConfigAddForm addForm) {
        PromotionConfig promotionConfig = SolvelaBeanUtil.copy(addForm, PromotionConfig.class);
        promotionConfigDao.insert(promotionConfig);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PromotionConfigUpdateForm updateForm) {
        PromotionConfig promotionConfig = SolvelaBeanUtil.copy(updateForm, PromotionConfig.class);
        promotionConfigDao.updateById(promotionConfig);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        promotionConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        promotionConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
