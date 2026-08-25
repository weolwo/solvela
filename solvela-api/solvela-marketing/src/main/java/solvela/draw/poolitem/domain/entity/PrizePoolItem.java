package solvela.draw.poolitem.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 奖池奖项库 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */

@Data
@TableName("t_prize_pool_item")
public class PrizePoolItem {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 归属活动编码
     */
    private String activityCode;

    /**
     * 关联(t_prize_config)
     */
    private String prizeCode;

    /**
     * 单人限领次数: -1不限, 1表示每人最多中一次
     */
    private Integer userMaxCount;

    /**
     * 本次活动总共出几个？-1不限
     */
    private Integer totalStock;

    /**
     * 跨奖池累计已出数量
     */
    private Integer usedStock;

    /**
     * 版本号
     * 乐观锁插件生效的前提是，你调用的必须是 MyBatis-Plus 提供的
     * baseMapper.updateById(entity) 或 baseMapper.update(entity, wrapper) 方法。
     */
    @Version
    private Integer version;

    /**
     * 活动级白名单：指定用户必中
     */
    private String whiteList;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
