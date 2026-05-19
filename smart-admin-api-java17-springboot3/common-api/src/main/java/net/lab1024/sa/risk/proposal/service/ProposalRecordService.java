package net.lab1024.sa.risk.proposal.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
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
        int targetStatus = calculateInitStatus(req.getPromotionValue(), config);

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
            log.info("【提案免审】金额未触发审批阈值，立即调起底层资产服务发钱! 提案ID: {}", proposal.getId());

            // 调用底层的账务微服务/类去真实动账 (同步或发MQ异步皆可)
            // 动账成功后，这个引擎内部会把提案状态改成 50(成功)
            //assetDispatchEngine.execute(proposal, config);

        } else {
            log.info("【提案挂起】金额触发审批阈值，进入人工审核池。提案ID: {}, 状态: {}", proposal.getId(), targetStatus);
            // 流程驻留在此，等待财务人员在后台调用 approve() 接口
        }

        return ResponseDTO.ok();
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
        record.setMemberName(req.getMemberName());
        record.setSourceType(req.getSourceType());
        record.setSourceBizId(req.getSourceBizId());
        record.setPromotionConfigId(config.getId());
        record.setPromotionValue(req.getPromotionValue());

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
