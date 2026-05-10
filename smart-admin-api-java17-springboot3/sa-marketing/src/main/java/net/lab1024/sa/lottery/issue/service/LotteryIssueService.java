package net.lab1024.sa.lottery.issue.service;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.annoation.RedisLock;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.constant.RedisKey;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.JsonUtils;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.util.DateUtils;
import net.lab1024.sa.enums.PatternModeEnum;
import net.lab1024.sa.enums.TicketStatusEnum;
import net.lab1024.sa.lottery.issue.dao.LotteryIssueDao;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueAddForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueQueryForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueUpdateForm;
import net.lab1024.sa.lottery.issue.domain.vo.LotteryIssueVO;
import net.lab1024.sa.lottery.prizerule.domain.vo.PrizeDetailItem;
import net.lab1024.sa.enums.IssueStatusEnum;
import net.lab1024.sa.lottery.issue.manager.LotteryIssueManager;
import net.lab1024.sa.lottery.prizerule.domain.entity.LotteryPrizeRule;
import net.lab1024.sa.lottery.prizerule.service.LotteryPrizeRuleService;
import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordQueryForm;
import net.lab1024.sa.lottery.record.service.LotteryRecordService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 期号配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:23:43
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LotteryIssueService {

    private final LotteryIssueDao lotteryIssueDao;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryRecordService lotteryRecordService;
    private final LotteryPrizeRuleService lotteryPrizeRuleService;
    private final ApplicationEventPublisher eventPublisher;

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
        int nextInt = ThreadLocalRandom.current().nextInt(1, 10000);
        lotteryIssue.setStartOffset(nextInt);
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
     *
     * @param lotteryCode 彩票配置编码
     * @param issueNo     期号
     * @return
     */
    public LotteryIssue getLotteryIssue(String lotteryCode, String issueNo) {
        return lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getIssueNo, lotteryCode)
                .eq(LotteryIssue::getLotteryCode, issueNo)
                .one();

    }



    /**
     * 开奖
     *
     * @param issue
     * @return
     */
    @RedisLock(prefixKey = RedisKey.MARKETING_LOTTERY_NUMBER_OPEN, key = "#lotteryCode+':'+#issueNo")
    public ResponseDTO computePrize(LotteryIssue issue) {
        log.info("【开奖引擎启动】期号: {}, 中奖号码: {}", issue.getIssueNo(), issue.getWinningNumber());
        LotteryIssue lotteryIssue = lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, issue.getLotteryCode())
                .eq(LotteryIssue::getIssueNo, issue.getIssueNo()).one();
        // 只有 0，1 才能开奖
        if (lotteryIssue == null || Objects.equals(lotteryIssue.getStatus(), IssueStatusEnum.OPENED.getCode())) {
            log.warn("期号 {} 不存在或已达终态(全部开奖完毕)，拒绝执行。", issue.getIssueNo());
            return ResponseDTO.error(UserErrorCode.STATUS_ERROR);
        }
        //组装累积的开奖号码 (JSON)
        // ==========================================
        JSONObject currentWinningJson = new JSONObject();
        if (StringUtils.isNotBlank(lotteryIssue.getWinningNumber())) {
            currentWinningJson = JsonUtils.parseObject(lotteryIssue.getWinningNumber(), JSONObject.class);
        }
        // 记录本次开奖轨迹
        currentWinningJson.put(DateUtils.format(LocalDateTime.now()), issue.getWinningNumber());
        lotteryIssueManager.lambdaUpdate()
                .eq(LotteryIssue::getLotteryCode, issue.getLotteryCode())
                .eq(LotteryIssue::getIssueNo, issue.getIssueNo())
                .in(LotteryIssue::getStatus, IssueStatusEnum.WAIT.getCode(), IssueStatusEnum.STAGED.getCode())
                .set(LotteryIssue::getStatus, issue.getStatus())
                .set(LotteryIssue::getWinningNumber, currentWinningJson) // 写入开奖号码
                .set(LotteryIssue::getOpenTime, LocalDateTime.now())
                .update();
        LotteryPrizeRule prizeRule = lotteryPrizeRuleService.queryLotteryPrizeRule(issue.getLotteryCode(), issue.getIssueNo());
        List<PrizeDetailItem> prizeDetailItems = JsonUtils.parseList(prizeRule.getPrizeDetails(), PrizeDetailItem.class);
        // 查询所有当期号码记录
        LotteryRecordQueryForm queryForm = new LotteryRecordQueryForm();
        queryForm.setLotteryCode(issue.getLotteryCode());
        queryForm.setIssueNo(issue.getIssueNo());
        queryForm.setPageSize(1000L);
        String winningNumber = lotteryIssue.getWinningNumber();
        Set<Long> wonIds = new HashSet<>();

        for (PrizeDetailItem item : prizeDetailItems) {
            queryForm.setPageNum(0L); // 重置游标
            queryForm.setCreateTimeBegin(item.getStartTime());
            queryForm.setCreateTimeEnd(item.getEndTime());

            // 【性能优化】：把字符串截取放在几十万次循环的外面！只执行 1 次。
            Integer matchCount = item.getMatchCount();
            Integer winCount = item.getWinCount();
            boolean isFrontMatch = item.getPatternMode().equals(PatternModeEnum.FRONT_MATCH.getCode());
            String targetStr = isFrontMatch
                    ? winningNumber.substring(0, matchCount)
                    : winningNumber.substring(winningNumber.length() - matchCount);

            // 记录当前这个奖级发了多少个
            int currentRuleWinTotal = 0;
            while (true) {
                List<LotteryRecord> lotteryRecords = lotteryRecordService.queryLotteryList(queryForm);
                if (lotteryRecords.isEmpty()) {
                    break;
                }
                // 专门用来存放本批次中奖的记录，用于批量更新到数据库
                List<LotteryRecord> batchWinRecords = new ArrayList<>();
                for (LotteryRecord record : lotteryRecords) {
                    // 1. 如果这个奖级的名额已经发满了，直接停止比对！
                    if (currentRuleWinTotal >= winCount) {
                        break;
                    }
                    // 2. 如果这个记录 ID 已经中过更高级的奖了，直接跳过！
                    if (wonIds.contains(record.getId())) {
                        continue;
                    }
                    // 3. 极速比对
                    boolean isMatch = isFrontMatch
                            ? record.getTicketNumber().startsWith(targetStr)
                            : record.getTicketNumber().endsWith(targetStr);
                    // 4. 命中记录
                    if (isMatch) {
                        record.setPrizeLevel(item.getPrizeLevel());
                        record.setWinStatus(TicketStatusEnum.SUCCESS_MATCH.getCode()); // 代表中奖
                        batchWinRecords.add(record); // 加入待落库列表
                        wonIds.add(record.getId()); // 加入全局去重 Set
                        currentRuleWinTotal++; // 名额计数器 +1
                    }
                }
                // 只把中奖的记录更新到数据库！未中奖的不管它。
                if (!batchWinRecords.isEmpty()) {
                    // 你需要有这个批量更新的方法
                    lotteryRecordService.updateBatchById(batchWinRecords);
                }
                // 如果名额满了，没必要再翻下一页了，直接结束这个奖项的捞取！(数据库减负)
                if (currentRuleWinTotal >= winCount) {
                    break;
                }
                // 更新游标 (你用的 PageNum 字段当游标，逻辑没问题)
                queryForm.setPageNum(lotteryRecords.get(lotteryRecords.size() - 1).getId());
            }
        }
        lotteryRecordService.updateStatus(lotteryIssue.getLotteryCode(),lotteryIssue.getIssueNo(),TicketStatusEnum.FAILURE_MATCH.getCode());
        // 异步派奖
       // eventPublisher.publishEvent();
        return ResponseDTO.ok();
    }
}
