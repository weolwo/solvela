package solvela.task.runtime.domain;

import tools.jackson.core.type.TypeReference;
import solvela.base.common.util.JsonUtils;
import solvela.task.constant.TaskConst;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code t_task_record.progress_data} 的类型化视图。
 *
 * <p><b>保留 raw 是刻意的</b>：读出来再写回去时，不认识的键必须原样带回。
 * 否则任何一个后加的键都会被上一版本的代码悄悄抹掉，
 * 而这类丢失在测试里几乎不可能被发现（字段还在、只是没了值）。
 *
 * @author alaric
 * @date 2026-08-01
 */
public record TaskProgressData(Map<String, Object> raw) {

    public TaskProgressData {
        raw = raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
    }

    public static TaskProgressData parse(String json) {
        if (json == null || json.isBlank()) {
            return new TaskProgressData(new LinkedHashMap<>());
        }
        Map<String, Object> map = JsonUtils.parseType(json, new TypeReference<Map<String, Object>>() {
        });
        return new TaskProgressData(map);
    }

    /**
     * 最近一次命中的自然日 yyyyMMdd，未命中过返回 null（STREAK 的断档判据）
     */
    public String lastHitDate() {
        Object value = raw.get(TaskConst.PROGRESS_KEY_LAST_HIT_DATE);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 已派发的档位。
     *
     * <p>⚠️ 展示字段，<b>不是判据</b>，允许滞后。发奖防重的唯一判据是
     * {@code t_prize_log.uk_external_biz}（幂等键 recordId:stageLevel）。
     */
    @SuppressWarnings("unchecked")
    public List<Integer> dispatchedStages() {
        Object value = raw.get(TaskConst.PROGRESS_KEY_DISPATCHED_STAGES);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>(list.size());
        for (Object item : (List<Object>) list) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            } else if (item != null) {
                try {
                    result.add(Integer.parseInt(String.valueOf(item).trim()));
                } catch (NumberFormatException ignored) {
                    // 脏数据单点降级：跳过这一项，不让一个坏值把整条记录读不出来
                }
            }
        }
        return result;
    }

    public TaskProgressData withLastHitDate(String yyyyMMdd) {
        Map<String, Object> next = new LinkedHashMap<>(raw);
        next.put(TaskConst.PROGRESS_KEY_LAST_HIT_DATE, yyyyMMdd);
        return new TaskProgressData(next);
    }

    /**
     * 追加一个已派发档位（去重且保持升序，便于人读）
     */
    public TaskProgressData withDispatchedStage(Integer stageLevel) {
        List<Integer> stages = new ArrayList<>(dispatchedStages());
        if (stageLevel != null && !stages.contains(stageLevel)) {
            stages.add(stageLevel);
            stages.sort(Integer::compareTo);
        }
        Map<String, Object> next = new LinkedHashMap<>(raw);
        next.put(TaskConst.PROGRESS_KEY_DISPATCHED_STAGES, stages);
        return new TaskProgressData(next);
    }

    public String toJson() {
        return JsonUtils.toJson(raw);
    }
}
