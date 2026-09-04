package solvela.lottery.config.service;

import solvela.enums.LotteryConfigStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.activity.ActivityConfig;
import solvela.activity.service.ActivityConfigService;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.enums.ActivityTypeEnum;
import solvela.lottery.config.dao.LotteryConfigDao;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.domain.command.FpePreviewCommand;
import solvela.lottery.config.domain.query.LotteryConfigQuery;
import solvela.lottery.config.domain.command.LotteryWorkbenchRuleCommand;
import solvela.lottery.config.domain.command.LotteryWorkbenchSaveCommand;
import solvela.lottery.config.domain.dto.LotteryConfigOptionDTO;
import solvela.lottery.config.domain.dto.LotteryConfigDTO;
import solvela.lottery.config.domain.dto.LotteryWorkbenchRuleDTO;
import solvela.lottery.config.domain.dto.LotteryWorkbenchDTO;
import solvela.lottery.config.manager.LotteryConfigManager;
import solvela.lottery.constant.LotteryConst;
import solvela.lottery.engine.FpeCipherFactory;
import solvela.lottery.engine.MatchRuleEnum;
import solvela.lottery.LotteryIssue;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.prizerule.dao.LotteryPrizeRuleDao;
import solvela.lottery.LotteryPrizeRule;
import solvela.lottery.prizerule.manager.LotteryPrizeRuleManager;
import solvela.prize.PrizeConfig;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 彩票配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LotteryConfigService {

    private final LotteryConfigDao lotteryConfigDao;
    private final LotteryConfigManager lotteryConfigManager;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryPrizeRuleDao lotteryPrizeRuleDao;
    private final LotteryPrizeRuleManager lotteryPrizeRuleManager;
    private final ActivityConfigService activityConfigService;
    private final PrizeConfigService prizeConfigService;
    private final FpeCipherFactory fpeCipherFactory;
    /**
     * 活动类型：彩票工作台只接受这一类活动。
     * 取值统一走 ActivityTypeEnum，不再在本类里裸写 "LOTTERY" 字符串（铁律 3：消除魔法值）。
     */
    private static final String ACTIVITY_TYPE_LOTTERY = ActivityTypeEnum.LOTTERY.getValue();

    // ==================== 编码 ====================

    /**
     * 生成一个未被占用的彩票编码（铁律 8：10 位大写字母+数字，全局唯一）
     */
    public String generateCode() {
        return SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.LOTTERY, this::existsByLotteryCode);
    }

    public boolean existsByLotteryCode(String lotteryCode) {
        return lotteryConfigManager.lambdaQuery().eq(LotteryConfig::getLotteryCode, lotteryCode).exists();
    }

    public LotteryConfig getByLotteryCode(String lotteryCode) {
        if (StringUtils.isBlank(lotteryCode)) {
            return null;
        }
        return lotteryConfigManager.lambdaQuery().eq(LotteryConfig::getLotteryCode, lotteryCode).one();
    }

    /**
     * 按活动查其下的彩票玩法（一个活动可以有多个玩法，如「5位数字号」「7位数字号」并存）
     */
    public List<LotteryConfig> queryByActivityCode(String activityCode) {
        if (StringUtils.isBlank(activityCode)) {
            return List.of();
        }
        return lotteryConfigManager.lambdaQuery()
                .eq(LotteryConfig::getActivityCode, activityCode)
                .orderByAsc(LotteryConfig::getId).list();
    }

    // ==================== 下拉 ====================

    /**
     * 工作台顶部「当前玩法」下拉，按活动过滤。
     *
     * 活动是容器，一个活动下可以并存多个玩法（不同的号码长度/发行量就是不同的玩法），
     * 所以顶部是「活动 + 玩法」两级下拉，玩法列表随活动切换而变。
     */
    public List<LotteryConfigOptionDTO> optionList(String activityCode) {
        return queryByActivityCode(activityCode).stream()
                .map(config -> new LotteryConfigOptionDTO(
                        config.getLotteryCode(),
                        config.getLotteryName(),
                        config.getActivityCode(),
                        config.getNumberLength(),
                        config.getTotalCount(),
                        config.getStatus()))
                .toList();
    }

    // ==================== 工作台：聚合回显 ====================

    /**
     * 彩票工作台聚合回显：与 {@link #workbenchSave} 的入参同构，前端拿到即可直接填回表单。
     *
     * <p>顶部是「活动 + 玩法」两级：activityCode 决定资产大库与玩法下拉的范围，
     * lotteryCode 决定具体加载哪一个玩法。lotteryCode 为空表示「在该活动下新建玩法」，
     * 返回一个带预生成编码的空壳而非报错，前端据此进入「从零配置」态。
     */
    public LotteryWorkbenchDTO workbenchDetail(String activityCode, String lotteryCode) {
        ActivityConfig activity = requireLotteryActivity(activityCode);

        LotteryConfig config = getByLotteryCode(lotteryCode);
        if (config == null) {
            return blankWorkbench(activity);
        }
        // 前端传来的两个参数必须自洽，否则会出现「顶部显示活动A、内容却是活动B的玩法」这种错位
        if (!activityCode.equals(config.getActivityCode())) {
            throw new BusinessException("玩法 " + lotteryCode + " 不属于活动 " + activityCode);
        }

        List<LotteryIssue> issueList = lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, config.getLotteryCode()).list();
        long soldTotal = soldTotalOf(issueList);
        // 结构锁：一旦发过号，发号引擎参数就永久冻结
        String lockReason = resolveLockReason(config, soldTotal);

        return new LotteryWorkbenchDTO(
                config.getActivityCode(),
                activity.getActivityName(),
                config.getLotteryCode(),
                config.getLotteryName(),
                config.getNumberLength(),
                config.getTotalCount(),
                config.getStatus(),
                true,
                lockReason != null,
                lockReason,
                issueList.size(),
                soldTotal,
                ruleTab(config));
    }

    /** 新建玩法态：活动信息已知，预填一个可用编码，运营可直接用也可重新生成 */
    private LotteryWorkbenchDTO blankWorkbench(ActivityConfig activity) {
        return new LotteryWorkbenchDTO(
                activity.getActivityCode(), activity.getActivityName(),
                SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.LOTTERY, this::existsByLotteryCode),
                null, null, null, null,
                false, false, null, 0L, 0L, List.of());
    }

    /**
     * 奖级规则。规则表只存 prize_code，名称/类型/价值回查资产大库补齐（SKU 化，与抽奖工作台一致）。
     */
    private List<LotteryWorkbenchRuleDTO> ruleTab(LotteryConfig config) {
        List<LotteryPrizeRule> ruleList = lotteryPrizeRuleManager.lambdaQuery()
                .eq(LotteryPrizeRule::getLotteryCode, config.getLotteryCode())
                .orderByAsc(LotteryPrizeRule::getPrizeLevel).list();
        Map<String, PrizeConfig> prizeMap = prizeConfigService.queryListByActivityCode(config.getActivityCode())
                .stream().collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (first, ignored) -> first));

        return ruleList.stream().map(rule -> {
            PrizeConfig prize = prizeMap.get(rule.getPrizeCode());
            return new LotteryWorkbenchRuleDTO(
                    rule.getPrizeLevel(),
                    rule.getMatchRule(),
                    rule.getMatchLength(),
                    rule.getPrizeCode(),
                    // 奖品被删时回退成编码，让运营看得见「这一奖级绑的奖没了」，而不是渲染出空白行
                    prize == null ? rule.getPrizeCode() : prize.getPrizeName(),
                    prize == null ? null : prize.getPrizeType(),
                    prize == null ? null : prize.getPrizeValue());
        }).toList();
    }

    private long soldTotalOf(List<LotteryIssue> issueList) {
        return issueList.stream().mapToLong(issue -> issue.getSoldCount() == null ? 0 : issue.getSoldCount()).sum();
    }

    /**
     * 结构冻结的判定与人话文案，返回 null 表示未冻结。
     *
     * <p>两个触发条件的严重程度不同，文案要分开写，否则运营看到「禁止修改」只会来问为什么：
     * 已发过号是<b>密码学层面的不可逆</b>（改了历史号码就没法验证了），上线只是流程管控。
     */
    private String resolveLockReason(LotteryConfig config, long soldTotal) {
        if (soldTotal > 0) {
            return "已发出 " + soldTotal + " 个号码，发号引擎参数（号码长度、发售上限）永久冻结。"
                    + "号码是由游标加密得来的，改参数会让已发号码无法验证、且新号码可能与历史重复。"
                    + "如需不同的发行规格，请新建一个彩票玩法。";
        }
        if (config.getStatus() == LotteryConfigStatusEnum.ONLINE) {
            return "玩法已上线，发号引擎参数不可修改。如需调整请先下线。";
        }
        return null;
    }

    // ==================== 工作台：聚合保存 ====================

    /**
     * 彩票工作台聚合保存：{@code t_lottery_config} + {@code t_lottery_prize_rule} 同一事务。
     *
     * <p>前端的所有校验都只是 UI 防呆，下面每一条服务端重算一遍（铁律 2）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void workbenchSave(LotteryWorkbenchSaveCommand form) {
        requireLotteryActivity(form.getActivityCode());

        LotteryConfig existed = getByLotteryCode(form.getLotteryCode());
        checkCodeOwnership(form, existed);
        checkNumberSpace(form);
        checkStructureLock(form, existed);

        List<LotteryWorkbenchRuleCommand> ruleList =
                form.getPrizeRuleList() == null ? List.of() : form.getPrizeRuleList();
        checkRules(ruleList, form);

        saveConfig(form, existed);
        rebuildRules(form, ruleList);
    }

    private ActivityConfig requireLotteryActivity(String activityCode) {
        ActivityConfig activity = activityConfigService.getByActivityCode(activityCode);
        if (activity == null) {
            throw new BusinessException("活动不存在：" + activityCode);
        }
        if (!ACTIVITY_TYPE_LOTTERY.equals(activity.getActivityType())) {
            throw new BusinessException("活动「" + activity.getActivityName() + "」不是彩票类活动");
        }
        return activity;
    }

    /**
     * 编码格式由 {@code @Pattern} 拦，这里补唯一性判重（铁律 8 落点清单第 ② 项）。
     *
     * <p>一个活动可以有多个玩法，但编码是全局唯一的（{@code uk_lottery_code}）：
     * 手输的编码可能撞上别的活动的玩法，提前给人话提示而不是抛 SQL 异常。
     */
    private void checkCodeOwnership(LotteryWorkbenchSaveCommand form, LotteryConfig existed) {
        if (existed != null && !Objects.equals(existed.getActivityCode(), form.getActivityCode())) {
            throw new BusinessException("彩票编码 " + form.getLotteryCode()
                    + " 已被活动 " + existed.getActivityCode() + " 占用，请重新生成");
        }
    }

    /**
     * 号码空间必须装得下发售量。
     *
     * <p>{@code 10^length} 与 totalCount 都是 int 存储，用 long 比较防溢出 ——
     * 9 位号码的空间是 10 亿，int 还装得下，10 位就不行了。
     */
    private void checkNumberSpace(LotteryWorkbenchSaveCommand form) {
        long domain = (long) Math.pow(10, form.getNumberLength());
        if (form.getTotalCount() > domain) {
            throw new BusinessException("单期发售上限 " + form.getTotalCount()
                    + " 超过了 " + form.getNumberLength() + " 位号码的空间上限 " + domain + "，请增加号码长度或降低发售量");
        }
    }

    /**
     * 结构锁：冻结之后<b>只有发号引擎参数不能动</b>，玩法名与奖级规则照旧可以改。
     *
     * <p>所以这里不是「冻结了就整个保存拒绝」，而是先看这次提交有没有真的动那两个字段 ——
     * 否则运营连改个错别字都得先下线。
     */
    private void checkStructureLock(LotteryWorkbenchSaveCommand form, LotteryConfig existed) {
        if (existed == null) {
            return;
        }
        String lockReason = resolveLockReason(existed, soldTotalOf(lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, form.getLotteryCode()).list()));
        if (lockReason == null) {
            return;
        }
        boolean lengthChanged = !Objects.equals(existed.getNumberLength(), form.getNumberLength());
        boolean totalChanged = !Objects.equals(existed.getTotalCount(), form.getTotalCount());
        if (lengthChanged || totalChanged) {
            throw new BusinessException(lockReason);
        }
    }

    /**
     * 彩票配置 upsert。
     *
     * <p>两个字段刻意<b>不接受前端值</b>：字符集固定十进制；status 走独立的上下线接口 ——
     * 让保存配置顺带把玩法推上线（或改回下线）是最容易出事的那种「顺手」。
     */
    private void saveConfig(LotteryWorkbenchSaveCommand form, LotteryConfig existed) {
        if (existed == null) {
            LotteryConfig entity = new LotteryConfig();
            entity.setActivityCode(form.getActivityCode());
            entity.setLotteryCode(form.getLotteryCode());
            entity.setLotteryName(form.getLotteryName());
            entity.setNumberLength(form.getNumberLength());
            entity.setTotalCount(form.getTotalCount());
            entity.setNumberCharset(LotteryConst.NUMBER_CHARSET);
            // 新建默认下线，上线是独立的动作
            entity.setStatus(LotteryConfigStatusEnum.OFFLINE);
            lotteryConfigDao.insert(entity);
            return;
        }
        LotteryConfig update = new LotteryConfig();
        update.setId(existed.getId());
        update.setLotteryName(form.getLotteryName());
        update.setNumberLength(form.getNumberLength());
        update.setTotalCount(form.getTotalCount());
        update.setNumberCharset(LotteryConst.NUMBER_CHARSET);
        lotteryConfigDao.updateById(update);
    }

    /**
     * 奖级规则<b>整表重建</b>（子表整体替换语义，与抽奖工作台的坑位映射一致）。
     */
    private void rebuildRules(LotteryWorkbenchSaveCommand form, List<LotteryWorkbenchRuleCommand> ruleList) {
        lotteryPrizeRuleManager.lambdaUpdate()
                .eq(LotteryPrizeRule::getLotteryCode, form.getLotteryCode()).remove();
        for (LotteryWorkbenchRuleCommand rule : ruleList) {
            MatchRuleEnum matchRule = MatchRuleEnum.resolve(rule.getMatchRule());

            LotteryPrizeRule entity = new LotteryPrizeRule();
            entity.setLotteryCode(form.getLotteryCode());
            entity.setPrizeLevel(rule.getPrizeLevel());
            entity.setMatchRule(matchRule.getValue());
            // EXACT 的匹配长度恒等于号码长度：前端传什么都不算数，服务端归一，
            // 避免存进去一个自相矛盾的值
            entity.setMatchLength(matchRule == MatchRuleEnum.EXACT ? form.getNumberLength() : rule.getMatchLength());
            entity.setPrizeCode(rule.getPrizeCode());
            lotteryPrizeRuleDao.insert(entity);
        }
    }

    /**
     * 奖级规则逐条校验。五条里每一条都对应一种「配得下去、开奖时才出事」的形态。
     */
    private void checkRules(List<LotteryWorkbenchRuleCommand> ruleList, LotteryWorkbenchSaveCommand form) {
        Set<Integer> levels = new HashSet<>();
        Set<String> prizeCodes = new HashSet<>();
        for (LotteryWorkbenchRuleCommand rule : ruleList) {
            if (!levels.add(rule.getPrizeLevel())) {
                throw new BusinessException("奖级重复：" + rule.getPrizeLevel() + " 级配置了多条规则");
            }
            if (rule.getPrizeLevel() == LotteryConst.PRIZE_LEVEL_NONE) {
                throw new BusinessException("奖级不能设为 " + LotteryConst.PRIZE_LEVEL_NONE
                        + "：该值被「未中奖」占用，否则用户端无法区分未中奖与 " + LotteryConst.PRIZE_LEVEL_NONE + " 等奖");
            }
            // 从 DB/前端拿到的字符串一律显式转枚举，不做 map 直查（本项目已复发三次的缺陷模式）
            MatchRuleEnum matchRule = MatchRuleEnum.resolve(rule.getMatchRule());
            if (matchRule == null) {
                throw new BusinessException("奖级 " + rule.getPrizeLevel() + " 的匹配规则非法：" + rule.getMatchRule());
            }
            if (matchRule != MatchRuleEnum.EXACT && rule.getMatchLength() > form.getNumberLength()) {
                throw new BusinessException("奖级 " + rule.getPrizeLevel() + " 的匹配长度 " + rule.getMatchLength()
                        + " 超过号码长度 " + form.getNumberLength() + "，这条规则永远不可能命中");
            }
            // 同一个奖品被两个奖级引用，中奖后无法判断该按哪一级的口径统计，直接拦掉
            if (!prizeCodes.add(rule.getPrizeCode())) {
                throw new BusinessException("奖品 " + rule.getPrizeCode() + " 被多个奖级重复绑定");
            }
            if (prizeConfigService.getByActivityCodeAndPrizeCode(form.getActivityCode(), rule.getPrizeCode()) == null) {
                throw new BusinessException("奖级 " + rule.getPrizeLevel()
                        + " 绑定的奖品不存在于本活动的资产大库：" + rule.getPrizeCode());
            }
        }
    }

    // ==================== 上下线 ====================

    /**
     * 上线：允许开始发号。
     *
     * 上线前必须已配置奖级规则 —— 否则号码发出去了、开奖时却无奖可发，
     * 而那时号码已经在用户手里，配置补救的代价远高于此刻拦一下。
     *
     * 用条件更新做并发闸门（WHERE status = 0）：两个运营同时点，第二次 rows=0，
     * 不会出现「都以为自己上线成功」的假象。
     */
    public void online(String lotteryCode) {
        LotteryConfig config = getByLotteryCode(lotteryCode);
        if (config == null) {
            throw new BusinessException("彩票玩法不存在：" + lotteryCode);
        }
        if (config.getStatus() == LotteryConfigStatusEnum.ONLINE) {
            throw new BusinessException("该玩法已经是上线状态");
        }
        String notReady = checkOnlineReady(lotteryCode);
        if (notReady != null) {
            throw new BusinessException(notReady);
        }
        int rows = lotteryConfigDao.updateStatus(config.getId(), LotteryConfigStatusEnum.OFFLINE, LotteryConfigStatusEnum.ONLINE);
        if (rows == 0) {
            throw new BusinessException("上线失败：状态已被其他人变更，请刷新后重试");
        }
    }

    /**
     * 上线前检查：返回 null 表示可以上线，非 null 是「还差什么」的人话说明。
     *
     * 抽成独立方法是为了让 {@link #online(String)} 与「活动是否已配置完备」的统计
     * （LotteryActivityRefProvider）<b>共用同一份判据</b>。
     * 若两处各写一套，迟早漂移 —— 本项目已经吃过这种亏（TicketMatcher 与结算 SQL 守卫是
     * 同一规则的两种实现，SettleSemanticsTest 就是专门防它们漂移的）。
     *
     * 目前唯一的条件是「必须配了奖级规则」：否则号码发出去了、开奖时却无奖可发，
     * 而那时号码已经在用户手里，配置补救的代价远高于此刻拦一下。
     */
    public String checkOnlineReady(String lotteryCode) {
        boolean hasRule = lotteryPrizeRuleManager.lambdaQuery()
                .eq(LotteryPrizeRule::getLotteryCode, lotteryCode).exists();
        if (!hasRule) {
            return "尚未配置任何奖级规则，上线后号码发出去了却无奖可发。请先在「引擎配置与奖级映射」配置奖级";
        }
        return null;
    }

    /**
     * 下线：停止发号。已发出的号码不受影响，期号照常可以开奖。
     *
     * 下线不做「已发过号就不许下线」的限制 —— 恰恰相反，出问题时能立刻止血地停止发号，
     * 是运营最需要的能力。下线只影响后续领号，不动任何已有数据。
     */
    public void offline(String lotteryCode) {
        LotteryConfig config = getByLotteryCode(lotteryCode);
        if (config == null) {
            throw new BusinessException("彩票玩法不存在：" + lotteryCode);
        }
        if (config.getStatus() == LotteryConfigStatusEnum.OFFLINE) {
            throw new BusinessException("该玩法已经是下线状态");
        }
        int rows = lotteryConfigDao.updateStatus(config.getId(), LotteryConfigStatusEnum.ONLINE, LotteryConfigStatusEnum.OFFLINE);
        if (rows == 0) {
            throw new BusinessException("下线失败：状态已被其他人变更，请刷新后重试");
        }
    }

    // ==================== FPE 推演台 ====================

    /**
     * 配置态推演：用固定演示期号算一个样例号码。
     *
     * 与线上发号<b>共用同一个 FpeCipher 与同一套密钥派生</b>，所见即所得；
     * 但 tweak 用的是演示期号，所以结果只代表「号码长什么样」，不等于某一期的真实号码。
     */
    public String fpePreview(FpePreviewCommand form) {
        long domain = (long) Math.pow(10, form.getNumberLength());
        if (form.getSequenceNo() > domain) {
            throw new BusinessException("游标 " + form.getSequenceNo()
                    + " 超过 " + form.getNumberLength() + " 位号码的空间上限 " + domain);
        }
        // 入参是 1-indexed（运营心智：第 1 个领号的人），FPE 定义域是 [0, N)，此处减 1
        long sequenceNo = form.getSequenceNo() - 1;
        return fpeCipherFactory
                .createForPreview(form.getLotteryCode(), form.getNumberLength())
                .encrypt(sequenceNo);
    }

    /**
     * 批量下线（列表页的「批量禁用」）。
     *
     * <p>刻意<b>不</b>做成一个事务里全成或全败：批量下线是止血动作，
     * 选中的 10 个里有 1 个已经是下线态，不该把另外 9 个的止血一起回滚掉。
     * 逐个走 {@link #offline(String)}，复用它的条件更新闸门，最后回一句人话汇总。
     *
     * <p>「已是下线」不算失败，计入跳过 —— 运营框选一批时本来就分不清哪些已经下线了。
     */
    public String batchOffline(List<String> lotteryCodeList) {
        if (SolvelaCollectionUtil.isEmpty(lotteryCodeList)) {
            return "没有需要禁用的玩法";
        }
        int success = 0;
        int skipped = 0;
        List<String> failed = new ArrayList<>();
        for (String lotteryCode : lotteryCodeList) {
            LotteryConfig config = getByLotteryCode(lotteryCode);
            if (config == null) {
                failed.add(lotteryCode + "（玩法不存在）");
                continue;
            }
            if (config.getStatus() == LotteryConfigStatusEnum.OFFLINE) {
                skipped++;
                continue;
            }
            try {
                offline(lotteryCode);
                success++;
            } catch (BusinessException e) {
                failed.add(lotteryCode + "（" + e.getMessage() + "）");
            }
        }
        String summary = "已禁用 " + success + " 个玩法"
                + (skipped > 0 ? "，跳过 " + skipped + " 个（本就是下线态）" : "")
                + (failed.isEmpty() ? "" : "，失败 " + failed.size() + " 个：" + String.join("、", failed));
        // 有失败也正常返回、不抛：成功的那部分已经落库了，抛异常会让运营以为一个都没生效
        return summary;
    }

    // ==================== 生成器产出的 CRUD ====================

    /**
     * 分页查询
     */
    public PageResult<LotteryConfigDTO> queryPage(LotteryConfigQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<LotteryConfigDTO> list = lotteryConfigDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }
}
