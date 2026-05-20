package net.lab1024.sa.ledger.handler;

import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;

public interface IAssetHandler {
    ResponseDTO dispatch(ProposalRecord proposal);
}
