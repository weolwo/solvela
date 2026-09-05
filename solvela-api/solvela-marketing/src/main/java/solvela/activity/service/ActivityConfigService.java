package solvela.activity.service;

import solvela.enums.ActivityStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.activity.dao.ActivityConfigDao;
import solvela.activity.ActivityConfig;
import solvela.activity.domain.command.ActivityConfigAddCommand;
import solvela.activity.domain.query.ActivityConfigQuery;
import solvela.activity.domain.command.ActivityConfigUpdateCommand;
import solvela.activity.domain.command.ActivityWizardCreateCommand;
import solvela.activity.domain.dto.ActivityConfigDTO;
import solvela.activity.domain.dto.ActivityDeleteCheckDTO;
import solvela.activity.domain.dto.ActivityRefItem;
import solvela.activity.manager.ActivityConfigManager;
import solvela.activity.spi.ActivityRefProvider;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.enums.ActivityTypeEnum;
import solvela.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import solvela.prize.prizeconfig.domain.command.PrizeConfigAddCommand;
import solvela.prize.prizeconfig.manager.PrizeConfigManager;
import solvela.prize.prizeconfig.service.PrizeConfigService;
import solvela.prize.PrizeConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动配置 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ActivityConfigService {


    private final ActivityConfigDao activityConfigDao;
    private final ActivityConfigManager activityConfigManager;
    private final PrizeConfigManager prizeConfigManager;
    private final PrizeConfigService prizeConfigService;

    /**
     * 各玩法的下游引用查询实现，由 solvela-marketing 注册（依赖倒置，见 ActivityRefProvider 类注释）。
     * <p>
     * 用 ObjectProvider 而非直接注入 List：common-api 单独跑（如自身单测）时一个实现都没有，
     * 直接注入 List 会因找不到候选 Bean 启动失败。
     */
    private final ObjectProvider<ActivityRefProvider> refProviders;

    /**
     * 按活动编码查询（奖池工作台保存时判定活动是否上线等场景使用）
     */
    public ActivityConfig getByActivityCode(String activityCode) {
        return activityConfigManager.lambdaQuery().eq(ActivityConfig::getActivityCode, activityCode).one();
    }

    /**
     * 按活动类型查询活动列表（BASIC/DRAW/TASK/LOTTERY）：供各业务工作台顶部的活动下拉切换。
     * activityType 为空时不加类型条件，返回全部活动。
     *
     * <p><b>includeInactive=false（默认）时过滤掉「此刻不可配置」的活动</b>：已下线、已过期。
     *
     * <p>⚠️ 刻意<b>不</b>过滤 status=0 未开始 —— 向导第一步刚落库的活动就是这个状态，
     * 过滤掉它，新建的活动将永远无法进入工作台配置，整个创建向导当场失效。
     * 「状态正常」这个说法在这里是陷阱：判据应当是「此刻能不能配置」，而不是「是不是上线中」。
     *
     * <p>⚠️ 同样不过滤「未配置玩法」的活动 —— 它恰恰最需要出现在工作台下拉里，就等着被配置。
     *
     * <p>注意这只是 UI 便利，不是守卫：各工作台的 save 接口并不拒绝已过期的活动，
     * 绕过页面直接 POST 依然进得来。这是刻意的 —— 给活动延期是正常运营操作，
     * 过期只该是提醒，不该是硬拦。
     */
    /**
     * C 端可见的活动：<b>上线中</b>且还没结束的（含未开始）。
     *
     * <p>与 {@link #queryOptionList} 的区别：那个是给<b>运营下拉框</b>用的，
     * 判据是「此刻能不能配置」，所以放行了 DRAFT 之类的状态；
     * 这个是给<b>C 端用户</b>看的，只能是 ONLINE —— 用户看到一个草稿活动就是事故。
     * 两个方法的判据不同，所以刻意不合成一个带布尔开关的。
     *
     * <p>⚠️ <b>这张表没有排序权重列</b>，所以这里只给出稳定的默认序（开始时间倒序），
     * 「进行中的排在未开始的前面」由 {@code ActivityRuntimeService.listOpenActivities} 排。
     * 真要让运营控制首页焦点位的顺序，那是加一列 {@code sort_weight} 的事 ——
     * 现在没加是因为活动数量是十位数，且还没有人提出要手工控顺序。
     * <b>网关不重排</b>：重排一次就是第二份排序规则。
     */
    public List<ActivityConfig> listVisibleForClient() {
        return activityConfigManager.lambdaQuery()
                .eq(ActivityConfig::getStatus, ActivityStatusEnum.ONLINE)
                // 已结束的不出现：用户点进去只会看到一个空页面
                .ge(ActivityConfig::getEndTime, LocalDateTime.now())
                .orderByDesc(ActivityConfig::getStartTime)
                // 开始时间相同时按 id 兜底，保证分页/重复请求的顺序稳定
                .orderByDesc(ActivityConfig::getId)
                .list();
    }

    public List<ActivityConfigDTO> queryOptionList(String activityType, Boolean includeInactive) {
        boolean keepAll = Boolean.TRUE.equals(includeInactive);
        List<ActivityConfig> list = activityConfigManager.lambdaQuery()
                .eq(StringUtils.isNotBlank(activityType), ActivityConfig::getActivityType, activityType)
                .ne(!keepAll, ActivityConfig::getStatus, ActivityStatusEnum.OFFLINE)
                .ge(!keepAll, ActivityConfig::getEndTime, LocalDateTime.now())
                .orderByDesc(ActivityConfig::getId)
                .list();
        return SolvelaBeanUtil.copyList(list, ActivityConfigDTO.class);
    }

    /**
     * 批量查询「玩法是否已配置完备」，供活动列表页一次算完（不要每行发一个请求）。
     *
     * <p>BASIC 恒为 true：它按定义就不挂玩法，永远不该显示「未配置玩法」。
     * 这正是引入 BASIC 的意义 —— 让「未配置玩法」这个标记只表示「该配而没配」，
     * 不再混入「本来就不用配」。
     */
    public Map<String, Boolean> queryConfiguredStatus(List<String> activityCodeList) {
        Map<String, Boolean> result = new HashMap<>();
        if (SolvelaCollectionUtil.isEmpty(activityCodeList)) {
            return result;
        }
        List<ActivityConfig> activityList = activityConfigManager.lambdaQuery()
                .in(ActivityConfig::getActivityCode, activityCodeList).list();
        for (ActivityConfig activity : activityList) {
            result.put(activity.getActivityCode(), checkConfigured(activity) == null);
        }
        return result;
    }

    /**
     * 玩法是否配置完备：返回 null 表示完备，非 null 是「还差什么」的说明。
     * 判据委托给对应玩法的 Provider（= 它自己的上线前校验），本类不自造一套。
     */
    private String checkConfigured(ActivityConfig activity) {
        if (!ActivityTypeEnum.hasGameplay(activity.getActivityType())) {
            // BASIC（以及理论上不该出现的非法类型）无需配置玩法
            return null;
        }
        ActivityRefProvider provider = findProvider(activity.getActivityType());
        if (provider == null) {
            // 玩法模块没装载（如 common-api 单独运行）时不做判断，宁可不显示也不误报
            return null;
        }
        return provider.checkConfigured(activity.getActivityCode());
    }

    private ActivityRefProvider findProvider(String activityType) {
        ActivityTypeEnum type = ActivityTypeEnum.resolve(activityType);
        if (type == null) {
            return null;
        }
        return refProviders.stream().filter(p -> type == p.supportType()).findFirst().orElse(null);
    }

    /**
     * 删除前检查：能不能删 + 下游引用明细。
     */
    public ActivityDeleteCheckDTO checkDeletable(Long id) {
        ActivityConfig activity = activityConfigDao.selectById(id);
        if (activity == null) {
            return ActivityDeleteCheckDTO.reject("活动不存在", List.of());
        }
        return checkDeletable(activity);
    }

    /**
     * 删除守卫，两层：
     *
     * <p><b>第一层：上线过的活动永不可删</b>。t_prize_log.activity_code 是 NOT NULL 且建了
     * (member_id, activity_code) 索引 —— 发奖流水就是按活动编码追溯的，
     * 删掉活动等于把资损追溯链路断在源头。运营口中的「删除」，绝大多数场景是「建错了」，
     * 那种活动一定还没上线过；真正跑过的活动只该下线，不该消失。
     *
     * <p><b>第二层：有下游引用则拒绝</b>，并把明细给运营看，让他知道该去哪儿清理。
     * 不做级联删除 —— 下游挂着发奖流水与资产账务。
     */
    private ActivityDeleteCheckDTO checkDeletable(ActivityConfig activity) {
        if (activity.getStatus() != ActivityStatusEnum.NOT_START) {
            String statusDesc = activity.getStatus() == ActivityStatusEnum.ONLINE ? "上线中" : "已下线";
            return ActivityDeleteCheckDTO.reject(
                    "该活动" + statusDesc + "，已产生或可能已产生发奖记录，不允许删除；如需停止请将其下线。",
                    List.of());
        }
        List<ActivityRefItem> refs = countRefs(activity);
        if (SolvelaCollectionUtil.isEmpty(refs)) {
            return ActivityDeleteCheckDTO.ok();
        }
        String detail = refs.stream()
                .map(r -> r.bizName() + " " + r.count() + " 个")
                .collect(Collectors.joining(" / "));
        return ActivityDeleteCheckDTO.reject("该活动下已配置 " + detail + "，请先清理后再删除。", refs);
    }

    /**
     * 统计活动的下游引用。
     *
     * <p>🔴 <b>资产大库这一条对全部类型都要查，绝不能只查玩法表。</b>
     * t_prize_config.activity_code 是 NOT NULL，而资产大库与玩法无关 ——
     * BASIC 活动按定义没有玩法下游，<b>资产大库是它唯一的引用来源</b>。
     * 漏了这张表，一个挂着 10 个真实奖品的 BASIC 活动会被判「无引用」而顺滑删除，
     * 10 个奖品当场失去宿主变成悬空孤儿，财务与发奖链路直接断裂 —— 等于对 BASIC 完全没有守卫。
     */
    private List<ActivityRefItem> countRefs(ActivityConfig activity) {
        List<ActivityRefItem> refs = new ArrayList<>();

        // 玩法下游（BASIC 没有，findProvider 返回 null）
        ActivityRefProvider provider = findProvider(activity.getActivityType());
        if (provider != null) {
            refs.addAll(provider.countRefs(activity.getActivityCode()));
        }

        // 资产大库：全类型都要查
        long prizeCount = prizeConfigManager.lambdaQuery()
                .eq(PrizeConfig::getActivityCode, activity.getActivityCode()).count();
        if (prizeCount > 0) {
            refs.add(new ActivityRefItem("奖品", prizeCount));
        }
        return refs;
    }

    /**
     * 分页查询
     */
    public PageResult<ActivityConfigDTO> queryPage(ActivityConfigQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<ActivityConfigDTO> list = activityConfigDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 生成一个未被占用的活动编码（10 位大写字母+数字），供前端「生成」按钮调用
     */
    public String generateActivityCode() {
        return SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.ACTIVITY, code -> getByActivityCode(code) != null);
    }

    /**
     * 数据截止时间必须落在活动周期内。
     *
     * <p><b>为空不校验</b> —— 为空的语义是「与结束时间相同」，永远合法。
     *
     * <p>只拦这一条，不顺手加「开始必须早于结束」：那是另一件事，
     * 现在全仓没有这条校验，在这里补一半会让人以为时间校验已经齐了。要补就单独补全。
     */
    private static void validateDataEndTime(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime dataEndTime) {
        if (dataEndTime == null) {
            return;
        }
        if (endTime != null && dataEndTime.isAfter(endTime)) {
            throw new BusinessException("数据截止时间不能晚于活动结束时间");
        }
        if (startTime != null && dataEndTime.isBefore(startTime)) {
            throw new BusinessException("数据截止时间不能早于活动开始时间");
        }
    }

    /**
     * 添加
     * 活动编码允许手工输入，故服务端必须重校验格式与唯一性（表上虽有唯一索引，但直接抛 SQL 异常对运营不友好）
     */
    public void add(ActivityConfigAddCommand addForm) {
        validateDataEndTime(addForm.getStartTime(), addForm.getEndTime(), addForm.getDataEndTime());
        if (!SolvelaCodeUtil.isValidBizCode(addForm.getActivityCode())) {
            throw new BusinessException("活动" + SolvelaCodeUtil.BIZ_CODE_MESSAGE);
        }
        if (getByActivityCode(addForm.getActivityCode()) != null) {
            throw new BusinessException("活动编码已存在：" + addForm.getActivityCode());
        }
        ActivityConfig activityConfig = SolvelaBeanUtil.copy(addForm, ActivityConfig.class);
        activityConfigDao.insert(activityConfig);
    }

    /**
     * 活动创建向导第一步：建活动 + 随手建的若干奖品，一次事务落库。
     *
     * <p><b>为什么必须聚合成一个接口</b>：奖品的 activityCode 是必填，
     * 也就是活动必须先存在奖品才建得出来。若由前端串行发起，中途任一奖品失败就会留下
     * 「活动建好了、奖品只建了一半」的残局，而运营看到的只是一个失败提示 ——
     * 他不会知道库里已经躺了一个半成品活动。
     *
     * <p><b>为什么把校验全部前置到插入之前</b>：这里刻意不采用「边插边校验、失败就
     * setRollbackOnly」的写法。那样外层提交会抛 UnexpectedRollbackException，
     * 把本该给运营看的人话提示变成一个 500 —— 本项目在提案域已经踩过这个坑
     * （交接文档「事务边界隐患已被证实」一节）。
     * 先验完再插，插入阶段就只剩真正的 DB 异常，那种异常抛出去正好由 @Transactional 回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void wizardCreate(ActivityWizardCreateCommand form) {
        List<ActivityWizardCreateCommand.WizardPrizeCommand> prizeList =
                form.getPrizeList() == null ? List.of() : form.getPrizeList();

        checkActivity(form);
        checkPrizes(prizeList);

        ActivityConfig activityConfig = SolvelaBeanUtil.copy(form, ActivityConfig.class);
        activityConfig.setStatus(ActivityStatusEnum.NOT_START);
        activityConfigDao.insert(activityConfig);
        insertPrizes(form.getActivityCode(), prizeList);
    }

    private void checkActivity(ActivityWizardCreateCommand form) {
        validateDataEndTime(form.getStartTime(), form.getEndTime(), form.getDataEndTime());
        if (!SolvelaCodeUtil.isValidBizCode(form.getActivityCode())) {
            throw new BusinessException("活动" + SolvelaCodeUtil.BIZ_CODE_MESSAGE);
        }
        if (getByActivityCode(form.getActivityCode()) != null) {
            throw new BusinessException("活动编码已存在：" + form.getActivityCode());
        }
    }

    /**
     * 奖品全量预校验，<b>一条都不能留到插入阶段</b> —— 理由见 {@link #wizardCreate} 的方法注释。
     */
    private void checkPrizes(List<ActivityWizardCreateCommand.WizardPrizeCommand> prizeList) {
        // 一次问清「这批编码里哪些已被占用」，而不是逐个 exists —— 20 个奖品就是 20 次查询
        Set<String> takenCodes = prizeConfigService.filterExistingPrizeCodes(
                prizeList.stream().map(ActivityWizardCreateCommand.WizardPrizeCommand::getPrizeCode).toList());

        Set<String> batchCodes = new HashSet<>();
        for (ActivityWizardCreateCommand.WizardPrizeCommand prize : prizeList) {
            if (!SolvelaCodeUtil.isValidBizCode(prize.getPrizeCode())) {
                throw new BusinessException("奖品「" + prize.getPrizeName() + "」的" + SolvelaCodeUtil.BIZ_CODE_MESSAGE);
            }
            // 本次提交内重复：唯一索引只能挡住与库里已有的冲突，挡不住同一批次里的两条
            if (!batchCodes.add(prize.getPrizeCode())) {
                throw new BusinessException("本次提交的奖品编码重复：" + prize.getPrizeCode());
            }
            if (takenCodes.contains(prize.getPrizeCode())) {
                throw new BusinessException("奖品编码已存在：" + prize.getPrizeCode());
            }
            String matchError = prizeConfigService.checkPromotionConfigMatch(
                    prize.getPromotionConfigId(), prize.getPrizeType());
            if (matchError != null) {
                throw new BusinessException("奖品「" + prize.getPrizeName() + "」" + matchError);
            }
        }
    }

    private void insertPrizes(String activityCode, List<ActivityWizardCreateCommand.WizardPrizeCommand> prizeList) {
        for (ActivityWizardCreateCommand.WizardPrizeCommand prize : prizeList) {
            PrizeConfigAddCommand addForm = SolvelaBeanUtil.copy(prize, PrizeConfigAddCommand.class);
            addForm.setActivityCode(activityCode);
            try {
                prizeConfigService.add(addForm);
            } catch (BusinessException e) {
                // 预校验已覆盖全部可预期的失败，走到这里说明是并发抢占编码之类的意外。
                // 重新抛出并补上是哪个奖品：@Transactional 靠异常回滚，
                // 吞掉它活动会连同半截奖品一起提交。
                throw new BusinessException("奖品「" + prize.getPrizeName() + "」创建失败：" + e.getMessage(), e);
            }
        }
    }

    /**
     * 复制一个活动：连同奖品配置与该玩法下的全部配置一起复制成新的一份。
     *
     * <h3>复制什么、不复制什么</h3>
     * <pre>
     * 复制： t_activity_config + t_prize_config + 玩法配置（奖池 / 任务 / 彩票，走 SPI）
     * 不复制：t_prize_log、t_draw_prize_log、t_task_record、彩票期号与已售号码
     *         —— 那些是运行态数据，跟着复制就是伪造历史
     * </pre>
     *
     * <h3>🔴 新活动一律落「未开始」</h3>
     * 不管源活动是不是上线中。复制一个正在跑的活动时，新活动如果跟着变成上线，
     * 那一刻它的奖池概率还是抄来的、预算也还没调，却已经对外可见了。
     * 同理各玩法主体也由 SPI 实现落成不可运行状态 —— 尤其是彩票：
     * {@code TicketIssueService} 只校验彩票玩法自己的状态，<b>根本不看活动状态</b>，
     * 玩法留在上线就是真的能领号。
     *
     * <h3>奖品编码的重映射</h3>
     * 奖品先复制并拿到「旧编码 -&gt; 新编码」的映射，再把映射交给玩法侧 ——
     * 奖池物资、任务奖励、彩票奖级三处都按 {@code prize_code} 引用奖品，
     * 不重映射的话新活动的奖池会指向老活动的奖品，而这是能跑通的，
     * 只是抽中之后发出去的是另一个活动的奖。
     *
     * <p>⚠️ <b>规则脚本绑定（t_script_ref）不在复制范围内</b>：那张表归 solvela-scriptengine，
     * 而本模块与 solvela-marketing 都不依赖它。源活动的奖池若绑了规则脚本，
     * 新活动要到「脚本管理」里重新绑一次。
     *
     * @param activityName 新活动名称，留空则在原名后加「副本」
     * @return 新活动编码
     */
    @Transactional(rollbackFor = Exception.class)
    public String copy(String sourceActivityCode, String activityName) {
        ActivityConfig source = getByActivityCode(sourceActivityCode);
        if (source == null) {
            throw new BusinessException("活动不存在：" + sourceActivityCode);
        }

        ActivityConfig target = new ActivityConfig();
        target.setActivityCode(SolvelaCodeUtil.generateUniqueBizCode(
                SolvelaCodeUtil.BizCodePrefix.ACTIVITY, code -> getByActivityCode(code) != null));
        target.setActivityName(StringUtils.isBlank(activityName)
                ? source.getActivityName() + " 副本" : activityName);
        // 类型必须照抄：它决定了下面走哪个玩法 Provider，也是活动创建后就不可改的
        target.setActivityType(source.getActivityType());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setStatus(ActivityStatusEnum.NOT_START);
        activityConfigDao.insert(target);

        // 奖品先复制，玩法侧要靠这份映射重指向
        Map<String, String> prizeCodeMap =
                prizeConfigService.copyForActivity(sourceActivityCode, target.getActivityCode());

        // BASIC 没有 Provider（它本来就是「仅外壳，不挂玩法」），找不到实现直接跳过
        ActivityRefProvider provider = findProvider(source.getActivityType());
        if (provider != null) {
            provider.copyTo(sourceActivityCode, target.getActivityCode(), prizeCodeMap);
        }

        log.info("【活动复制】{} -> {}（{}），复制奖品 {} 个",
                sourceActivityCode, target.getActivityCode(), target.getActivityName(), prizeCodeMap.size());
        return target.getActivityCode();
    }

    /**
     * 更新（不含活动类型）
     *
     * <p>ActivityConfigUpdateCommand 里没有 activityType 字段，SolvelaBeanUtil.copy 后该字段为 null，
     * MyBatis-Plus 默认 NOT_NULL 策略会把它排除在 SET 之外 —— 类型天然不会被改。
     *
     * <p>但这里<b>再显式置一次 null</b>，不是冗余：那层保护依赖的是「Form 里没这个字段」+
     * 「ORM 的隐式默认策略」两个条件，一旦有人用代码生成器重新生成 Form 把字段加回来，
     * 保护会<b>静默失效</b>。本项目已经吃过「只改 handler 不够、注解也得摘」那种亏（铁律 9），
     * 教训就是别把正确性寄托在 ORM 的默认行为上。
     */
    public void update(ActivityConfigUpdateCommand updateForm) {
        validateDataEndTime(updateForm.getStartTime(), updateForm.getEndTime(), updateForm.getDataEndTime());
        ActivityConfig activityConfig = SolvelaBeanUtil.copy(updateForm, ActivityConfig.class);
        activityConfig.setActivityType(null);
        activityConfig.setActivityCode(null);
        activityConfigDao.updateById(activityConfig);
    }

    /**
     * 活动启用 / 禁用（单个开关与批量禁用共用）。
     *
     * <p><b>启用前会校验玩法完备度</b>：启用一个还没配奖池的抽奖活动，
     * 用户点进去什么也抽不到 —— 而那时活动已经对外可见，补救的代价远高于此刻拦一下。
     * 判据直接复用 {@link ActivityRefProvider#checkConfigured}，与列表页「配置完备度」列
     * 是同一份逻辑，不另造一套（另造必然漂移，见方案 §1.5）。
     *
     * <p>BASIC 活动天然通过：它按定义不挂玩法，`checkConfigured` 返回 null。
     *
     * <p><b>禁用不做任何校验</b> —— 恰恰相反，出问题时能立刻止血地停掉活动是运营最需要的能力
     * （对齐 `LotteryConfigService.offline()` 的既定取舍）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(List<Long> idList, ActivityStatusEnum status) {
        // 入参换成枚举之后，「取值合不合法」由反序列化保证（非法值直接 400）。
        // 这里只剩一条业务规则：活动上过线就回不到「未开始」。
        if (status != ActivityStatusEnum.ONLINE && status != ActivityStatusEnum.OFFLINE) {
            throw new BusinessException("目标状态只能是 上线 或 下线");
        }
        List<ActivityConfig> activityList = activityConfigDao.selectBatchIds(idList);
        if (SolvelaCollectionUtil.isEmpty(activityList)) {
            throw new BusinessException("活动不存在");
        }

        // 启用：逐个校验完备度。任一不通过则整批拒绝并点名是哪个活动 ——
        // 部分成功会让运营以为「都启用了」，而实际有几个没启，这种结果比整批失败更难排查
        if (status == ActivityStatusEnum.ONLINE) {
            for (ActivityConfig activity : activityList) {
                String notReady = checkConfigured(activity);
                if (notReady != null) {
                    throw new BusinessException("「" + activity.getActivityName() + "」" + notReady + "，请先配置完成再启用");
                }
            }
        }

        for (ActivityConfig activity : activityList) {
            ActivityConfig update = new ActivityConfig();
            update.setId(activity.getId());
            update.setStatus(status);
            activityConfigDao.updateById(update);
        }
    }

    /**
     * 升级活动类型：仅 BASIC → DRAW/TASK/LOTTERY。
     *
     * <p>为什么这个方向可以放行、反向一律不行：
     * 冻结类型的真实理由是「改类型会让已配的下游配置变成查不到也删不掉的孤儿数据」。
     * BASIC 按定义没有玩法下游，升级不产生任何孤儿；它已挂的奖品（t_prize_config）
     * 升级成 DRAW 后正好成为抽奖的资产大库，不但不冲突，反而顺理成章。
     * 而玩法类之间互转、或降级回 BASIC，都会遗弃已配好的玩法配置，一律拒绝。
     */
    public void upgradeType(Long id, String targetType) {
        ActivityConfig activity = activityConfigDao.selectById(id);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        if (ActivityTypeEnum.hasGameplay(activity.getActivityType())) {
            throw new BusinessException("只有基础活动可以升级玩法；玩法类活动之间不可互转，也不可降级回基础活动 —— 那会遗弃已配好的玩法配置。");
        }
        ActivityTypeEnum target = ActivityTypeEnum.resolve(targetType);
        if (target == null || !target.isGameplay()) {
            throw new BusinessException("升级目标必须是 DRAW / TASK / LOTTERY 之一");
        }

        // 二次校验：BASIC 理论上没有玩法下游，但编码可能被历史数据复用过，落库前再确认一次
        ActivityRefProvider provider = findProvider(target.getValue());
        if (provider != null) {
            List<ActivityRefItem> refs = provider.countRefs(activity.getActivityCode());
            if (SolvelaCollectionUtil.isNotEmpty(refs)) {
                String detail = refs.stream()
                        .map(r -> r.bizName() + " " + r.count() + " 个")
                        .collect(Collectors.joining(" / "));
                throw new BusinessException("该活动编码下已存在 " + target.getDesc() + " 配置（" + detail + "），无法升级");
            }
        }

        ActivityConfig update = new ActivityConfig();
        update.setId(activity.getId());
        update.setActivityType(target.getValue());
        activityConfigDao.updateById(update);
    }

    /**
     * 批量删除：逐个走与单个删除相同的守卫，任一不通过则整批拒绝并说明是哪个活动。
     * 不做「跳过不能删的、删掉能删的」—— 部分成功对运营是更难排查的结果。
     */
    public void batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }
        List<ActivityConfig> activityList = activityConfigDao.selectBatchIds(idList);
        for (ActivityConfig activity : activityList) {
            ActivityDeleteCheckDTO check = checkDeletable(activity);
            if (!check.deletable()) {
                throw new BusinessException("「" + activity.getActivityName() + "」" + check.reason());
            }
        }
        activityConfigDao.deleteBatchIds(idList);
    }

    /**
     * 单个删除
     */
    public void delete(Long id) {
        if (null == id) {
            return;
        }
        ActivityConfig activity = activityConfigDao.selectById(id);
        if (activity == null) {
            return;
        }
        // 前端已经调过 checkDeletable，这里再拦一次 —— 前端校验只是防呆（铁律 2）
        ActivityDeleteCheckDTO check = checkDeletable(activity);
        if (!check.deletable()) {
            throw new BusinessException(check.reason());
        }
        activityConfigDao.deleteById(id);
    }
}
