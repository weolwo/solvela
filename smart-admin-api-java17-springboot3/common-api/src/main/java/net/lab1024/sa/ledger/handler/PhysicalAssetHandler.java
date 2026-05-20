package net.lab1024.sa.ledger.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.anno.AssetStrategy;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.enums.PrizeTypeEnum;
import net.lab1024.sa.ledger.logistic.dao.PhysicalDeliveryDao;
import net.lab1024.sa.ledger.logistic.domain.entity.PhysicalDelivery;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AssetStrategy(PrizeTypeEnum.PHYSICAL)
public class PhysicalAssetHandler implements IAssetHandler {
    @Resource
    private PhysicalDeliveryDao deliveryDao;

    @Override
    public ResponseDTO dispatch(ProposalRecord proposal) {
        PhysicalDelivery delivery = new PhysicalDelivery();
        delivery.setTenantId(proposal.getTenantId());
        delivery.setMemberName(proposal.getMemberName());
        delivery.setProposalId(proposal.getId());
        delivery.setSourceType("PROPOSAL");
        delivery.setStatus(0); // 0-待发货
        // 注意：收件人信息通常在生成提案前（前台用户填写的）就已经存到 proposal.ext 或另外一张收货表里了

        try {
            deliveryDao.insert(delivery);
            return ResponseDTO.ok();
        } catch (DuplicateKeyException e) {
            log.warn("【防重拦截】该提案已生成物流单: {}", proposal.getId());
            return ResponseDTO.ok();
        }
    }
}
