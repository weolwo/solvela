package net.lab1024.sa.lottery.prizerule.service;

import java.util.List;
import net.lab1024.sa.lottery.prizerule.dao.LotteryPrizeRuleDao;
import net.lab1024.sa.lottery.prizerule.domain.entity.LotteryPrizeRule;
import net.lab1024.sa.lottery.prizerule.domain.form.LotteryPrizeRuleAddForm;
import net.lab1024.sa.lottery.prizerule.domain.form.LotteryPrizeRuleQueryForm;
import net.lab1024.sa.lottery.prizerule.domain.form.LotteryPrizeRuleUpdateForm;
import net.lab1024.sa.lottery.prizerule.domain.vo.LotteryPrizeRuleVO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 彩票奖励配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryPrizeRuleService {

    private final LotteryPrizeRuleDao lotteryPrizeRuleDao;

    /**
     * 分页查询
     */
    public PageResult<LotteryPrizeRuleVO> queryPage(LotteryPrizeRuleQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryPrizeRuleVO> list = lotteryPrizeRuleDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(LotteryPrizeRuleAddForm addForm) {
        LotteryPrizeRule lotteryPrizeRule = SmartBeanUtil.copy(addForm, LotteryPrizeRule.class);
        lotteryPrizeRuleDao.insert(lotteryPrizeRule);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(LotteryPrizeRuleUpdateForm updateForm) {
        LotteryPrizeRule lotteryPrizeRule = SmartBeanUtil.copy(updateForm, LotteryPrizeRule.class);
        lotteryPrizeRuleDao.updateById(lotteryPrizeRule);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        lotteryPrizeRuleDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        lotteryPrizeRuleDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
