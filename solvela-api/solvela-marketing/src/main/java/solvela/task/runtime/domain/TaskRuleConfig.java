package solvela.task.runtime.domain;

import tools.jackson.core.type.TypeReference;
import solvela.base.json.JsonUtils;
import solvela.task.constant.TaskConst;
import solvela.task.constant.TaskTypeEnum;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code t_task_config.rule_config} 的类型化视图（三层分离的第②层，方案 §3.2）。
 *
 * <p>第②层是「怎么算的参数」，由 ui_schema 驱动生成、运营在向导里填。
 * 新增玩法绝大多数只该落在这一层 —— 不用改 Java，也不用写脚本。
 *
 * <p>刻意做成 record 包一个 Map 而不是给每种玩法定义一个 POJO：
 * 参数集合是<b>开放</b>的（模板作者随时能加一个 ui_schema 参数），
 * 定死字段等于每加一个参数就要改 Java，正好违背这一层存在的意义。
 *
 * @author alaric
 * @date 2026-08-01
 */
public record TaskRuleConfig(Map<String, Object> raw) {

    /**
     * 达标判定的容差（铁律 1：浮点求和判等一律带容差，严禁裸比较）。
     *
     * <p>金额累加走 BigDecimal 不会有二进制误差，但上游传进来的 amount 可能是
     * double 序列化过的（499.99999999999994 这种），不带容差会出现
     * 「界面显示已满 500 却判不达标」—— 与当年「显示 100% 却保存不了」是同一种死锁。
     */
    public static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

    public TaskRuleConfig {
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    public static TaskRuleConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return new TaskRuleConfig(Map.of());
        }
        Map<String, Object> map = JsonUtils.parseType(json, new TypeReference<Map<String, Object>>() {
        });
        return new TaskRuleConfig(map == null ? Map.of() : new HashMap<>(map));
    }

    /**
     * 任务类型。服务端在 wizardSubmit 时已从模板强制覆写进 rule_config，前端伪造不了
     */
    public TaskTypeEnum taskType() {
        return TaskTypeEnum.resolve(string(TaskConst.RULE_KEY_TASK_TYPE));
    }

    /**
     * 达标目标值：AMOUNT 读 {@code targetAmount}，其余读 {@code targetCount}。
     *
     * <p>末尾兜一个 {@code targetDays}：存量模板 {@code FRWAYF2X6N}（每日签到）的 ui_schema
     * 用的就是这个键。对齐本项目 {@code visibleWhen} 的做法 —— 主形态优先、兼容形态兜底、
     * <b>新增模板一律用主形态</b>。详见 {@link TaskConst#RULE_KEY_TARGET_DAYS_LEGACY}。
     *
     * @return 未配置返回 null，由调用方判定为「不达标」而不是默默当 0 处理 ——
     *         当 0 会让每个事件一进来就立刻达标发奖，比不完成危险得多
     */
    public BigDecimal target() {
        BigDecimal amount = decimal(TaskConst.RULE_KEY_TARGET_AMOUNT);
        if (amount != null) {
            return amount;
        }
        BigDecimal count = decimal(TaskConst.RULE_KEY_TARGET_COUNT);
        return count != null ? count : decimal(TaskConst.RULE_KEY_TARGET_DAYS_LEGACY);
    }

    /**
     * 单次事件的最小金额门槛，未配置返回 null（不限）
     */
    public BigDecimal minAmount() {
        return decimal(TaskConst.RULE_KEY_MIN_AMOUNT);
    }

    /**
     * 连续型允许断档的次数，缺省 0（断一次即清零）
     */
    public int tolerance() {
        BigDecimal value = decimal(TaskConst.RULE_KEY_TOLERANCE);
        return value == null ? 0 : Math.max(0, value.intValue());
    }

    /**
     * 是否已达标：{@code metric + 容差 >= target}
     */
    public boolean reached(BigDecimal metric, BigDecimal target) {
        if (metric == null || target == null) {
            return false;
        }
        return metric.add(TOLERANCE).compareTo(target) >= 0;
    }

    public String string(String key) {
        Object value = raw.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * JSON 里的数字可能被反序列化成 Integer / Double / String / BigDecimal，统一归一化。
     *
     * <p>走 {@code new BigDecimal(String)} 而不是 {@code BigDecimal.valueOf(double)}：
     * 后者会把 0.1 变成 0.1000000000000000055511151231257827，一路带进金额计算。
     */
    public BigDecimal decimal(String key) {
        Object value = raw.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
