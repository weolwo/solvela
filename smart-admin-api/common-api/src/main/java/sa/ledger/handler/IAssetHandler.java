package sa.ledger.handler;

import sa.base.common.domain.ResponseDTO;
import sa.risk.proposal.domain.entity.ProposalRecord;

public interface IAssetHandler {
    ResponseDTO dispatch(ProposalRecord proposal);
}
