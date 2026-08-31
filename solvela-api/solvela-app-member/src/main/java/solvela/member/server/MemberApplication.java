package solvela.member.server;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import solvela.base.listener.Ip2RegionListener;

/**
 * 会员服务启动类：会员与会话、资产、提案与优惠配置、发奖派发。
 *
 * <p>四个进程之一：admin 1024 / app 1025(网关) / marketing 1026 / <b>member 1027</b>。
 *
 * <h3>🔴 组件扫描是「列出要什么」，不是「扫全世界」</h3>
 * 与 {@code AppApplication} / {@code MarketingApplication} 同一套做法。
 * 漏了会怎样：启动即失败，报 NoSuchBeanDefinitionException 并指名道姓说缺哪个。
 *
 * <h3>清单里为什么有 solvela.prize</h3>
 * 派发链路要读奖品配置、写发奖流水（{@code prize.prizeconfig} / {@code prize.prizelog}）。
 * 而营销侧配活动时也要读奖品配置 —— 所以 <b>{@code solvela-prize} 同时被两个服务装配</b>。
 *
 * <p>共库阶段这不是正确性问题（两边读同一张表），但它说明
 * {@code solvela-prize} 也横跨了两个归属：<b>奖品配置属营销，发奖流水属资产</b>。
 * 与 {@code solvela-risk} 是同一个毛病。真要拆库之前，这两个模块都得先按归属拆开。
 *
 * <p><b>写路径只能有一个服务拥有</b>：{@code prize_log} 只由本服务写，营销侧只读配置。
 * 这条是共库阶段唯一的硬规则 —— 两个服务都往同一张表写，是分布式单体里最难查的一类问题。
 *
 * <h3>清单里为什么【没有】 solvela.activity</h3>
 * 派发链路里 {@code ProposalSourceResolver} 原本要查活动类型。那属于
 * <b>发送方已经知道、却让接收方回头再查一次</b> —— 事件驱动里这是反模式：
 * 接收方一旦反向依赖发送方的域，两个服务就又绑在一起了。
 * 正解是把 {@code activityType} 放进消息本身，消费方自带上下文。
 *
 * <h3>刻意没有 @EnableScheduling</h3>
 * 定时任务归 admin 独占，理由同其它两个服务：四个进程连同一个库，多开一个就多跑一遍。
 */
@EnableCaching
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan({
        // 本模块：启动类、HTTP 薄壳、错误出口
        "solvela.member.server",
        // 基础设施
        "solvela.base",
        // 服务间调用的错误出口。刻意不在 solvela.base 之下 —— 网关不能扫到它，
        // 否则那边会出现第二个 @RestControllerAdvice（见该类注释）
        "solvela.server.internal",
        // 域：会员与会话、资产、风控与提案
        "solvela.member", "solvela.ledger", "solvela.risk",
        // PII 加解密（散落在 model 里的 @Component）
        "solvela.crypto"
})
@MapperScan(value = {
        "solvela.base", "solvela.member", "solvela.ledger", "solvela.risk"
}, annotationClass = Mapper.class)
@SpringBootApplication
public class MemberApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MemberApplication.class);
        // ip2region 的 xdb 要在容器启动前落到本地文件，会员登录日志的「IP 归属地」依赖它
        application.addListeners(new Ip2RegionListener());
        application.run(args);
    }
}
