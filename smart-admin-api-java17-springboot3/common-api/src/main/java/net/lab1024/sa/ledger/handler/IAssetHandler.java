package net.lab1024.sa.ledger.handler;

import net.lab1024.sa.risk.proposal.domain.entity.ProposalRecord;

public interface IAssetHandler {
    boolean dispatch(ProposalRecord proposal);
}
