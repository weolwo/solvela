package net.lab1024.sa.draw.prizemapping.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.draw.prizemapping.dao.PoolPrizeMappingDao;
import net.lab1024.sa.draw.prizemapping.domain.entity.PoolPrizeMapping;
import net.lab1024.sa.draw.prizemapping.domain.form.PoolPrizeMappingAddForm;
import net.lab1024.sa.draw.prizemapping.domain.form.PoolPrizeMappingQueryForm;
import net.lab1024.sa.draw.prizemapping.domain.form.PoolPrizeMappingUpdateForm;
import net.lab1024.sa.draw.prizemapping.domain.vo.PoolPrizeMappingVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 奖池奖项映射 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PoolPrizeMappingService {

    private final PoolPrizeMappingDao poolPrizeMappingDao;

    /**
     * 分页查询
     */
    public PageResult<PoolPrizeMappingVO> queryPage(PoolPrizeMappingQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PoolPrizeMappingVO> list = poolPrizeMappingDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PoolPrizeMappingAddForm addForm) {
        PoolPrizeMapping poolPrizeMapping = SmartBeanUtil.copy(addForm, PoolPrizeMapping.class);
        poolPrizeMappingDao.insert(poolPrizeMapping);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PoolPrizeMappingUpdateForm updateForm) {
        PoolPrizeMapping poolPrizeMapping = SmartBeanUtil.copy(updateForm, PoolPrizeMapping.class);
        poolPrizeMappingDao.updateById(poolPrizeMapping);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        poolPrizeMappingDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        poolPrizeMappingDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
