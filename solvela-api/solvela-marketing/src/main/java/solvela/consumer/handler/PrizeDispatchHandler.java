package solvela.consumer.handler;

import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.PrizeApproveStatusEnum;
import solvela.enums.EnableStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.EventRoute;
import solvela.dispatch.DispatchOutcome;
import solvela.exception.BusinessException;
import solvela.base.util.SolvelaStringUtil;
import solvela.consumer.strategy.PrizeStrategyFactory;
import solvela.event.UserPrizeEvent;
import solvela.enums.ApproveModeEnum;
import solvela.enums.PrizeProposalStatusEnum;
import solvela.enums.EventCategoryEnum;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.prize.prizelog.dao.PrizeLogDao;
import solvela.prize.PrizeLog;
import solvela.prize.prizelog.service.PrizeLogService;
import solvela.scriptengine.annotation.ScriptFunction;
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
        if (config == null || config.getStatus() == EnableStatusEnum.DISABLED) {
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
        if (config.getApproveMode() == ApproveModeEnum.MANUAL) {
            log.info("【风控拦截】命中人工审批，提案已挂起。LogId: {}", prizeLog.getId());
            return; // 流程到此结束！后台状态停留在：待审批(1) + 等待执行(0)
        }

        // 5. 自动免审通道，全速放行！
        doDispatch(prizeLog);
    }

    /**
     * 运营审批通过发奖：这是 approve_mode=1 的奖品唯一的出口
     * <p>
     * 注意本系统有**两层审批**，语义不同、可叠加：
     * ① 本层（t_prize_config.approve_mode）——「这个奖该不该发给这个人」，运营视角，通过后才生成提案；
     * ② 提案层（t_promotion_config.review_level）——「这笔钱该不该出」，财务视角，见 ProposalRecordService.approve。
     * 两层都配了审批，就需要运营和财务各批一次。
     * <p>
     * 条件更新做并发闸门：两个运营同时点通过，只有一个能推进状态，另一个会被告知已处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveDispatch(Long prizeLogId, String approveBy) {
        PrizeLog prizeLog = prizeLogDao.selectById(prizeLogId);
        if (prizeLog == null) {
            throw new BusinessException("发奖记录不存在");
        }
        int rows = prizeLogDao.updateApproveStatus(prizeLogId, PrizeApproveStatusEnum.PENDING, PrizeApproveStatusEnum.PASSED, approveBy);
        if (rows == 0) {
            throw new BusinessException("该发奖记录已被处理，请刷新后重试");
        }
        log.info("【发奖审批通过】LogId: {}, 审批人: {}", prizeLogId, approveBy);
        // 状态已在内存里同步，避免 doDispatch 里回写时把审批结果覆盖掉
        prizeLog.setApproveStatus(PrizeApproveStatusEnum.PASSED);
        prizeLog.setApproveBy(approveBy);
        doDispatch(prizeLog);
    }

    /**
     * 运营审批驳回：不再派发，记录留痕
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectDispatch(Long prizeLogId, String approveBy, String reason) {
        int rows = prizeLogDao.updateApproveStatus(prizeLogId, PrizeApproveStatusEnum.PENDING, PrizeApproveStatusEnum.REJECTED, approveBy);
        if (rows == 0) {
            throw new BusinessException("该发奖记录已被处理，请刷新后重试");
        }
        log.info("【发奖审批驳回】LogId: {}, 审批人: {}, 理由: {}", prizeLogId, approveBy, reason);
    }

    /**
     * 【极其核心】真正的发货引擎！
     *
     * <p>必须是 public：运营后台点「审批通过」之后也要重入这个方法。
     *
     * <p>整段包 try/catch 是<b>刻意的</b>：这里是发奖链路的末端，抛出去没有人再接，
     * 而一个未捕获的异常会让发奖记录永远停在 0-等待执行。所以任何意外都要落成 FAIL。
     */
    @ScriptFunction(name = "_doDispatch", description = "奖励派发")
    public void doDispatch(PrizeLog prizeLog) {
        log.info(">>>> [执行发货] 开始派发奖品，LogId: {}, 类型: {}", prizeLog.getId(), prizeLog.getPrizeType());
        try {
            // 工厂选策略，彻底消灭 switch/if-else
            IPrizeHandler handler = strategyFactory.getHandler(prizeLog.getPrizeType());
            applyOutcome(prizeLog, handler.dispatch(prizeLog));
        } catch (Exception e) {
            log.error("【发奖异常】执行策略时发生严重错误，LogId: {}", prizeLog.getId(), e);
            prizeLog.setStatus(PrizeDispatchStatusEnum.FAIL);
            // 必须截断：异常 message 动辄几百字，直接塞 varchar(128) 会抛 Data too long，
            // 一抛异常连状态都刷不进去，最终表现为「状态永远停在 0」——已踩过
            prizeLog.setFailReason(SolvelaStringUtil.truncate(e.getMessage(), FAIL_REASON_MAX_LENGTH));
            updateQuietly(prizeLog);
        }
    }

    /**
     * 把派发结果落到发奖记录上。三条路，区别全在<b>谁来写最终的 status</b>：
     *
     * <ul>
     *   <li><b>当场失败</b> —— 由这里落 FAIL，没有后续了；</li>
     *   <li><b>无需发放</b>（0 值占位奖）—— 压根不会生成提案，引擎不会跑，
     *       没人回写就会永远悬在 0-等待执行。它本就是当场的终态，这里直接落成功；</li>
     *   <li><b>已受理</b> —— 这里<b>只落提案侧状态</b>，status 留空等回写。</li>
     * </ul>
     *
     * <p>🔴 最后一条是踩出来的：已受理 ≠ 用户拿到了。提案可能进人工审批池，
     * 资产入账也在提案事务提交之后。此刻抢先写 status=1，会把
     * {@code AssetDispatchEngine} 随后回写的真实状态覆盖掉 ——
     * 表现为「发奖记录显示成功、用户其实没收到」（预算耗尽那批就是这么被写成成功的）。
     */
    private void applyOutcome(PrizeLog prizeLog, DispatchOutcome outcome) {
        if (!outcome.ok()) {
            prizeLog.setStatus(PrizeDispatchStatusEnum.FAIL);
            // 提案侧被拒 —— 原因来自会员服务，会落库并可能展示给用户，
            // 这正是「同步调用」而不是发消息的理由：拒绝的原因当场就要拿到
            prizeLog.setProposalStatus(PrizeProposalStatusEnum.REJECTED);
            prizeLog.setFailReason(SolvelaStringUtil.truncate(outcome.failReason(), FAIL_REASON_MAX_LENGTH));
            log.warn("【发货失败】LogId: {}, 原因: {}", prizeLog.getId(), outcome.failReason());
            updateQuietly(prizeLog);
            return;
        }
        if (isNoDeliveryNeeded(prizeLog)) {
            prizeLog.setStatus(PrizeDispatchStatusEnum.SUCCESS);
            updateQuietly(prizeLog);
            log.info("【无需发放】LogId: {}, 奖品价值为0，直接判成功", prizeLog.getId());
            return;
        }
        prizeLog.setProposalStatus(PrizeProposalStatusEnum.ACCEPTED);
        prizeLog.setProposalId(outcome.proposalId());
        updateQuietly(prizeLog);
        log.info("【发货已受理】LogId: {}, 提案ID: {}, 最终状态由资产分发引擎回写",
                prizeLog.getId(), outcome.proposalId());
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
        // 玩法类型由【发放方】填在事件里，这里原样落库。
        // 不在这里查活动表反推 —— 派发与活动配置将来不在同一个进程里，见 ProposalSourceResolver 的注释
        log.setActivityType(event.getActivityType());
        log.setPrizeValue(event.getPrizeValue()); // 通常价值以 Event(彩票引擎算出的)为准
        log.setPrizeLevel(event.getPrizeLevel());

        // --- 2. 来自 Config 的静态规则 (资产相关) ---
        log.setPrizeCode(config.getPrizeCode());
        log.setPrizeName(config.getPrizeName());
        log.setPrizeType(config.getPrizeType());

        // --- 3. 初始状态与时效 ---
        log.setApproveStatus(config.getApproveMode() == ApproveModeEnum.MANUAL
                ? PrizeApproveStatusEnum.PENDING
                : PrizeApproveStatusEnum.NOT_REQUIRED);
        log.setStatus(PrizeDispatchStatusEnum.WAITING);

        // 如果有配置过期时间，在这里相加
        // log.setExpireTime(LocalDateTime.now().plusHours(config.getExpireHours()));

        return log;
    }
}
