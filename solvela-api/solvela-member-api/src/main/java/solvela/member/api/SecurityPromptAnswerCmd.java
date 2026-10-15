package solvela.member.api;

/**
 * 回答一条「刚才是你吗」。
 *
 * <h3>🔴 会员号与当前设备号都由入口层从凭证解析，不收客户端传的</h3>
 * {@code isMe = false} 会冻结资产出口 —— 收客户端传的会员号，等于任何人都能把别人的账号冻住。
 * 而当前设备号决定「这一答算不算数」（必须在受信任的老设备上答），同样不能让客户端自报。
 *
 * @param isMe true = 「是我」（弱标注：确认本人）；false = 「不是我」，等同「这不是我」的全套处置
 */
public record SecurityPromptAnswerCmd(Long memberId, String promptId, boolean isMe,
                                      String currentDeviceId, String clientIp) {
}
