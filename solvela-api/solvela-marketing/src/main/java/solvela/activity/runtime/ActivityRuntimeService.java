package solvela.activity.runtime;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.Objects;
import solvela.enums.EnableStatusEnum;
import solvela.prize.prizeconfig.service.PrizeCatalog;
import solvela.prize.PrizeConfig;
import solvela.draw.poolitem.manager.PrizePoolItemManager;
import solvela.draw.PrizePoolItem;
import solvela.marketing.api.ActivityPrizeView;
import solvela.marketing.api.ActivityBriefView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.activity.ActivityConfig;
import solvela.activity.ActivityDisplay;
import solvela.marketing.api.ActivityRuleView;
import solvela.activity.service.ActivityConfigService;
import solvela.activity.service.ActivityDisplayService;

/**
 * 活动的<b>运行态</b>查询：C 端要看的那份活动。
 *
 * <h3>与 ActivityConfigService / ActivityDisplayService 的分工</h3>
 * 那两个是<b>后台视角</b>的服务：增删改、向导创建、复制、删除守卫、上下线。
 * 本类只做一件事 —— 把「配置」和「展示配置」两张表拼成 C 端要的那一个对象，并且<b>只读</b>。
 *
 * <p>分开而不是给它们加个方法，是因为将来网关只依赖 {@code solvela-marketing-api}：
 * 那时后台那两个服务在网关的 classpath 上根本不存在，而本类会被搬进 app-activity 服务里。
 * 现在就把「谁是对外的」这条线画出来，比拆分那天再找便宜。
 *
 * <h3>查不到返回 null，不抛异常</h3>
 * 理由见 {@code ActivityApi} 的类注释。注意<b>不要</b>顺手改用
 * {@code ActivityDisplayService#getByActivityCode} —— 那个方法查不到会抛
 * {@code BusinessException}（后台保存路径需要它拦住脏数据），用在 C 端读路径上
 * 会把一次正常的「链接过期」变成 500。
 *
 * @Date 2026-08-30
 */
@Service
@RequiredArgsConstructor
public class ActivityRuntimeService {

    private final ActivityConfigService activityConfigService;

    private final ActivityDisplayService activityDisplayService;

    private final PrizePoolItemManager prizePoolItemManager;

    private final PrizeCatalog prizeCatalog;

    public ActivityRuleView getActivityRule(String activityCode) {
        ActivityConfig activity = activityConfigService.getByActivityCode(activityCode);
        if (activity == null) {
            return null;
        }
        // 展示配置允许没有：活动建出来还没配过展示，详情页该正常打开，只是没有图和副标题
        ActivityDisplay display = activityDisplayService.getByActivityId(activity.getId());
        return toView(activity, display, prizesOf(activityCode));
    }

    /**
     * C 端可见的活动列表。
     *
     * <p>展示配置<b>一次批量取</b>（{@code mapByActivityIds}），不是逐条查 ——
     * 这是 C 端首页每次进都会打的接口，逐条查就是 N+1。
     */
    public List<ActivityBriefView> listOpenActivities() {
        List<ActivityConfig> activities = activityConfigService.listVisibleForClient();
        if (activities.isEmpty()) {
            return List.of();
        }
        Map<Long, ActivityDisplay> displays = activityDisplayService.mapByActivityIds(
                activities.stream().map(ActivityConfig::getId).toList());
        /*
         * 进行中的排在未开始的前面，组内保持 DAO 给的开始时间倒序。
         *
         * 🔴 用【一次算好的 now】排序，不要在比较器里反复 LocalDateTime.now()：
         * 那样比较器不满足传递性，Java 的排序在元素多时会直接抛
         * "Comparison method violates its general contract"。
         */
        LocalDateTime now = LocalDateTime.now();
        return activities.stream()
                .map(activity -> toBriefView(activity, displays.get(activity.getId())))
                .sorted(Comparator.comparing((ActivityBriefView v) -> !v.joinable(now)))
                .toList();
    }

