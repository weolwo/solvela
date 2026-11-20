package solvela.security;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 安全事件在 {@link SecurityKeys#EVENT_QUEUE} 里的<b>线上格式</b>：投递方（网关、会员域）与消费方（后台的落库任务）共用。
 * 字段与 {@code t_security_event} 一一对应，取值含义以那张表的列注释为准；另有两个只给消费方的指令
 * （{@code listUntilEpochSec}、{@code observeDevice}），不落库。
 *
 * <h3>时间一律传 epoch，不传 LocalDateTime</h3>
 * 网关与后台是两个进程，各自的时区配置未必一致；而 MySQL 服务端跑在 UTC、应用会话强制 Asia/Shanghai
 * （SQL README）。传「墙上时间」的话，同一个时刻在两边会被解释成差 8 小时的两个值。
 * 消费方落库时交给 SQL 的 {@code FROM_UNIXTIME} 按会话时区还原。
 *
 * <h3>🔴 {@code matchedSample}、{@code userAgent} 是攻击者可控的内容</h3>
 * 投递方负责截断（128 / 256），消费方不信任它、照样截断；后台只按纯文本展示。
 * 投递方绝不能把请求体、完整查询串、密码类字段放进来。
 *
 * <h3>谁决定处置</h3>
 * 投递方。它知道自己刚做了什么（罚没罚、被哪道保险压下了）以及配置里的时长；消费方只照着 {@code action} 改名单，
 * 不再读一遍网关的配置 —— 两边各读一份，迟早读出两个答案。
 *
 * <p>⚠️ 加字段向后兼容（消费方忽略不认识的字段）；删字段、改含义要两边同时发版。
 *
 * @param eventType            {@link #HONEYPOT} / {@link #CANARY} / {@link #KEYWORD} / {@link #RATE_LIMIT} / {@link #UNKNOWN_FIELD}
 * @param action               {@link #ACTION_RECORD} / {@link #ACTION_GREY} / {@link #ACTION_BLACK}：对客户端 IP 的名单处置
 * @param suppressReason       {@link #SUPPRESS_NONE} / {@link #SUPPRESS_CROSS_SITE} / {@link #SUPPRESS_BREAKER} / {@link #SUPPRESS_OLD_DEVICE}
 * @param penaltyLevel         限流判罚的档位（1–6），其它类型为 null
 * @param identityHashHex      蜜罐账号命中时的身份摘要（十六进制），其它为 null
 * @param matchedField         {@link #FIELD_UA} / {@link #FIELD_PATH} / {@link #FIELD_QUERY} / {@link #FIELD_BODY}
 * @param penaltyUntilEpochSec 处罚到期时间（epoch 秒），没有处罚为 null
 * @param listUntilEpochSec    名单条目的到期（epoch 秒）；{@code action} 为「仅记录」时为 null
 * @param observeDevice        要不要把这台设备推进观察档（限流的设备维度罚到高档时）
 * @param memberListUntilEpochSec 把 {@code memberId} 拉进会员灰名单到这一刻（epoch 秒）；不拉为 null。
 *                             只会来自<b>登录后</b>场景的账号维度 —— 匿名场景的「账号」是别人填的目标，永远不拉灰
 * @param occurredAtEpochMilli 发生时间（epoch 毫秒）
 * @Date 2026-10-17
 */
public record SecurityEvent(
        /*
         * 🔴 改名前叫 incidentType。旧版生产方发来的 JSON 里只有 incidentType 的话，不认这个名字时
         * Jackson 3（默认 FAIL_ON_NULL_FOR_PRIMITIVES）会因为 int 拿不到值而抛异常，落库任务把它当坏事件丢弃 ——
         * 发版间隙里旧版网关投的每一条事件都会这样丢掉，只剩一行 ERROR。
         * TEMP(安全事件改名-收口): 旧名只作读入别名；移除条件同 SecurityKeys.LEGACY_INCIDENT_QUEUE
         * （旧队列清空、旧版生产方全部下线后，不会再有带 incidentType 的消息）
         */
        @JsonAlias("incidentType")
        int eventType,
        String ruleCode,
        int action,
        int suppressReason,
        Integer penaltyLevel,
        String clientIp,
        String deviceId,
        Long memberId,
        String identityHashHex,
        String httpMethod,
        String requestPath,
        Integer matchedField,
        String matchedParam,
        String matchedSample,
        String userAgent,
        Long penaltyUntilEpochSec,
        Long listUntilEpochSec,
        boolean observeDevice,
        Long memberListUntilEpochSec,
        String traceId,
        long occurredAtEpochMilli) {

    public static final int HONEYPOT = 1, CANARY = 2, KEYWORD = 3, RATE_LIMIT = 4;

    /** 请求体里有请求对象没有的字段。<b>低置信、只记录</b>：前后端版本错开时真人也会撞到 */
    public static final int UNKNOWN_FIELD = 5;

    public static final int ACTION_RECORD = 0, ACTION_GREY = 1, ACTION_BLACK = 2;

    public static final int SUPPRESS_NONE = 0, SUPPRESS_CROSS_SITE = 1, SUPPRESS_BREAKER = 2, SUPPRESS_OLD_DEVICE = 3;

    public static final int FIELD_UA = 1, FIELD_PATH = 2, FIELD_QUERY = 3, FIELD_BODY = 4;

    public static Builder builder(int eventType, String ruleCode) {
        return new Builder(eventType, ruleCode);
    }

    /** 二十个字段的记录，按位置传参必然传错一个 */
    public static final class Builder {

        private final int eventType;
        private final String ruleCode;
        private int action;
        private int suppressReason;
        private Integer penaltyLevel;
        private String clientIp;
        private String deviceId;
        private Long memberId;
        private String identityHashHex;
        private String httpMethod;
        private String requestPath;
        private Integer matchedField;
        private String matchedParam;
        private String matchedSample;
        private String userAgent;
        private Long penaltyUntilEpochSec;
        private Long listUntilEpochSec;
        private boolean observeDevice;
        private Long memberListUntilEpochSec;
        private String traceId;

        private Builder(int eventType, String ruleCode) {
            this.eventType = eventType;
            this.ruleCode = ruleCode;
        }

        public Builder action(int action, Long listUntilEpochSec) {
            this.action = action;
            this.listUntilEpochSec = listUntilEpochSec;
            return this;
        }

        public Builder suppressed(int reason) {
            this.suppressReason = reason;
            return this;
        }

        public Builder penalty(Integer level, Long untilEpochSec) {
            this.penaltyLevel = level;
            this.penaltyUntilEpochSec = untilEpochSec;
            return this;
        }

        public Builder who(String clientIp, String deviceId, Long memberId) {
            this.clientIp = clientIp;
            this.deviceId = deviceId;
            this.memberId = memberId;
            return this;
        }

        public Builder identityHash(String hex) {
            this.identityHashHex = hex;
            return this;
        }

        public Builder request(String httpMethod, String requestPath, String userAgent, String traceId) {
            this.httpMethod = httpMethod;
            this.requestPath = truncate(requestPath, 256);
            this.userAgent = truncate(userAgent, 256);
            this.traceId = traceId;
            return this;
        }

        public Builder matched(Integer field, String param, String sample) {
            this.matchedField = field;
            this.matchedParam = truncate(param, 32);
            this.matchedSample = truncate(sample, 128);
            return this;
        }

        public Builder observeDevice(boolean observe) {
            this.observeDevice = observe;
            return this;
        }

        public Builder greyMember(Long untilEpochSec) {
            this.memberListUntilEpochSec = untilEpochSec;
            return this;
        }

        public SecurityEvent build() {
            return new SecurityEvent(eventType, ruleCode, action, suppressReason, penaltyLevel, clientIp,
                    deviceId, memberId, identityHashHex, httpMethod, requestPath, matchedField, matchedParam,
                    matchedSample, userAgent, penaltyUntilEpochSec, listUntilEpochSec, observeDevice,
                    memberListUntilEpochSec, traceId, System.currentTimeMillis());
        }
    }

    /** 截断到 {@code max} 个字符；null 原样返回 */
    public static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
