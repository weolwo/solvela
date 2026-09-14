package solvela.notification;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告已读游标 —— <b>一人一行</b>，与公告条数无关。
 *
 * <h3>这张表就是「不会爆」的那个形状</h3>
 * 发 10 条公告和发 10000 条公告，本表行数<b>完全一样</b>。这是读扩散 + 游标的全部意义。
 *
 * <p>参照物就在同一个库里：{@code t_member_wallet} 是
 * {@code UNIQUE(member_id, asset_type)}，一人 × 资产类型数行，比一人一行<b>更多</b>。
 * 钱包不爆，本表更不会。
 *
 * <h3>🔴 只有一个 bigint，没有例外集合</h3>
 * 早期方案给游标加过一个 {@code read_exception_ids} JSON 列来支持「跳读」
 * （点开中间某条，前面的仍算未读）。<b>已经删掉了</b>，因为：
 *
 * <ul>
 *   <li>前端公告与通知是<b>两个 tab</b>，两种已读语义各待在自己的列表里，
 *       用户不会在同一个列表看到「点通知清一条、点公告清一片」两种行为；</li>
 *   <li>那个 JSON 列会随用户跳读不断变宽，而一个不断变宽的列被频繁 UPDATE
 *       会导致 InnoDB 页分裂和碎片 —— 慢性的，但确实是雷。</li>
 * </ul>
 *
 * <p>🔴 将来若有人提「把两个 tab 合并成一个列表」，必须连带重新评估这里。
 *
 * <h3>懒创建</h3>
 * 不在注册时给每个人建行，用户第一次打开公告 tab 才 insert。
 * 没有行 = {@code lastReadId} 视为 0 = 所有未过期公告都未读（红点亮着），
 * 这恰好是想要的行为。效果是行数从注册用户数降到活跃用户数。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Data
@TableName("t_member_announcement_cursor")
public class MemberAnnouncementCursor {

    /** 会员号：一人一行，直接做主键 */
    @TableId
    private Long memberId;

    /**
     * 此 id 及以前的公告全部视为已读。
     *
     * <p>不支持跳读，所以只需要这一个 bigint —— 见类注释。
     */
    private Long lastReadId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
