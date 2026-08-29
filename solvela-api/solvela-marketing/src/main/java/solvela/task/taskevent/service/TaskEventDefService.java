package solvela.task.taskevent.service;

import solvela.enums.EnableStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.domain.PageResult;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.task.taskconfig.dao.TaskConfigDao;
import solvela.task.TaskConfig;
import solvela.task.taskevent.dao.TaskEventDao;
import solvela.task.TaskEvent;
import solvela.task.taskevent.domain.command.TaskEventAddCommand;
import solvela.task.taskevent.domain.query.TaskEventQuery;
import solvela.task.taskevent.domain.command.TaskEventUpdateCommand;
import solvela.task.taskevent.domain.dto.TaskEventOptionDTO;
import solvela.task.taskevent.domain.dto.TaskEventDTO;
import solvela.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务事件注册表 Service（配置态）。
 *
 * <p>运行态怎么用它见 {@code TaskEventService} —— 那边把本表当作
 * 「哪些事件是合法的、上游必须传什么」的<b>唯一真源</b>。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TaskEventDefService {

    private final TaskEventDao taskEventDao;
    private final TaskConfigDao taskConfigDao;

    /**
     * 计量来源的「不计量」取值
     */
    public static final String METRIC_SOURCE_NONE = "NONE";

    /** 0/1 布尔标记列的取值（bizIdRequired / isHighFrequency / discardLogFlag），与 status 无关 */
    private static final int FLAG_NO = 0;
    private static final int FLAG_YES = 1;

    public PageResult<TaskEventDTO> queryPage(TaskEventQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<TaskEventDTO> list = taskEventDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 向导下拉：只给启用中的。
     *
     * <p>{@code payloadSchema} 解析成对象下发，前端拿到即可直接渲染字段提示。
     * <b>单个事件的 schema 解析失败只降级这一个字段，不影响整个下拉</b> ——
     * 与 {@code TaskTemplateService.optionList} 对 ui_schema 的处理同一口径：
     * 一行脏数据不该让运营连事件都选不了。
     */
    public List<TaskEventOptionDTO> optionList() {
        return taskEventDao.selectEnabledList().stream()
                .map(e -> new TaskEventOptionDTO(
                        e.getEventCode(),
                        e.getEventName(),
                        e.getMetricSource(),
                        Integer.valueOf(1).equals(e.getBizIdRequired()),
                        parseSchemaQuietly(e)))
                .toList();
    }

    private Object parseSchemaQuietly(TaskEvent event) {
        if (StringUtils.isBlank(event.getPayloadSchema())) {
            return null;
        }
        try {
            return JsonUtils.parseType(event.getPayloadSchema(), new TypeReference<Object>() {
            });
        } catch (RuntimeException e) {
            log.warn("[任务事件] payload_schema 解析失败，该字段降级为空。eventCode={}", event.getEventCode(), e);
            return null;
        }
    }

    public void add(TaskEventAddCommand addForm) {
        TaskEvent exists = taskEventDao.selectByEventCode(addForm.getEventCode());
        if (exists != null) {
            // 唯一索引只是兜底：直接抛 SQL 异常对运营不友好
            throw new BusinessException("事件编码已存在：" + addForm.getEventCode());
        }
        TaskEvent entity = SolvelaBeanUtil.copy(addForm, TaskEvent.class);
        applyDefaults(entity);
        taskEventDao.insert(entity);
    }

    public void update(TaskEventUpdateCommand updateForm) {
        TaskEvent exists = taskEventDao.selectById(updateForm.getId());
        if (exists == null) {
            throw new BusinessException("事件不存在");
        }
        TaskEvent entity = SolvelaBeanUtil.copy(updateForm, TaskEvent.class);
        // eventCode 不在 UpdateForm 里（刻意的，见该类注释），这里显式带上原值，
        // 避免 updateById 把它更新成 null
        entity.setEventCode(exists.getEventCode());
        applyDefaults(entity);
        taskEventDao.updateById(entity);
    }

    /**
     * 删除守卫：仍被任务配置引用的事件不能删。
     *
     * <p>与活动/奖品的删除守卫同一模式。少了它，删掉一个事件后
     * 引用它的任务会安静地永远收不到事件 —— 没有报错、没有线索。
     * <b>建议优先用「停用」而不是删除</b>：停用同样阻断触发，但保留了这条记录，
     * 排查时能看出「这个事件是被人关掉的」，而删除只留下一片空白。
     */
    public void delete(Long id) {
        TaskEvent exists = taskEventDao.selectById(id);
        if (exists == null) {
            return;
        }
        Long refCount = taskConfigDao.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskConfig>()
                        .eq(TaskConfig::getTriggerEvent, exists.getEventCode()));
        if (refCount != null && refCount > 0) {
            throw new BusinessException("该事件已被 " + refCount
                    + " 个任务配置引用，不能删除。若要停止触发请改为「停用」");
        }
        taskEventDao.deleteById(id);
    }

    /**
     * 按编码取启用中的事件；不存在或已停用返回 null（运行态据此拒绝事件）
     */
    public TaskEvent getEnabledByCode(String eventCode) {
        if (StringUtils.isBlank(eventCode)) {
            return null;
        }
        TaskEvent event = taskEventDao.selectByEventCode(eventCode);
        return event != null && event.getStatus() == EnableStatusEnum.ENABLED ? event : null;
    }

    /**
     * 兜默认值：这几列都是 NOT NULL，靠调用方记得填迟早会漏
     * （「NOT NULL 无默认值」在本项目已复发 6 次）
     */
    private void applyDefaults(TaskEvent entity) {
        if (StringUtils.isBlank(entity.getMetricSource())) {
            entity.setMetricSource(METRIC_SOURCE_NONE);
        }
        // ⚠️ 下面三个是 0/1 的布尔标记列，与 status 不是一个概念。
        //    原先它们借用 STATUS_ENABLED/STATUS_DISABLED 赋值 —— 值恰好一样，语义完全不同，
        //    status 一旦改成枚举，这种借用就会把两件事绑死。故拆出独立常量。
        if (entity.getBizIdRequired() == null) {
            entity.setBizIdRequired(FLAG_NO);
        }
        if (entity.getIsHighFrequency() == null) {
            entity.setIsHighFrequency(FLAG_NO);
        }
        if (entity.getDiscardLogFlag() == null) {
            entity.setDiscardLogFlag(FLAG_YES);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(EnableStatusEnum.ENABLED);
        }
    }
}
