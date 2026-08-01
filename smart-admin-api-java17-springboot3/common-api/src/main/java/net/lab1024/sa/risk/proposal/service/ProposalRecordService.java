package net.lab1024.sa.risk.proposal.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCodeUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.ledger.engine.AssetDispatchEngine;
import net.lab1024.sa.risk.engine.RiskChainEngine;
import net.lab1024.sa.risk.engine.RiskContext;
import net.lab1024.sa.risk.engine.RiskResult;
import net.lab1024.sa.risk.promotionconfig.domain.entity.PromotionConfig;
import net.lab1024.sa.risk.promotionconfig.service.PromotionConfigService;
import net.lab1024.sa.risk.proposal.dao.ProposalRecordDao;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordAddForm;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordQueryForm;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordUpdateForm;
import net.lab1024.sa.risk.proposal.domain.vo.ProposalRecordVO;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
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

    private final AssetDispatchEngine assetDispatchEngine;

    /**
     * 提案单号前缀
     */
    private static final String TRADE_NO_PREFIX = "PRP";

    /**
     * 调用方未指定发放数量时的默认值
     */
    private static final int DEFAULT_QUANTITY = 1;

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
            // 落地一条 status=80(风控拦截) 的提案记录，用于合规审计和客诉排查
            saveProposal(req, config, 80, "风控拦截: " + riskResult.getReason());
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
            proposal = saveProposal(req, config, targetStatus, "提案生成成功");
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
     */
    private ProposalRecord saveProposal(ProposalRecordAddForm req, PromotionConfig config, int status, String remark) {
        ProposalRecord record = new ProposalRecord();
        // 单号由提案域自己生成，不采信调用方传值：它是本域对外的凭证，交易号的唯一性必须由发号方保证
        record.setTradeNo(SmartCodeUtil.generateTradeNo(TRADE_NO_PREFIX));
        record.setMemberName(req.getMemberName());

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

        proposalRecordDao.insert(record);
        return record;
    }

    /**
     * 分页查询
     */
    public PageResult<ProposalRecordVO> queryPage(ProposalRecordQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<ProposalRecordVO> list = proposalRecordDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(ProposalRecordAddForm addForm) {
        ProposalRecord proposalRecord = SmartBeanUtil.copy(addForm, ProposalRecord.class);
        proposalRecordDao.insert(proposalRecord);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(ProposalRecordUpdateForm updateForm) {
        ProposalRecord proposalRecord = SmartBeanUtil.copy(updateForm, ProposalRecord.class);
        proposalRecordDao.updateById(proposalRecord);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        proposalRecordDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        proposalRecordDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
