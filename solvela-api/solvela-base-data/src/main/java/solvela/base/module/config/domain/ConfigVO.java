package solvela.base.module.config.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置信息
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-03-14 20:46:27
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class ConfigVO {
    /** 主键 */
    private Long configId;

    /** 参数key */
    private String configKey;

    /** 参数的值 */
    private String configValue;

    /** 参数名称 */
    private String configName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 上次修改时间 */
    private LocalDateTime updateTime;
}
