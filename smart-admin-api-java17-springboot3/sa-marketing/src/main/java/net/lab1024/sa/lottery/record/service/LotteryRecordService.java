package net.lab1024.sa.lottery.record.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.annoation.RedisLock;
import net.lab1024.sa.base.common.constant.RedisKey;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.enumeration.LuaScriptEnum;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.module.support.scriptengine.annotation.ScriptFunction;
import net.lab1024.sa.base.module.support.scriptengine.annotation.ScriptFunctionGroup;
import net.lab1024.sa.base.module.support.scriptengine.spi.ScriptEngineFunctionHandler;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.service.LotteryIssueService;
import net.lab1024.sa.lottery.record.dao.LotteryRecordDao;
import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordAddForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordQueryForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordUpdateForm;
import net.lab1024.sa.lottery.record.domain.vo.LotteryRecordVO;
import net.lab1024.sa.lottery.record.manager.LotteryRecordManager;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final LotteryIssueService lotteryIssueService;
    private final LotteryRecordManager lotteryRecordManager;
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

    /**
     * 彩票分配
     *
     * @param lotteryRecord
     * @param applyNum      申请数量
     * @param totalNum      总数量
     */
    @ScriptFunction(name = "_assignNumbers", description = "彩票分配")
    public ResponseDTO assignNumbers(LotteryRecordAddForm lotteryRecord, Integer applyNum, Integer totalNum) {

        LotteryIssue lotteryIssue = lotteryIssueService.getLotteryIssue(lotteryRecord.getLotteryCode(), lotteryRecord.getIssueNo());
        if (lotteryIssue == null) {
            return ResponseDTO.userErrorParam("期号不存在");
        }
        log.info("彩票号码分配，用户 {}，总数 {}，已售数量 {}", lotteryRecord.getMemberName(), totalNum, lotteryIssue.getSoldCount());
        String redisKey = String.join(":", RedisKey.MARKETING_LOTTERY_NUMBER_ASSIGN,
                lotteryIssue.getLotteryCode(), lotteryIssue.getIssueNo());
        // 第一次尝试扣减
        long result = stringRedisTemplate.execute(LuaScriptEnum.ASSIGN_LOTTERY.getRedisScript(), List.of(redisKey), String.valueOf(applyNum));
        // 4. 异常与补偿机制
        if (result < NumberUtils.LONG_ZERO) {
            // 如果返回 -1，先检查是不是缓存丢了（被驱逐或宕机重启）
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(redisKey))) {
                log.warn("期号 {} 的 Redis 库存丢失，触发热加载补偿机制...", lotteryIssue.getIssueNo());
                int remainStock = totalNum - lotteryIssue.getSoldCount();

                // 安全的时间计算（哪怕 openTime 异常，保底给 1 小时过期时间）
                long seconds = Duration.between(LocalDateTime.now(), lotteryIssue.getOpenTime()).getSeconds();
                long safeExpireSeconds = Math.max(seconds, 3600); // 至少保留 1 小时
                // 利用 setIfAbsent 防止多个线程同时查询到无 Key 时，引发覆盖写
                stringRedisTemplate.opsForValue().setIfAbsent(redisKey, String.valueOf(remainStock), safeExpireSeconds, TimeUnit.SECONDS);
                // 补偿完毕后，当前线程重新执行一次 Lua 扣减尝试！
                result = stringRedisTemplate.execute(LuaScriptEnum.ASSIGN_LOTTERY.getRedisScript(), List.of(redisKey), String.valueOf(applyNum));
            }
            // 如果补偿后再次扣减还是 < 0，说明真的是没票了
            if (result < NumberUtils.LONG_ZERO) {
                return ResponseDTO.userErrorParam("余号不足");
            }
        }

        // MySQL UPDATE 行锁同步扣减、并分配具体的 sequence_no 号码
        // 注意：如果下面 MySQL 异常，记得 try-catch 并对 redisKey 做 increment 回滚操作！
        lotteryIssueService.updateSoldNum(lotteryRecord.getLotteryCode(), lotteryRecord.getIssueNo(),lotteryIssue.getSoldCount()+applyNum);
        //获取号码

        return ResponseDTO.ok();
    }
}
