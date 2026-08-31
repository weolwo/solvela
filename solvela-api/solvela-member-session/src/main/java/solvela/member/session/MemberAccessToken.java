package solvela.member.session;

import java.time.Duration;

/**
 * 一次签发的访问令牌。
 *
 * @param value     令牌原文，<b>只在签发这一刻存在</b> —— 服务端存的是它的摘要，
 *                  丢了就只能重新登录，没有「查出来」这条路
 * @param expiresIn 有效期，下发给客户端用于提前续期
 */
public record MemberAccessToken(String value, Duration expiresIn) {
}
