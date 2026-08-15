package net.lab1024.sa.lottery.config.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.activity.domain.entity.ActivityConfig;
import net.lab1024.sa.activity.service.ActivityConfigService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartCodeUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.enums.ActivityTypeEnum;
import net.lab1024.sa.lottery.config.dao.LotteryConfigDao;
import net.lab1024.sa.lottery.config.domain.entity.LotteryConfig;
import net.lab1024.sa.lottery.config.domain.form.FpePreviewForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigQueryForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryWorkbenchRuleForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryWorkbenchSaveForm;
import net.lab1024.sa.lottery.config.domain.vo.LotteryConfigOptionVO;
import net.lab1024.sa.lottery.config.domain.vo.LotteryConfigVO;
import net.lab1024.sa.lottery.config.domain.vo.LotteryWorkbenchRuleVO;
import net.lab1024.sa.lottery.config.domain.vo.LotteryWorkbenchVO;
import net.lab1024.sa.lottery.config.manager.LotteryConfigManager;
import net.lab1024.sa.lottery.constant.LotteryConst;
import net.lab1024.sa.lottery.engine.FpeCipherFactory;
import net.lab1024.sa.lottery.engine.MatchRuleEnum;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.manager.LotteryIssueManager;
import net.lab1024.sa.lottery.prizerule.dao.LotteryPrizeRuleDao;
import net.lab1024.sa.lottery.prizerule.domain.entity.LotteryPrizeRule;
import net.lab1024.sa.lottery.prizerule.manager.LotteryPrizeRuleManager;
import net.lab1024.sa.prize.prizeconfig.domain.entity.PrizeConfig;
import net.lab1024.sa.prize.prizeconfig.service.PrizeConfigService;
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
     * 彩票配置状态：1-上线
     */
    private static final Integer STATUS_ONLINE = 1;

    private static final Integer STATUS_OFFLINE = 0;

    /**
     * 活动类型：彩票工作台只接受这一类活动。
     * 取值统一走 ActivityTypeEnum，不再在本类里裸写 "LOTTERY" 字符串（铁律 3：消除魔法值）。
     */
    private static final String ACTIVITY_TYPE_LOTTERY = ActivityTypeEnum.LOTTERY.getValue();

    // ==================== 编码 ====================

    /**
     * 生成一个未被占用的彩票编码（铁律 8：10 位大写字母+数字，全局唯一）
     */
    public ResponseDTO<String> generateCode() {
        return ResponseDTO.ok(SmartCodeUtil.generateUniqueBizCode(SmartCodeUtil.BizCodePrefix.LOTTERY, this::existsByLotteryCode));
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
    public ResponseDTO<List<LotteryConfigOptionVO>> optionList(String activityCode) {
        return ResponseDTO.ok(queryByActivityCode(activityCode).stream()
                .map(config -> new LotteryConfigOptionVO(
                        config.getLotteryCode(),
                        config.getLotteryName(),
                        config.getActivityCode(),
                        config.getNumberLength(),
                        config.getTotalCount(),
                        config.getStatus()))
                .toList());
    }

    // ==================== 工作台：聚合回显 ====================

    /**
     * 彩票工作台聚合回显：与 workbenchSave 的入参同构，前端拿到即可直接填回表单。
     *
     * 顶部是「活动 + 玩法」两级：activityCode 决定资产大库与玩法下拉的范围，
     * lotteryCode 决定具体加载哪一个玩法。lotteryCode 为空表示「在该活动下新建玩法」，
     * 返回一个带预生成编码的空壳而非报错，前端据此进入「从零配置」态。
     */
    public ResponseDTO<LotteryWorkbenchVO> workbenchDetail(String activityCode, String lotteryCode) {
        ActivityConfig activity = activityConfigService.getByActivityCode(activityCode);
        if (activity == null) {
            return ResponseDTO.userErrorParam("活动不存在：" + activityCode);
        }
        if (!ACTIVITY_TYPE_LOTTERY.equals(activity.getActivityType())) {
            return ResponseDTO.userErrorParam("活动「" + activity.getActivityName() + "」不是彩票类活动");
        }

        LotteryConfig config = getByLotteryCode(lotteryCode);
        if (config == null) {
            // 新建玩法态：活动信息已知，预填一个可用编码，运营可直接用也可重新生成
            return ResponseDTO.ok(new LotteryWorkbenchVO(
                    activity.getActivityCode(), activity.getActivityName(),
                    SmartCodeUtil.generateUniqueBizCode(SmartCodeUtil.BizCodePrefix.LOTTERY, this::existsByLotteryCode),
                    null, null, null, null,
                    false, false, null, 0L, 0L, List.of()));
        }
        // 前端传来的两个参数必须自洽，否则会出现「顶部显示活动A、内容却是活动B的玩法」这种错位
        if (!activityCode.equals(config.getActivityCode())) {
            return ResponseDTO.userErrorParam("玩法 " + lotteryCode + " 不属于活动 " + activityCode);
        }

        // 结构锁：一旦发过号，发号引擎参数就永久冻结
        List<LotteryIssue> issueList = lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, config.getLotteryCode()).list();
        long soldTotal = issueList.stream()
                .mapToLong(issue -> issue.getSoldCount() == null ? 0 : issue.getSoldCount()).sum();
        String lockReason = resolveLockReason(config, soldTotal);

        // 奖级规则：只存 prize_code，展示信息回查资产大库补齐（SKU 化，与抽奖工作台一致）
        List<LotteryPrizeRule> ruleList = lotteryPrizeRuleManager.lambdaQuery()
                .eq(LotteryPrizeRule::getLotteryCode, config.getLotteryCode())
                .orderByAsc(LotteryPrizeRule::getPrizeLevel).list();
        Map<String, PrizeConfig> prizeMap = prizeConfigService.queryListByActivityCode(config.getActivityCode())
                .stream().collect(Collectors.toMap(PrizeConfig::getPrizeCode, Function.identity(), (a, b) -> a));

        List<LotteryWorkbenchRuleVO> ruleVOList = ruleList.stream().map(rule -> {
            PrizeConfig prize = prizeMap.get(rule.getPrizeCode());
            return new LotteryWorkbenchRuleVO(
                    rule.getPrizeLevel(),
                    rule.getMatchRule(),
                    rule.getMatchLength(),
                    rule.getPrizeCode(),
                    // 奖品被删时回退成编码，让运营看得见「这一奖级绑的奖没了」，而不是渲染出空白行
                    prize == null ? rule.getPrizeCode() : prize.getPrizeName(),
                    prize == null ? null : prize.getPrizeType(),
                    prize == null ? null : prize.getPrizeValue())
                    ;
        }).toList();

        return ResponseDTO.ok(new LotteryWorkbenchVO(
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
                ruleVOList));
    }

    /**
     * 结构冻结的判定与人话文案，返回 null 表示未冻结。
     *
     * 两个触发条件的严重程度不同，文案要分开写，否则运营看到「禁止修改」只会来问为什么：
     * 已发过号是密码学层面的不可逆（改了历史号码就没法验证了），上线只是流程管控。
     */
    private String resolveLockReason(LotteryConfig config, long soldTotal) {
        if (soldTotal > 0) {
            return "已发出 " + soldTotal + " 个号码，发号引擎参数（号码长度、发售上限）永久冻结。"
                    + "号码是由游标加密得来的，改参数会让已发号码无法验证、且新号码可能与历史重复。"
                    + "如需不同的发行规格，请新建一个彩票玩法。";
        }
        if (STATUS_ONLINE.equals(config.getStatus())) {
            return "玩法已上线，发号引擎参数不可修改。如需调整请先下线。";
        }
        return null;
    }

    // ==================== 工作台：聚合保存 ====================

    /**
     * 彩票工作台聚合保存：t_lottery_config + t_lottery_prize_rule 同一事务。
     * 前端的所有校验都只是 UI 防呆，这里全部服务端重算（铁律 2）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> workbenchSave(LotteryWorkbenchSaveForm form) {
        // 1. 活动必须存在且是彩票类
        ActivityConfig activity = activityConfigService.getByActivityCode(form.getActivityCode());
        if (activity == null) {
            return ResponseDTO.userErrorParam("活动不存在：" + form.getActivityCode());
        }
        if (!ACTIVITY_TYPE_LOTTERY.equals(activity.getActivityType())) {
            return ResponseDTO.userErrorParam("活动「" + activity.getActivityName() + "」不是彩票类活动");
        }

        // 2. 编码格式由 @Pattern 拦，这里补唯一性判重（铁律 8 落点清单第 ② 项）
        LotteryConfig existed = getByLotteryCode(form.getLotteryCode());

        // 一个活动可以有多个玩法，但编码是全局唯一的（uk_lottery_code）：
        // 手输的编码可能撞上别的活动的玩法，提前给人话提示而不是抛 SQL 异常
        if (existed != null && !Objects.equals(existed.getActivityCode(), form.getActivityCode())) {
            return ResponseDTO.userErrorParam("彩票编码 " + form.getLotteryCode()
                    + " 已被活动 " + existed.getActivityCode() + " 占用，请重新生成");
        }

        // 3. 号码空间必须装得下发售量。10^length 与 totalCount 都是 int 存储，用 long 比较防溢出
        long domain = (long) Math.pow(10, form.getNumberLength());
        if (form.getTotalCount() > domain) {
            return ResponseDTO.userErrorParam("单期发售上限 " + form.getTotalCount()
                    + " 超过了 " + form.getNumberLength() + " 位号码的空间上限 " + domain + "，请增加号码长度或降低发售量");
        }

        // 4. 结构锁：绕过前端直接调接口同样拦截
        if (existed != null) {
            long soldTotal = lotteryIssueManager.lambdaQuery()
                    .eq(LotteryIssue::getLotteryCode, form.getLotteryCode()).list().stream()
                    .mapToLong(issue -> issue.getSoldCount() == null ? 0 : issue.getSoldCount()).sum();
            String lockReason = resolveLockReason(existed, soldTotal);
            if (lockReason != null) {
                boolean lengthChanged = !Objects.equals(existed.getNumberLength(), form.getNumberLength());
                boolean totalChanged = !Objects.equals(existed.getTotalCount(), form.getTotalCount());
                if (lengthChanged || totalChanged) {
                    return ResponseDTO.userErrorParam(lockReason);
                }
            }
        }

        // 5. 奖级规则逐条校验
        List<LotteryWorkbenchRuleForm> ruleList = form.getPrizeRuleList() == null ? List.of() : form.getPrizeRuleList();
        String ruleError = validateRules(ruleList, form);
        if (ruleError != null) {
            return ResponseDTO.userErrorParam(ruleError);
        }

        // 6. 落库：彩票配置 upsert
        if (existed == null) {
            LotteryConfig entity = new LotteryConfig();
            entity.setActivityCode(form.getActivityCode());
            entity.setLotteryCode(form.getLotteryCode());
            entity.setLotteryName(form.getLotteryName());
            entity.setNumberLength(form.getNumberLength());
            entity.setTotalCount(form.getTotalCount());
            // 字符集固定十进制，不接受前端值
            entity.setNumberCharset(LotteryConst.NUMBER_CHARSET);
            // 新建默认下线，上线是独立的动作，不能靠保存配置顺带把玩法推上线
            entity.setStatus(STATUS_OFFLINE);
            lotteryConfigDao.insert(entity);
        } else {
            LotteryConfig update = new LotteryConfig();
            update.setId(existed.getId());
            update.setLotteryName(form.getLotteryName());
            update.setNumberLength(form.getNumberLength());
            update.setTotalCount(form.getTotalCount());
            update.setNumberCharset(LotteryConst.NUMBER_CHARSET);
            // status 不在此处覆盖：上线/下线走独立接口，避免保存配置时把状态改回去
            lotteryConfigDao.updateById(update);
        }

        // 7. 落库：奖级规则整表重建（子表整体替换语义，与抽奖工作台的坑位映射一致）
        lotteryPrizeRuleManager.lambdaUpdate()
                .eq(LotteryPrizeRule::getLotteryCode, form.getLotteryCode()).remove();
        for (LotteryWorkbenchRuleForm rule : ruleList) {
            LotteryPrizeRule entity = new LotteryPrizeRule();
            entity.setTenantId("0");
            entity.setLotteryCode(form.getLotteryCode());
            entity.setPrizeLevel(rule.getPrizeLevel());
            MatchRuleEnum matchRule = MatchRuleEnum.resolve(rule.getMatchRule());
            entity.setMatchRule(matchRule.getValue());
            // EXACT 的匹配长度恒等于号码长度：前端传什么都不算数，服务端归一，避免存进去一个自相矛盾的值
            entity.setMatchLength(matchRule == MatchRuleEnum.EXACT ? form.getNumberLength() : rule.getMatchLength());
            entity.setPrizeCode(rule.getPrizeCode());
            lotteryPrizeRuleDao.insert(entity);
        }

        return ResponseDTO.ok();
    }

    /**
     * 奖级规则校验，返回 null 表示通过
     */
    private String validateRules(List<LotteryWorkbenchRuleForm> ruleList, LotteryWorkbenchSaveForm form) {
        Set<Integer> levels = new HashSet<>();
        Set<String> prizeCodes = new HashSet<>();
        for (LotteryWorkbenchRuleForm rule : ruleList) {
            if (!levels.add(rule.getPrizeLevel())) {
                return "奖级重复：" + rule.getPrizeLevel() + " 级配置了多条规则";
            }
            if (rule.getPrizeLevel() == LotteryConst.PRIZE_LEVEL_NONE) {
                return "奖级不能设为 " + LotteryConst.PRIZE_LEVEL_NONE
                        + "：该值被「未中奖」占用，否则用户端无法区分未中奖与 " + LotteryConst.PRIZE_LEVEL_NONE + " 等奖";
            }
            // 从 DB/前端拿到的字符串一律显式转枚举，不做 map 直查（本项目已复发三次的缺陷模式）
            MatchRuleEnum matchRule = MatchRuleEnum.resolve(rule.getMatchRule());
            if (matchRule == null) {
                return "奖级 " + rule.getPrizeLevel() + " 的匹配规则非法：" + rule.getMatchRule();
            }
            if (matchRule != MatchRuleEnum.EXACT && rule.getMatchLength() > form.getNumberLength()) {
                return "奖级 " + rule.getPrizeLevel() + " 的匹配长度 " + rule.getMatchLength()
                        + " 超过号码长度 " + form.getNumberLength() + "，这条规则永远不可能命中";
            }
            // 同一个奖品被两个奖级引用，中奖后无法判断该按哪一级的口径统计，直接拦掉
            if (!prizeCodes.add(rule.getPrizeCode())) {
                return "奖品 " + rule.getPrizeCode() + " 被多个奖级重复绑定";
            }
            if (prizeConfigService.getByActivityCodeAndPrizeCode(form.getActivityCode(), rule.getPrizeCode()) == null) {
                return "奖级 " + rule.getPrizeLevel() + " 绑定的奖品不存在于本活动的资产大库：" + rule.getPrizeCode();
            }
        }
        return null;
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
    public ResponseDTO<String> online(String lotteryCode) {
        LotteryConfig config = getByLotteryCode(lotteryCode);
        if (config == null) {
            return ResponseDTO.userErrorParam("彩票玩法不存在：" + lotteryCode);
        }
        if (STATUS_ONLINE.equals(config.getStatus())) {
            return ResponseDTO.userErrorParam("该玩法已经是上线状态");
        }
        String notReady = checkOnlineReady(lotteryCode);
        if (notReady != null) {
            return ResponseDTO.userErrorParam(notReady);
        }
        int rows = lotteryConfigDao.updateStatus(config.getId(), STATUS_OFFLINE, STATUS_ONLINE);
        if (rows == 0) {
            return ResponseDTO.userErrorParam("上线失败：状态已被其他人变更，请刷新后重试");
        }
        return ResponseDTO.ok();
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
    public ResponseDTO<String> offline(String lotteryCode) {
        LotteryConfig config = getByLotteryCode(lotteryCode);
        if (config == null) {
            return ResponseDTO.userErrorParam("彩票玩法不存在：" + lotteryCode);
        }
        if (STATUS_OFFLINE.equals(config.getStatus())) {
            return ResponseDTO.userErrorParam("该玩法已经是下线状态");
        }
        int rows = lotteryConfigDao.updateStatus(config.getId(), STATUS_ONLINE, STATUS_OFFLINE);
        if (rows == 0) {
            return ResponseDTO.userErrorParam("下线失败：状态已被其他人变更，请刷新后重试");
        }
        return ResponseDTO.ok();
    }

    // ==================== FPE 推演台 ====================

    /**
     * 配置态推演：用固定演示期号算一个样例号码。
     *
     * 与线上发号<b>共用同一个 FpeCipher 与同一套密钥派生</b>，所见即所得；
     * 但 tweak 用的是演示期号，所以结果只代表「号码长什么样」，不等于某一期的真实号码。
     */
    public ResponseDTO<String> fpePreview(FpePreviewForm form) {
        long domain = (long) Math.pow(10, form.getNumberLength());
        if (form.getSequenceNo() > domain) {
            return ResponseDTO.userErrorParam("游标 " + form.getSequenceNo()
                    + " 超过 " + form.getNumberLength() + " 位号码的空间上限 " + domain);
        }
        // 入参是 1-indexed（运营心智：第 1 个领号的人），FPE 定义域是 [0, N)，此处减 1
        long sequenceNo = form.getSequenceNo() - 1;
        return ResponseDTO.ok(fpeCipherFactory
                .createForPreview(form.getLotteryCode(), form.getNumberLength())
                .encrypt(sequenceNo));
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
    public ResponseDTO<String> batchOffline(List<String> lotteryCodeList) {
        if (SmartCollectionUtil.isEmpty(lotteryCodeList)) {
            return ResponseDTO.ok();
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
            if (STATUS_OFFLINE.equals(config.getStatus())) {
                skipped++;
                continue;
            }
            ResponseDTO<String> result = offline(lotteryCode);
            if (result.getOk()) {
                success++;
            } else {
                failed.add(lotteryCode + "（" + result.getMsg() + "）");
            }
        }
        String summary = "已禁用 " + success + " 个玩法"
                + (skipped > 0 ? "，跳过 " + skipped + " 个（本就是下线态）" : "")
                + (failed.isEmpty() ? "" : "，失败 " + failed.size() + " 个：" + String.join("、", failed));
        // 有失败也返回 ok：成功的那部分已经落库了，报错会让运营以为一个都没生效
        return ResponseDTO.ok(summary);
    }

    // ==================== 生成器产出的 CRUD ====================

    /**
     * 分页查询
     */
    public PageResult<LotteryConfigVO> queryPage(LotteryConfigQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<LotteryConfigVO> list = lotteryConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }
}
