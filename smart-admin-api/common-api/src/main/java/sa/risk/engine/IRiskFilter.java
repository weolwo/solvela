package sa.risk.engine;

public interface IRiskFilter {

    /**
     * 执行风控校验
     */
    RiskResult doFilter(RiskContext context);

    /**
     * 排序权重（越小越先执行）
     */
    int getOrder();
}
