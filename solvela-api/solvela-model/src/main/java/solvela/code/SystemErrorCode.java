package solvela.code;

import lombok.Getter;

/**
 * 系统错误状态码（此类返回码应该高度重视）
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021/10/24 20:09
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Getter
public enum SystemErrorCode implements ErrorCode {

    /**
     * 系统错误
     */
    SYSTEM_ERROR("系统似乎出现了点小问题"),

    ;

    private final String msg;

    SystemErrorCode(String msg) {
        this.msg = msg;
    }

}

