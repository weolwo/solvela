package sa.base.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * Mybatis Plus 插入或者更新时指定字段设置值
 *
 * ⚠️ create_time / update_time 已刻意不再在此填充，统一由数据库产生（DEFAULT CURRENT_TIMESTAMP
 * 与 ON UPDATE CURRENT_TIMESTAMP），原因见 v3.38.0.sql：
 *
 * 此前时间由 JVM 的 LocalDateTime.now() 填充，而绕过实体的 raw SQL 以及 ON UPDATE CURRENT_TIMESTAMP
 * 走的是 MySQL 时钟，两套时钟源并存产生过两类真实故障 ——
 *   ① 时区不一致：MySQL 在 UTC、JVM 在东八区，update_time 比 create_time 早整 8 小时；
 *   ② 亚秒舍入不一致：datetime(0) 截断时两边舍入方向不同，被 update 过的记录出现 update 早于 create 1 秒。
 * 只留数据库一个时钟即可根治，多实例部署时也不再受各节点 JVM 时钟漂移影响。
 *
 * ⚠️ 实体上的 {@code @TableField(fill = ...)} 注解<b>必须一并摘除</b>，只把这里改空是不够的。
 * MyBatis-Plus 3.5.17 的 {@code TableFieldInfo#getInsertSqlPropertyMaybeIf}：
 * <pre>
 *   if (withInsertFill) {
 *       return sqlScript;          // 直接返回，不走 convertIf，即不生成 &lt;if&gt; 判空
 *   }
 *   return convertIf(sqlScript, newPrefix + property, insertStrategy);
 * </pre>
 * 而 {@code withInsertFill = fill == INSERT || fill == INSERT_UPDATE}。
 * 只要注解还在，字段就会被无条件拼进 INSERT，此处不填得到的 null 会被<b>显式写入</b>，
 * 反而把 DDL 的 DEFAULT CURRENT_TIMESTAMP 挡掉 —— 实测已在 t_task_prize_mapping 留下 create_time 为 NULL 的行。
 *
 * 若将来需要自动填充**非时间**字段（如租户ID、操作人），在此扩展即可。
 *
 * @author zhoumingfa
 */
@Component
@Slf4j
public class MybatisPlusFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 时间字段交由数据库产生，此处刻意留空
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 时间字段交由数据库产生，此处刻意留空
    }

}
