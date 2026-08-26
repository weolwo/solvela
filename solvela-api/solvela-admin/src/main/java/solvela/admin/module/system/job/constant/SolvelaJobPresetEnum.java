package solvela.admin.module.system.job.constant;

import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.base.enumeration.BaseEnum;

/**
 * 任务配置预设档位。
 *
 * <p>🔴 <b>这一条是给「好用」用的，它和「先进」有张力。</b>
 * 把超时、阻塞、错过、打散、重试全部开放出来之后，一个任务有十来个旋钮，
 * 运营面对这一屏下拉框会直接懵 —— <b>先进做到了，好用会被这堆旋钮毁掉。</b>
 *
 * <p>而这些参数本来就高度相关：轻量高频的任务几乎必然是 {@code DISCARD}
 * （错过一次无所谓，这正是它敢 DISCARD 的原因）；重批处理必然要 {@code FIRE_ONCE}
 * （那天的数据不能少）。<b>用档位把这种相关性表达出来，比让运营自己去悟这些组合便宜得多。</b>
 *
 * <p>两条实现约束：
 * <ol>
 *   <li><b>档位必须与执行器声明的 {@code lane} 兼容</b>，保存时校验：
 *       {@code lane = FAST} 的执行器只能选 {@link #LIGHT}，
 *       因为其余档位的超时都突破了快车道 30 秒的硬上限。</li>
 *   <li><b>{@code preset_code} 只记录来源，落库的是展开后的具体值。</b>
 *       以后调整档位默认值<b>不回溯</b>已建任务 ——
 *       否则改一次预设，几十个线上任务的行为一起变，那是灾难。</li>
 * </ol>
 *
 * @author alaric
 * @date 2026-08-11
 */
@Getter
@AllArgsConstructor
public enum SolvelaJobPresetEnum implements BaseEnum {

    /**
     * 轻量高频：探活、状态流转。跑在快车道，错过一次无所谓
     */
    LIGHT("LIGHT", "轻量高频", SolvelaJobLaneEnum.FAST,
            30, SolvelaJobBlockStrategyEnum.DISCARD,
            SolvelaJobMisfireStrategyEnum.SKIP, 60, 30, 0),

    /**
     * 常规：清理、扫描。
     *
     * <p>🔴 <b>它的 misfire 阈值是三档里最需要放宽的那个</b>（60 秒 → 5 分钟）。
     * 原因是它与背压跳过直接冲突：慢车道满时任务会被「跳过」留在库里排队，
     * 阈值若是 60 秒，排队超过一分钟就会被误判成 misfire，
     * 而它的策略是 SKIP —— 于是被静默丢弃，完全违背背压排队的初衷。
     *
     * <p>为什么危害最大的是这一档而不是另外两档：LIGHT 跑在快车道且丢了本就无所谓；
     * HEAVY 的策略是 FIRE_ONCE，误判成 misfire 之后是「立即补跑一次」，
     * 效果与排队后执行等价。只有 NORMAL 是「慢车道 + SKIP」，误判就是真丢。
     */
    NORMAL("NORMAL", "常规", SolvelaJobLaneEnum.SLOW,
            300, SolvelaJobBlockStrategyEnum.DISCARD,
            SolvelaJobMisfireStrategyEnum.SKIP, 300, 60, 1),

    /**
     * 重批处理：统计、对账。串行 + 补跑 —— 那天的数据不能少
     */
    HEAVY("HEAVY", "重批处理", SolvelaJobLaneEnum.SLOW,
            1800, SolvelaJobBlockStrategyEnum.SERIAL,
            SolvelaJobMisfireStrategyEnum.FIRE_ONCE, 1800, 0, 2),

    /**
     * 自定义：运营展开「高级」逐项调过，保存时不再套用任何档位默认值
     */
    CUSTOM("CUSTOM", "自定义", null, 0, null, null, 0, 0, 0),

    ;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）
     */
    private final String value;

    private final String desc;

    /**
     * 该档位适用的车道；{@code null} 表示不限（CUSTOM）
     */
    private final SolvelaJobLaneEnum lane;

    private final int timeoutSeconds;

    private final SolvelaJobBlockStrategyEnum blockStrategy;

    private final SolvelaJobMisfireStrategyEnum misfireStrategy;

    /**
     * 🔴 判定 misfire 的阈值秒数。绝不能全局写死，理由见 {@link #NORMAL} 的注释
     */
    private final int misfireThresholdSeconds;

    private final int jitterSeconds;

    private final int retryTimes;

    public boolean isCustom() {
        return this == CUSTOM;
    }

    /**
     * 该档位是否适用于某个车道。CUSTOM 不受限（运营已明确表示自己知道在做什么）
     */
    public boolean matchLane(SolvelaJobLaneEnum targetLane) {
        return isCustom() || this.lane == targetLane;
    }

    public static SolvelaJobPresetEnum resolve(String value) {
        for (SolvelaJobPresetEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return CUSTOM;
    }
}
