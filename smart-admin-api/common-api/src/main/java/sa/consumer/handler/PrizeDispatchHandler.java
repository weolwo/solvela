package sa.consumer.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import sa.anno.EventRoute;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartStringUtil;
import sa.consumer.strategy.PrizeStrategyFactory;
import sa.domain.event.UserPrizeEvent;
import sa.enums.ApproveModeEnum;
import sa.enums.EventCategoryEnum;
import sa.prize.prizeconfig.domain.entity.PrizeConfig;
import sa.prize.prizeconfig.service.PrizeConfigService;
import sa.prize.prizelog.dao.PrizeLogDao;
import sa.prize.prizelog.domain.entity.PrizeLog;
import sa.prize.prizelog.service.PrizeLogService;
import sa.scriptengine.annotation.ScriptFunction;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Slf4j
@Service
@EventRoute(EventCategoryEnum.PRIZE)
public class PrizeDispatchHandler implements BizEventHandler<UserPrizeEvent> {

    @Resource
    private PrizeLogService prizeLogService;
    @Resource
    private PrizeConfigService prizeConfigService;
    @Resource
    private PrizeStrategyFactory strategyFactory;
    @Resource
    private PrizeLogDao prizeLogDao;

    /**
     * 对齐 t_prize_log.fail_reason 的列长度
     */
    private static final int FAIL_REASON_MAX_LENGTH = 128;

    @Override
    public void handle(UserPrizeEvent event) {
        log.info(">>>> [奖励派发链路] 收到事件，来源单号: {}, 奖品: {}", event.getSourceBizId(), event.getPrizeCode());

        // 1. 获取最新鲜的配置 (带本地缓存最佳)
        PrizeConfig config = prizeConfigService.getByPrizeCode(event.getPrizeCode());
        if (config == null || config.getStatus() == 0) {
            log.error("【重大异常】奖品配置不存在或已停用！prizeCode: {}", event.getPrizeCode());
            return;
        }

        // 2. 缝合 Event 与 Config，构造发奖流水记录
        PrizeLog prizeLog = buildPrizeLog(event, config);

        try {
            // 3. 落库防重！利用数据库的 uk_external_biz 唯一索引兜底
            prizeLogService.save(prizeLog);
        } catch (DuplicateKeyException e) {
            log.warn("【防重拦截】该业务单号已存在发奖提案，自动忽略。单号: {}", event.getSourceBizId());
            return;
        }

        // 4. 风控与审批拦截阀门
        if (Objects.equals(config.getApproveMode(), ApproveModeEnum.MANUAL.getCode())) {
            log.info("【风控拦截】命中人工审批，提案已挂起。LogId: {}", prizeLog.getId());
            return; // 流程到此结束！后台状态停留在：待审批(1) + 等待执行(0)
        }

        // 5. 自动免审通道，全速放行！
        doDispatch(prizeLog);
    }

    /**
     * 发奖审批状态（对齐 t_prize_log.approve_status）
     */
    private static final int APPROVE_PENDING = 1;
    private static final int APPROVE_PASSED = 2;
    private static final int APPROVE_REJECTED = 3;

