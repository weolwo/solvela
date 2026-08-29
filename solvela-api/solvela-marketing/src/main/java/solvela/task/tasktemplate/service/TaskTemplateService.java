package solvela.task.tasktemplate.service;

import solvela.enums.EnableStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.domain.PageResult;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCodeUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.task.constant.TaskTypeEnum;
import solvela.task.tasktemplate.dao.TaskTemplateDao;
import solvela.task.TaskTemplate;
import solvela.task.tasktemplate.domain.command.TaskTemplateAddCommand;
import solvela.task.tasktemplate.domain.query.TaskTemplateQuery;
import solvela.task.tasktemplate.domain.command.TaskTemplateSaveCommand;
import solvela.task.tasktemplate.domain.command.TaskTemplateUpdateCommand;
import solvela.task.tasktemplate.domain.dto.TaskTemplateOptionDTO;
import solvela.task.tasktemplate.domain.dto.TaskTemplateDTO;
import solvela.task.tasktemplate.manager.TaskTemplateManager;
import solvela.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public String generateTemplateCode() {
        return SolvelaCodeUtil.generateUniqueBizCode(SolvelaCodeUtil.BizCodePrefix.TASK_TEMPLATE, this::existsByTemplateCode);
    }

    /**
     * 任务配置向导用的模板列表：ui_schema 以 JSON 对象下发，前端直接喂给 SchemaFormRenderer
     * 单个模板的 ui_schema 脏了不该让整个向导开不了，解析失败的模板跳过并告警
     */
    public List<TaskTemplateOptionDTO> queryOptionList() {
        // 只给启用中的模板：禁用的意义就是「不再让人拿它建新任务」，
        // 已经用它建好的任务不受影响（运行态按 template_code 取脚本，与这里的候选列表无关）
        List<TaskTemplate> list = taskTemplateManager.lambdaQuery()
                .eq(TaskTemplate::getStatus, EnableStatusEnum.ENABLED)
                .orderByDesc(TaskTemplate::getId)
                .list();
        List<TaskTemplateOptionDTO> optionList = new ArrayList<>(list.size());
        for (TaskTemplate template : list) {
            Map<String, Object> uiSchema = parseUiSchema(template);
            if (uiSchema == null) {
                continue;
            }
            optionList.add(new TaskTemplateOptionDTO(
                    template.getTemplateCode(),
                    template.getTemplateName(),
                    template.getTaskType(),
                    template.getTriggerEvent(),
                    stringValue(uiSchema.get(SCHEMA_KEY_ICON), DEFAULT_ICON),
                    stringValue(uiSchema.get(SCHEMA_KEY_DESC), ""),
                    uiSchema));
        }
        return optionList;
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
    public Boolean save(TaskTemplateSaveCommand saveForm) {
        String schemaError = checkUiSchema(saveForm.getUiSchema());
        if (schemaError != null) {
            throw new BusinessException(schemaError);
        }
        String contractError = checkTargetParamDeclared(saveForm.getTaskType(), saveForm.getUiSchema());
        if (contractError != null) {
            throw new BusinessException(contractError);
        }

        TaskTemplate taskTemplate = SolvelaBeanUtil.copy(saveForm, TaskTemplate.class);
        taskTemplate.setUiSchema(JsonUtils.toJson(saveForm.getUiSchema()));

        TaskTemplate existed = getByTemplateCode(saveForm.getTemplateCode());
        boolean createFlag = existed == null;
        if (createFlag) {
            taskTemplateDao.insert(taskTemplate);
        } else {
            taskTemplate.setId(existed.getId());
            taskTemplateDao.updateById(taskTemplate);
        }
        return createFlag;
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
     * 🔴 契约校验：ui_schema 必须声明该 task_type 用来判达标的目标参数。
     *
     * <p><b>为什么值得为它单开一道校验</b>：这是本模块最难查的一类错。
     * 模板作者把参数键起成 {@code targetX}，运营照样能配出任务、事件照样进来、进度照样涨，
     * 就是<b>永远不完成</b> —— 没有异常、没有失败流水、没有任何线索，
     * 因为从每一层看它都在正常工作。把它提前成「保存模板时就报错」是唯一能堵住的地方。
     *
     * <p>校验的是 ui_schema 里<b>声明</b>了这个参数，而不是它有值 ——
     * 有没有值是向导提交时的事（{@code TaskConfigService.wizardSubmit} 已按 required 反向校验）。
     *
     * @return null 表示通过
     */
    @SuppressWarnings("unchecked")
    private String checkTargetParamDeclared(String taskTypeValue, Map<String, Object> uiSchema) {
        TaskTypeEnum taskType = TaskTypeEnum.resolve(taskTypeValue);
        if (taskType == null) {
            return "任务类型非法：" + taskTypeValue + "，可选值 "
                    + java.util.Arrays.stream(TaskTypeEnum.values()).map(TaskTypeEnum::getValue).toList();
        }
        List<String> requiredKeys = taskType.targetParamKeys();
        if (requiredKeys.isEmpty()) {
            return null;
        }
        Set<String> declared = new HashSet<>();
        for (Object item : (List<Object>) uiSchema.get("params")) {
            if (item instanceof Map<?, ?> param && param.get("key") instanceof String key) {
                declared.add(key);
            }
        }
        if (requiredKeys.stream().anyMatch(declared::contains)) {
            return null;
        }
        // 报错要给出可直接照抄的键名，别让人去翻文档
        return "任务类型 " + taskType.getValue() + "（" + taskType.getDesc()
                + "）必须在 ui_schema 的 params 里声明目标参数「" + requiredKeys.get(0)
                + "」，否则任务进度会一直涨但永远判不了达标（且不会报任何错）。"
                + "当前已声明的参数：" + (declared.isEmpty() ? "无" : declared);
    }

    /**
     * 分页查询
     */
    public PageResult<TaskTemplateDTO> queryPage(TaskTemplateQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<TaskTemplateDTO> list = taskTemplateDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     * 模板编码允许手工输入，故服务端必须重校验唯一性（格式由 AddForm 的 @Pattern 拦）
     */
    public void add(TaskTemplateAddCommand addForm) {
        if (existsByTemplateCode(addForm.getTemplateCode())) {
            throw new BusinessException("模板编码已存在：" + addForm.getTemplateCode());
        }
        // 这两条 CRUD 接口收的是 ui_schema 字符串，是绕过设计器的另一条入口 ——
        // 校验必须两边都做，否则「防不住绕过页面直接 POST」（铁律 2）
        String error = checkRawUiSchema(addForm.getTaskType(), addForm.getUiSchema());
        if (error != null) {
            throw new BusinessException(error);
        }
        TaskTemplate taskTemplate = SolvelaBeanUtil.copy(addForm, TaskTemplate.class);
        taskTemplateDao.insert(taskTemplate);
    }

    /**
     * 更新
     *
     */
    public void update(TaskTemplateUpdateCommand updateForm) {
        String error = checkRawUiSchema(updateForm.getTaskType(), updateForm.getUiSchema());
        if (error != null) {
            throw new BusinessException(error);
        }
        TaskTemplate taskTemplate = SolvelaBeanUtil.copy(updateForm, TaskTemplate.class);
        taskTemplateDao.updateById(taskTemplate);
    }

    /**
     * ui_schema 以字符串传入时的校验入口（生成器 CRUD 用）。
     *
     * <p>解析失败直接判非法：一个存不进去的 JSON 比一个「存进去了但解析不了」的好得多 ——
     * 后者会在向导拉模板列表时被静默剔除，运营只会看到「我建的模板不见了」。
     */
    @SuppressWarnings("unchecked")
    private String checkRawUiSchema(String taskType, String uiSchemaJson) {
        Map<String, Object> uiSchema;
        try {
            uiSchema = JsonUtils.parseObject(uiSchemaJson, Map.class);
        } catch (RuntimeException e) {
            return "ui_schema 不是合法 JSON";
        }
        if (uiSchema == null) {
            return "ui_schema 不是合法 JSON";
        }
        String schemaError = checkUiSchema(uiSchema);
        return schemaError != null ? schemaError : checkTargetParamDeclared(taskType, uiSchema);
    }

    /**
     * 模板启用 / 禁用（单个开关与批量禁用共用）。
     *
     * <p>管理端用它替代删除：模板被 t_task_config.template_code 引用，运行态还要按 code 取
     * ui_schema / rule_script。删掉不会立刻报错，而是让引用它的任务安静地不再推进 ——
     * 禁用则只是不再出现在向导的候选里，存量任务照常跑。
     */
    public void updateStatus(List<Long> idList, EnableStatusEnum status) {
        // 枚举只有两个取值，原先那段「只能是 1 或 0」的运行期校验已无意义
        for (Long id : idList) {
            TaskTemplate update = new TaskTemplate();
            update.setId(id);
            update.setStatus(status);
            taskTemplateDao.updateById(update);
        }
    }

    /**
     * 模板详情：供模板设计器的编辑态回显 ui_schema / rule_script。
     */
    public TaskTemplateDTO detail(Long id) {
        TaskTemplate template = taskTemplateDao.selectById(id);
        if (template == null) {
            throw new BusinessException("任务模板不存在");
        }
        return SolvelaBeanUtil.copy(template, TaskTemplateDTO.class);
    }

    /**
     * 批量删除
     */
    public void batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }

        taskTemplateDao.deleteBatchIds(idList);
    }

    /**
     * 单个删除
     */
    public void delete(Long id) {
        if (null == id){
            return;
        }

        taskTemplateDao.deleteById(id);
    }
}
