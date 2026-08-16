package sa.task.taskconfig.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import sa.base.common.util.SmartCollectionUtil;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.JsonUtils;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartPageUtil;
import sa.task.prizemapping.dao.TaskPrizeMappingDao;
import sa.task.prizemapping.domain.entity.TaskPrizeMapping;
import sa.task.taskconfig.dao.TaskConfigDao;
import sa.task.taskconfig.domain.entity.TaskConfig;
import sa.task.taskconfig.domain.form.TaskConfigAddForm;
import sa.task.taskconfig.domain.form.TaskConfigQueryForm;
import sa.task.taskconfig.domain.form.TaskConfigStatusUpdateForm;
import sa.task.taskconfig.domain.form.TaskConfigUpdateForm;
import sa.task.taskconfig.domain.form.TaskConfigWizardConfigForm;
import sa.task.taskconfig.domain.form.TaskConfigWizardSubmitForm;
import sa.task.taskconfig.domain.form.TaskConfigWizardUpdateForm;
import sa.task.taskconfig.domain.vo.TaskConfigVO;
import sa.task.taskconfig.domain.vo.TaskConfigWizardDetailVO;
import sa.task.tasktemplate.domain.entity.TaskTemplate;
import sa.task.tasktemplate.service.TaskTemplateService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
     * 任务状态：2-生效中 / 3-已下线（对齐 TaskConst.CONFIG_STATUS_*）
     */
    private static final Integer STATUS_ACTIVE = 2;
    private static final Integer STATUS_OFFLINE = 3;

    /**
     * image_upload 控件的参数值随 uiConfig 提交，与前端 splitSchemaValues 同一拆分语义
     */
    private static final String WIDGET_IMAGE_UPLOAD = "image_upload";

    /**
     * rule_config / ui_config / 子表 JSON 里的固定 key。
     * 新增与回显是一对互逆操作，key 写死两遍迟早会漂 —— 收在这里。
     */
    private static final String KEY_TASK_TYPE = "taskType";
    private static final String KEY_TASK_DESC = "taskDesc";
    private static final String KEY_RULE_DESC = "ruleDesc";
    private static final String KEY_BADGE = "badge";
    private static final String KEY_STAGE_TARGET = "target";
    private static final String KEY_PRIZE_VALUE = "value";

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
        ruleConfig.put(KEY_TASK_TYPE, template.getTaskType());
        if (StringUtils.isNotBlank(configForm.getTaskDesc())) {
            uiConfig.put(KEY_TASK_DESC, configForm.getTaskDesc());
        }
        if (StringUtils.isNotBlank(configForm.getRuleDesc())) {
            uiConfig.put(KEY_RULE_DESC, configForm.getRuleDesc());
        }

        // 4. 主表落库
        TaskConfig taskConfig = SmartBeanUtil.copy(configForm, TaskConfig.class);
        taskConfig.setRuleConfig(JsonUtils.toJson(ruleConfig));
        taskConfig.setUiConfig(JsonUtils.toJson(uiConfig));
        taskConfig.setStatus(STATUS_PENDING);
        taskConfigDao.insert(taskConfig);

        // 5. 子表批量落库（insertBatch 由 CustomizedBaseMapper 提供）
        taskPrizeMappingDao.insertBatch(buildMappingList(form, taskConfig.getId()));

        // 返回主表ID，前端成功页据此定位刚创建的任务
        return ResponseDTO.ok(taskConfig.getId());
    }

    /**
     * 任务配置 上/下线（列表页的批量下线用它）。
     *
     * <p>替代删除：任务记录里存着 task_config_id，删配置会让历史记录指向一条不存在的配置。
     * 下线后运行态不再订阅该任务的事件（判据是 status != 3，见 TaskConst.CONFIG_STATUS_OFFLINE），
     * 已在跑的记录按接取时的快照走完，不受影响。
     */
    public ResponseDTO<String> updateStatus(TaskConfigStatusUpdateForm form) {
        if (!STATUS_PENDING.equals(form.getStatus())
                && !STATUS_ACTIVE.equals(form.getStatus())
                && !STATUS_OFFLINE.equals(form.getStatus())) {
            return ResponseDTO.userErrorParam("目标状态只能是 1-待生效、2-生效中 或 3-已下线");
        }
        for (Long id : form.getIdList()) {
            TaskConfig update = new TaskConfig();
            update.setId(id);
            update.setStatus(form.getStatus());
            taskConfigDao.updateById(update);
        }
        return ResponseDTO.ok();
    }

    /**
     * 向导回显：主子表一次性返回，结构与提交表单对称，前端拿到就能铺回 5 个步骤。
     */
    public ResponseDTO<TaskConfigWizardDetailVO> wizardDetail(Long id) {
        TaskConfig taskConfig = taskConfigDao.selectById(id);
        if (taskConfig == null) {
            return ResponseDTO.userErrorParam("任务配置不存在");
        }

        TaskConfigWizardDetailVO vo = SmartBeanUtil.copy(taskConfig, TaskConfigWizardDetailVO.class);

        // ruleConfig 里的 taskType 是服务端按模板强制覆写的派生值，不是运营填的参数，回显时剔掉
        Map<String, Object> ruleConfig = parseJsonMap(taskConfig.getRuleConfig());
        ruleConfig.remove(KEY_TASK_TYPE);
        vo.setRuleConfig(ruleConfig);

        // taskDesc / ruleDesc / badge 提交时被并进 ui_config，这里摘回独立字段，
        // 剩下的才是 ui_schema 里 image_upload 参数的取值
        Map<String, Object> uiConfig = parseJsonMap(taskConfig.getUiConfig());
        vo.setTaskDesc(toStringOrNull(uiConfig.remove(KEY_TASK_DESC)));
        vo.setRuleDesc(toStringOrNull(uiConfig.remove(KEY_RULE_DESC)));
        vo.setBadge(toStringOrNull(uiConfig.remove(KEY_BADGE)));
        vo.setUiConfig(uiConfig);

        List<TaskPrizeMapping> mappingList = taskPrizeMappingDao.selectList(
                Wrappers.<TaskPrizeMapping>lambdaQuery()
                        .eq(TaskPrizeMapping::getTaskConfigId, id)
                        .orderByAsc(TaskPrizeMapping::getStageLevel));
        vo.setPrizeMappingList(mappingList.stream().map(item -> {
            TaskConfigWizardDetailVO.PrizeLadder ladder = new TaskConfigWizardDetailVO.PrizeLadder();
            ladder.setStageLevel(item.getStageLevel());
            ladder.setPrizeCode(item.getPrizeCode());
            ladder.setPrizeMode(item.getPrizeMode());
            // 落库时包了一层 {"target": x} / {"value": y}，这里原样拆回来
            ladder.setStageCondition(toInteger(parseJsonMap(item.getStageCondition()).get(KEY_STAGE_TARGET)));
            ladder.setPrizeValue(toBigDecimal(parseJsonMap(item.getPrizeStrategy()).get(KEY_PRIZE_VALUE)));
            return ladder;
        }).collect(Collectors.toList()));

        return ResponseDTO.ok(vo);
    }

    /**
     * 向导更新：主表更新 + 子表整体替换，同一事务。
     *
     * <p>🔴 只有 activityCode 以库里的为准，忽略表单传入值 ——
     * 换活动会让阶梯里的 prize_code 全部失效（奖品按活动隔离），本质是「重建一个任务」。
     * 前端把这个下拉置灰了，但服务端不能只靠前端置灰。
     * 模板则允许更换，rule_config 会按新模板的 ui_schema 重新校验与落库。
     *
     * <p>status 刻意不动：任务可能已经是 3-已下线，保存一次配置不该把它悄悄改回待生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<Long> wizardUpdate(TaskConfigWizardUpdateForm form) {
        TaskConfig exist = taskConfigDao.selectById(form.getId());
        if (exist == null) {
            return ResponseDTO.userErrorParam("任务配置不存在");
        }

        TaskConfigWizardConfigForm configForm = form.getTaskConfig();
        // 模板可改：按表单传来的模板校验参数。换模板意味着 rule_config 整套按新 ui_schema 重填，
        // 向导那边切模板时已经用新模板的默认值重建了 ruleParams，这里只需照新模板验一遍
        TaskTemplate template = taskTemplateService.getByTemplateCode(configForm.getTemplateCode());
        if (template == null) {
            return ResponseDTO.userErrorParam("任务模板不存在：" + configForm.getTemplateCode());
        }

        Map<String, Object> ruleConfig = new HashMap<>(configForm.getRuleConfig());
        Map<String, Object> uiConfig = configForm.getUiConfig() == null ? new HashMap<>() : new HashMap<>(configForm.getUiConfig());
        String paramError = checkParamBySchema(template.getUiSchema(), ruleConfig, uiConfig);
        if (paramError != null) {
            return ResponseDTO.userErrorParam(paramError);
        }

        ruleConfig.put(KEY_TASK_TYPE, template.getTaskType());
        if (StringUtils.isNotBlank(configForm.getTaskDesc())) {
            uiConfig.put(KEY_TASK_DESC, configForm.getTaskDesc());
        }
        if (StringUtils.isNotBlank(configForm.getRuleDesc())) {
            uiConfig.put(KEY_RULE_DESC, configForm.getRuleDesc());
        }

        TaskConfig update = SmartBeanUtil.copy(configForm, TaskConfig.class);
        update.setId(exist.getId());
        // 唯一锁定项：归属活动。换活动会让阶梯里的 prize_code 全部失效（奖品按活动隔离），
        // 那是「重建一个任务」而不是改配置。前端把这个下拉置灰了，服务端再兜一道
        update.setActivityCode(exist.getActivityCode());
        update.setRuleConfig(JsonUtils.toJson(ruleConfig));
        update.setUiConfig(JsonUtils.toJson(uiConfig));
        // status 不在更新范围内：MyBatis-Plus 默认 NOT_NULL 策略会把 null 字段排除出 UPDATE
        update.setStatus(null);
        taskConfigDao.updateById(update);

        // 子表整体替换：阶梯的增删改在向导里是「一张表格提交一次」，
        // 逐行 diff 既复杂又容易在唯一键 (task_config_id, stage_level) 上撞车
        taskPrizeMappingDao.delete(Wrappers.<TaskPrizeMapping>lambdaQuery()
                .eq(TaskPrizeMapping::getTaskConfigId, exist.getId()));
        taskPrizeMappingDao.insertBatch(buildMappingList(form, exist.getId()));

        return ResponseDTO.ok(exist.getId());
    }

    /**
     * 阶梯表单 -> 子表实体。新增与更新共用，避免两处各写一遍包装格式。
     */
    private List<TaskPrizeMapping> buildMappingList(TaskConfigWizardSubmitForm form, Long taskConfigId) {
        return form.getPrizeMappingList().stream().map(item -> {
            TaskPrizeMapping mapping = new TaskPrizeMapping();
            mapping.setTenantId("0");
            mapping.setTaskConfigId(taskConfigId);
            mapping.setStageLevel(item.getStageLevel());
            mapping.setStageCondition(JsonUtils.toJson(Map.of(KEY_STAGE_TARGET, item.getStageCondition())));
            mapping.setPrizeCode(item.getPrizeCode());
            mapping.setPrizeMode(item.getPrizeMode());
            mapping.setPrizeStrategy(JsonUtils.toJson(Map.of(KEY_PRIZE_VALUE, item.getPrizeValue())));
            return mapping;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (StringUtils.isBlank(json)) {
            return new HashMap<>();
        }
        Map<String, Object> map = JsonUtils.parseType(json, new TypeReference<Map<String, Object>>() {
        });
        return map == null ? new HashMap<>() : new HashMap<>(map);
    }

    private String toStringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value == null ? null : new BigDecimal(String.valueOf(value));
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
        if (SmartCollectionUtil.isEmpty(idList)){
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
