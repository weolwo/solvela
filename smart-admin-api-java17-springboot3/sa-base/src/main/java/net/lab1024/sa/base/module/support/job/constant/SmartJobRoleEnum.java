package net.lab1024.sa.base.module.support.job.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.lab1024.sa.base.common.enumeration.BaseEnum;

/**
 * 定时任务节点角色。
 *
 * <p>🔴 <b>本枚举存在的意义是把「执行」和「管理」拆开</b>（方案 §1.1）：
 * 原实现里管理接口 {@code SmartJobService} 与消息通道都挂着
 * {@code @ConditionalOnBean(SmartJobAutoConfiguration.class)}，
 * 而那个配置类由 {@code smart.job.enabled} 控制 ——
 * 于是「业务节点不执行任务、但仍能在后台管理任务」这个诉求<b>物理上做不到</b>：
 * 关掉 enabled，后台菜单直接报错。
 *
 * <p>而这恰恰是独立部署要的形态：同一份 fat jar，靠启动参数分角色。
 * <pre>
 * java -jar sa-admin.jar --smart.job.role=ADMIN    # 业务节点：能管不能跑
 * java -jar sa-admin.jar --smart.job.role=WORKER   # 任务节点：能跑不接流量
 * </pre>
 * 好处是业务代码发版只滚 ADMIN 节点，WORKER 不动，正在跑的任务一次都不受影响。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Getter
@AllArgsConstructor
public enum SmartJobRoleEnum implements BaseEnum {

    /**
     * 只装管理接口：CRUD、手动触发、日志查询。不执行任何任务
     */
    ADMIN("ADMIN", "仅管理"),

    /**
     * 只装调度与执行。不暴露管理接口
     */
    WORKER("WORKER", "仅执行"),

    /**
     * 两者都装。开发环境与小规模单机部署的默认值
     */
    ALL("ALL", "管理+执行"),

    /**
     * 都不装。纯 C 端网关节点
     */
    NONE("NONE", "不启用"),

    ;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）：{@code @CheckEnum} 的校验器用
     * {@code map(BaseEnum::getValue)} 建白名单，叫 code 会导致 Lombok 生成不出 getValue()
     */
    private final String value;

    private final String desc;

    public boolean isAdmin() {
        return this == ADMIN || this == ALL;
    }

    public boolean isWorker() {
        return this == WORKER || this == ALL;
    }
}
