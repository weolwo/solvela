package solvela.member.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberRiskListDimensionEnum;
import solvela.enums.MemberRiskListStatusEnum;
import solvela.enums.MemberRiskListTypeEnum;
import solvela.enums.MemberRiskModeEnum;
import solvela.member.MemberRiskList;
import solvela.member.MemberRiskRule;
import solvela.member.risk.config.IpRange;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.config.RiskRuleRegistry;
import solvela.member.risk.dao.MemberRiskEventDao;
import solvela.member.risk.dao.MemberRiskHitDao;
import solvela.member.risk.dao.MemberRiskListDao;
import solvela.member.risk.dao.MemberRiskRuleDao;
import solvela.member.risk.domain.dto.MemberRiskEventDTO;
import solvela.member.risk.domain.dto.MemberRiskEventDetailDTO;
import solvela.member.risk.domain.dto.MemberRiskHitDTO;
import solvela.member.risk.domain.dto.MemberRiskStatDTO;
import solvela.member.risk.domain.MemberRiskListAddCmd;
import solvela.member.risk.domain.query.MemberRiskEventQuery;
import solvela.member.risk.domain.query.MemberRiskListQuery;
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
 * <h3>名单：撤销不删行</h3>
 * 「谁加的、谁解的、为什么」都要查得到。删行的话，下一个人看到的是
 * 「这个 IP 从来没被封过」—— 而他正要判断「上次是不是误封」。
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

    private final MemberRiskListDao listDao;

    private final solvela.member.risk.dao.MemberRiskStatDao statDao;

    private final RiskListCache listCache;



    // ------------------------------------------------------------------ 看板

    /** 看板的默认窗口。比流水那个长：趋势看一周看不出趋势 */
    private static final Duration DEFAULT_STAT_WINDOW = Duration.ofDays(30);

    /** 看板一次最多看多久。再长就该导出离线算了 —— 聚合一个季度会把分区全扫一遍 */
    private static final Duration MAX_STAT_WINDOW = Duration.ofDays(90);

    /**
     * 看板。四组数一次取回 —— 它们共用同一个时间窗，分成四个接口只会让前端有机会
     * 用四个不同的窗去问，然后把对不上的数摆在同一个屏幕上。
     *
     * @param from 不传兜最近 30 天
     * @param to   不传到现在
     */
    public MemberRiskStatDTO stat(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime begin = from == null ? end.minus(DEFAULT_STAT_WINDOW) : from;
        if (begin.isAfter(end)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        if (Duration.between(begin, end).compareTo(MAX_STAT_WINDOW) > 0) {
            // 🔴 当场拒绝，不是悄悄截断：截断的话页面上那个「最近一年」的筛选器
            // 会给出一个只含 90 天的数，而没人知道
            throw new IllegalArgumentException("一次最多看 "
                    + MAX_STAT_WINDOW.toDays() + " 天 —— 更长的区间请导出离线统计");
        }

        MemberRiskStatDTO stat = new MemberRiskStatDTO();
        stat.setTotalCount(statDao.countAttempts(begin, end));
        stat.setDecisionDaily(statDao.decisionDaily(begin, end));
        stat.setMatrixCells(statDao.matrixCells(begin, end));
        stat.setOtpFunnel(new MemberRiskStatDTO.OtpFunnel(
                statDao.countChallenged(begin, end), statDao.countVerified(begin, end)));
        stat.setRuleHits(statDao.ruleHits(begin, end));
        stat.setShadowDiffCount(statDao.countShadowDiff(begin, end));
        return stat;
    }

    // ------------------------------------------------------------------ 名单

    /** 新增时默认的有效期。IP 会被回收重新分配 —— 永久黑名单迟早误伤一个无辜的新住户 */
    private static final Duration DEFAULT_LIST_TTL = Duration.ofDays(30);

    /** 身份摘要是 {@code PiiHasher} 的输出：64 位 hex */
    private static final int IDENTITY_HASH_HEX_LENGTH = 64;

    /**
     * 名单分页。不传状态时<b>只看有效的</b> —— 客服日常要的是「现在谁被封着」，
     * 而历史（含已撤销）是另一个问题，得显式问。
     */
    public PageResult<MemberRiskList> queryListPage(MemberRiskListQuery query) {
        Page<MemberRiskList> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<MemberRiskList> wrapper = new LambdaQueryWrapper<MemberRiskList>()
                .eq(query.getListType() != null, MemberRiskList::getListType, query.getListType())
                .eq(query.getDimension() != null, MemberRiskList::getDimension, query.getDimension())
                .eq(SolvelaStringUtil.isNotBlank(query.getListValue()),
                        MemberRiskList::getListValue, query.getListValue())
                .eq(MemberRiskList::getStatus,
                        query.getStatus() == null ? MemberRiskListStatusEnum.VALID : query.getStatus())
                .orderByDesc(MemberRiskList::getId);
        // 单表查询走 BaseMapper.selectPage，所以这里自己造 Page（SolvelaPageUtil 那个返回 Page<?>，
        // 泛型对不上 selectPage 的签名），再用同一个工具把结果裹成 PageResult
        listDao.selectPage(page, wrapper);
        return SolvelaPageUtil.convert2PageResult(page, page.getRecords());
    }

    /**
     * 新增一条。
     *
     * <h3>🔴 校验在这里，不在表单上</h3>
     * 「灰名单必须有分、黑白名单必须没分」「会员白名单必须填到期」这类规则是
     * <b>名单自己的语义</b>，不是某个页面的输入约束。放在表单注解上的话，
     * 下一个调用方（客服工具、脚本）绕过去就能写出一条读出来会炸的名单。
     *
     * <h3>🔴 值要当场解析一遍</h3>
     * IP / CIDR 写错的表现是「加了黑名单但永远不命中」—— 而那在页面上看不出来，
     * 列表里那一行好端端地躺着。所以这里拿缓存用的同一个解析器
     * （{@link IpRange}）先试一次，解不出来直接拒绝。
     *
     * @return 新增后的那一行
     */
    public MemberRiskList addList(MemberRiskListAddCmd cmd) {
        if (cmd.getListType() == null || cmd.getDimension() == null) {
            throw new IllegalArgumentException("名单类型与维度必填");
        }
        if (SolvelaStringUtil.isBlank(cmd.getReason())) {
            throw new IllegalArgumentException("必须写明为什么进名单 —— 这句话是给下一个看到它的人的");
        }
        String value = normalizeListValue(cmd.getDimension(), cmd.getListValue());
        validateScore(cmd.getListType(), cmd.getScore());
        LocalDateTime expireTime = resolveExpireTime(cmd);

        if (cmd.getDimension() == MemberRiskListDimensionEnum.MEMBER
                && cmd.getListType() != MemberRiskListTypeEnum.WHITE) {
            /*
             * 🔴 会员只允许白名单。黑名单那一份由 t_member.status（冻结）管 ——
             * 名单里再放一份就有了两个答案，而「这个人到底能不能用」只该有一个。
             */
            throw new IllegalArgumentException("会员维度只允许白名单：封号请用会员状态（冻结）");
        }

        MemberRiskList row = new MemberRiskList();
        row.setListType(cmd.getListType());
        row.setDimension(cmd.getDimension());
        row.setListValue(value);
        row.setScore(cmd.getScore());
        row.setReason(cmd.getReason());
        row.setSource(1);
        row.setStatus(MemberRiskListStatusEnum.VALID);
        row.setExpireTime(expireTime);
        row.setOperator(cmd.getOperator());
        listDao.insert(row);
        listCache.reload();
        log.info("【风控名单】新增, 类型: {}, 维度: {}, 值: {}, 到期: {}, 操作人: {}",
                cmd.getListType(), cmd.getDimension(), value, expireTime, cmd.getOperator());
        return row;
    }

    /**
     * 撤销。<b>不删行</b>，记下撤销人、原因、时间。
     *
     * @return false 表示这一行不存在或本来就已撤销（幂等，连点两次不会覆盖第一次记的人）
     */
    public boolean revokeList(Long id, String operator, String reason) {
        if (id == null || SolvelaStringUtil.isBlank(reason)) {
            throw new IllegalArgumentException("撤销必须写原因 —— 「上次为什么解的」和「为什么加的」一样重要");
        }
        MemberRiskList row = listDao.selectById(id);
        if (row == null || row.getStatus() == MemberRiskListStatusEnum.REVOKED) {
            return false;
        }
        MemberRiskList update = new MemberRiskList();
        update.setId(id);
        update.setStatus(MemberRiskListStatusEnum.REVOKED);
        update.setRevokeOperator(operator);
        update.setRevokeReason(reason);
        update.setRevokeTime(LocalDateTime.now());
        listDao.updateById(update);
        listCache.reload();
        log.info("【风控名单】撤销, id: {}, 操作人: {}, 原因: {}", id, operator, reason);
        return true;
    }

    /** 各维度的值形状。解不出来当场拒绝，而不是留一行「永远不命中」的名单 */
    private static String normalizeListValue(MemberRiskListDimensionEnum dimension, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("名单值必填");
        }
        switch (dimension) {
            case IP, IP_RANGE -> {
                // 用缓存命中时的同一个解析器：这里解得出来，那里就一定匹配得上
                if (IpRange.parse(value) == null) {
                    throw new IllegalArgumentException("IP 或网段格式不对: " + value);
                }
            }
            case IDENTITY -> {
                if (value.length() != IDENTITY_HASH_HEX_LENGTH
                        || !value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                    throw new IllegalArgumentException("身份摘要必须是 64 位十六进制（由接入层对明文算出）");
                }
                // 库里统一存大写，免得同一个摘要因为大小写进了两行
                return value.toUpperCase(java.util.Locale.ROOT);
            }
            case MEMBER -> {
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("会员维度的值必须是会员号: " + value);
                }
            }
        }
        return value;
    }

    private static void validateScore(MemberRiskListTypeEnum listType, Integer score) {
        if (listType == MemberRiskListTypeEnum.GREY) {
            if (score == null || score <= 0) {
                throw new IllegalArgumentException("灰名单必须给分 —— 不给分的灰名单命中了也什么都不会发生");
            }
            return;
        }
        if (score != null) {
            throw new IllegalArgumentException("只有灰名单有分：黑名单直接拒绝，白名单直接放宽，分数在这两档上没有意义");
        }
    }

    /**
     * 到期时间。
     *
     * <h3>🔴 会员白名单必须有到期</h3>
     * 它的典型用途是应用市场审核账号、压测账号 —— 都是「这一阵子」的事。
     * 长期白名单会在没人记得的时候继续生效，而白名单上的账号恰恰是最值得猜的。
     */
    private static LocalDateTime resolveExpireTime(MemberRiskListAddCmd cmd) {
        if (cmd.getExpireTime() != null) {
            if (cmd.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("到期时间不能是过去 —— 那条名单加完立刻就失效了");
            }
            return cmd.getExpireTime();
        }
        if (cmd.getDimension() == MemberRiskListDimensionEnum.MEMBER) {
            throw new IllegalArgumentException("会员白名单必须填到期时间，不允许长期");
        }
        return LocalDateTime.now().plus(DEFAULT_LIST_TTL);
    }

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
