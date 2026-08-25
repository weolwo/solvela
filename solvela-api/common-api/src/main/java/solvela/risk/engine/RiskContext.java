package solvela.risk.engine;

import lombok.Data;
import solvela.risk.promotionconfig.domain.entity.PromotionConfig;
import solvela.risk.proposal.domain.form.ProposalRecordAddForm;

@Data
public class RiskContext {

    private final ProposalRecordAddForm request;
    private final PromotionConfig config;

    // 预留扩展：可以在前置过滤器里解析出用户画像标签，存放在这里供后置过滤器使用
    // private Map<String, Object> extAttributes = new HashMap<>();

    public RiskContext(ProposalRecordAddForm request, PromotionConfig config) {
        this.request = request;
        this.config = config;
    }
}
