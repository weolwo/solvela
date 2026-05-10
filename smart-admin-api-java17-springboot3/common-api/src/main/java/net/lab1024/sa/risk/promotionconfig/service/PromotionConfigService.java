package net.lab1024.sa.risk.promotionconfig.service;

import java.util.List;

import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.risk.promotionconfig.dao.PromotionConfigDao;
import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
import net.lab1024.sa.risk.promotionconfig.domain.form.PromotionConfigAddForm;
import net.lab1024.sa.risk.promotionconfig.domain.form.PromotionConfigQueryForm;
import net.lab1024.sa.risk.promotionconfig.domain.form.PromotionConfigUpdateForm;
import net.lab1024.sa.risk.promotionconfig.domain.vo.PromotionConfigVO;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

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
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PromotionConfigVO> list = promotionConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PromotionConfigAddForm addForm) {
        PromotionConfig promotionConfig = SmartBeanUtil.copy(addForm, PromotionConfig.class);
        promotionConfigDao.insert(promotionConfig);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PromotionConfigUpdateForm updateForm) {
        PromotionConfig promotionConfig = SmartBeanUtil.copy(updateForm, PromotionConfig.class);
        promotionConfigDao.updateById(promotionConfig);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
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
