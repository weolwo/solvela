package solvela.base.module.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.enums.BaseEnum;

/**
 * 系统配置常量类
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-03-14 20:46:27
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Getter
@AllArgsConstructor
public enum ConfigKeyEnum implements BaseEnum {

    /**
     * 万能密码
     */
    SUPER_PASSWORD("super_password", "万能密码"),

    LEVEL3_PROTECT_CONFIG("level3_protect_config", "三级等保配置"),

    /** 网关限流的全局参数（JSON）：阶梯、清零、封顶、老设备豁免。见 docs/架构/网关阶梯限流-技术方案.md §4 */
    GATEWAY_RATE_LIMIT("gateway_rate_limit", "网关限流"),

    /** 网关安全闸的全局参数（JSON）：蜜罐 / 关键字封禁、转灰、同段连坐、熔断。见同一方案 §5 */
    GATEWAY_SECURITY_GATE("gateway_security_gate", "网关安全闸"),
    ;

    private final String value;

    private final String desc;
}
