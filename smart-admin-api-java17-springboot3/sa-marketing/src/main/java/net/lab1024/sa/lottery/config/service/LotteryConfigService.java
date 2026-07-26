package net.lab1024.sa.lottery.config.service;

import java.util.List;
import net.lab1024.sa.lottery.config.dao.LotteryConfigDao;
import net.lab1024.sa.lottery.config.domain.entity.LotteryConfig;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigAddForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigQueryForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigUpdateForm;
import net.lab1024.sa.lottery.config.domain.vo.LotteryConfigVO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 彩票配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryConfigService {

    private final LotteryConfigDao lotteryConfigDao;

    /**
     * 分页查询
     */
    public PageResult<LotteryConfigVO> queryPage(LotteryConfigQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryConfigVO> list = lotteryConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(LotteryConfigAddForm addForm) {
        LotteryConfig lotteryConfig = SmartBeanUtil.copy(addForm, LotteryConfig.class);
        lotteryConfigDao.insert(lotteryConfig);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(LotteryConfigUpdateForm updateForm) {
        LotteryConfig lotteryConfig = SmartBeanUtil.copy(updateForm, LotteryConfig.class);
        lotteryConfigDao.updateById(lotteryConfig);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        lotteryConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        lotteryConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
