package solvela.draw;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 抽奖配置 实体类。<b>抽奖玩法这一层的配置</b>，与 {@code t_lottery_config} 同层。
 *
 * <h3>它补的是哪个洞</h3>
 * 抽奖原本只有两层：活动 → N 个奖池，中间没有「抽奖玩法」这一层。
 * 于是玩法级的参数（重置周期、抽奖算法）只能挂到奖池上 ——
 * 那是<b>挂错了层</b>：奖池回答「奖项怎么摆」，玩法回答「这次抽奖怎么玩」。
 *
 * <p>直接后果是脚本没地方挂：脚本引擎要一个业务对象编码，而「一次抽奖」当时不是一个对象。
 * {@code draw_code} 就是为此而来的。
 *
 * <h3>一个活动一套</h3>
 * 由唯一键 {@code uk_draw_activity} 保证，不靠代码自觉。
 * 将来若要放开成一个活动多套抽奖，去掉那个唯一键即可 —— {@code draw_code} 本身是全局唯一的，
 * 已经为那一天留好了位置。
 *
 * @Date 2026-08-31
 */
@Data
@TableName("t_draw_config")
public class DrawConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动编码。一个活动至多一条（uk_draw_activity）
     */
    private String activityCode;

    /**
     * 抽奖配置编码。<b>脚本挂载点 {@code DRAW_PLAY} 用的就是它</b>
     */
    private String drawCode;

    /**
     * 抽奖名称
     */
    private String drawName;

    /**
     * 抽奖算法。
     *
     * <p>⚠️ <b>目前只有「按概率」是真的</b>：{@code STOCK_RATIO} 还没有实现，
     * 选了它照样按概率抽。这一列从奖池上移过来时就是这个状态，上移没有改变它 ——
     * 真要做第二种算法时，读它的地方在 {@code DrawExecuteService}。
     */
    private DrawModeEnum drawMode;

    /**
     * 重置周期：DAY / WEEK / MONTH / ACTIVITY。
     *
     * <p>🔴 它重置的是<b>单人限领计数</b>（{@code t_prize_pool_item.user_max_count}），
     * 不是「每人每天能抽几次」—— 后者全仓目前没有任何存储，由脚本自己决定。
     * 两者很容易混，取值含义以 {@code DrawPeriodResolver} 为准。
     */
    private String resetPeriod;

    /**
     * 状态：0-关闭, 1-开启
     */
    private EnableStatusEnum status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
