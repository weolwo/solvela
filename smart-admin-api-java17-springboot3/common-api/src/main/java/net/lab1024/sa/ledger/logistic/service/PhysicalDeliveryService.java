package net.lab1024.sa.ledger.logistic.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.ledger.logistic.dao.PhysicalDeliveryDao;
import net.lab1024.sa.ledger.logistic.domain.entity.PhysicalDelivery;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryAddForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryQueryForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryUpdateForm;
import net.lab1024.sa.ledger.logistic.domain.vo.PhysicalDeliveryVO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发货物流表 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PhysicalDeliveryService {

    private final PhysicalDeliveryDao physicalDeliveryDao;

    /**
     * 分页查询
     */
    public PageResult<PhysicalDeliveryVO> queryPage(PhysicalDeliveryQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PhysicalDeliveryVO> list = physicalDeliveryDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PhysicalDeliveryAddForm addForm) {
        PhysicalDelivery physicalDelivery = SmartBeanUtil.copy(addForm, PhysicalDelivery.class);
        physicalDeliveryDao.insert(physicalDelivery);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PhysicalDeliveryUpdateForm updateForm) {
        PhysicalDelivery physicalDelivery = SmartBeanUtil.copy(updateForm, PhysicalDelivery.class);
        physicalDeliveryDao.updateById(physicalDelivery);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        physicalDeliveryDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        physicalDeliveryDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
