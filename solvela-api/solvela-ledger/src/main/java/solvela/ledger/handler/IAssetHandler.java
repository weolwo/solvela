package solvela.ledger.handler;

import solvela.dispatch.DispatchOutcome;
import solvela.risk.ProposalRecord;

public interface IAssetHandler {
    DispatchOutcome dispatch(ProposalRecord proposal);
}
