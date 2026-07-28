package net.lab1024.sa.lottery.issue.service;

import java.util.List;
import net.lab1024.sa.lottery.issue.dao.LotteryIssueDao;
import net.lab1024.sa.lottery.issue.manager.LotteryIssueManager;
import net.lab1024.sa.lottery.config.service.LotteryConfigService;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueAddForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueQueryForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueUpdateForm;
import net.lab1024.sa.lottery.issue.domain.vo.LotteryIssueVO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 期号配置 Service
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class LotteryIssueService {

    private final LotteryIssueDao lotteryIssueDao;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryConfigService lotteryConfigService;

    /**
     * 期号状态：0-待开奖（可售卖、可编辑、可删除）
     */
    private static final Integer STATUS_WAIT = 0;

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
        if (lotteryConfigService.getByLotteryCode(addForm.getLotteryCode()) == null) {
            return ResponseDTO.userErrorParam("彩票玩法不存在：" + addForm.getLotteryCode());
        }
        if (existsIssueNo(addForm.getLotteryCode(), addForm.getIssueNo())) {
            return ResponseDTO.userErrorParam("期号已存在：" + addForm.getIssueNo());
        }
        String timeError = checkSaleWindow(addForm.getSaleStartTime(), addForm.getSaleEndTime());
        if (timeError != null) {
            return ResponseDTO.userErrorParam(timeError);
        }
        LotteryIssue lotteryIssue = SmartBeanUtil.copy(addForm, LotteryIssue.class);
        lotteryIssue.setSoldCount(0);
        lotteryIssue.setStatus(STATUS_WAIT);
        lotteryIssueDao.insert(lotteryIssue);
        return ResponseDTO.ok();
    }

    /**
     * 更新。
     *
     * ⚠️ issue_no 与 lottery_code 是 FPE 的密钥派生素材（key = HMAC(secret, lotteryCode|issueNo)），
     * 改动它们等于换了一套加密映射：已发号码再也反解不回游标、签名全部失效、
     * 新号码还可能与历史号码重复。所以这两个字段一律忽略前端传值，只允许改时间。
     */
    public ResponseDTO<String> update(LotteryIssueUpdateForm updateForm) {
        LotteryIssue existed = lotteryIssueDao.selectById(updateForm.getId());
        if (existed == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        if (!STATUS_WAIT.equals(existed.getStatus())) {
            return ResponseDTO.userErrorParam("该期已开奖或正在核销，不能再修改");
        }
        String timeError = checkSaleWindow(updateForm.getSaleStartTime(), updateForm.getSaleEndTime());
        if (timeError != null) {
            return ResponseDTO.userErrorParam(timeError);
        }
        // 已经发过号之后，连售卖开始时间都不该往后挪 —— 那会让「已发出的号」落在窗口之外，自相矛盾
        int sold = existed.getSoldCount() == null ? 0 : existed.getSoldCount();
        if (sold > 0 && updateForm.getSaleStartTime() != null
                && updateForm.getSaleStartTime().isAfter(existed.getSaleStartTime())) {
            return ResponseDTO.userErrorParam("本期已发出 " + sold + " 个号码，售卖开始时间不能再往后调整");
        }

        LotteryIssue update = new LotteryIssue();
        update.setId(existed.getId());
        update.setSaleStartTime(updateForm.getSaleStartTime());
        update.setSaleEndTime(updateForm.getSaleEndTime());
        update.setPlanDrawTime(updateForm.getPlanDrawTime());
        // lottery_code / issue_no / sold_count / status / winning_number 一律不接受前端值
        lotteryIssueDao.updateById(update);
        return ResponseDTO.ok();
    }

    public boolean existsIssueNo(String lotteryCode, String issueNo) {
        return lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                .eq(LotteryIssue::getIssueNo, issueNo).exists();
    }

    /**
     * 售卖窗口合法性，返回 null 表示通过
     */
    private String checkSaleWindow(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null) {
            return "售卖起止时间不能为空";
        }
        if (!end.isAfter(start)) {
            return "售卖结束时间必须晚于开始时间";
        }
        return null;
    }

    /**
     * 已发过号的期号禁止删除：记录还在 t_lottery_record 里，删了期号会让那些记录变成孤儿，
     * 而它们既是用户的凭证、也是开奖核销的依据
     */
    private String checkDeletable(Long id) {
        LotteryIssue issue = lotteryIssueDao.selectById(id);
        if (issue == null) {
            return null;
        }
        int sold = issue.getSoldCount() == null ? 0 : issue.getSoldCount();
        if (sold > 0) {
            return "期号「" + issue.getIssueNo() + "」已发出 " + sold + " 个号码，不能删除";
        }
        if (!STATUS_WAIT.equals(issue.getStatus())) {
            return "期号「" + issue.getIssueNo() + "」已开奖或正在核销，不能删除";
        }
        return null;
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }
        for (Long id : idList) {
            String error = checkDeletable(id);
            if (error != null) {
                return ResponseDTO.userErrorParam(error);
            }
        }
        lotteryIssueDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }
        String error = checkDeletable(id);
        if (error != null) {
            return ResponseDTO.userErrorParam(error);
        }
        lotteryIssueDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
