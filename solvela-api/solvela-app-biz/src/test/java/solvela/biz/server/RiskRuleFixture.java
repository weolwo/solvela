package solvela.biz.server;

import solvela.enums.MemberRiskModeEnum;
import solvela.member.MemberRiskRule;
import solvela.member.risk.dao.MemberRiskRuleDao;

import java.util.ArrayList;
import java.util.List;

/**
 * 集成测试里临时改风控规则，用完原样还回去。
 *
 * <h3>为什么需要它</h3>
 * 阈值、档位、开关从配置项搬进了 {@code t_member_risk_rule} —— 那是改造的目的之一
 * （后台可改、不发版）。代价是集成测试不能再靠 {@code @TestPropertySource} 调阈值了：
 * 调了也没人读。所以要能改表。
 *
 * <h3>🔴 两个约定，不守住就会污染别的用例</h3>
 * <ul>
 *   <li><b>一定要 restore</b>：这些测试打的是真实开发库，改坏一行规则会让之后所有用例莫名其妙；
 *       所以本类是 {@link AutoCloseable}，用 try-with-resources；</li>
 *   <li><b>测试类要把缓存刷新间隔设成 0</b>（{@code solvela.member.risk.cache-refresh=0s}），
 *       否则改完表最多要等 30 秒才生效，而用例立刻就跑完了。</li>
 * </ul>
 *
 * @Date 2026-10-15
 */
final class RiskRuleFixture implements AutoCloseable {

    private final MemberRiskRuleDao ruleDao;

    private final List<MemberRiskRule> original = new ArrayList<>();

    RiskRuleFixture(MemberRiskRuleDao ruleDao) {
        this.ruleDao = ruleDao;
    }

    /** 把这条规则的档位改掉 */
    RiskRuleFixture mode(String ruleCode, MemberRiskModeEnum mode) {
        MemberRiskRule row = remember(ruleCode);
        MemberRiskRule update = new MemberRiskRule();
        update.setId(row.getId());
        update.setMode(mode);
        ruleDao.updateById(update);
        return this;
    }

    /** 把这条规则的 params 换成给定的 JSON */
    RiskRuleFixture params(String ruleCode, String paramsJson) {
        MemberRiskRule row = remember(ruleCode);
        MemberRiskRule update = new MemberRiskRule();
        update.setId(row.getId());
        update.setParams(paramsJson);
        ruleDao.updateById(update);
        return this;
    }

    /** 关掉这几条规则 —— 想单独看某一条的行为时，把会抢先命中的那些让开 */
    RiskRuleFixture off(String... ruleCodes) {
        for (String code : ruleCodes) {
            mode(code, MemberRiskModeEnum.OFF);
        }
        return this;
    }

    private MemberRiskRule remember(String ruleCode) {
        MemberRiskRule row = ruleDao.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<MemberRiskRule>().eq(MemberRiskRule::getRuleCode, ruleCode));
        if (row == null) {
            throw new IllegalStateException("规则表里没有 " + ruleCode
                    + " —— 种子数据没灌全？见 数据库SQL脚本/mysql/data-baseline.sql");
        }
        // 只记第一次的原值：同一条规则被改两次时，还回去的要是最初那一份
        if (original.stream().noneMatch(r -> r.getId().equals(row.getId()))) {
            original.add(row);
        }
        return row;
    }

    @Override
    public void close() {
        for (MemberRiskRule row : original) {
            MemberRiskRule restore = new MemberRiskRule();
            restore.setId(row.getId());
            restore.setMode(row.getMode());
            // params 为 null 的规则要能还成 null，所以不能用 updateById（它跳过 null 字段）
            restore.setParams(row.getParams());
            ruleDao.update(restore, new com.baomidou.mybatisplus.core.conditions.update
                    .LambdaUpdateWrapper<MemberRiskRule>()
                    .eq(MemberRiskRule::getId, row.getId())
                    .set(MemberRiskRule::getParams, row.getParams()));
        }
    }
}
