package net.lab1024.sa.task.tasktemplate.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.JsonUtils;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCodeUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.task.tasktemplate.dao.TaskTemplateDao;
import net.lab1024.sa.task.tasktemplate.domain.entity.TaskTemplate;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateAddForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateQueryForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateSaveForm;
import net.lab1024.sa.task.tasktemplate.domain.form.TaskTemplateUpdateForm;
import net.lab1024.sa.task.tasktemplate.domain.vo.TaskTemplateOptionVO;
import net.lab1024.sa.task.tasktemplate.domain.vo.TaskTemplateVO;
import net.lab1024.sa.task.tasktemplate.manager.TaskTemplateManager;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 任务模板表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 21:12:49
 * @Copyright weolwo
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TaskTemplateService {

    private final TaskTemplateDao taskTemplateDao;
    private final TaskTemplateManager taskTemplateManager;

    /**
     * ui_schema 里承载卡片展示信息的两个可选字段：模板设计器写在 JSON 里，向导卡片读出来渲染
     */
    private static final String SCHEMA_KEY_ICON = "icon";
    private static final String SCHEMA_KEY_DESC = "desc";
    private static final String DEFAULT_ICON = "🧩";

    /**
     * 按模板编码查询（向导校验 ruleConfig 时也会使用）
     */
    public TaskTemplate getByTemplateCode(String templateCode) {
        return taskTemplateManager.lambdaQuery().eq(TaskTemplate::getTemplateCode, templateCode).one();
    }

    /**
     * 模板编码是否已被占用（全局唯一，对齐 uk_task_tpl_code）
     */
    public boolean existsByTemplateCode(String templateCode) {
        return taskTemplateManager.lambdaQuery().eq(TaskTemplate::getTemplateCode, templateCode).exists();
    }

    /**
     * 生成一个未被占用的模板编码（10 位大写字母+数字），供模板设计器「生成」按钮调用
     */
    public ResponseDTO<String> generateTemplateCode() {
        return ResponseDTO.ok(SmartCodeUtil.generateUniqueBizCode(this::existsByTemplateCode));
    }

    /**
     * 任务配置向导用的模板列表：ui_schema 以 JSON 对象下发，前端直接喂给 SchemaFormRenderer
     * 单个模板的 ui_schema 脏了不该让整个向导开不了，解析失败的模板跳过并告警
     */
    public ResponseDTO<List<TaskTemplateOptionVO>> queryOptionList() {
        List<TaskTemplate> list = taskTemplateManager.lambdaQuery().orderByDesc(TaskTemplate::getId).list();
        List<TaskTemplateOptionVO> optionList = new ArrayList<>(list.size());
        for (TaskTemplate template : list) {
            Map<String, Object> uiSchema = parseUiSchema(template);
            if (uiSchema == null) {
                continue;
            }
            optionList.add(new TaskTemplateOptionVO(
                    template.getTemplateCode(),
                    template.getTemplateName(),
                    template.getTaskType(),
                    template.getTriggerEvent(),
                    stringValue(uiSchema.get(SCHEMA_KEY_ICON), DEFAULT_ICON),
                    stringValue(uiSchema.get(SCHEMA_KEY_DESC), ""),
                    uiSchema));
        }
        return ResponseDTO.ok(optionList);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseUiSchema(TaskTemplate template) {
        try {
            return JsonUtils.parseObject(template.getUiSchema(), Map.class);
        } catch (RuntimeException e) {
            log.error("[任务模板] ui_schema 解析失败，该模板已从向导列表剔除: {}", template.getTemplateCode(), e);
            return null;
        }
    }

    private String stringValue(Object value, String defaultValue) {
        return value instanceof String text && StringUtils.isNotBlank(text) ? text : defaultValue;
    }

    /**
     * 任务模板设计器保存：按 templateCode upsert，保存前做 ui_schema 结构校验
     *
     * @return true-新建模板，false-覆盖更新了已存在的模板（前端据此区分提示，防止误覆盖线上模板）
     */
    public ResponseDTO<Boolean> save(TaskTemplateSaveForm saveForm) {
        String schemaError = checkUiSchema(saveForm.getUiSchema());
        if (schemaError != null) {
            return ResponseDTO.userErrorParam(schemaError);
        }

        TaskTemplate taskTemplate = SmartBeanUtil.copy(saveForm, TaskTemplate.class);
        taskTemplate.setUiSchema(JsonUtils.toJson(saveForm.getUiSchema()));

        TaskTemplate existed = getByTemplateCode(saveForm.getTemplateCode());
        boolean createFlag = existed == null;
        if (createFlag) {
            taskTemplateDao.insert(taskTemplate);
        } else {
            taskTemplate.setId(existed.getId());
            taskTemplateDao.updateById(taskTemplate);
        }
        return ResponseDTO.ok(createFlag);
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
     * 模板编码允许手工输入，故服务端必须重校验唯一性（格式由 AddForm 的 @Pattern 拦）
     */
    public ResponseDTO<String> add(TaskTemplateAddForm addForm) {
        if (existsByTemplateCode(addForm.getTemplateCode())) {
            return ResponseDTO.userErrorParam("模板编码已存在：" + addForm.getTemplateCode());
        }
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
