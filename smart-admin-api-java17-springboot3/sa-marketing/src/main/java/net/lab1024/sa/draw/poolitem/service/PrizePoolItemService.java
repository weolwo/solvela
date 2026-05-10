package net.lab1024.sa.draw.poolitem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.draw.poolitem.dao.PrizePoolItemDao;
import net.lab1024.sa.draw.poolitem.domain.entity.PrizePoolItem;
import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemAddForm;
import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemQueryForm;
import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemUpdateForm;
import net.lab1024.sa.draw.poolitem.domain.vo.PrizePoolItemVO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 奖池奖项库 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizePoolItemService {

    private final PrizePoolItemDao prizePoolItemDao;

    /**
     * 分页查询
     */
    public PageResult<PrizePoolItemVO> queryPage(PrizePoolItemQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrizePoolItemVO> list = prizePoolItemDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PrizePoolItemAddForm addForm) {
        PrizePoolItem prizePoolItem = SmartBeanUtil.copy(addForm, PrizePoolItem.class);
        prizePoolItemDao.insert(prizePoolItem);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PrizePoolItemUpdateForm updateForm) {
        PrizePoolItem prizePoolItem = SmartBeanUtil.copy(updateForm, PrizePoolItem.class);
        prizePoolItemDao.updateById(prizePoolItem);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        prizePoolItemDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        prizePoolItemDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
