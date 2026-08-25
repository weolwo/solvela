package solvela.admin.module.system.datatracer.constant;


import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.common.enumeration.BaseEnum;

/**
 * 数据业务类型
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-07-23 19:38:52-
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@AllArgsConstructor
@Getter
public enum DataTracerTypeEnum implements BaseEnum {

    /**
     * 商品
     */
    GOODS(1, "商品"),

    /**
     *通知公告
     */
    OA_NOTICE(2, "OA-通知公告"),

    /**
     * 企业信息
     */
    OA_ENTERPRISE(3, "OA-企业信息"),

    /**
     * 定时任务配置变更。
     *
     * <p>🔴 定时任务能触发发奖与结算，改 cron、改参数、启停都属高危操作，
     * <b>改动无痕是不可接受的</b>。复用本模块而不是新建审计表 ——
     * 能力已经有了，新建等于多一份要维护的东西。
     */
    SOLVELA_JOB(10, "定时任务"),

    ;

    private final Integer value;

    private final String desc;
}
