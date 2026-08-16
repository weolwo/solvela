package sa.risk.proposal.manager;

import sa.risk.proposal.dao.ProposalRecordDao;
import sa.risk.proposal.domain.entity.ProposalRecord;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 提案表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class ProposalRecordManager extends ServiceImpl<ProposalRecordDao, ProposalRecord> {


}
