package net.lab1024.sa.prize.prizelog.service;

import java.util.List;

import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.prize.prizelog.dao.PrizeLogDao;
import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogAddForm;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogQueryForm;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogUpdateForm;
import net.lab1024.sa.prize.prizelog.domain.vo.PrizeLogVO;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 奖励记录表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PrizeLogService {

    private final PrizeLogDao prizeLogDao;

    /**
     * 分页查询
     */
    public PageResult<PrizeLogVO> queryPage(PrizeLogQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrizeLogVO> list = prizeLogDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PrizeLogAddForm addForm) {
        PrizeLog prizeLog = SmartBeanUtil.copy(addForm, PrizeLog.class);
        prizeLogDao.insert(prizeLog);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PrizeLogUpdateForm updateForm) {
        PrizeLog prizeLog = SmartBeanUtil.copy(updateForm, PrizeLog.class);
        prizeLogDao.updateById(prizeLog);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        prizeLogDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        prizeLogDao.deleteById(id);
        return ResponseDTO.ok();
    }

    /**
     * 统一批量派发奖励入口
     * @param list
     * @return
     */
    public ResponseDTO sendPrize(List<PrizeLog> list){

        return ResponseDTO.ok();
    }

}
