package solvela.member.api;

/**
 * 「这不是我」：用户在「我的登录设备」里举报了某一次登录。
 *
 * <h3>🔴 会员号从令牌解析而来，不收客户端传的</h3>
 * 这个命令会冻结资产出口、给流水打上「确认被盗」—— 收客户端传的会员号，
 * 等于任何人都能把别人的账号冻住。入口层（网关）负责填它。
 *
 * @param memberId         举报人
 * @param reportedDeviceId 被举报那次登录用的设备号。老会话可能没有（{@code null}）——
 *                         那时只冻结、不打标（打标要靠它才找得到流水）
 * @param loginEpochSecond 被举报那次登录的时间（Unix 秒）。0 表示未知（2026-09-10 之前签发的会话）
 * @param currentDeviceId  举报人<b>此刻</b>这台设备。信任撤销要保留它
 * @param clientIp         举报来源 IP，只进日志
 */
public record SessionReportCmd(Long memberId, String reportedDeviceId, long loginEpochSecond,
                              String currentDeviceId, String clientIp) {
}
