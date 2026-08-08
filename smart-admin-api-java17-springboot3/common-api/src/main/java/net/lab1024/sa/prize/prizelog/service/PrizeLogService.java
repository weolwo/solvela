package net.lab1024.sa.prize.prizelog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.prize.prizelog.dao.PrizeLogDao;
import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogAddForm;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogQueryForm;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogUpdateForm;
import net.lab1024.sa.prize.prizelog.domain.vo.PrizeLogVO;
import org.springframework.stereotype.Service;

import java.util.List;

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
     */
    public ResponseDTO<String> update(PrizeLogUpdateForm updateForm) {
        PrizeLog prizeLog = SmartBeanUtil.copy(updateForm, PrizeLog.class);
        prizeLogDao.updateById(prizeLog);
        return ResponseDTO.ok();
    }

    public ResponseDTO<String> updateById(PrizeLog prizeLog) {
        int updated = prizeLogDao.updateById(prizeLog);
        if (updated > 0) {
            return ResponseDTO.ok();
        }
        return ResponseDTO.userErrorParam();
    }

    public int save(PrizeLog prizeLog) {
        return prizeLogDao.insert(prizeLog);
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        prizeLogDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id) {
            return ResponseDTO.ok();
        }

        prizeLogDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
