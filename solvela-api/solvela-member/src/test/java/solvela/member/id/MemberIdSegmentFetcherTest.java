package solvela.member.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 号段批发器的守卫。
 *
 * <h3>🔴 盯的是「种子行不存在」这一种启动即坏、却报得驴唇不对马嘴的情况</h3>
 * {@code t_member_id_seq} 是单行表，发号靠 {@code UPDATE ... WHERE id = 1}。
 * 那一行没被灌进去时（2026-09-12 上线首个注册就撞上了：schema 建了表、
 * data-baseline 漏了 seed），UPDATE 匹配 0 行、{@code LAST_INSERT_ID()} 返回 0，
 * 号段算成 {@code [-1000, 0)} —— 序号 -1000 一路飘到 {@code MemberIdCodec}
 * 才炸出「序号非法：-1000」，离真正的原因隔了好几层。
 *
 * <p>守卫把它拦在源头，报一句能直接照做的话（去 INSERT 那行）。
 */
@DisplayName("会员号段批发器")
class MemberIdSegmentFetcherTest {

    @Test
    @DisplayName("🔴 种子行不存在（advance 影响 0 行）→ 当场报清楚，而不是发出 -1000 号段")
    void 种子行缺失时当场失败() {
        MemberIdSeqDao dao = mock(MemberIdSeqDao.class);
        when(dao.selectStep()).thenReturn(1000);
        // UPDATE ... WHERE id = 1 没匹配到行
        when(dao.advance(1000)).thenReturn(0);

        MemberIdSegmentFetcher fetcher = new MemberIdSegmentFetcher(dao, new MemberIdProperties());

        IllegalStateException e = assertThrows(IllegalStateException.class, fetcher::fetch);
        assertTrue(e.getMessage().contains("t_member_id_seq"),
                "报错要点名是哪张表没初始化，而不是给一个 -1000 让人去猜");
    }

    @Test
    @DisplayName("正常：advance 命中 → 返回 [end-step, end) 号段")
    void 正常批发() {
        MemberIdSeqDao dao = mock(MemberIdSeqDao.class);
        when(dao.selectStep()).thenReturn(1000);
        when(dao.advance(1000)).thenReturn(1);
        when(dao.lastSegmentEnd()).thenReturn(1000L);

        MemberIdSegmentFetcher.Segment seg =
                new MemberIdSegmentFetcher(dao, new MemberIdProperties()).fetch();

        assertEquals(0L, seg.start());
        assertEquals(1000L, seg.end());
    }
}
