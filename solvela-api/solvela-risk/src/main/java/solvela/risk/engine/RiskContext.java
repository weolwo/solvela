package solvela.risk.engine;

import lombok.Data;
import solvela.risk.PromotionConfig;
import solvela.risk.proposal.domain.command.ProposalRecordAddCommand;

@Data
public class RiskContext {

    private final ProposalRecordAddCommand request;
    private final PromotionConfig config;

    // 预留扩展：可以在前置过滤器里解析出用户画像标签，存放在这里供后置过滤器使用
    // private Map<String, Object> extAttributes = new HashMap<>();

    public RiskContext(ProposalRecordAddCommand request, PromotionConfig config) {
        this.request = request;
        this.config = config;
    }
}
