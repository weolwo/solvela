package solvela.marketing.server;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 营销服务启动类：活动、抽奖、任务、彩票、商城、奖品、发奖派发。
 *
 * <p>四个进程之一：admin 1024 / app 1025(网关) / <b>marketing 1026</b> / member 1027。
 *
 * <h3>🔴 组件扫描是「列出要什么」，不是「扫全世界」</h3>
 * 与 {@code AppApplication} 同一套做法：本进程<b>只装配自己列出来的这些包</b>。
 * 漏了会怎样：启动即失败，报 NoSuchBeanDefinitionException 并指名道姓说缺哪个 ——
 * 这比「多装了一个不该有的 bean 而没人发现」好得多。
 *
 * <h3>⚠️ 清单里为什么还有 solvela.member</h3>
 * 按服务边界它属于 member 服务，出现在这里是<b>唯一一处已知妥协</b>：
 * 抽奖、彩票、任务、商城四条链路都要用 {@code MemberService} 做<b>会员标识翻译</b>
 * —— 会员号换账号（流水快照、白名单判定）、账号换会员号（后台按账号查）。
 * 全仓只有这两个操作，缝很窄。
 *
 * <p><b>下一步把它换成 {@code MemberIdentityApi}</b>：那时本行删掉，
 * 营销进程的 classpath 上就不再有会员域的实现。在那之前，
 * 本服务对 {@code solvela.member} 的用法要克制 —— 只用这两个翻译操作，不要新增。
 *
 * <h3>⚠️ 第二处妥协：只扫 solvela.risk 的两个子包</h3>
 * <b>奖品配置关联优惠配置</b>（{@code PrizeConfigService → PromotionConfigService}），
 * 所以营销侧必须读得到 {@code risk.promotionconfig} / {@code risk.promotiongroup}。
 * 但 {@code solvela.risk} 全包扫不得 —— 里面的 {@code proposal} 经 {@code AssetDispatcher}
 * 牵出整个 ledger，那是 member 服务的东西。
 *
 * <p>这暴露了一件事：<b>{@code solvela-risk} 其实装着两个不同归属的域</b> ——
 * 优惠配置跟着奖品走（营销），提案跟着资产走（会员）。按子包精确扫描是当下的止血，
 * 正解是把这个模块拆开。在拆开之前，这两行必须精确到子包，
 * 谁把它改回 {@code "solvela.risk"} 都会让营销进程重新背上整个账务域。
 *
 * <h3>发奖派发在这里，但资产不在</h3>
 * {@code consumer} 写的是 {@code t_prize_log} —— <b>营销侧的账</b>，所以归本服务。
 * 它同步调 {@code MemberProposalApi} 把奖交给会员服务，当场拿到「受理 / 拒绝 + 原因」；
 * 资产是否真的入账则由会员服务<b>异步回消息</b>（{@code PrizeDispatchResultListener} 收）。
 *
 * <p>{@code risk.proposal} 与 {@code ledger} 不在这里 —— 那两个是会员侧的。
 * 分界不在「派发链路的终点」，在<b>账本归属</b>：谁的表谁写。
 *
 * <h3>刻意没有 @EnableScheduling</h3>
 * 四个进程连同一个库，{@code @Scheduled} 每多开一个进程就多跑一遍 ——
 * 发奖、结算重复执行，而多发的那一份没人会主动来报。定时任务归 admin 独占。
 *
 * <h3>刻意没有 solvela.web</h3>
 * 那是管理端的 HTTP 层，它的 {@code GlobalExceptionHandler} 一律返回 200 + {@code ResponseDTO}。
 * 两个 {@code @RestControllerAdvice} 同时在场时，返回什么取决于哪个先匹配上 ——
 * 那是最难查的一类问题。本服务的错误出口只有 {@code InternalExceptionHandler} 一个。
 */
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan({
        // 本模块：启动类、HTTP 薄壳、错误出口
        "solvela.marketing.server",
        // 基础设施
        "solvela.base",
        // 服务间调用的错误出口。刻意不在 solvela.base 之下 —— 网关不能扫到它，
        // 否则那边会出现第二个 @RestControllerAdvice（见该类注释）
        "solvela.server.internal",
        // 脚本引擎（solvela.config 是它的自动配置，solvela.aspect 是执行监控切面）
        "solvela.scriptengine", "solvela.config", "solvela.aspect",
        // 域：活动、抽奖、任务、彩票、商城、统计、奖品、发奖派发
        "solvela.activity", "solvela.draw", "solvela.task", "solvela.lottery",
        "solvela.mall", "solvela.stat", "solvela.prize",
        // 发奖投递：中奖之后「把奖交出去」的唯一出口
        "solvela.dispatch",
        // 发奖派发：写的是 t_prize_log，营销侧的账
        "solvela.consumer",
        // PII 加解密（散落在 model 里的 @Component）
        "solvela.crypto",
        // ⚠️ 两处妥协，见类注释
        "solvela.member",
        // 只扫【优惠配置】这两个子包，不扫 solvela.risk 全包 ——
        // 扫全包会把 proposal 拉进来，而它经 AssetDispatcher 牵出整个 ledger
        "solvela.risk.promotionconfig", "solvela.risk.promotiongroup"
})
@MapperScan(value = {
        "solvela.base", "solvela.activity", "solvela.draw", "solvela.task", "solvela.lottery",
        "solvela.mall", "solvela.stat", "solvela.prize",
        // 发奖投递：PrizeEventPublisher 与 outbox。中奖之后「把奖交出去」的唯一出口
        "solvela.dispatch", "solvela.scriptengine",
        "solvela.member", "solvela.risk.promotionconfig", "solvela.risk.promotiongroup"
}, annotationClass = Mapper.class)
@SpringBootApplication
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