    /**
     * 运营审批通过发奖：这是 approve_mode=1 的奖品唯一的出口
     *
     * 注意本系统有**两层审批**，语义不同、可叠加：
     * ① 本层（t_prize_config.approve_mode）——「这个奖该不该发给这个人」，运营视角，通过后才生成提案；
     * ② 提案层（t_promotion_config.review_level）——「这笔钱该不该出」，财务视角，见 ProposalRecordService.approve。
     * 两层都配了审批，就需要运营和财务各批一次。
     *
     * 条件更新做并发闸门：两个运营同时点通过，只有一个能推进状态，另一个会被告知已处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> approveDispatch(Long prizeLogId, String approveBy) {
        PrizeLog prizeLog = prizeLogDao.selectById(prizeLogId);
        if (prizeLog == null) {
            return ResponseDTO.userErrorParam("发奖记录不存在");
        }
        int rows = prizeLogDao.updateApproveStatus(prizeLogId, APPROVE_PENDING, APPROVE_PASSED, approveBy);
        if (rows == 0) {
            return ResponseDTO.userErrorParam("该发奖记录已被处理，请刷新后重试");
        }
        log.info("【发奖审批通过】LogId: {}, 审批人: {}", prizeLogId, approveBy);
        // 状态已在内存里同步，避免 doDispatch 里回写时把审批结果覆盖掉
        prizeLog.setApproveStatus(APPROVE_PASSED);
        prizeLog.setApproveBy(approveBy);
        doDispatch(prizeLog);
        return ResponseDTO.ok();
    }

    /**
     * 运营审批驳回：不再派发，记录留痕
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> rejectDispatch(Long prizeLogId, String approveBy, String reason) {
        int rows = prizeLogDao.updateApproveStatus(prizeLogId, APPROVE_PENDING, APPROVE_REJECTED, approveBy);
        if (rows == 0) {
            return ResponseDTO.userErrorParam("该发奖记录已被处理，请刷新后重试");
        }
        log.info("【发奖审批驳回】LogId: {}, 审批人: {}, 理由: {}", prizeLogId, approveBy, reason);
        return ResponseDTO.ok();
    }

    /**
     * 【极其核心】真正的发货引擎！
     * 必须是 public！因为运营后台点击“审批通过”后，也要调用这个方法！
     */
    @ScriptFunction(name = "_doDispatch",description = "奖励派发")
    public void doDispatch(PrizeLog prizeLog) {
        log.info(">>>> [执行发货] 开始派发奖品，LogId: {}, 类型: {}", prizeLog.getId(), prizeLog.getPrizeType());

        try {
            // A. 通过工厂获取具体的发货策略（完全消灭 switch/if-else）
            IPrizeHandler handler = strategyFactory.getHandler(prizeLog.getPrizeType());

            // B. 执行发货，返回标准结果 (建议自己封装一个 DispatchResult 类)
            // Result result = handler.dispatch(prizeLog);
            ResponseDTO result = handler.dispatch(prizeLog); // 假设返回 boolean 演示

            // C. 只有「当场就失败」才由这里落终态。
            //    成功路径刻意不写 status=1：资产下发已被挪到提案事务提交之后（方案A），
            //    此刻真实结果还没出来，这里若抢先写 1，会把 AssetDispatchEngine 随后回写的真实状态覆盖掉，
            //    表现为「发奖记录显示成功、用户其实没收到」（预算耗尽那批就是这么被写成成功的）。
            //    成功路径的终态由 AssetDispatchEngine.updateStatusByExternalBizNo 回写；
            //    若提案进了人工审批池，则合理地停在 0-等待执行。
            if (!result.getOk()) {
                prizeLog.setStatus(2); // 2-失败
                prizeLog.setFailReason(SmartStringUtil.truncate(result.getMsg(), FAIL_REASON_MAX_LENGTH));
                log.warn("【发货失败】LogId: {}, 原因: {}", prizeLog.getId(), result.getMsg());
                updateQuietly(prizeLog);
            } else if (isNoDeliveryNeeded(prizeLog)) {
                // 0 值奖品（谢谢参与这类占位奖）压根不会生成提案，引擎不会跑，
                // 状态没人回写就会永远悬在 0-等待执行。它本就是当场的终态，这里直接落成功
                prizeLog.setStatus(1);
                updateQuietly(prizeLog);
                log.info("【无需发放】LogId: {}, 奖品价值为0，直接判成功", prizeLog.getId());
            } else {
                log.info("【发货已受理】LogId: {}, 最终状态由资产分发引擎回写", prizeLog.getId());
            }
        } catch (Exception e) {
            // 捕获不可预知的异常（如网络超时、空指针），防止影响整个应用的稳定性
            log.error("【发奖异常】执行策略时发生严重错误，LogId: {}", prizeLog.getId(), e);
            prizeLog.setStatus(2); // 2-失败
            // 必须截断：异常 message 动辄几百字，直接塞 varchar(128) 会抛 Data too long，
            // 一抛异常连状态都刷不进去，最终表现为「状态永远停在 0」——已踩过
            prizeLog.setFailReason(SmartStringUtil.truncate(e.getMessage(), FAIL_REASON_MAX_LENGTH));
            updateQuietly(prizeLog);
        }
    }

    /**
     * 是否属于「无需实际发放」的奖品：价值为 0（如谢谢参与）。
     * 判定放在这里而不是各 handler 里，是因为审批通过也会重入 doDispatch，两条入口都得覆盖
     */
    private boolean isNoDeliveryNeeded(PrizeLog prizeLog) {
        try {
            return new BigDecimal(prizeLog.getPrizeValue()).compareTo(BigDecimal.ZERO) <= 0;
        } catch (RuntimeException e) {
            // 价值解析不了就当成需要正常发放，交给下游 handler 去报错，别在这里吞掉问题
            return false;
        }
    }

    /**
     * 状态回写自身再失败就彻底没痕迹了，单独兜一层，只落日志不外抛
     */
    private void updateQuietly(PrizeLog prizeLog) {
        try {
            prizeLogService.updateById(prizeLog);
        } catch (Exception e) {
            log.error("【发奖状态回写失败】LogId: {}, 状态将停留在旧值，请人工核对", prizeLog.getId(), e);
        }
    }

    /**
     * 拼装提案日志
     */
    private PrizeLog buildPrizeLog(UserPrizeEvent event, PrizeConfig config) {
        PrizeLog log = new PrizeLog();

        // --- 1. 来自 Event 的动态数据 (用户相关) ---
        // 关联键与展示快照一起落：memberId 是查询/对账用的键，memberName 是「中奖当时那个账号」
        log.setMemberId(event.getMemberId());
        log.setMemberName(event.getMemberName());
        log.setExternalBizNo(event.getSourceBizId());
        log.setActivityCode(event.getActivityCode());
        log.setPrizeValue(event.getPrizeValue()); // 通常价值以 Event(彩票引擎算出的)为准
        log.setPrizeLevel(event.getPrizeLevel());

        // --- 2. 来自 Config 的静态规则 (资产相关) ---
        log.setPrizeCode(config.getPrizeCode());
        log.setPrizeName(config.getPrizeName());
        log.setPrizeType(config.getPrizeType());

        // --- 3. 初始状态与时效 ---
        log.setApproveStatus(config.getApproveMode() == 1 ? 1 : 0); // 1-待审, 0-无需
        log.setStatus(0); // 0-等待执行

        // 如果有配置过期时间，在这里相加
        // log.setExpireTime(LocalDateTime.now().plusHours(config.getExpireHours()));

        return log;
    }
}
