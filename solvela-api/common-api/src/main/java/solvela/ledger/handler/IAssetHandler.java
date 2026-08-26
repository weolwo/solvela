package solvela.ledger.handler;

import solvela.base.domain.ResponseDTO;
import solvela.risk.ProposalRecord;

public interface IAssetHandler {
    ResponseDTO dispatch(ProposalRecord proposal);
}
