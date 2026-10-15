package solvela.member.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.enums.MemberRiskModeEnum;
import solvela.member.MemberRiskRule;
import solvela.member.risk.config.RiskRuleRegistry;
import solvela.member.risk.dao.MemberRiskEventDao;
import solvela.member.risk.dao.MemberRiskHitDao;
import solvela.member.risk.dao.MemberRiskRuleDao;
import solvela.member.risk.domain.dto.MemberRiskEventDTO;
import solvela.member.risk.domain.dto.MemberRiskEventDetailDTO;
import solvela.member.risk.domain.dto.MemberRiskHitDTO;
import solvela.member.risk.domain.query.MemberRiskEventQuery;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台要看、要改风控的那些事：规则列表与改档位、决策流水与命中明细。
 *
 * <h3>规则的「逻辑在代码、参数在表」是本服务的全部前提</h3>
 * 所以这里<b>只允许改 mode / score / params</b>。编码、阶段、类型都是代码决定的 ——
 * 让后台改它们等于让人在页面上改代码，而改错的表现是启动失败或者规则静默失效。
 *
 * <h3>🔴 保存前一定要用规则自己的 validate 校验参数</h3>
 * 加载时与保存时走<b>同一个方法</b>。只在加载时校验的话，一个读不出来的参数能被存进去，
 * 然后那条规则在下一次缓存刷新时静默关闭 —— 页面上看着保存成功、列表里也有那一行。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRiskAdminService {

    /** 查流水时调用方没给时间范围，就兜这么长。理由见 {@link MemberRiskEventQuery} */
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

    private final MemberRiskRuleDao ruleDao;

    private final MemberRiskEventDao eventDao;

    private final MemberRiskHitDao hitDao;

    private final RiskRuleRegistry registry;

    // ------------------------------------------------------------------ 规则

    /** 规则列表，按 id 排（也就是建表时灌进去的顺序：前置在前、后置在后） */
    public List<MemberRiskRule> listRules() {
        return ruleDao.selectList(new LambdaQueryWrapper<MemberRiskRule>()
                .orderByAsc(MemberRiskRule::getId));
    }

    public MemberRiskRule getRule(Long id) {
        return ruleDao.selectById(id);
    }

    /**
     * 改一条规则的档位、分值、参数。
     *
     * @param params 新的参数 JSON；传 null 表示不动它
     * @return 改完之后的那一行
     */
    public MemberRiskRule updateRule(Long id, MemberRiskModeEnum mode, Integer score, String params) {
        MemberRiskRule exist = ruleDao.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("规则不存在: " + id);
        }
        if (params != null) {
            /*
             * 🔴 用这条规则【自己】的 validate 校验，而不是只看 JSON 语法。
             * 形状对不对只有规则自己知道（分级要 tiers 递增、矩阵不许配成拒绝、
             * 机房关键字不能为空……），而存进一个读不出来的参数等于把这条规则静默关掉。
             */
            registry.validateParams(exist.getRuleCode(), params);
        }

        MemberRiskRule update = new MemberRiskRule();
        update.setId(id);
        update.setMode(mode);
        update.setScore(score);
        ruleDao.updateById(update);
        if (params != null) {
            // params 要能被改成 NULL（清空参数用默认值），而 updateById 会跳过 null 字段
            ruleDao.update(null, new com.baomidou.mybatisplus.core.conditions.update
                    .LambdaUpdateWrapper<MemberRiskRule>()
                    .eq(MemberRiskRule::getId, id)
                    .set(MemberRiskRule::getParams, params.isBlank() ? null : params));
        }
        log.info("【风控规则】{} 已更新：档位 {} → {}", exist.getRuleCode(), exist.getMode(), mode);
        return ruleDao.selectById(id);
    }

    // ------------------------------------------------------------------ 决策流水

    public PageResult<MemberRiskEventDTO> queryEventPage(MemberRiskEventQuery query) {
        applyDefaultWindow(query);
        Page<?> page = SolvelaPageUtil.convert2PageQuery(query);
        List<MemberRiskEventDTO> list = eventDao.queryPage(page, query);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 一次尝试的全貌：结论 + 每条命中的规则与依据。
     *
     * <p>这是客服回答「为什么让我验证」时真正要看的那一页。
     *
     * @param createTime 🔴 必须传：表按月分区，只按 id 查不裁剪分区
     */
    public MemberRiskEventDetailDTO getEventDetail(Long id, LocalDateTime createTime) {
        MemberRiskEventDTO event = eventDao.selectOneAt(id, createTime);
        if (event == null) {
            return null;
        }
        List<MemberRiskHitDTO> hits = hitDao.selectByEvent(id, createTime);
        return new MemberRiskEventDetailDTO(event, hits);
    }

    /**
     * 没给时间范围就兜最近 7 天。
     *
     * <p>🔴 不是「不限时间」：表按月分区，不带 {@code create_time} 的查询在每个分区上
     * 各探一次索引。而且后台真正想看的几乎总是最近发生的事 ——
     * 要查更早的，筛选框里把时间往前拨。
     */
    private void applyDefaultWindow(MemberRiskEventQuery query) {
        if (query.getEndTime() == null) {
            query.setEndTime(LocalDateTime.now());
        }
        if (query.getStartTime() == null) {
            query.setStartTime(query.getEndTime().minus(DEFAULT_WINDOW));
        }
    }
}
