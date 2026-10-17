package solvela.security;

/**
 * 安全事件在 {@link SecurityKeys#INCIDENT_STREAM} 里的<b>线上格式</b>：投递方（网关、会员域）与消费方（app-biz）共用。
 * 字段与 {@code t_security_incident} 一一对应，取值含义以那张表的列注释为准。
 *
 * <h3>时间一律传 epoch，不传 LocalDateTime</h3>
 * 网关与 app-biz 是两个进程，各自的时区配置未必一致；而 MySQL 服务端跑在 UTC、应用会话强制 Asia/Shanghai
 * （SQL README）。传「墙上时间」的话，同一个时刻在两边会被解释成差 8 小时的两个值。
 * 消费方落库时用自己的会话时区还原。
 *
 * <h3>🔴 {@code matchedSample}、{@code userAgent} 是攻击者可控的内容</h3>
 * 投递方负责截断（128 / 256），消费方不信任它、照样截断；后台只按纯文本展示。
 * 投递方绝不能把请求体、完整查询串、密码类字段放进来。
 *
 * <p>⚠️ 加字段向后兼容（消费方忽略不认识的字段）；删字段、改含义要两边同时发版。
 *
 * @param incidentType         1-蜜罐接口, 2-蜜罐账号, 3-关键字, 4-限流判罚 / 升档
 * @param action               0-仅记录, 1-拉灰, 2-拉黑
 * @param suppressReason       0-无, 1-跨站发起, 2-熔断中, 3-老设备豁免（仅 IP 部分）
 * @param penaltyLevel         限流判罚的档位（1–6），其它类型为 null
 * @param identityHashHex      蜜罐账号命中时的身份摘要（十六进制），其它为 null
 * @param matchedField         1-UA, 2-路径, 3-查询参数, 4-请求体字段
 * @param penaltyUntilEpochSec 处罚到期时间（epoch 秒），没有处罚为 null
 * @param occurredAtEpochMilli 发生时间（epoch 毫秒）
 * @Date 2026-10-17
 */
public record SecurityIncidentEvent(
        int incidentType,
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
        String traceId,
        long occurredAtEpochMilli) {
}
