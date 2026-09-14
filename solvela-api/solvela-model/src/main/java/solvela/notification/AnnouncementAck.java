package solvela.notification;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 强制确认公告的确认记录 —— 只给 {@code force_ack = 1} 的公告产生。
 *
 * <h3>🔴 只记确认过的，不预建「待确认」行</h3>
 * 反面做法是公告发布时给全部目标人群预建一批 {@code ack_time = NULL} 的待确认行 ——
 * 那就是「广播写扩散」原封不动的复刻：行数 = 人群数 × 必读公告数。<b>绝对不要。</b>
 *
 * <p>这个选择有个必须接受的后果：<b>能回答「谁确认了」，答不了「谁还没确认」。</b>
 * 后者 = 目标人群 ➖ ack 集合，是个反连接，人群一大就很贵；而要让它快就得物化人群名单，
 * 又回到 N 行。<b>所以不要提供那个查询。</b>
 *
 * <p>好在业务上不需要它 —— 强制确认公告的机制本身就是「没确认就每次进来都弹，
 * 直到确认为止」，它自带催办。
 *
 * <h3>为什么不叫 t_member_announcement_ack</h3>
 * 本项目的 {@code t_member_*} 前缀意味着「一人一行、生命周期跟着会员走」
 * （{@code t_member_wallet} / {@code t_member_coupon} 都是）。
 * 而这是张<b>关联表</b>：主键是 {@code (announcement_id, member_id)} 复合，
 * 生命周期跟着<b>公告</b>走（公告没了 ack 就没意义），主查询方向也是运营侧的
 * 「这条公告确认覆盖率多少」。关联表在本项目的命名规律是<b>主体在前、不加双前缀</b>
 * （{@code t_role_menu} / {@code t_task_prize_mapping}）。
 *
 * @Author alaric
 * @Date 2026-09-14
 * @Copyright weolwo
 */
@Data
@TableName("t_announcement_ack")
public class AnnouncementAck {

    private Long announcementId;

    /** 会员号：关联键 */
    private Long memberId;

    /** 确认时间：合规留痕，只增不改 */
    private LocalDateTime ackTime;

    /** 确认时 IP。合规场景可能要，平时为空也无妨 */
    private String ackIp;
}
