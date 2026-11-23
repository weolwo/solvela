package solvela.lottery;

import solvela.enums.LotteryConfigStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * 彩票配置 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */

@Data
@TableName("t_lottery_config")
public class LotteryConfig {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 彩票编码
     */
    private String lotteryCode;

    /**
     * 彩票名称
     */
    private String lotteryName;

    /**
     * 发号字符集
     */
    private String numberCharset;

    /**
     * 号码长度
     */
    private Integer numberLength;

    /**
     * 单期发行总数上限
     */
    private Integer totalCount;

    /**
     * 状态：0-下线, 1-上线
     *
     * <p>🔴 没有公开 setter：新建与复制都经 {@link #startOffline} 落为下线，上下线只走
     * {@code LotteryConfigDao.markOnline / markOffline}（状态机见 {@link LotteryConfigStatusEnum#canTransitTo}）。
     * 复制活动时 {@code SolvelaBeanUtil.copy} 会跳过这一列，源玩法是上线的也带不过来。
     */
    @Setter(AccessLevel.NONE)
    private LotteryConfigStatusEnum status;

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

    /**
     * 落为<b>下线</b>：新建与复制出来的玩法一律下线，上线是独立的动作（要先过上线前检查）。
     */
    public void startOffline() {
        if (status != null) {
            throw new IllegalStateException("彩票玩法 " + id + " 已是 " + status + "，不能再定初始状态");
        }
        this.status = LotteryConfigStatusEnum.OFFLINE;
    }
}
