package sa.member.id;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 号段批发器 —— <b>刻意独立成一个 Bean</b>。
 *
 * <p>🔴 <b>为什么不写在 {@link MemberIdAllocator} 里</b>（铁律 11）：
 * {@code @Transactional} 靠 Spring AOP 代理生效，<b>同类内部方法互调不经过代理</b>，
 * 注解会静默失效 —— 不报错、编译和单测全过，只在数据不一致时才暴露。
 * 本项目 2026-07-28 连踩两次，都是这个形状。
 * 判别口诀：凡是「一个方法里调用另一个带 {@code @Transactional} 的方法」，
 * 先问一句它俩是不是同一个类。
 *
 * @Date 2026-08-22
 */
@Slf4j
@Service
public class MemberIdSegmentFetcher {

    private final MemberIdSeqDao memberIdSeqDao;
    private final MemberIdProperties properties;

    public MemberIdSegmentFetcher(MemberIdSeqDao memberIdSeqDao, MemberIdProperties properties) {
        this.memberIdSeqDao = memberIdSeqDao;
        this.properties = properties;
    }

    /**
     * 批发一个号段，返回它的<b>上界</b>（不含）。本段区间是 [返回值 - step, 返回值)。
     *
     * <p>🔴 {@code REQUIRES_NEW}：发号必须<b>独立于调用方的事务</b>。
     * 注册流程通常包在一个事务里，如果发号跟着它回滚，水位会退回去 ——
     * 而这一段号可能已经发出去用了，退回就意味着<b>重复发号</b>。
     * 号段跳掉是无害的（对外号本来就是跳着走的），重复发号是致命的。
     * 所以这里宁可跳号也不回滚。
     *
     * <p>另外 {@code advance} 与 {@code lastSegmentEnd} 必须在同一个连接上执行 ——
     * {@code LAST_INSERT_ID()} 是<b>会话级</b>的。放在同一个事务里正好保证了这一点。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public Segment fetch() {
        int step = resolveStep();
        memberIdSeqDao.advance(step);
        long end = memberIdSeqDao.lastSegmentEnd();
        Segment segment = new Segment(end - step, end);
        log.info("[会员发号] 批发新号段 [{}, {})，step={}", segment.start(), segment.end(), step);
        return segment;
    }

    /**
     * 号段大小：库里的值优先，方便运维临时调整而不用发版；库里没有才回落到应用配置。
     */
    private int resolveStep() {
        Integer fromDb = memberIdSeqDao.selectStep();
        return (fromDb != null && fromDb > 0) ? fromDb : properties.getStep();
    }

    /** 一个号段，左闭右开。 */
    public record Segment(long start, long end) {
    }
}
