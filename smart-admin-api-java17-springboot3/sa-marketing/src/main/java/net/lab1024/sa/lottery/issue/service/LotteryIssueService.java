package net.lab1024.sa.lottery.issue.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.lottery.issue.dao.LotteryIssueDao;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueAddForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueQueryForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueUpdateForm;
import net.lab1024.sa.lottery.issue.domain.vo.LotteryIssueVO;
import net.lab1024.sa.lottery.issue.manager.LotteryIssueManager;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 期号配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:23:43
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryIssueService {

    private final LotteryIssueDao lotteryIssueDao;
    private final LotteryIssueManager lotteryIssueManager;

    /**
     * 分页查询
     */
    public PageResult<LotteryIssueVO> queryPage(LotteryIssueQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryIssueVO> list = lotteryIssueDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(LotteryIssueAddForm addForm) {
        LotteryIssue lotteryIssue = SmartBeanUtil.copy(addForm, LotteryIssue.class);
        lotteryIssueDao.insert(lotteryIssue);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     */
    public ResponseDTO<String> update(LotteryIssueUpdateForm updateForm) {
        LotteryIssue lotteryIssue = SmartBeanUtil.copy(updateForm, LotteryIssue.class);
        lotteryIssueDao.updateById(lotteryIssue);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return ResponseDTO.ok();
        }

        lotteryIssueDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id) {
            return ResponseDTO.ok();
        }

        lotteryIssueDao.deleteById(id);
        return ResponseDTO.ok();
    }

    /**
     * 查询彩票期号
     * @param lotteryCode 彩票配置编码
     * @param issueNo 期号
     * @return
     */
    public LotteryIssue getLotteryIssue(String lotteryCode,String issueNo) {
        return lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getIssueNo, lotteryCode)
                .eq(LotteryIssue::getLotteryCode, issueNo)
                .one();

    }

    /**
     * 更新已售数量
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSoldNum(String lotteryCode,String issueNo,Integer maxSeqNo){
        lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                .eq(LotteryIssue::getIssueNo, issueNo)
                .last("FOR UPDATE") //加上原生的悲观锁
                .one();

        return lotteryIssueManager.lambdaUpdate()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                .eq(LotteryIssue::getIssueNo, issueNo)
                .set(LotteryIssue::getSoldCount, maxSeqNo)
                .update();
    }
}
