package net.lab1024.sa.lottery.record.service;

import java.util.List;
import net.lab1024.sa.lottery.record.dao.LotteryRecordDao;
import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordAddForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordQueryForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordUpdateForm;
import net.lab1024.sa.lottery.record.domain.vo.LotteryRecordVO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 用户号码记录 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryRecordService {

    private final LotteryRecordDao lotteryRecordDao;

    /**
     * 分页查询
     */
    public PageResult<LotteryRecordVO> queryPage(LotteryRecordQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryRecordVO> list = lotteryRecordDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(LotteryRecordAddForm addForm) {
        LotteryRecord lotteryRecord = SmartBeanUtil.copy(addForm, LotteryRecord.class);
        lotteryRecordDao.insert(lotteryRecord);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(LotteryRecordUpdateForm updateForm) {
        LotteryRecord lotteryRecord = SmartBeanUtil.copy(updateForm, LotteryRecord.class);
        lotteryRecordDao.updateById(lotteryRecord);
        return ResponseDTO.ok();
    }

}
