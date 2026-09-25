package solvela.task.taskconfig.service;

import solvela.enums.TaskConfigStatusEnum;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import solvela.base.util.SolvelaCollectionUtil;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.json.JsonUtils;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.module.file.service.FileAssetService;
import solvela.base.module.file.service.RichTextImageExtractor;
import solvela.task.prizemapping.dao.TaskPrizeMappingDao;
import solvela.task.TaskPrizeMapping;
import solvela.task.taskconfig.dao.TaskConfigDao;
import solvela.task.TaskConfig;
import solvela.task.taskconfig.domain.command.TaskConfigAddCommand;
import solvela.task.taskconfig.domain.query.TaskConfigQuery;
import solvela.task.taskconfig.domain.command.TaskConfigUpdateCommand;
import solvela.task.taskconfig.domain.command.TaskConfigWizardConfigCommand;
import solvela.task.taskconfig.domain.command.TaskConfigWizardSubmitCommand;
import solvela.task.taskconfig.domain.command.TaskConfigWizardUpdateCommand;
import solvela.task.taskconfig.domain.dto.TaskConfigDTO;
import solvela.task.taskconfig.domain.dto.TaskConfigWizardDetailDTO;
import solvela.task.TaskTemplate;
import solvela.task.tasktemplate.service.TaskTemplateService;
import solvela.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    /** 规则说明富文本里的图要登记引用，否则会被孤儿清理任务当垃圾删掉 */
    private final FileAssetService fileAssetService;

    /**
     * 规则说明富文本内嵌图的引用类型。
     *
     * <p>🔴 2026-09-25 补。向导里的「规则说明」是 wangEditor，插图会真的走
     * {@code fileApi.uploadFileByCategory(..., CONTENT)} 上传到素材库，
     * 但这边只把 HTML 塞进 {@code ui_config} 就完事了 —— 那些图一直停在 {@code TEMP}。
     * 与 {@code ActivityDisplayService} / {@code MallCommodityService} 是同一件事，
     * 只有这一处当初漏了。
     *
     * <p>⚠️ 模板里的 {@code image_upload} 控件<b>不在此列</b>：前端那个是占位交互
     *（{@code SchemaFormRenderer.vue} 写着「仅写入 mock URL，不发起真实上传请求」），
     * 根本没有文件产生。哪天它接了真实上传，这里要一起改。
     */
    private static final String BIZ_TYPE = "TASK_CONFIG";

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
    public Long wizardSubmit(TaskConfigWizardSubmitCommand form) {
        TaskConfigWizardConfigCommand configForm = form.getTaskConfig();
        ConfigJson configJson = resolveConfigJson(configForm);

        TaskConfig taskConfig = SolvelaBeanUtil.copy(configForm, TaskConfig.class);
        taskConfig.setRuleConfig(configJson.ruleConfig());
        taskConfig.setUiConfig(configJson.uiConfig());
        taskConfig.setStatus(TaskConfigStatusEnum.PENDING);
        taskConfigDao.insert(taskConfig);

        // 子表批量落库（insertBatch 由 CustomizedBaseMapper 提供）
        taskPrizeMappingDao.insertBatch(buildMappingList(form, taskConfig.getId()));

        confirmRuleDescImages(taskConfig.getId(), configForm.getRuleDesc());

        // 返回主表ID，前端成功页据此定位刚创建的任务
        return taskConfig.getId();
    }

    /**
     * 把规则说明正文里的图登记进 {@code t_file_relation}。
     *
     * <p>⚠️ 没有图时<b>也要调</b>（传空集合）：那表示「这次把图都删了」，
     * {@code confirm} 会把旧关系清掉。提前 return 的话，删光图的任务会一直挂着旧引用，
     * 那些图从此删不掉 —— 完整的踩坑记录在 {@code FileAssetService.confirm} 里。
     */
    private void confirmRuleDescImages(Long taskConfigId, String ruleDesc) {
        Set<String> sources = RichTextImageExtractor.extractImageSources(ruleDesc);
        List<Long> ids = sources.isEmpty() ? List.of() : fileAssetService.resolveFileIds(sources);
        fileAssetService.confirm(ids, BIZ_TYPE, taskConfigId);
    }

    /**
     * 存量回填：把已有任务配置的规则说明内嵌图补进 {@code t_file_relation}。
     *
     * <p>🔴 <b>孤儿清理任务上线前必须跑一次。</b>补 {@code confirm} 只管住以后新存的。
     *
     * <p>⚠️ 从 {@code ui_config} 这个 JSON 列里把 {@code ruleDesc} 读回来，
     * 而不是从向导的入参 —— 存量数据只有库里这一份。
     * 解析不出来的行<b>跳过而不是清空</b>：清空等于亲手把引用删掉，
     * 然后清理任务就会去删那些图，方向正好反了。
     *
     * <p>幂等：{@code confirm} 先清后建，跑几遍结果一样。
     *
     * @return 处理过的任务配置数
     */
    @Transactional(rollbackFor = Exception.class)
    public int backfillRuleDescRelations() {
        List<TaskConfig> all = taskConfigDao.selectList(null);
        int handled = 0;
        for (TaskConfig config : all) {
            String ruleDesc = readRuleDesc(config.getUiConfig());
            if (ruleDesc == null) {
                continue;
            }
            confirmRuleDescImages(config.getId(), ruleDesc);
            handled++;
        }
        return handled;
    }

    /** @return ui_config 里的 ruleDesc；列为空或不是合法 JSON 时返回 null（调用方据此跳过） */
    private String readRuleDesc(String uiConfigJson) {
        if (StringUtils.isBlank(uiConfigJson)) {
            // 空 ui_config 是明确的「没有规则说明」，可以放心清成 0 条引用
            return "";
        }
        try {
            Map<String, Object> uiConfig = JsonUtils.parseType(
                    uiConfigJson, new TypeReference<Map<String, Object>>() {
                    });
            Object value = uiConfig == null ? null : uiConfig.get(KEY_RULE_DESC);
            return value == null ? "" : String.valueOf(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 两个 JSON 列的最终形态。它们成对产生：taskType 写进 ruleConfig、副标题写进 uiConfig，
     * 都发生在同一次校验之后，分开算会漏掉其中一半。
     */
    private record ConfigJson(String ruleConfig, String uiConfig) {
    }

    /**
     * 模板校验 + 配置归一。<b>新建与更新共用一份</b> ——
     * 改造前这段在 wizardSubmit 与 wizardUpdate 里各写了一遍，
     * 而它管着「taskType 由服务端强制覆写」这条防伪造的规则，两份实现漂一次就等于开了个口子。
     *
     * <p>三件事：模板必须存在；按模板 ui_schema 校验参数（必填、visibleWhen 可见性、
     * image 参数归属）；taskType 以模板为准强制覆写，副标题与规则说明并进 ui_config。
     */
    private ConfigJson resolveConfigJson(TaskConfigWizardConfigCommand configForm) {
        TaskTemplate template = taskTemplateService.getByTemplateCode(configForm.getTemplateCode());
        if (template == null) {
            throw new BusinessException("任务模板不存在：" + configForm.getTemplateCode());
        }

        Map<String, Object> ruleConfig = new HashMap<>(configForm.getRuleConfig());
        Map<String, Object> uiConfig = configForm.getUiConfig() == null
                ? new HashMap<>() : new HashMap<>(configForm.getUiConfig());
        String paramError = checkParamBySchema(template.getUiSchema(), ruleConfig, uiConfig);
        if (paramError != null) {
            throw new BusinessException(paramError);
        }

        // 🔴 taskType 以模板为准，服务端强制覆写，防止前端伪造
        ruleConfig.put(KEY_TASK_TYPE, template.getTaskType());
        if (StringUtils.isNotBlank(configForm.getTaskDesc())) {
            uiConfig.put(KEY_TASK_DESC, configForm.getTaskDesc());
        }
        if (StringUtils.isNotBlank(configForm.getRuleDesc())) {
            uiConfig.put(KEY_RULE_DESC, configForm.getRuleDesc());
        }
        return new ConfigJson(JsonUtils.toJson(ruleConfig), JsonUtils.toJson(uiConfig));
    }

    /**
     * 任务配置 上/下线（列表页的批量下线用它）。
     *
     * <p>替代删除：任务记录里存着 task_config_id，删配置会让历史记录指向一条不存在的配置。
     * 下线后运行态不再订阅该任务的事件（判据是 status != OFFLINE，见 TaskConfigStatusEnum），
     * 已在跑的记录按接取时的快照走完，不受影响。
     */
    public void updateStatus(List<Long> idList, TaskConfigStatusEnum status) {
        // 原先这里逐个比对三个合法值。入参换成枚举之后，非法值在反序列化阶段就被拒了
        // （400 / INVALID_ARGUMENT），这段校验没有存在意义。
        for (Long id : idList) {
            TaskConfig update = new TaskConfig();
            update.setId(id);
            update.setStatus(status);
            taskConfigDao.updateById(update);
        }
    }

    /**
     * 向导回显：主子表一次性返回，结构与提交表单对称，前端拿到就能铺回 5 个步骤。
     */
    public TaskConfigWizardDetailDTO wizardDetail(Long id) {
        TaskConfig taskConfig = taskConfigDao.selectById(id);
        if (taskConfig == null) {
            throw new BusinessException("任务配置不存在");
        }

        TaskConfigWizardDetailDTO vo = SolvelaBeanUtil.copy(taskConfig, TaskConfigWizardDetailDTO.class);

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
            TaskConfigWizardDetailDTO.PrizeLadder ladder = new TaskConfigWizardDetailDTO.PrizeLadder();
            ladder.setStageLevel(item.getStageLevel());
            ladder.setPrizeCode(item.getPrizeCode());
            ladder.setPrizeMode(item.getPrizeMode());
            // 落库时包了一层 {"target": x} / {"value": y}，这里原样拆回来
            ladder.setStageCondition(toInteger(parseJsonMap(item.getStageCondition()).get(KEY_STAGE_TARGET)));
            ladder.setPrizeValue(toBigDecimal(parseJsonMap(item.getPrizeStrategy()).get(KEY_PRIZE_VALUE)));
            return ladder;
        }).collect(Collectors.toList()));

        return vo;
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
    public Long wizardUpdate(TaskConfigWizardUpdateCommand form) {
        TaskConfig exist = taskConfigDao.selectById(form.getId());
        if (exist == null) {
            throw new BusinessException("任务配置不存在");
        }

        TaskConfigWizardConfigCommand configForm = form.getTaskConfig();
        // 模板可改：换模板意味着 rule_config 整套按新 ui_schema 重填，
        // 向导那边切模板时已经用新模板的默认值重建了 ruleParams，这里只需照新模板验一遍
        ConfigJson configJson = resolveConfigJson(configForm);

        TaskConfig update = SolvelaBeanUtil.copy(configForm, TaskConfig.class);
        update.setId(exist.getId());
        // 唯一锁定项：归属活动。换活动会让阶梯里的 prize_code 全部失效（奖品按活动隔离），
        // 那是「重建一个任务」而不是改配置。前端把这个下拉置灰了，服务端再兜一道
        update.setActivityCode(exist.getActivityCode());
        update.setRuleConfig(configJson.ruleConfig());
        update.setUiConfig(configJson.uiConfig());
        // status 不在更新范围内：MyBatis-Plus 默认 NOT_NULL 策略会把 null 字段排除出 UPDATE
        update.setStatus(null);
        taskConfigDao.updateById(update);

        // 子表整体替换：阶梯的增删改在向导里是「一张表格提交一次」，
        // 逐行 diff 既复杂又容易在唯一键 (task_config_id, stage_level) 上撞车
        taskPrizeMappingDao.delete(Wrappers.<TaskPrizeMapping>lambdaQuery()
                .eq(TaskPrizeMapping::getTaskConfigId, exist.getId()));
        taskPrizeMappingDao.insertBatch(buildMappingList(form, exist.getId()));

        confirmRuleDescImages(exist.getId(), configForm.getRuleDesc());

        return exist.getId();
    }

    /**
     * 阶梯表单 -> 子表实体。新增与更新共用，避免两处各写一遍包装格式。
     */
    private List<TaskPrizeMapping> buildMappingList(TaskConfigWizardSubmitCommand form, Long taskConfigId) {
        return form.getPrizeMappingList().stream().map(item -> {
            TaskPrizeMapping mapping = new TaskPrizeMapping();
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
    public PageResult<TaskConfigDTO> queryPage(TaskConfigQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<TaskConfigDTO> list = taskConfigDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public void add(TaskConfigAddCommand addForm) {
        TaskConfig taskConfig = SolvelaBeanUtil.copy(addForm, TaskConfig.class);
        taskConfigDao.insert(taskConfig);
    }

    /**
     * 更新
     *
     */
    public void update(TaskConfigUpdateCommand updateForm) {
        TaskConfig taskConfig = SolvelaBeanUtil.copy(updateForm, TaskConfig.class);
        taskConfigDao.updateById(taskConfig);
    }

    /**
     * 批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }

        taskConfigDao.deleteBatchIds(idList);
        // 只解除关系，不删文件 —— 同一张图可能被别的任务复用
        idList.forEach(id -> fileAssetService.releaseRelation(BIZ_TYPE, id));
    }

    /**
     * 单个删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (null == id){
            return;
        }

        taskConfigDao.deleteById(id);
        fileAssetService.releaseRelation(BIZ_TYPE, id);
    }
}
