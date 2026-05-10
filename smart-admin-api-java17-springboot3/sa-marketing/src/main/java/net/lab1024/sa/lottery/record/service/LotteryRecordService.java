package net.lab1024.sa.lottery.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.constant.RedisKey;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.enumeration.LuaScriptEnum;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.module.support.scriptengine.annotation.ScriptFunction;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.manager.LotteryIssueManager;
import net.lab1024.sa.lottery.numberpool.domain.entity.LotteryNumberPool;
import net.lab1024.sa.lottery.numberpool.service.LotteryNumberPoolService;
import net.lab1024.sa.lottery.prizerule.service.LotteryPrizeRuleService;
import net.lab1024.sa.lottery.record.dao.LotteryRecordDao;
import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordAddForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordQueryForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordUpdateForm;
import net.lab1024.sa.lottery.record.domain.vo.LotteryRecordVO;
import net.lab1024.sa.lottery.record.manager.LotteryRecordManager;
import net.lab1024.sa.scriptengine.spi.ScriptEngineFunctionHandler;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户号码记录 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LotteryRecordService implements ScriptEngineFunctionHandler {

    private final LotteryRecordDao lotteryRecordDao;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryNumberPoolService lotteryNumberPoolService;
    private final LotteryRecordManager lotteryRecordManager;
    private final LotteryPrizeRuleService lotteryPrizeRuleService;
    private final StringRedisTemplate stringRedisTemplate;

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
     */
    public ResponseDTO<String> update(LotteryRecordUpdateForm updateForm) {
        LotteryRecord lotteryRecord = SmartBeanUtil.copy(updateForm, LotteryRecord.class);
        lotteryRecordDao.updateById(lotteryRecord);
        return ResponseDTO.ok();
    }

    @ScriptFunction(name = "_assignNumbers", description = "彩票分配")
    public ResponseDTO assignNumbers(LotteryRecordAddForm lotteryRecord, Integer applyNum, Integer startOffset, Integer totalNum) {

        LotteryIssue lotteryIssue = lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getIssueNo, lotteryRecord.getIssueNo())
                .eq(LotteryIssue::getLotteryCode, lotteryRecord.getLotteryCode())
                .one();
        if (lotteryIssue == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }

        String redisKey = String.join(":", RedisKey.MARKETING_LOTTERY_NUMBER_ASSIGN,
                lotteryIssue.getLotteryCode(), lotteryIssue.getIssueNo());

        // 1. 极速拦截：获取扣减后的【剩余库存】
        long remainStock = stringRedisTemplate.execute(LuaScriptEnum.ASSIGN_LOTTERY.getRedisScript(), List.of(redisKey), String.valueOf(applyNum));

        // 2. 异常与补偿机制
        if (remainStock < NumberUtils.LONG_ZERO) {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(redisKey))) {
                log.warn("期号 {} 的 Redis 库存丢失，触发热加载补偿...", lotteryIssue.getIssueNo());
                lotteryIssue =  lotteryIssueManager.lambdaQuery()
                        .eq(LotteryIssue::getIssueNo, lotteryRecord.getIssueNo())
                        .eq(LotteryIssue::getLotteryCode, lotteryRecord.getLotteryCode())
                        .one();
                int dbRemain = totalNum - lotteryIssue.getSoldCount();
                long safeExpireSeconds = Math.max(Duration.between(LocalDateTime.now(), lotteryIssue.getOpenTime()).getSeconds(), 3600);
                stringRedisTemplate.opsForValue().setIfAbsent(redisKey, String.valueOf(dbRemain), safeExpireSeconds, TimeUnit.SECONDS);
                remainStock = stringRedisTemplate.execute(LuaScriptEnum.ASSIGN_LOTTERY.getRedisScript(), List.of(redisKey), String.valueOf(applyNum));
            }
            if (remainStock < NumberUtils.LONG_ZERO) {
                return ResponseDTO.userErrorParam("余号不足");
            }
        }

        // 3. 计算切片起点
        int startIndex = totalNum - (int) remainStock - applyNum;

        // ==========================================
        // 🚨 核心防线：所有可能报错的 DB 操作，必须包在 try-catch 里
        // ==========================================
        try {
            // 4. 去数据库捞真实的号码 (传入 startIndex)
            List<LotteryNumberPool> tickets = lotteryNumberPoolService.queryNumbersBySeqNo(
                    lotteryRecord.getLotteryCode(), totalNum, startIndex, applyNum, startOffset);

            // 5. 构建并插入记录
            List<LotteryRecord> records = tickets.stream().map(e -> {
                LotteryRecord record = new LotteryRecord();
                record.setCreateBy(lotteryRecord.getCreateBy());
                record.setCreateTime(LocalDateTime.now());
                record.setIssueNo(lotteryRecord.getIssueNo());
                record.setLotteryCode(lotteryRecord.getLotteryCode());
                record.setMemberName(lotteryRecord.getMemberName());
                record.setObtainTime(LocalDateTime.now());
                record.setPrizeLevel(0);
                record.setWinStatus(0);
                record.setSourceType("00");
                record.setSecuritySign("00");
                record.setTicketNumber(e.getTicketNumber());
                record.setSourceBizId("");
                return record;
            }).collect(Collectors.toList());

            if (!records.isEmpty()) {
                lotteryRecordDao.insertBatch(records);
            }

            // 6. 原子增加主表已售数量 (传入增量 applyNum)
            updateSoldNum(lotteryRecord.getLotteryCode(), lotteryRecord.getIssueNo(), applyNum);
            return ResponseDTO.ok();

        } catch (Exception e) {
            log.error("分配彩票落库失败，准备回滚 Redis 库存。用户: {}", lotteryRecord.getMemberName(), e);
            // 【救命稻草】：数据库挂了，一定要把吃进去的 Redis 库存吐出来！
            stringRedisTemplate.opsForValue().increment(redisKey, applyNum);
            return ResponseDTO.userErrorParam("系统繁忙，出票失败");
        }
    }
    /**
     * 更新已售数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSoldNum(String lotteryCode, String issueNo, Integer applyNum) {
        boolean success = lotteryIssueManager.lambdaUpdate()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                .eq(LotteryIssue::getIssueNo, issueNo)
                // 【终极杀招】：利用底层的原子操作，绝对不会出现乱序覆盖！
                .setSql("sold_count = sold_count + " + applyNum)
                .update();

        if (!success) {
            throw new RuntimeException("期号不存在或更新失败");
        }
    }
    public List<LotteryRecord> queryLotteryList(LotteryRecordQueryForm queryForm) {

        return lotteryRecordManager.lambdaQuery()
                .eq(LotteryRecord::getLotteryCode, queryForm.getLotteryCode())
                .eq(LotteryRecord::getIssueNo, queryForm.getIssueNo())
                .ge(Objects.nonNull(queryForm.getCreateTimeBegin()),LotteryRecord::getCreateTime, queryForm.getCreateTimeBegin())
                .le(Objects.nonNull(queryForm.getCreateTimeEnd()),LotteryRecord::getCreateTime, queryForm.getCreateTimeEnd())
                // 【核心防线】：只查 ID 大于上一批最大 ID 的数据
                .gt(LotteryRecord::getId, queryForm.getPageNum())
                // 【必须排序】：必须按 ID 升序排，保证数据连续且不遗漏
                .orderByAsc(LotteryRecord::getId)
                // 每次只拉 1000 条
                .last("LIMIT " + queryForm.getPageSize())
                .list();
    }

    public int updateBatchById(List<LotteryRecord> recordList) {
       return lotteryRecordDao.updateBatchById(recordList);
    }

    public boolean updateStatus(String lotteryCode,String issueNo,Integer status) {
        return lotteryRecordManager.lambdaUpdate()
                .eq(LotteryRecord::getLotteryCode, lotteryCode)
                .eq(LotteryRecord::getIssueNo,issueNo)
                .set(LotteryRecord::getWinStatus,status)
                .set(LotteryRecord::getUpdateTime,LocalDateTime.now())
                .set(LotteryRecord::getUpdateBy,"system")
                .update();
    }
}
