package solvela.risk.proposal.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import solvela.base.common.domain.PageResult;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.util.SolvelaCodeUtil;
import solvela.base.common.util.SolvelaPageUtil;
import solvela.enums.ProposalSourceTypeEnum;
import solvela.ledger.engine.AssetDispatchEngine;
import solvela.member.service.MemberService;
import solvela.risk.engine.RiskBlockCode;
import solvela.risk.engine.RiskChainEngine;
import solvela.risk.engine.RiskContext;
import solvela.risk.engine.RiskResult;
import solvela.risk.promotionconfig.domain.entity.PromotionConfig;
import solvela.risk.promotionconfig.service.PromotionConfigService;
import solvela.risk.proposal.dao.ProposalRecordDao;
import solvela.risk.proposal.domain.entity.ProposalRecord;
import solvela.risk.proposal.domain.form.ProposalRecordAddForm;
import solvela.risk.proposal.domain.form.ProposalRecordQueryForm;
import solvela.risk.proposal.domain.vo.ProposalFunnelVO;
import solvela.risk.proposal.domain.vo.ProposalRecordVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提案表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ProposalRecordService {

    private final ProposalRecordDao proposalRecordDao;

    private final PromotionConfigService promotionConfigService;

    // 风控责任链引擎 (负责卡频率、卡预算等)
    private final RiskChainEngine riskChainEngine;

    private final AssetDispatchEngine assetDispatchEngine;

    /**
     * 只用来把会员号翻成<b>展示快照</b>。提案是单据，落库时要记下「当时那个账号」；
     * 顺带做存在性校验 —— 关联键指向一个查不到的会员，等于凭空造出一张无主提案。
     */
    private final MemberService memberService;

    /**
     * 提案单号前缀
     */
    private static final String TRADE_NO_PREFIX = "PRP";

    /**
     * 调用方未指定发放数量时的默认值
     */
    private static final int DEFAULT_QUANTITY = 1;

    /**
     * 比率保留位数（与彩票/抽奖/任务三个漏斗一致）
     */
    private static final int RATE_SCALE = 4;

    private static final long MINUTES_PER_HOUR = 60L;

    /**
     * 待审积压超过一天才提示：审批本来就不是分钟级的事，门槛太低会天天报警，报警就没人看了
     */
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO addProposal(ProposalRecordAddForm req) {
        log.info(">>>> [风控提案域] 收到提案申请，来源: {}, 单号: {}", req.getSourceType(), req.getSourceBizId());

        // 1. 获取底层资产（优惠）配置
        PromotionConfig config = promotionConfigService.getById(req.getPromotionConfigId());
        if (config == null || config.getStatus() == 0) {
            log.error("【提案阻断】优惠配置不存在或已停用, ID: {}", req.getPromotionConfigId());
            return ResponseDTO.userErrorParam("资产配置异常");
        }

        // ==========================================
        // 2. 【核心】风控责任链前置校验 (防刷、防超发)
        // ==========================================
        RiskContext riskContext = new RiskContext(req, config);
        RiskResult riskResult = riskChainEngine.execute(riskContext);

        if (!riskResult.isPassed()) {
            log.warn("【风控拦截】提案未通过安全校验: {}", riskResult.getReason());
            /*
             * 落地一条 status=80(风控拦截) 的提案记录，用于合规审计和客诉排查。
             * ruleCode 必须一起落库：文案是给用户看的、会改，编码才是漏斗聚类的判据
             * （此前它只进了日志，于是漏斗只能按 remark 自由文本聚类）。
             */
            saveProposal(req, config, 80, "风控拦截: " + riskResult.getReason(), riskResult.getRuleCode());
            return ResponseDTO.userErrorParam(riskResult.getReason());
        }

        // ==========================================
        // 3. 计算提案的初始审批状态
        // ==========================================
        int targetStatus = calculateInitStatus(req.getAmount(), config);

        // ==========================================
        // 4. 落地正式提案记录 (依赖 uk_t_prm_prop_tsk_stg 防重)
        // ==========================================
        ProposalRecord proposal;
        try {
            // 没被拦截，risk_code 留空 —— 它只在 status=80 时有意义
            proposal = saveProposal(req, config, targetStatus, "提案生成成功", null);
        } catch (DuplicateKeyException e) {
            log.warn("【提案防重】该业务单号已存在提案记录，直接忽略: {}", req.getSourceBizId());
            return ResponseDTO.ok(); // 幂等返回成功
        }

        // ==========================================
        // 5. 【分流】判断是否需要立即加钱
        // ==========================================
        if (targetStatus == 30) {
            log.info("【提案免审】金额未触发审批阈值，提交后立即调起底层资产服务发钱! 提案ID: {}", proposal.getId());
            dispatchAfterCommit(proposal, config);
        } else {
            log.info("【提案挂起】金额触发审批阈值，进入人工审核池。提案ID: {}, 状态: {}", proposal.getId(), targetStatus);
            // 流程驻留在此，等待财务人员在后台调用 approve() 接口
        }

        return ResponseDTO.ok();
    }

    /**
     * 提案状态字典（对齐 t_proposal_record.status）
     */
    private static final int STATUS_FIRST_REVIEW = 10;
    private static final int STATUS_SECOND_REVIEW = 11;
    private static final int STATUS_REJECTED = 20;
    private static final int STATUS_PENDING_EXECUTE = 30;

    /**
     * 审批人字段名，收敛在此，不接受外部传入（Mapper 里用 ${} 拼接）
     */
    private static final String FIELD_FIRST_REVIEWER = "first_reviewer";
    private static final String FIELD_SECOND_REVIEWER = "second_reviewer";

    /**
     * 需要双层审批
     */
    private static final int REVIEW_LEVEL_DOUBLE = 2;

    /**
     * 审批通过：一审通过后按 review_level 决定进二审还是直接放行下发
     *
     * 并发安全靠条件更新：两个审批人同时点通过，只有一个拿到 rows=1，另一个被告知已处理，
     * 避免重复审批引发重复发放。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> approve(Long id, String reviewer, String comment) {
        ProposalRecord proposal = proposalRecordDao.selectById(id);
        if (proposal == null) {
            return ResponseDTO.userErrorParam("提案不存在");
        }
        PromotionConfig config = promotionConfigService.getById(proposal.getPromotionConfigId());
        if (config == null) {
            return ResponseDTO.userErrorParam("优惠配置不存在，无法审批");
        }

        Integer current = proposal.getStatus();
        int rows;
        int targetStatus;
        if (STATUS_FIRST_REVIEW == current) {
            // 一审通过：双层审批则转二审，否则直接待执行
            targetStatus = config.getReviewLevel() == REVIEW_LEVEL_DOUBLE ? STATUS_SECOND_REVIEW : STATUS_PENDING_EXECUTE;
            rows = proposalRecordDao.updateReview(id, STATUS_FIRST_REVIEW, targetStatus,
                    FIELD_FIRST_REVIEWER, reviewer, comment);
        } else if (STATUS_SECOND_REVIEW == current) {
            targetStatus = STATUS_PENDING_EXECUTE;
            rows = proposalRecordDao.updateReview(id, STATUS_SECOND_REVIEW, targetStatus,
                    FIELD_SECOND_REVIEWER, reviewer, comment);
        } else {
            return ResponseDTO.userErrorParam("当前状态不可审批：" + current);
        }

        if (rows == 0) {
            return ResponseDTO.userErrorParam("该提案已被处理，请刷新后重试");
        }

        // 审批到「待执行」才触发下发，且同样放在事务提交后 —— 理由与 addProposal 一致：
        // 下发失败不能把审批记录一起回滚掉
        if (targetStatus == STATUS_PENDING_EXECUTE) {
            proposal.setStatus(STATUS_PENDING_EXECUTE);
            dispatchAfterCommit(proposal, config);
        }
        return ResponseDTO.ok();
    }

    /**
     * 审批驳回：一审/二审均可驳回，驳回后不再下发
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> reject(Long id, String reviewer, String comment) {
        ProposalRecord proposal = proposalRecordDao.selectById(id);
        if (proposal == null) {
            return ResponseDTO.userErrorParam("提案不存在");
        }
        Integer current = proposal.getStatus();
        String reviewerField;
        if (STATUS_FIRST_REVIEW == current) {
            reviewerField = FIELD_FIRST_REVIEWER;
        } else if (STATUS_SECOND_REVIEW == current) {
            reviewerField = FIELD_SECOND_REVIEWER;
        } else {
            return ResponseDTO.userErrorParam("当前状态不可驳回：" + current);
        }
        int rows = proposalRecordDao.updateReview(id, current, STATUS_REJECTED, reviewerField, reviewer, comment);
        if (rows == 0) {
            return ResponseDTO.userErrorParam("该提案已被处理，请刷新后重试");
        }
        return ResponseDTO.ok();
    }

    /**
     * 把资产下发挪到提案事务**提交之后**再执行（方案A：提案与下发解耦）
     *
     * 为什么必须这么做：下发若跑在本事务内，资产层任何一次插入失败都会把事务标成 rollback-only，
     * 于是「提案记录」连同引擎写下的 status=70 失败痕迹一起被回滚 —— 压测时 51 条发券失败，
     * 提案表里一条记录都查不到，只能靠翻日志定位。提交后再发，提案一定留得下，
     * 失败也能稳稳落在 70，运营和研发都能从提案列表直接看到卡在哪。
     *
     * 语义上也更顺：提案是「决定发」，下发是「真的发」，本就该是两个阶段。
     */
    private void dispatchAfterCommit(ProposalRecord proposal, PromotionConfig config) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 理论上 addProposal 带 @Transactional 不会走到这里；兜底为直接执行，避免静默不发
            assetDispatchEngine.execute(proposal, config);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 此处已无事务上下文，引擎内部每次状态更新都是独立自动提交，天然不受回滚影响
                assetDispatchEngine.execute(proposal, config);
            }
        });
    }

    /**
     * 根据 t_promotion_config 的配置，精准计算提案状态
     */
    private int calculateInitStatus(BigDecimal amount, PromotionConfig config) {
        // 如果配置了不需要审批，直接变成 30(待执行)
        if (config.getReviewLevel() == 0) {
            return 30;
        }

        // 如果需要审批，且发放金额 >= 一审阈值，变成 10(待一审)
        if (amount.compareTo(config.getFirstReviewThreshold()) >= 0) {
            return 10;
        }

        // 如果配置了审批，但本次发的钱太少（比如只发1毛钱），没达到一审门槛，自动豁免！
        return 30;
    }

    /**
     * 构建并保存提案实体
     *
     * @param riskCode 风控拦截分类，仅 status=80 时传值；其余场景传 null
     */
    private ProposalRecord saveProposal(ProposalRecordAddForm req, PromotionConfig config,
                                        int status, String remark, String riskCode) {
        ProposalRecord record = new ProposalRecord();
        // 单号由提案域自己生成，不采信调用方传值：它是本域对外的凭证，交易号的唯一性必须由发号方保证
        record.setTradeNo(SolvelaCodeUtil.generateTradeNo(TRADE_NO_PREFIX));
        // 关联键取调用方传的会员号；展示快照由服务端查会员表补 ——
        // 让调用方自己传名字的话，名字与会员号迟早会对不上，而且对不上时不报错
        record.setMemberId(req.getMemberId());
        record.setMemberName(memberService.requireMemberName(req.getMemberId()));

        // 发什么：assetType 决定下发走哪个策略，assetRef 指向具体资产（值类资产为空）
        record.setAssetType(req.getAssetType());
        record.setAssetRef(req.getAssetRef());
        // 展示名随提案一起落库：账务侧发券/发货时要用，且不能回头查营销域（依赖方向是单向的）
        record.setAssetName(req.getAssetName());
        record.setAmount(req.getAmount());
        // 数量参与 used_quota 扣减，调用方不传按 1 计
        record.setQuantity(req.getQuantity() == null ? DEFAULT_QUANTITY : req.getQuantity());

        record.setSourceType(req.getSourceType());
        record.setSourceBizId(req.getSourceBizId());
        record.setPromotionConfigId(config.getId());

        record.setStatus(status);
        record.setRemark(remark);
        record.setRiskCode(riskCode);

        proposalRecordDao.insert(record);
        return record;
    }

    /**
     * 分页查询
     */
    public PageResult<ProposalRecordVO> queryPage(ProposalRecordQueryForm queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<ProposalRecordVO> list = proposalRecordDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 提案漏斗。
     *
     * <p>回答的是翻十页提案也答不出来的三件事：今天发出去多少（按资产类型分开算）、
     * 有没有单子压在审批池里没人管、<b>有没有钱卡在半路既没发也没标失败</b>。
     *
     * <p>最后一个是本页独有的：下发是在提案事务提交后同步调起的，进程中途退出就没有第二次机会，
     * 而全工程没有任何重试/补偿的定时任务 —— 卡住的提案会一直停在 30/40，没人查就发现不了。
     */
    public ProposalFunnelVO funnel(ProposalRecordQueryForm queryForm) {
        Map<String, Object> row = proposalRecordDao.selectFunnel(queryForm);
        ProposalFunnelVO vo = new ProposalFunnelVO();

        long total = toLong(row.get("totalCount"));
        long firstReview = toLong(row.get("firstReviewCount"));
        long secondReview = toLong(row.get("secondReviewCount"));
        long success = toLong(row.get("successCount"));
        long partial = toLong(row.get("partialCount"));
        long failed = toLong(row.get("failedCount"));
        long blocked = toLong(row.get("blockedCount"));
        long waiting = toLong(row.get("waitingCount"));
        long stuckDispatch = toLong(row.get("stuckDispatchCount"));

        vo.setTotalCount(total);
        vo.setMemberCount(toLong(row.get("memberCount")));
        vo.setWaitingCount(waiting);
        vo.setFirstReviewCount(firstReview);
        vo.setSecondReviewCount(secondReview);
        vo.setRejectedCount(toLong(row.get("rejectedCount")));
        vo.setPendingExecuteCount(toLong(row.get("pendingExecuteCount")));
        vo.setExecutingCount(toLong(row.get("executingCount")));
        vo.setSuccessCount(success);
        vo.setPartialCount(partial);
        vo.setFailedCount(failed);
        vo.setBlockedCount(blocked);
        /*
         * 到账率与拦截率的分母都用「提案总数」：提案链路上的每一步都可能把钱拦下来，
         * 剔掉任何一段都会让剩下那个比率虚高，而运营要的恰恰是「一百个提案里最后几个到账」。
         */
        vo.setSuccessRate(rate(success, total));
        vo.setBlockRate(rate(blocked, total));
        vo.setPendingReviewCount(firstReview + secondReview);
        vo.setPendingReviewOldestMinutes(toLong(row.get("pendingReviewOldestMinutes")));
        vo.setStuckDispatchCount(stuckDispatch);

        // ---- 资产维度：金额按 asset_type 分开算，绝不合并 ----
        List<ProposalFunnelVO.AssetStatVO> assetList = new ArrayList<>();
        for (Map<String, Object> stat : proposalRecordDao.selectAssetStat(queryForm)) {
            ProposalFunnelVO.AssetStatVO item = new ProposalFunnelVO.AssetStatVO();
            item.setAssetType(stat.get("assetType") == null ? null : String.valueOf(stat.get("assetType")));
            item.setProposalCount(toLong(stat.get("proposalCount")));
            item.setSuccessCount(toLong(stat.get("successCount")));
            item.setSuccessAmount(toDecimal(stat.get("successAmount")));
            item.setPendingAmount(toDecimal(stat.get("pendingAmount")));
            item.setBlockedAmount(toDecimal(stat.get("blockedAmount")));
            assetList.add(item);
        }
        vo.setAssetList(assetList);

        // ---- 来源维度 ----
        long unknownSourceCount = 0L;
        List<ProposalFunnelVO.SourceStatVO> sourceList = new ArrayList<>();
        for (Map<String, Object> stat : proposalRecordDao.selectSourceStat(queryForm)) {
            ProposalFunnelVO.SourceStatVO item = new ProposalFunnelVO.SourceStatVO();
            String sourceType = stat.get("sourceType") == null ? null : String.valueOf(stat.get("sourceType"));
            ProposalSourceTypeEnum sourceEnum = sourceType == null ? null : ProposalSourceTypeEnum.resolve(sourceType);
            long count = toLong(stat.get("proposalCount"));
            long sourceSuccess = toLong(stat.get("successCount"));
            item.setSourceType(sourceType);
            // 字典外的取值原样回显，不要用「其它」盖住它 —— 那正是要被看见的东西
            item.setSourceDesc(sourceEnum == null ? sourceType : sourceEnum.getDesc());
            item.setProposalCount(count);
            item.setSuccessCount(sourceSuccess);
            item.setSuccessRate(rate(sourceSuccess, count));
            item.setUnknownSource(sourceEnum == null);
            if (sourceEnum == null) {
                unknownSourceCount += count;
            }
            sourceList.add(item);
        }
        vo.setSourceList(sourceList);

        // ---- 风控拦截原因：按 risk_code 聚类，文案改了统计也不会裂 ----
        long blockAttention = 0L;
        List<ProposalFunnelVO.BlockReasonVO> blockReasonList = new ArrayList<>();
        for (Map<String, Object> stat : proposalRecordDao.selectBlockReasonStat(queryForm)) {
            ProposalFunnelVO.BlockReasonVO item = new ProposalFunnelVO.BlockReasonVO();
            String code = stat.get("riskCode") == null ? null : String.valueOf(stat.get("riskCode"));
            String sampleRemark = stat.get("sampleRemark") == null ? null : String.valueOf(stat.get("sampleRemark"));
            RiskBlockCode blockCode = code == null ? null : RiskBlockCode.resolve(code);
            long count = toLong(stat.get("blockCount"));
            item.setRiskCode(code);
            /*
             * 归不了类的回显 remark 原文，不用「其它」盖掉：那批是回填规则没覆盖到的历史文案，
             * 盖住之后就再也没人知道它们是什么了。
             */
            item.setReason(blockCode != null ? blockCode.getDesc()
                    : sampleRemark != null ? sampleRemark : "（未记录原因）");
            item.setBlockCount(count);
            item.setBlockShare(rate(count, blocked));
            item.setNeedsAttention(blockCode != null && blockCode.needsAttention());
            if (Boolean.TRUE.equals(item.getNeedsAttention())) {
                blockAttention += count;
            }
            blockReasonList.add(item);
        }
        vo.setBlockReasonList(blockReasonList);

        // ---- 流程与一致性体检 ----
        List<String> issues = new ArrayList<>();
        if (stuckDispatch > 0) {
            issues.add("有 " + stuckDispatch + " 条提案卡在「待执行/执行中」超过 30 分钟：下发是在提案事务提交后"
                    + "同步调起的，进程中途退出就没有第二次机会，而工程里没有任何重试/补偿任务 —— "
                    + "这些钱既没发出去也没标成失败，需要人工确认后重新触发");
        }
        long oldestMinutes = vo.getPendingReviewOldestMinutes();
        if (oldestMinutes >= MINUTES_PER_DAY) {
            issues.add("待审提案里最久的一条已经等了 " + (oldestMinutes / MINUTES_PER_HOUR) + " 小时："
                    + "提案压在审批池里，对用户就是「奖一直没发」");
        }
        if (failed > 0 || partial > 0) {
            issues.add("有 " + failed + " 条彻底失败、" + partial + " 条部分成功：部分成功意味着奖只发出去一半，"
                    + "用户拿到的与承诺的不一致，需要人工补齐。失败原因见每条提案的备注");
        }
        if (blockAttention > 0) {
            issues.add("有 " + blockAttention + " 条拦截属于「单次金额超限」或「预算已耗尽」："
                    + "前者是系统兜底真的被触发了（上游算出了超过配置上限的金额），"
                    + "后者意味着从那一刻起所有人都拿不到奖 —— 这两类和防刷拦截性质不同，"
                    + "光看拦截总量会被防刷淹没");
        }
        long sameReviewer = toLong(row.get("sameReviewerCount"));
        if (sameReviewer > 0) {
            issues.add("有 " + sameReviewer + " 条提案的一审人与二审人是同一个人：审批接口不校验这一点，"
                    + "双层审批变成同一个人点两次，这道防线只剩形式");
        }
        if (waiting > 0) {
            issues.add("有 " + waiting + " 条提案停在「等待中」：正常链路只会落 待一审/待执行/风控拦截 三种初始状态，"
                    + "出现 0 说明这些记录是绕过提案链路直接写进来的（后台「新建」按钮就能做到），"
                    + "它们不会被任何流程推进");
        }
        long rejectNoComment = toLong(row.get("rejectNoCommentCount"));
        if (rejectNoComment > 0) {
            issues.add("有 " + rejectNoComment + " 条驳回没有填写理由：事后说不清为什么不给这个人发，"
                    + "客诉与审计时都拿不出依据");
        }
        long reviewerNoTime = toLong(row.get("reviewerNoTimeCount"));
        if (reviewerNoTime > 0) {
            issues.add("有 " + reviewerNoTime + " 条提案有审批人却没有审批时间：这两个字段是同一条 SQL 一起写的，"
                    + "只有一半说明该行被人工改过");
        }
        if (unknownSourceCount > 0) {
            issues.add("有 " + unknownSourceCount + " 条提案的来源不在字典内（TASK/DRAW/LOTTERY/MANUAL）："
                    + "历史上四个发奖 handler 都硬编码写了 LOTTERY_DRAW，任务发的奖也被记成彩票抽奖，"
                    + "这批数据按来源统计时会被算错");
        }
        vo.setIssueList(issues);
        return vo;
    }

    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /**
     * 金额统一保留两位。DECIMAL(13,4) 直接透出会变成「1100.0000 积分」，多出来的两位没有意义。
     */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal decimal = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }
}
