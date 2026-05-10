package net.lab1024.sa.lottery.config.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.lottery.config.dao.LotteryConfigDao;
import net.lab1024.sa.lottery.config.domain.entity.LotteryConfig;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigAddForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigQueryForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigUpdateForm;
import net.lab1024.sa.lottery.config.domain.vo.LotteryConfigVO;
import net.lab1024.sa.lottery.numberpool.service.DynamicNumbersGenerator;
import net.lab1024.sa.lottery.numberpool.service.LotteryNumberPoolService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 彩票配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class LotteryConfigService {

    private final LotteryConfigDao lotteryConfigDao;
    private final LotteryNumberPoolService lotteryNumberPoolService;

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
    public ResponseDTO<String> add(LotteryConfigAddForm addForm) throws Exception {
        LotteryConfig lotteryConfig = SmartBeanUtil.copy(addForm, LotteryConfig.class);
        lotteryConfigDao.insert(lotteryConfig);
        createNumberPool(lotteryConfig);
        return ResponseDTO.ok();
    }

    /*
     一次性生成好所有的号码，并且保证序号是连续的
     */
    private void createNumberPool(LotteryConfig config) throws Exception {
        Integer totalCount = config.getTotalCount();
        Integer numberLength = config.getNumberLength();
        String numberCharset = config.getNumberCharset();
        //根据配置生成号码
        long start = System.currentTimeMillis();
        List<String> numbers = DynamicNumbersGenerator.generateNumbers(numberCharset, numberLength, totalCount);
        lotteryNumberPoolService.fastLoadData(numbers, config);
        log.info("耗时：{}",start-System.currentTimeMillis());
    }

    /**
     * 更新
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
        if (CollectionUtils.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        lotteryConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id) {
            return ResponseDTO.ok();
        }

        lotteryConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
