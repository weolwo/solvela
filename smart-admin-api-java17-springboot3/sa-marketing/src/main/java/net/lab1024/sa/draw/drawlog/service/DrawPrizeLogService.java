package net.lab1024.sa.draw.drawlog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.draw.drawlog.dao.DrawPrizeLogDao;
import net.lab1024.sa.draw.drawlog.domain.entity.DrawPrizeLog;
import net.lab1024.sa.draw.drawlog.domain.form.DrawPrizeLogAddForm;
import net.lab1024.sa.draw.drawlog.domain.form.DrawPrizeLogQueryForm;
import net.lab1024.sa.draw.drawlog.domain.form.DrawPrizeLogUpdateForm;
import net.lab1024.sa.draw.drawlog.domain.vo.DrawPrizeLogVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 抽奖记录 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class DrawPrizeLogService {

    private final DrawPrizeLogDao drawPrizeLogDao;

    /**
     * 分页查询
     */
    public PageResult<DrawPrizeLogVO> queryPage(DrawPrizeLogQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<DrawPrizeLogVO> list = drawPrizeLogDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(DrawPrizeLogAddForm addForm) {
        DrawPrizeLog drawPrizeLog = SmartBeanUtil.copy(addForm, DrawPrizeLog.class);
        drawPrizeLogDao.insert(drawPrizeLog);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(DrawPrizeLogUpdateForm updateForm) {
        DrawPrizeLog drawPrizeLog = SmartBeanUtil.copy(updateForm, DrawPrizeLog.class);
        drawPrizeLogDao.updateById(drawPrizeLog);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        drawPrizeLogDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        drawPrizeLogDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
