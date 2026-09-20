package solvela.member.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 会员人群口径。
 *
 * <h3>🔴 「新会员」的定义属于会员域，不许别人各算各的</h3>
 * {@code TaskEventContext.isNewMember} 的注释写着这一点：
 * 「营销域不拥有会员数据，而且『新会员』的定义本就属于会员域的业务概念
 * （注册 7 天内？首单前？各家不同），不该由任务引擎去猜」。
 *
 * <p>所以判定放在 {@link MemberService#isNewMember}，配置放在这里。
 * 打点的适配层只是<b>调用</b>它 —— 定义没有搬家。
 *
 * <p>⚠️ 反面做法是让适配层自己写 {@code createTime.isAfter(now.minusDays(7))}：
 * 编译通过、测试也过，但从那一刻起「新会员」就有了两个定义，
 * 而它们会在某次只改了一边的时候开始不一致 —— 表现是
 * 「限新会员的公告发到了他，限新会员的任务却不给他算」，没有任何报错。
 *
 * @author alaric
 * @date 2026-09-17
 */
@Data
@Component
@ConfigurationProperties(prefix = "solvela.member.audience")
public class MemberAudienceProperties {

    /**
     * 注册后多久之内算<b>新会员</b>。
     *
     * <p>默认 7 天。改它会同时影响所有「限新会员」的任务与营销规则 ——
     * 那是刻意的：一个口径只该有一个旋钮。
     */
    private Duration newMemberWithin = Duration.ofDays(7);
}
