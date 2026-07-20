package net.lab1024.sa.task.taskconfig.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.JsonUtils;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.task.prizemapping.dao.TaskPrizeMappingDao;
import net.lab1024.sa.task.prizemapping.domain.entity.TaskPrizeMapping;
import net.lab1024.sa.task.taskconfig.dao.TaskConfigDao;
import net.lab1024.sa.task.taskconfig.domain.entity.TaskConfig;
import net.lab1024.sa.task.taskconfig.domain.form.TaskConfigAddForm;
import net.lab1024.sa.task.taskconfig.domain.form.TaskConfigQueryForm;
import net.lab1024.sa.task.taskconfig.domain.form.TaskConfigUpdateForm;
import net.lab1024.sa.task.taskconfig.domain.form.TaskConfigWizardConfigForm;
import net.lab1024.sa.task.taskconfig.domain.form.TaskConfigWizardSubmitForm;
import net.lab1024.sa.task.taskconfig.domain.vo.TaskConfigVO;
import net.lab1024.sa.task.tasktemplate.domain.entity.TaskTemplate;
import net.lab1024.sa.task.tasktemplate.service.TaskTemplateService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 任务配置表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 20:55:10
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class TaskConfigService {

    private final TaskConfigDao taskConfigDao;
    private final TaskPrizeMappingDao taskPrizeMappingDao;
    private final TaskTemplateService taskTemplateService;

    /**
     * 任务状态：1-待生效
     */
    private static final Integer STATUS_PENDING = 1;

    /**
     * image_upload 控件的参数值随 uiConfig 提交，与前端 splitSchemaValues 同一拆分语义
     */
    private static final String WIDGET_IMAGE_UPLOAD = "image_upload";

    /**
     * 任务配置向导提交：主子表（t_task_config + t_task_prize_mapping）同一事务落库
     * 前端表单只是第一道防线，服务端按模板 ui_schema 反向校验参数完整性
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<Long> wizardSubmit(TaskConfigWizardSubmitForm form) {
        TaskConfigWizardConfigForm configForm = form.getTaskConfig();

        // 1. 模板必须存在
        TaskTemplate template = taskTemplateService.getByTemplateCode(configForm.getTemplateCode());
        if (template == null) {
            return ResponseDTO.userErrorParam("任务模板不存在：" + configForm.getTemplateCode());
        }

        // 2. 按模板 ui_schema 校验参数（必填、visibleWhen 可见性、image 参数归属）
        Map<String, Object> ruleConfig = new HashMap<>(configForm.getRuleConfig());
        Map<String, Object> uiConfig = configForm.getUiConfig() == null ? new HashMap<>() : new HashMap<>(configForm.getUiConfig());
        String paramError = checkParamBySchema(template.getUiSchema(), ruleConfig, uiConfig);
        if (paramError != null) {
            return ResponseDTO.userErrorParam(paramError);
        }

        // 3. taskType 以模板为准，服务端强制覆写，防止前端伪造；副标题/规则说明并入 ui_config 存储
        ruleConfig.put("taskType", template.getTaskType());
        if (StringUtils.isNotBlank(configForm.getTaskDesc())) {
            uiConfig.put("taskDesc", configForm.getTaskDesc());
        }
        if (StringUtils.isNotBlank(configForm.getRuleDesc())) {
            uiConfig.put("ruleDesc", configForm.getRuleDesc());
        }

        // 4. 主表落库
        TaskConfig taskConfig = SmartBeanUtil.copy(configForm, TaskConfig.class);
        taskConfig.setRuleConfig(JsonUtils.toJson(ruleConfig));
        taskConfig.setUiConfig(JsonUtils.toJson(uiConfig));
        taskConfig.setStatus(STATUS_PENDING);
        taskConfigDao.insert(taskConfig);

        // 5. 子表批量落库（insertBatch 由 CustomizedBaseMapper 提供）
        List<TaskPrizeMapping> mappingList = form.getPrizeMappingList().stream().map(item -> {
            TaskPrizeMapping mapping = new TaskPrizeMapping();
            mapping.setTenantId("0");
            mapping.setTaskConfigId(taskConfig.getId());
            mapping.setStageLevel(item.getStageLevel());
            mapping.setStageCondition(JsonUtils.toJson(Map.of("target", item.getStageCondition())));
            mapping.setPrizeCode(item.getPrizeCode());
            mapping.setPrizeMode(item.getPrizeMode());
            mapping.setPrizeStrategy(JsonUtils.toJson(Map.of("value", item.getPrizeValue())));
            return mapping;
        }).collect(Collectors.toList());
        taskPrizeMappingDao.insertBatch(mappingList);

        // 返回主表ID，前端成功页据此定位刚创建的任务
        return ResponseDTO.ok(taskConfig.getId());
    }

    /**
     * 按模板 ui_schema 反向校验提交参数：必填且当前可见的参数必须有值
     * 返回 null 表示校验通过，否则返回错误信息
     */
    @SuppressWarnings("unchecked")
    private String checkParamBySchema(String uiSchemaJson, Map<String, Object> ruleConfig, Map<String, Object> uiConfig) {
        Map<String, Object> uiSchema = JsonUtils.parseType(uiSchemaJson, new TypeReference<Map<String, Object>>() {
        });
        if (uiSchema == null || !(uiSchema.get("params") instanceof List)) {
            // 模板无参数定义则无需校验
            return null;
        }
        for (Object item : (List<Object>) uiSchema.get("params")) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> param = (Map<String, Object>) item;
            if (!Boolean.TRUE.equals(param.get("required"))) {
                continue;
            }
            // 被 visibleWhen 隐藏的必填项不校验、也不应提交，与前端语义一致
            if (!isParamVisible(param, ruleConfig)) {
                continue;
            }
            String key = String.valueOf(param.get("key"));
            Map<String, Object> source = WIDGET_IMAGE_UPLOAD.equals(param.get("widget")) ? uiConfig : ruleConfig;
            Object value = source.get(key);
            if (value == null || (value instanceof String str && StringUtils.isBlank(str))) {
                Object label = param.getOrDefault("label", key);
                return "模板参数「" + label + "」不能为空";
            }
        }
        return null;
    }

    /**
     * visibleWhen 可见性判定：契约主形态 { field, eq }，兼容 { key, value }（与前端 isSchemaParamVisible 同一语义）
     */
    @SuppressWarnings("unchecked")
    private boolean isParamVisible(Map<String, Object> param, Map<String, Object> ruleConfig) {
        Object visibleWhenObj = param.get("visibleWhen");
        if (!(visibleWhenObj instanceof Map)) {
            return true;
        }
        Map<String, Object> visibleWhen = (Map<String, Object>) visibleWhenObj;
        Object field = visibleWhen.containsKey("field") ? visibleWhen.get("field") : visibleWhen.get("key");
        Object expected = visibleWhen.containsKey("eq") ? visibleWhen.get("eq") : visibleWhen.get("value");
        return Objects.equals(ruleConfig.get(String.valueOf(field)), expected);
    }

    /**
     * 分页查询
     */
    public PageResult<TaskConfigVO> queryPage(TaskConfigQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<TaskConfigVO> list = taskConfigDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(TaskConfigAddForm addForm) {
        TaskConfig taskConfig = SmartBeanUtil.copy(addForm, TaskConfig.class);
        taskConfigDao.insert(taskConfig);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(TaskConfigUpdateForm updateForm) {
        TaskConfig taskConfig = SmartBeanUtil.copy(updateForm, TaskConfig.class);
        taskConfigDao.updateById(taskConfig);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        taskConfigDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        taskConfigDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
