package solvela.task.taskevent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务事件注册表 实体类。
 *
 * <p>存在的意义：{@code trigger_event} 此前是前端常量数组 + DDL 注释里写死的 5 个值，
 * 加一个事件要改前端常量、改 DDL 注释、可能还要加后端枚举 ——
 * 与「新增任务模板前端零改动」的目标正面冲突。<b>事件本质是开放集合，不该用封闭枚举表达。</b>
 *
 * <p>⚠️ 时间字段上不要加 {@code @TableField(fill = ...)}（铁律 9）。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Data
@TableName("t_task_event")
public class TaskEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件编码：DAILY_SIGN / ORDER_PAID / GOODS_SHARE
     */
    private String eventCode;

    /**
     * 展示名：签到 / 支付成功 / 分享商品
     */
    private String eventName;

    /**
     * 计量来源：NONE(计次) 或 payload 里的字段名(计额)。
     *
     * <p>调用方显式传 {@code amount} 时以 amount 为准，本字段只是「没显式传时去哪儿找」。
     */
    private String metricSource;

    /**
     * 该事件会带哪些字段，供模板设计器提示与校验。
     *
     * <p>顺带解决一个当前无解的问题：模板作者写规则时根本不知道这个事件会带来哪些字段。
     */
    private String payloadSchema;

    /**
     * 上游是否必须带幂等单号：1-必须, 0-可按事件日兜底。
     *
     * <p>🔴 这是把「上游必须带幂等键」<b>从口头约定变成表里的强制契约</b>。
     * ORDER_PAID 这类有天然单号的必须置 1 —— 不带单号时服务端只能按事件日兜底，
     * 那对订单事件意味着「一天只算一笔」，是错的。
     */
    private Integer bizIdRequired;

    /**
     * 是否高频事件：1-是。
     *
     * <p>⚠️ <b>本期只建字段、不实现路由优化</b>。理由见 v3.47.0.sql 的列注释：
     * 没有真实流量剖面时，缓存判定的一致性方案是凭空设计。
     */
    private Integer isHighFrequency;

    /**
     * 是否记录被丢弃事件的流水：1-记录, 0-不记录。
     *
     * <p>丢弃流水是客诉自证的关键，但高频事件每条不匹配都写一行会把
     * {@code t_task_record_flow} 写爆，故做成开关。关掉时仍打 DEBUG 日志。
     */
    private Integer discardLogFlag;

    private String remark;

    /**
     * 状态：0-停用, 1-启用
     */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
