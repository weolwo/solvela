package net.lab1024.sa.base.module.support.datamasking;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 脱敏数据类型
 *
 * @Author 1024创新实验室-创始人兼主任:卓大
 * @Date 2024/8/1
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a> ，Since 2012
 */

@AllArgsConstructor
@Getter
public enum DataMaskingTypeEnum {

    COMMON("通用"),
    PHONE("手机号"),
    CHINESE_NAME("中文名"),
    ID_CARD("身份证号"),
    FIXED_PHONE("座机号"),
    ADDRESS("地址"),
    EMAIL("电子邮件"),
    PASSWORD("密码"),
    CAR_LICENSE("中国大陆车牌"),
    BANK_CARD("银行卡"),
    USER_ID("用户id");



    /**
     * ⚠️ 原本还有一个 DesensitizedUtil.DesensitizedType 字段，随 hutool 一起删了。
     * 它从来没有被任何地方读取过 —— 打码规则全在 DataMaskingUtil 的 switch 里按枚举常量分发，
     * 那个字段只是把 hutool 的类型枚举又抄了一遍。
     */
    private String desc;


}
