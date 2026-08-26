package solvela.ledger.handler;

import solvela.base.domain.ResponseDTO;
import solvela.risk.proposal.domain.entity.ProposalRecord;

public interface IAssetHandler {
    ResponseDTO dispatch(ProposalRecord proposal);
}
