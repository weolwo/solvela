package solvela.risk.proposal.service;

import solvela.enums.EnableStatusEnum;
import solvela.enums.ReviewLevelEnum;
import solvela.enums.ProposalStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import solvela.base.domain.PageResult;
import solvela.base.stat.Checkup;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.enums.ProposalSourceTypeEnum;
import solvela.risk.spi.AssetDispatcher;
import solvela.member.service.MemberService;
import solvela.risk.engine.RiskBlockCode;
import solvela.risk.engine.RiskChainEngine;
import solvela.risk.engine.RiskContext;
import solvela.risk.engine.RiskResult;
import solvela.risk.PromotionConfig;
import solvela.risk.promotionconfig.service.PromotionConfigService;
import solvela.risk.proposal.dao.ProposalRecordDao;
import solvela.risk.ProposalRecord;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;
import solvela.risk.proposal.domain.query.ProposalRecordQuery;
import solvela.risk.proposal.domain.dto.ProposalFunnelDTO;
import solvela.risk.proposal.domain.dto.ProposalRecordDTO;
import solvela.exception.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 由 ledger 的 AssetDispatchEngine 实现。走接口是为了不让 risk 依赖 ledger，见 AssetDispatcher
     */
    private final AssetDispatcher assetDispatcher;

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

    private static final long MINUTES_PER_HOUR = 60L;

    /**
     * 待审积压超过一天才提示：审批本来就不是分钟级的事，门槛太低会天天报警，报警就没人看了
     */
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    /**
     * ⚠️ {@code noRollbackFor = BusinessException.class} 不是可有可无的调优。
     *
     * <p>风控拦截那条分支<b>先落一条 status=80 的提案记录再抛</b>，那条记录是合规审计和客诉排查的唯一证据。
     * 默认的 {@code rollbackFor = Exception.class} 会把它连同异常一起回滚掉 ——
     * 表现是「风控明明拦了，提案表里一条记录都没有」，只能靠翻日志复原。
     *
     * <p>本方法抛出的 BusinessException 全部来自显式校验，抛之前要么没写过库，
     * 要么写的正是那条必须留下的审计记录，所以对它一律不回滚是安全的；
     * DAO / 事务层的真实异常仍然照常回滚。
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BusinessException.class)
    public Long addProposal(ProposalRecordAddCommand req) {
        log.info(">>>> [风控提案域] 收到提案申请，来源: {}, 单号: {}", req.getSourceType(), req.getSourceBizId());

        PromotionConfig config = requireEnabledConfig(req.getPromotionConfigId());
        checkRisk(req, config);

        ProposalStatusEnum targetStatus = calculateInitStatus(req.getAmount(), config);
        ProposalRecord proposal;
        try {
            // 没被拦截，risk_code 留空 —— 它只在 status=80 时有意义。
            // 防重靠 uk_t_prm_prop_tsk_stg
            proposal = saveProposal(req, config, targetStatus, "提案生成成功", null);
        } catch (DuplicateKeyException e) {
            log.warn("【提案防重】该业务单号已存在提案记录，直接忽略: {}", req.getSourceBizId());
            // 幂等返回成功。id 给 null 而不是回查一次：调用方拿它只为人工排查，
            // 而「重复请求」这条路上真正要保证的是【不报错、不重复发】，这两点已经成立。
            // 真要那个 id 时按 sourceBizId 查提案表即可，不值得在热路径上多一次查询
            return null;
        }

        // 分流：免审的当场发钱，触发阈值的驻留在审批池，等财务在后台调 approve()
        if (targetStatus == ProposalStatusEnum.PENDING_EXECUTE) {
            log.info("【提案免审】金额未触发审批阈值，提交后立即调起底层资产服务发钱! 提案ID: {}", proposal.getId());
            dispatchAfterCommit(proposal, config);
        } else {
            log.info("【提案挂起】金额触发审批阈值，进入人工审核池。提案ID: {}, 状态: {}", proposal.getId(), targetStatus);
        }
        return proposal.getId();
    }

    /**
     * 底层资产（优惠）配置。它同时是预算与审批阈值的载体，缺了它整条链路无从判定。
     */
    private PromotionConfig requireEnabledConfig(Long promotionConfigId) {
        PromotionConfig config = promotionConfigService.getById(promotionConfigId);
        if (config == null || config.getStatus() == EnableStatusEnum.DISABLED) {
            log.error("【提案阻断】优惠配置不存在或已停用, ID: {}", promotionConfigId);
            throw new BusinessException("资产配置异常");
        }
        return config;
    }

    /**
     * 风控责任链前置校验（防刷、防超发）。
     *
     * <p>⚠️ 被拦时<b>先落一条 status=80 的提案记录再抛</b>，那条记录是合规审计与客诉排查的
     * 唯一证据 —— 本方法所在事务的 {@code noRollbackFor = BusinessException.class}
     * 就是为了它，见 {@link #addProposal} 的方法注释。
     *
     * <p>{@code ruleCode} 必须跟着一起落库：文案是给用户看的、会改，编码才是漏斗聚类的判据。
     * 此前它只进了日志，于是拦截原因分布只能按 remark 自由文本聚类。
     */
    private void checkRisk(ProposalRecordAddCommand req, PromotionConfig config) {
        RiskResult riskResult = riskChainEngine.execute(new RiskContext(req, config));
        if (riskResult.isPassed()) {
            return;
        }
        log.warn("【风控拦截】提案未通过安全校验: {}", riskResult.getReason());
        saveProposal(req, config, ProposalStatusEnum.RISK_BLOCKED,
                "风控拦截: " + riskResult.getReason(), riskResult.getRuleCode());
        throw new BusinessException(riskResult.getReason());
    }

    /**
     * 审批人字段名，收敛在此，不接受外部传入（Mapper 里用 ${} 拼接）
     */
    private static final String FIELD_FIRST_REVIEWER = "first_reviewer";
    private static final String FIELD_SECOND_REVIEWER = "second_reviewer";
    /**
     * 审批通过：一审通过后按 review_level 决定进二审还是直接放行下发
     * <p>
     * 并发安全靠条件更新：两个审批人同时点通过，只有一个拿到 rows=1，另一个被告知已处理，
     * 避免重复审批引发重复发放。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String reviewer, String comment) {
        ProposalRecord proposal = proposalRecordDao.selectById(id);
        if (proposal == null) {
            throw new BusinessException("提案不存在");
        }
        PromotionConfig config = promotionConfigService.getById(proposal.getPromotionConfigId());
        if (config == null) {
            throw new BusinessException("优惠配置不存在，无法审批");
        }

        ProposalStatusEnum current = proposal.getStatus();
        int rows;
        ProposalStatusEnum targetStatus;
        if (current == ProposalStatusEnum.FIRST_REVIEW) {
            // 一审通过：双层审批则转二审，否则直接待执行
            targetStatus = config.getReviewLevel() == ReviewLevelEnum.DOUBLE
                    ? ProposalStatusEnum.SECOND_REVIEW
                    : ProposalStatusEnum.PENDING_EXECUTE;
            rows = proposalRecordDao.updateReview(id, ProposalStatusEnum.FIRST_REVIEW, targetStatus,
                    FIELD_FIRST_REVIEWER, reviewer, comment);
        } else if (current == ProposalStatusEnum.SECOND_REVIEW) {
            targetStatus = ProposalStatusEnum.PENDING_EXECUTE;
            rows = proposalRecordDao.updateReview(id, ProposalStatusEnum.SECOND_REVIEW, targetStatus,
                    FIELD_SECOND_REVIEWER, reviewer, comment);
        } else {
            throw new BusinessException("当前状态不可审批：" + current.getDesc());
        }

        if (rows == 0) {
            throw new BusinessException("该提案已被处理，请刷新后重试");
        }

        // 审批到「待执行」才触发下发，且同样放在事务提交后 —— 理由与 addProposal 一致：
        // 下发失败不能把审批记录一起回滚掉
        if (targetStatus == ProposalStatusEnum.PENDING_EXECUTE) {
            proposal.setStatus(ProposalStatusEnum.PENDING_EXECUTE);
            dispatchAfterCommit(proposal, config);
        }
    }

    /**
     * 审批驳回：一审/二审均可驳回，驳回后不再下发
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reviewer, String comment) {
        ProposalRecord proposal = proposalRecordDao.selectById(id);
        if (proposal == null) {
            throw new BusinessException("提案不存在");
        }
        ProposalStatusEnum current = proposal.getStatus();
        String reviewerField;
        if (current == ProposalStatusEnum.FIRST_REVIEW) {
            reviewerField = FIELD_FIRST_REVIEWER;
        } else if (current == ProposalStatusEnum.SECOND_REVIEW) {
            reviewerField = FIELD_SECOND_REVIEWER;
        } else {
            throw new BusinessException("当前状态不可驳回：" + current.getDesc());
        }
        int rows = proposalRecordDao.updateReview(id, current, ProposalStatusEnum.REJECTED, reviewerField, reviewer, comment);
        if (rows == 0) {
            throw new BusinessException("该提案已被处理，请刷新后重试");
        }
    }

    /**
     * 把资产下发挪到提案事务**提交之后**再执行（方案A：提案与下发解耦）
     * <p>
     * 为什么必须这么做：下发若跑在本事务内，资产层任何一次插入失败都会把事务标成 rollback-only，
     * 于是「提案记录」连同引擎写下的 status=70 失败痕迹一起被回滚 —— 压测时 51 条发券失败，
     * 提案表里一条记录都查不到，只能靠翻日志定位。提交后再发，提案一定留得下，
     * 失败也能稳稳落在 70，运营和研发都能从提案列表直接看到卡在哪。
     * <p>
     * 语义上也更顺：提案是「决定发」，下发是「真的发」，本就该是两个阶段。
     */
    private void dispatchAfterCommit(ProposalRecord proposal, PromotionConfig config) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 理论上 addProposal 带 @Transactional 不会走到这里；兜底为直接执行，避免静默不发
            assetDispatcher.execute(proposal, config);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 此处已无事务上下文，引擎内部每次状态更新都是独立自动提交，天然不受回滚影响
                assetDispatcher.execute(proposal, config);
            }
        });
    }

    /**
     * 根据 t_promotion_config 的配置，精准计算提案状态
     */
    private ProposalStatusEnum calculateInitStatus(BigDecimal amount, PromotionConfig config) {
        // 配置了不需要审批，直接待执行
        if (config.getReviewLevel() == ReviewLevelEnum.NONE) {
            return ProposalStatusEnum.PENDING_EXECUTE;
        }

        // 需要审批，且发放金额 >= 一审阈值，落待一审
        if (amount.compareTo(config.getFirstReviewThreshold()) >= 0) {
            return ProposalStatusEnum.FIRST_REVIEW;
        }

        // 配置了审批，但本次发的钱太少（比如只发 1 毛钱），没达到一审门槛，自动豁免
        return ProposalStatusEnum.PENDING_EXECUTE;
    }

    /**
     * 构建并保存提案实体
     *
     * @param riskCode 风控拦截分类，仅 status=80 时传值；其余场景传 null
     */
    private ProposalRecord saveProposal(ProposalRecordAddCommand req, PromotionConfig config,
                                        ProposalStatusEnum status, String remark, String riskCode) {
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
    public PageResult<ProposalRecordDTO> queryPage(ProposalRecordQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<ProposalRecordDTO> list = proposalRecordDao.queryPage(page, queryForm);
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
    public ProposalFunnelDTO funnel(ProposalRecordQuery queryForm) {
        StatRow row = StatRow.of(proposalRecordDao.selectFunnel(queryForm));
        SourceStats sources = sourceStats(queryForm);
        BlockStats blocks = blockStats(queryForm, row.count("blockedCount"));

        ProposalFunnelDTO vo = new ProposalFunnelDTO();
        fillOverview(row, vo);
        vo.setAssetList(assetStats(queryForm));
        vo.setSourceList(sources.list());
        vo.setBlockReasonList(blocks.list());
        vo.setIssueList(checkup(row, sources.unknownCount(), blocks.attention()));
        return vo;
    }

    /**
     * 提案总量与它在链路各段的去向：等审批、已驳回、执行中、到账、拦截。
     */
    private void fillOverview(StatRow row, ProposalFunnelDTO vo) {
        long total = row.count("totalCount");
        long firstReview = row.count("firstReviewCount");
        long secondReview = row.count("secondReviewCount");
        long success = row.count("successCount");
        long blocked = row.count("blockedCount");

        vo.setTotalCount(total);
        vo.setMemberCount(row.count("memberCount"));
        vo.setWaitingCount(row.count("waitingCount"));
        vo.setFirstReviewCount(firstReview);
        vo.setSecondReviewCount(secondReview);
        vo.setRejectedCount(row.count("rejectedCount"));
        vo.setPendingExecuteCount(row.count("pendingExecuteCount"));
        vo.setExecutingCount(row.count("executingCount"));
        vo.setSuccessCount(success);
        vo.setPartialCount(row.count("partialCount"));
        vo.setFailedCount(row.count("failedCount"));
        vo.setBlockedCount(blocked);
        /*
         * 到账率与拦截率的分母都用「提案总数」：提案链路上的每一步都可能把钱拦下来，
         * 剔掉任何一段都会让剩下那个比率虚高，而运营要的恰恰是「一百个提案里最后几个到账」。
         */
        vo.setSuccessRate(Rate.share(success, total));
        vo.setBlockRate(Rate.share(blocked, total));
        vo.setPendingReviewCount(firstReview + secondReview);
        vo.setPendingReviewOldestMinutes(row.count("pendingReviewOldestMinutes"));
        vo.setStuckDispatchCount(row.count("stuckDispatchCount"));
    }

    /**
     * 资产维度：金额按 asset_type 分开算，<b>绝不合并</b> ——
     * 100 积分和 100 元不是同一个量纲，加出来的合计数会被当成钱看，很危险。
     */
    private List<ProposalFunnelDTO.AssetStatDTO> assetStats(ProposalRecordQuery queryForm) {
        List<ProposalFunnelDTO.AssetStatDTO> assetList = new ArrayList<>();
        for (StatRow stat : StatRow.of(proposalRecordDao.selectAssetStat(queryForm))) {
            ProposalFunnelDTO.AssetStatDTO item = new ProposalFunnelDTO.AssetStatDTO();
            item.setAssetType(stat.text("assetType"));
            item.setProposalCount(stat.count("proposalCount"));
            item.setSuccessCount(stat.count("successCount"));
            item.setSuccessAmount(stat.amount("successAmount"));
            item.setPendingAmount(stat.amount("pendingAmount"));
            item.setBlockedAmount(stat.amount("blockedAmount"));
            assetList.add(item);
        }
        return assetList;
    }

    /**
     * 来源分布，外加<b>字典外来源</b>的合计。
     *
     * @param unknownCount 来源不在 TASK/DRAW/LOTTERY/MANUAL 里的提案数。
     *                     单独数出来是因为它会让整张来源报表算错，而不只是多一行
     */
    private record SourceStats(long unknownCount, List<ProposalFunnelDTO.SourceStatDTO> list) {
    }

    private SourceStats sourceStats(ProposalRecordQuery queryForm) {
        long unknownCount = 0L;
        List<ProposalFunnelDTO.SourceStatDTO> sourceList = new ArrayList<>();
        for (StatRow stat : StatRow.of(proposalRecordDao.selectSourceStat(queryForm))) {
            String sourceType = stat.text("sourceType");
            ProposalSourceTypeEnum sourceEnum = sourceType == null ? null : ProposalSourceTypeEnum.resolve(sourceType);
            long count = stat.count("proposalCount");
            long success = stat.count("successCount");

            ProposalFunnelDTO.SourceStatDTO item = new ProposalFunnelDTO.SourceStatDTO();
            item.setSourceType(sourceType);
            // 字典外的取值原样回显，不要用「其它」盖住它 —— 那正是要被看见的东西
            item.setSourceDesc(sourceEnum == null ? sourceType : sourceEnum.getDesc());
            item.setProposalCount(count);
            item.setSuccessCount(success);
            item.setSuccessRate(Rate.share(success, count));
            item.setUnknownSource(sourceEnum == null);
            if (sourceEnum == null) {
                unknownCount += count;
            }
            sourceList.add(item);
        }
        return new SourceStats(unknownCount, sourceList);
    }

    /**
     * 风控拦截原因分布，外加<b>需要人处理</b>的那部分合计。
     *
     * @param attention 「单次金额超限」「预算已耗尽」这类拦截的条数。防刷拦截天然量大，
     *                  不单独数出来，这两类会被彻底淹没 —— 而它们才是要立刻有人看的
     */
    private record BlockStats(long attention, List<ProposalFunnelDTO.BlockReasonDTO> list) {
    }

    /**
     * @param blockedTotal 拦截总数，用作各原因占比的分母
     */
    private BlockStats blockStats(ProposalRecordQuery queryForm, long blockedTotal) {
        long attention = 0L;
        List<ProposalFunnelDTO.BlockReasonDTO> blockReasonList = new ArrayList<>();
        // 按 risk_code 聚类而不是按文案：文案改了统计也不会裂成两行
        for (StatRow stat : StatRow.of(proposalRecordDao.selectBlockReasonStat(queryForm))) {
            String code = stat.text("riskCode");
            String sampleRemark = stat.text("sampleRemark");
            RiskBlockCode blockCode = code == null ? null : RiskBlockCode.resolve(code);
            long count = stat.count("blockCount");

            ProposalFunnelDTO.BlockReasonDTO item = new ProposalFunnelDTO.BlockReasonDTO();
            item.setRiskCode(code);
            /*
             * 归不了类的回显 remark 原文，不用「其它」盖掉：那批是回填规则没覆盖到的历史文案，
             * 盖住之后就再也没人知道它们是什么了。
             */
            item.setReason(blockCode != null ? blockCode.getDesc()
                    : sampleRemark != null ? sampleRemark : "（未记录原因）");
            item.setBlockCount(count);
            item.setBlockShare(Rate.share(count, blockedTotal));
            item.setNeedsAttention(blockCode != null && blockCode.needsAttention());
            if (Boolean.TRUE.equals(item.getNeedsAttention())) {
                attention += count;
            }
            blockReasonList.add(item);
        }
        return new BlockStats(attention, blockReasonList);
    }

    /**
     * 流程与一致性体检。
     */
    private List<String> checkup(StatRow row, long unknownSourceCount, long blockAttention) {
        long failed = row.count("failedCount");
        long partial = row.count("partialCount");
        long oldestMinutes = row.count("pendingReviewOldestMinutes");
        return new Checkup()
                .countIf(row.count("stuckDispatchCount"),
                        "有 {} 条提案卡在「待执行/执行中」超过 30 分钟：下发是在提案事务提交后"
                                + "同步调起的，进程中途退出就没有第二次机会，而工程里没有任何重试/补偿任务 —— "
                                + "这些钱既没发出去也没标成失败，需要人工确认后重新触发")
                .when(oldestMinutes >= MINUTES_PER_DAY,
                        "待审提案里最久的一条已经等了 {} 小时：提案压在审批池里，对用户就是「奖一直没发」",
                        oldestMinutes / MINUTES_PER_HOUR)
                .when(failed > 0 || partial > 0,
                        "有 {} 条彻底失败、{} 条部分成功：部分成功意味着奖只发出去一半，"
                                + "用户拿到的与承诺的不一致，需要人工补齐。失败原因见每条提案的备注",
                        failed, partial)
                .countIf(blockAttention,
                        "有 {} 条拦截属于「单次金额超限」或「预算已耗尽」："
                                + "前者是系统兜底真的被触发了（上游算出了超过配置上限的金额），"
                                + "后者意味着从那一刻起所有人都拿不到奖 —— 这两类和防刷拦截性质不同，"
                                + "光看拦截总量会被防刷淹没")
                .countIf(row.count("sameReviewerCount"),
                        "有 {} 条提案的一审人与二审人是同一个人：审批接口不校验这一点，"
                                + "双层审批变成同一个人点两次，这道防线只剩形式")
                .countIf(row.count("waitingCount"),
                        "有 {} 条提案停在「等待中」：正常链路只会落 待一审/待执行/风控拦截 三种初始状态，"
                                + "出现 0 说明这些记录是绕过提案链路直接写进来的（后台「新建」按钮就能做到），"
                                + "它们不会被任何流程推进")
                .countIf(row.count("rejectNoCommentCount"),
                        "有 {} 条驳回没有填写理由：事后说不清为什么不给这个人发，"
                                + "客诉与审计时都拿不出依据")
                .countIf(row.count("reviewerNoTimeCount"),
                        "有 {} 条提案有审批人却没有审批时间：这两个字段是同一条 SQL 一起写的，"
                                + "只有一半说明该行被人工改过")
                .countIf(unknownSourceCount,
                        "有 {} 条提案的来源不在字典内（TASK/DRAW/LOTTERY/MANUAL）："
                                + "历史上四个发奖 handler 都硬编码写了 LOTTERY_DRAW，任务发的奖也被记成彩票抽奖，"
                                + "这批数据按来源统计时会被算错")
                .issues();
    }
}
