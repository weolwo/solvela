package net.lab1024.sa.risk.proposal.service;

import java.util.List;

import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.risk.proposal.dao.ProposalRecordDao;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordAddForm;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordQueryForm;
import net.lab1024.sa.risk.proposal.domain.form.ProposalRecordUpdateForm;
import net.lab1024.sa.risk.proposal.domain.vo.ProposalRecordVO;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 提案表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class ProposalRecordService {

    private final ProposalRecordDao proposalRecordDao;

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
