package solvela.admin.auth;

import java.time.Duration;

/**
 * 一次签发的访问令牌。
 *
 * @param value     令牌原文，<b>只在签发这一刻存在</b> —— 服务端存的是它的 SHA-256 摘要，
 *                  丢了就只能重新登录，没有「查出来」这条路
 * @param expiresIn 绝对有效期，下发给前端用于提前续期
 */
public record AccessToken(String value, Duration expiresIn) {
}