    /**
     * 拼成列表的形状。<b>同样逐字段拷贝</b>，理由见 {@link #toView} ——
     * 这一层的价值就在于它不自动。
     */
    private static ActivityBriefView toBriefView(ActivityConfig activity, ActivityDisplay display) {
        return new ActivityBriefView(
                activity.getActivityCode(),
                activity.getActivityName(),
                activity.getActivityType(),
                activity.getStatus(),
                activity.getStartTime(),
                activity.getDataEndTime(),
                activity.getEndTime(),
                display == null ? null : display.getSubTitle(),
                display == null ? null : display.getThemeColor(),
                display == null ? null : display.getMainImageId());
    }

    /**
     * 活动的奖品盘面。<b>来源是奖池，不是运营手写的 JSON。</b>
     *
     * <h3>🔴 为什么必须从 t_prize_pool_item 取</h3>
     * 抽奖引擎真正抽的就是这张表。此前 C 端的转盘是从
     * {@code t_activity_display.extra_config} 里一段手写 JSON 解析的 —— 第二个源，
     * 两个后果都发生过：没写就是空盘（用户点进活动页什么都没有），
     * 写了但对不上就是「转出一个奖池里没有的奖」。
     * <b>展示的奖品必须和会发的奖品同源。</b>
     *
     * <h3>顺序按 sort_weight，再按 id 兜底</h3>
     * 奖池项本身没有排序列，而盘面顺序是运营要控制的东西，所以按奖品配置的
     * {@code sort_weight} 排。相同权重按 id —— 没有兜底的话，
     * 每次查询返回的扇区顺序都可能不一样，转盘会「转一次换一个样」。
     *
     * <p>奖池项引用了一个<b>已停用或已删除</b>的奖品配置时，那一格直接不出：
     * 它抽不中（引擎那边同样会跳过），画在盘面上等于骗人。
     */
    private List<ActivityPrizeView> prizesOf(String activityCode) {
        List<PrizePoolItem> items = prizePoolItemManager.lambdaQuery()
                .eq(PrizePoolItem::getActivityCode, activityCode)
                .list();
        if (items.isEmpty()) {
            return List.of();
        }
        Map<String, PrizeConfig> prizes = prizeCatalog.mapByCodes(
                items.stream().map(PrizePoolItem::getPrizeCode).collect(Collectors.toSet()));

        return items.stream()
                .map(item -> prizes.get(item.getPrizeCode()))
                .filter(Objects::nonNull)
                .filter(prize -> EnableStatusEnum.ENABLED == prize.getStatus())
                .sorted(Comparator
                        .comparingInt((PrizeConfig p) -> p.getSortWeight() == null ? 0 : p.getSortWeight())
                        .thenComparing(PrizeConfig::getId))
                .map(prize -> new ActivityPrizeView(
                        prize.getPrizeCode(),
                        prize.getPrizeName(),
                        prize.getPrizeType(),
                        prize.getPrizeLevel() == null ? 0 : prize.getPrizeLevel()))
                .toList();
    }

    /**
     * 拼成 C 端的形状。
     *
     * <p>🔴 <b>逐字段拷贝，不要换成 BeanUtil.copy</b>：这里正在做的事是「决定哪些字段可以出公网」，
     * 而反射拷贝的语义是「有同名的就给」。以后谁往 entity 上加一个内部字段，
     * 拷贝版会<b>自动</b>把它下发出去，没有任何提示 —— 这一层的价值恰恰在于它不自动。
     */
    private static ActivityRuleView toView(ActivityConfig activity, ActivityDisplay display,
                                           List<ActivityPrizeView> prizes) {
        return new ActivityRuleView(
                activity.getActivityCode(),
                activity.getActivityName(),
                activity.getActivityType(),
                activity.getStatus(),
                activity.getStartTime(),
                activity.getDataEndTime(),
                activity.getEndTime(),
                display == null ? null : display.getSubTitle(),
                display == null ? null : display.getThemeColor(),
                display == null ? null : display.getMainImageId(),
                display == null ? null : display.getBgImageId(),
                display == null ? null : display.getShareImageId(),
                display == null ? null : display.getShareTitle(),
                display == null ? null : display.getShareDesc(),
                display == null ? null : display.getExtraConfig(),
                display == null ? null : display.getRuleContent(),
                prizes);
    }
}
