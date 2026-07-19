package net.lab1024.sa.task.tasktemplate.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.JsonUtils;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.task.tasktemplate.dao.TaskTemplateDao;
import net.lab1024.sa.task.tasktemplate.domain.entity.TaskTemplate;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateAddForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateQueryForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateSaveForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateUpdateForm;
import net.lab1024.sa.task.tasktemplate.domain.vo.TaskTemplateVO;
import net.lab1024.sa.task.tasktemplate.manager.TaskTemplateManager;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 任务模板表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskTemplateService {

    private final TaskTemplateDao taskTemplateDao;
    private final TaskTemplateManager taskTemplateManager;

    /**
     * 按模板编码查询（向导校验 ruleConfig 时也会使用）
     */
    public TaskTemplate getByTemplateCode(String templateCode) {
        return taskTemplateManager.lambdaQuery().eq(TaskTemplate::getTemplateCode, templateCode).one();
    }

    /**
     * 任务模板设计器保存：按 templateCode upsert，保存前做 ui_schema 结构校验
     */
    public ResponseDTO<String> save(TaskTemplateSaveForm saveForm) {
        String schemaError = checkUiSchema(saveForm.getUiSchema());
        if (schemaError != null) {
            return ResponseDTO.userErrorParam(schemaError);
        }

        TaskTemplate taskTemplate = SmartBeanUtil.copy(saveForm, TaskTemplate.class);
        taskTemplate.setUiSchema(JsonUtils.toJson(saveForm.getUiSchema()));

        TaskTemplate existed = getByTemplateCode(saveForm.getTemplateCode());
        if (existed == null) {
            taskTemplateDao.insert(taskTemplate);
        } else {
            taskTemplate.setId(existed.getId());
            taskTemplateDao.updateById(taskTemplate);
        }
        return ResponseDTO.ok();
    }

    /**
     * ui_schema 结构校验：params 必须为数组，每项必须有 key/widget，visibleWhen 必须为 { field, eq } 结构
     * 返回 null 表示校验通过，否则返回错误信息
     */
    @SuppressWarnings("unchecked")
    private String checkUiSchema(Map<String, Object> uiSchema) {
        Object paramsObj = uiSchema.get("params");
        if (!(paramsObj instanceof List)) {
            return "ui_schema 缺少 params 数组";
        }
        for (Object item : (List<Object>) paramsObj) {
            if (!(item instanceof Map)) {
                return "ui_schema.params 的每一项必须为对象";
            }
            Map<String, Object> param = (Map<String, Object>) item;
            if (!(param.get("key") instanceof String key) || StringUtils.isBlank(key)) {
                return "ui_schema.params 存在缺少 key 的参数项";
            }
            if (!(param.get("widget") instanceof String widget) || StringUtils.isBlank(widget)) {
                return "参数「" + param.get("key") + "」缺少 widget";
            }
            Object visibleWhen = param.get("visibleWhen");
            if (visibleWhen != null) {
                if (!(visibleWhen instanceof Map<?, ?> condition)
                        || !condition.containsKey("field") || !condition.containsKey("eq")) {
                    return "参数「" + param.get("key") + "」的 visibleWhen 必须为 { field, eq } 结构";
                }
            }
        }
        return null;
    }

    /**
     * 分页查询
     */
    public PageResult<TaskTemplateVO> queryPage(TaskTemplateQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TaskTemplateVO> list = taskTemplateDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(TaskTemplateAddForm addForm) {
        TaskTemplate taskTemplate = SmartBeanUtil.copy(addForm, TaskTemplate.class);
        taskTemplateDao.insert(taskTemplate);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(TaskTemplateUpdateForm updateForm) {
        TaskTemplate taskTemplate = SmartBeanUtil.copy(updateForm, TaskTemplate.class);
        taskTemplateDao.updateById(taskTemplate);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        taskTemplateDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        taskTemplateDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
