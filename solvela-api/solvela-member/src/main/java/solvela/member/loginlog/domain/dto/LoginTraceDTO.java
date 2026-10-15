package solvela.member.loginlog.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次成功登录的「从哪来、什么时候」。只给登录风控的画像用。
 *
 * <p>刻意只有两列：画像要回答的是「他平时在哪些网段、上一次在哪」，
 * 多取一列就多一列要跟着登录日志的 schema 变的东西。
 *
 * @Date 2026-10-15
 */
@Data
public class LoginTraceDTO {

    private String clientIp;

    private LocalDateTime createTime;
}
