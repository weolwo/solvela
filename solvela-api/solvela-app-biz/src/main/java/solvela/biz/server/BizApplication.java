package solvela.biz.server;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import solvela.base.listener.Ip2RegionListener;

/**
 * C 端业务服务：会员、营销、资产，全在这一个进程里。
 *
 * <p>三个进程之一：admin 1024 / app 1025（网关） / <b>biz 1026</b>。
 *
 * <h3>2026-08-31：app-member（1027）已撤销，并入本进程</h3>
 * 那个进程只有 6 个 java 文件，不装任何独有的域。四个进程连的是同一个库，
 * 所以那一刀<b>买不到任何数据隔离</b>，换来的却是：
 * <ul>
 *   <li>发奖热路径上一次同步 HTTP（读超时 3 秒，超时会让发奖流水停在「待提交」）；</li>
 *   <li>一条只为「入账结果回写」存在的 MQ 链路，以及一个必须在线的 broker；</li>
 *   <li>两处不得不开的扫描妥协 —— 整个 {@code solvela.member} 和半个 {@code solvela.risk}
 *       本来就已经在营销进程里了，所谓「营销碰不到会员数据」当时就不成立。</li>
 * </ul>
 *
 * <p>而拆服务最贵的部分 —— <b>契约</b> —— 不用扔：{@code MemberAuthApi} 与
 * {@code MemberProposalApi} 照旧，只是在本进程里解析成本地 bean
 * （{@code MemberAuthService} / {@code ProposalApiService}）而不是 HTTP 代理。
 * 将来要把资产域拆出去，是加回一个进程壳 + 换一个 bean，业务代码一行不动。
 *
 * <h3>🔴 组件扫描是「列出要什么」，不是「扫全世界」</h3>
 * 与 {@code AppApplication} 同一套做法：本进程<b>只装配自己列出来的这些包</b>。
 * 漏了会怎样：启动即失败，报 NoSuchBeanDefinitionException 并指名道姓说缺哪个 ——
 * 这比「多装了一个不该有的 bean 而没人发现」好得多。
 *
 * <h3>⚠️ 资产域（solvela.ledger）在这里，但它是将来要拆出去的那一个</h3>
 * 目标形态是：资产独立成服务，会员 + 资产用一个独立的后台控制台。
 * 所以 <b>marketing ↔ ledger 就是那条缝</b>，它现在没有 Maven 或扫描边界拦着 ——
 * 改由 {@code solvela-marketing} 里的 {@code LedgerBoundaryTest} 断言守：
 * <b>玩法侧要动资产只能经 {@code MemberProposalApi}</b>，不许直接引用
 * {@code solvela.ledger.*} 与 {@code solvela.risk.proposal.*}。
 *
 * <p>那条断言就是「将来还能不能便宜地拆出去」的全部保障。它红了别去改它，去改代码。
 *
 * <h3>⚠️ solvela.risk 现在是全包扫描</h3>
 * 此前只扫 {@code promotionconfig} / {@code promotiongroup} 两个子包，因为
 * {@code proposal} 会经 {@code AssetDispatcher} 牵出整个 ledger —— 而 ledger 属于另一个进程。
 * 现在两边同进程，精确扫描没有意义了。
 *
 * <p>但那次精确扫描暴露的事实仍然成立：<b>{@code solvela-risk} 装着两个不同归属的域</b> ——
 * 优惠配置跟着奖品走（营销），提案跟着资产走（资产）。查过了，这一刀切得很干净：
 * {@code engine}(8) 只被 {@code proposal}(9) 引用，{@code promotionconfig}(8) +
 * {@code promotiongroup}(7) 一处都不引用 proposal。资产域真要独立时按这条线拆。
 *
 * <h3>刻意没有 @EnableScheduling</h3>
 * 三个进程连同一个库，{@code @Scheduled} 每多开一个进程就多跑一遍 ——
 * 发奖、结算重复执行，而多发的那一份没人会主动来报。定时任务归 admin 独占。
 *
 * <h3>刻意没有 solvela.web</h3>
 * 那是管理端的 HTTP 层（2026-08-31 起住在 solvela-admin 里），它的
 * {@code GlobalExceptionHandler} 一律返回 200 + {@code ResponseDTO}。
 * 两个 {@code @RestControllerAdvice} 同时在场时，返回什么取决于哪个先匹配上 ——
 * 那是最难查的一类问题。本服务的错误出口只有 {@code InternalExceptionHandler} 一个。
 */
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan({
        // 本模块：启动类、HTTP 薄壳
        "solvela.biz.server",
        // 基础设施
        "solvela.base",
        // 服务间调用的错误出口。刻意不在 solvela.base 之下 —— 网关不能扫到它，
        // 否则那边会出现第二个 @RestControllerAdvice（见该类注释）
        "solvela.server.internal",
        // 脚本引擎（solvela.config 是它的自动配置，solvela.aspect 是执行监控切面）
        "solvela.scriptengine", "solvela.config", "solvela.aspect",
        // 营销域：活动、抽奖、任务、彩票、商城、统计、奖品
        "solvela.activity", "solvela.draw", "solvela.task", "solvela.lottery",
        "solvela.mall", "solvela.stat", "solvela.prize",
        // 发奖投递：中奖之后「把奖交出去」的唯一出口
        "solvela.dispatch",
        // 发奖派发：写 t_prize_log，再经 MemberProposalApi 把奖交给资产侧
        "solvela.consumer",
        // PII 加解密（散落在 model 里的 @Component）
        "solvela.crypto",
        // 会员域（含 solvela.member.session：会话签发在网关，吊销在这里）
        "solvela.member",
        // 资产域 + 风控与提案。⚠️ 见类注释：这是将来要拆出去的那一半，
        // 边界靠 LedgerBoundaryTest 守，不是靠这里
        "solvela.ledger", "solvela.risk"
})
@MapperScan(value = {
        "solvela.base", "solvela.activity", "solvela.draw", "solvela.task", "solvela.lottery",
        "solvela.mall", "solvela.stat", "solvela.prize", "solvela.scriptengine",
        "solvela.member", "solvela.ledger", "solvela.risk"
        // ⚠️ 这里【没有】 solvela.dispatch：删掉死掉的 PrizeDispatchOutboxDao 之后，
        // 那个包里一个 @Mapper 都不剩了。它仍在上面的 @ComponentScan 里
        // （LocalPrizeEventPublisher 在那儿）
}, annotationClass = Mapper.class)
@SpringBootApplication
public class BizApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BizApplication.class);
        // ip2region 的 xdb 要在容器启动前落到本地文件，会员登录日志的「IP 归属地」依赖它
        application.addListeners(new Ip2RegionListener());
        application.run(args);
    }
}
