<!--
  * 任务配置向导（第一阶段骨架：步骤框架 + 第1/4/5步 + 主子表 DTO 组装）
  * 第2步 ui_schema 动态表单、第3步阶梯搭建 以 a-empty 占位，下一阶段接入
  *
  * @Author:    alaric
  * @Date:      2026-07-19
-->
<template>
  <a-card :bordered="false">
    <a-steps :current="currentStep" :items="WIZARD_STEP_ITEMS" size="small" class="mb-6" />

    <a-form ref="formRef" :model="wizardForm" layout="vertical">
      <!-- ==================== 第1步：模板与基础信息 ==================== -->
      <div v-show="currentStep === WIZARD_STEP.BASE">
        <a-card size="small" title="选择任务模板" class="mb-4">
          <a-row :gutter="[12, 12]">
            <a-col :span="12" v-for="tpl in TASK_TEMPLATES" :key="tpl.templateCode">
              <a-card
                size="small"
                hoverable
                :class="wizardForm.base.templateCode === tpl.templateCode ? 'ring-2 ring-blue-500' : ''"
                @click="onSelectTemplate(tpl)"
              >
                <a-card-meta>
                  <template #avatar>
                    <a-avatar shape="square" :size="40">{{ tpl.icon }}</a-avatar>
                  </template>
                  <template #title>
                    <a-space>
                      <a-typography-text strong>{{ tpl.templateName }}</a-typography-text>
                      <a-tag color="blue">{{ tpl.taskType }}</a-tag>
                    </a-space>
                  </template>
                  <template #description>{{ tpl.desc }}</template>
                </a-card-meta>
              </a-card>
            </a-col>
          </a-row>
        </a-card>

        <a-card size="small" title="基础信息">
          <a-form-item
            label="所属活动大类 activity_code"
            :name="['base', 'activityCode']"
            :rules="FORM_RULES.activityCode"
            extra="原子任务必须归属一个活动大类，C端按大类聚合展示"
          >
            <a-select
              v-model:value="wizardForm.base.activityCode"
              :options="ACTIVITY_OPTIONS"
              placeholder="请选择所属活动"
              allow-clear
            />
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="任务名称" :name="['base', 'taskName']" :rules="FORM_RULES.taskName">
                <a-input v-model:value="wizardForm.base.taskName" placeholder="如：新人首单签到礼" :maxlength="128" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="触发事件 trigger_event" :name="['base', 'triggerEvent']" :rules="FORM_RULES.triggerEvent">
                <a-select v-model:value="wizardForm.base.triggerEvent" :options="TRIGGER_EVENT_OPTIONS" placeholder="请选择触发事件" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="任务分组 task_group" :name="['base', 'taskGroup']">
            <a-radio-group v-model:value="wizardForm.base.taskGroup" :options="TASK_GROUP_OPTIONS" option-type="button" />
          </a-form-item>
        </a-card>
      </div>

      <!-- ==================== 第2步：达标规则（ui_schema 动态表单） ==================== -->
      <div v-show="currentStep === WIZARD_STEP.RULE">
        <a-card size="small">
          <template #title>
            <a-space>
              <span>达标规则 · {{ summary.templateName }}</span>
              <a-tag color="blue">{{ summary.taskType }}</a-tag>
              <a-typography-text type="secondary" class="text-xs font-normal">由模板 ui_schema 动态生成，新增模板零前端改动</a-typography-text>
            </a-space>
          </template>

          <SchemaFormRenderer
            v-model="wizardForm.ruleParams"
            :schema-params="schemaParams"
            :name-prefix="RULE_PARAMS_NAME_PREFIX"
          />

          <a-divider />

          <a-form-item label="参与频次 limit_type" :name="['limit', 'limitType']">
            <a-radio-group v-model:value="wizardForm.limit.limitType" :options="LIMIT_TYPE_OPTIONS" option-type="button" />
          </a-form-item>
          <a-form-item
            v-if="wizardForm.limit.limitType !== LIMIT_TYPE_ENUM.ONCE"
            label="限制次数 limit_count"
            :name="['limit', 'limitCount']"
          >
            <a-input-number v-model:value="wizardForm.limit.limitCount" :min="1" />
          </a-form-item>
        </a-card>
      </div>

      <!-- ==================== 第3步：奖励阶梯 ==================== -->
      <div v-show="currentStep === WIZARD_STEP.PRIZE">
        <a-card size="small" title="奖励阶梯 t_task_prize_mapping">
          <a-space direction="vertical" class="w-full" :size="12">
            <a-alert
              type="info"
              show-icon
              message="支持配置多级阶梯（如签到3天/7天/30天分别给不同奖励），每级可选择 固定 FIXED / 比例 RATIO / 公式 FORMULA 三种发奖模式"
            />
            <PrizeLadderBuilder v-model="wizardForm.prizeLadders" :condition-unit="stageConditionUnit" />
          </a-space>
        </a-card>
      </div>

      <!-- ==================== 第4步：受众与时间 ==================== -->
      <div v-show="currentStep === WIZARD_STEP.AUDIENCE">
        <a-card size="small" title="受众与时间" class="mb-4">
          <a-form-item label="目标人群 target_audience" :name="['audience', 'targetAudience']">
            <a-radio-group v-model:value="wizardForm.audience.targetAudience" :options="TARGET_AUDIENCE_OPTIONS" option-type="button" />
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="生效时间" :name="['audience', 'timeRange']" :rules="FORM_RULES.timeRange">
                <a-range-picker
                  v-model:value="wizardForm.audience.timeRange"
                  show-time
                  value-format="YYYY-MM-DD HH:mm:ss"
                  :disabled="wizardForm.audience.longTerm"
                  class="w-full"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="长期有效" :name="['audience', 'longTerm']" extra="开启后不限结束时间">
                <a-switch v-model:checked="wizardForm.audience.longTerm" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="排序权重 sort_weight" :name="['audience', 'sortWeight']" extra="越大越靠前">
                <a-slider v-model:value="wizardForm.audience.sortWeight" :min="0" :max="100" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="跳转地址 action_url" :name="['audience', 'actionUrl']">
                <a-input v-model:value="wizardForm.audience.actionUrl" placeholder="/pages/sign/index" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="角标文案 ui_config.badge" :name="['audience', 'badge']">
            <a-input v-model:value="wizardForm.audience.badge" placeholder="如：新人专享" :maxlength="8" />
          </a-form-item>
        </a-card>

        <a-card size="small" title="C端展示与规则说明">
          <a-form-item
            label="任务副标题 task_desc"
            :name="['display', 'taskDesc']"
            extra="展示在 C 端任务名称下方，一句话吸引用户"
          >
            <a-input v-model:value="wizardForm.display.taskDesc" placeholder="如：连签7天领100积分" :maxlength="50" />
          </a-form-item>
          <a-form-item
            label="详细规则说明 rule_desc"
            :name="['display', 'ruleDesc']"
            extra="用户点击任务卡片弹出的详细活动规则说明（如：同一设备限领1次等）"
          >
            <a-textarea v-model:value="wizardForm.display.ruleDesc" :rows="4" :maxlength="500" show-count />
          </a-form-item>
        </a-card>
      </div>

      <!-- ==================== 第5步：预览提交 ==================== -->
      <div v-show="currentStep === WIZARD_STEP.SUMMARY">
        <a-card size="small" title="配置汇总">
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="所属活动大类">{{ summary.activityLabel }}</a-descriptions-item>
            <a-descriptions-item label="任务模板">
              <a-space>
                <span>{{ summary.templateName }}</span>
                <a-tag color="blue">{{ summary.taskType }}</a-tag>
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item label="任务名称">{{ wizardForm.base.taskName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="任务副标题">{{ wizardForm.display.taskDesc || '-' }}</a-descriptions-item>
            <a-descriptions-item label="触发事件">{{ wizardForm.base.triggerEvent || '-' }}</a-descriptions-item>
            <a-descriptions-item label="任务分组">{{ summary.taskGroupLabel }}</a-descriptions-item>
            <a-descriptions-item label="目标人群">{{ summary.audienceLabel }}</a-descriptions-item>
            <a-descriptions-item label="生效时间">{{ summary.timeText }}</a-descriptions-item>
            <a-descriptions-item label="奖励阶梯">共 {{ wizardForm.prizeLadders.length }} 级</a-descriptions-item>
            <a-descriptions-item label="参与频次">{{ summary.limitLabel }}</a-descriptions-item>
            <a-descriptions-item label="规则参数 rule_config" :span="2">
              <JsonViewer :value="summary.ruleConfigPreview" :expand-depth="2" copyable boxed sort class="w-full" />
            </a-descriptions-item>
            <a-descriptions-item label="UI 配置 ui_config" :span="2">
              <JsonViewer :value="summary.uiConfigPreview" :expand-depth="2" copyable boxed sort class="w-full" />
            </a-descriptions-item>
          </a-descriptions>

          <a-flex justify="end" class="mt-4">
            <a-space>
              <a-button @click="onSaveDraft">保存草稿</a-button>
              <a-button type="primary" @click="onSubmit">正式提交</a-button>
            </a-space>
          </a-flex>
        </a-card>
      </div>
    </a-form>

    <!-- ==================== 底部步骤控制 ==================== -->
    <a-flex justify="space-between" class="mt-4">
      <a-button :disabled="currentStep === WIZARD_STEP.BASE" @click="prevStep">上一步</a-button>
      <a-button v-if="currentStep < WIZARD_STEP.SUMMARY" type="primary" @click="nextStep">下一步</a-button>
    </a-flex>
  </a-card>
</template>

<script setup>
  import { computed, reactive, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import SchemaFormRenderer from './SchemaFormRenderer.vue';
  import PrizeLadderBuilder from './PrizeLadderBuilder.vue';
  import {
    WIZARD_STEP,
    WIZARD_STEP_ITEMS,
    ACTIVITY_OPTIONS,
    TRIGGER_EVENT_OPTIONS,
    TASK_GROUP_OPTIONS,
    LIMIT_TYPE_ENUM,
    LIMIT_TYPE_OPTIONS,
    TARGET_AUDIENCE_OPTIONS,
    STAGE_CONDITION_UNIT,
    TASK_TEMPLATES,
    isSchemaParamVisible,
    splitSchemaValues,
    buildDefaultRuleParams,
    buildDefaultWizardForm,
  } from './task-wizard-const';

  // ---------------------------- 单一状态源：向导全部表单输入（初始值统一由常量文件工厂构建） ----------------------------

  const wizardForm = reactive(buildDefaultWizardForm());

  const currentStep = ref(WIZARD_STEP.BASE);
  const formRef = ref();

  // ---------------------------- 模板选择与 ruleParams 初始化 ----------------------------

  const selectedTemplate = computed(() => TASK_TEMPLATES.find((t) => t.templateCode === wizardForm.base.templateCode));

  const schemaParams = computed(() => selectedTemplate.value?.uiSchema?.params || []);

  const stageConditionUnit = computed(() => STAGE_CONDITION_UNIT[selectedTemplate.value?.taskType] || '');

  // 第2步 schema 参数在外层表单校验树上的路径前缀
  const RULE_PARAMS_NAME_PREFIX = ['ruleParams'];

  function onSelectTemplate(tpl) {
    wizardForm.base.templateCode = tpl.templateCode;
  }

  // 模板切换即按 schema 默认值重建参数值，保证提交组装始终有完整 ruleParams
  watch(
    () => wizardForm.base.templateCode,
    () => {
      wizardForm.ruleParams = buildDefaultRuleParams(selectedTemplate.value);
    }
  );

  // ---------------------------- 表单校验（a-form rules 机制） ----------------------------

  function validateTimeRange(rule, value) {
    if (wizardForm.audience.longTerm || (Array.isArray(value) && value.length === 2)) {
      return Promise.resolve();
    }
    return Promise.reject('请选择生效时间，或开启「长期有效」');
  }

  const FORM_RULES = {
    activityCode: [{ required: true, message: '请选择所属活动大类' }],
    taskName: [{ required: true, message: '请输入任务名称' }],
    triggerEvent: [{ required: true, message: '请选择触发事件' }],
    timeRange: [{ validator: validateTimeRange, trigger: 'change' }],
  };

  // 每步「下一步」前需要校验的字段（无配置的步骤直接放行）
  const STEP_VALIDATE_FIELDS = {
    [WIZARD_STEP.BASE]: [
      ['base', 'activityCode'],
      ['base', 'taskName'],
      ['base', 'triggerEvent'],
    ],
    [WIZARD_STEP.AUDIENCE]: [['audience', 'timeRange']],
  };

  // 第2步的必填字段由所选模板 schema 动态决定，且仅校验当前 visibleWhen 可见的参数
  function getStepValidateFields(step) {
    if (step === WIZARD_STEP.RULE) {
      return schemaParams.value
        .filter((p) => p.required && isSchemaParamVisible(p, wizardForm.ruleParams))
        .map((p) => [...RULE_PARAMS_NAME_PREFIX, p.key]);
    }
    return STEP_VALIDATE_FIELDS[step];
  }

  async function nextStep() {
    try {
      const fields = getStepValidateFields(currentStep.value);
      if (fields && fields.length) {
        await formRef.value.validateFields(fields);
      }
      currentStep.value += 1;
    } catch (e) {
      // 校验失败由 a-form 负责展示错误信息，此处仅阻止步骤前进
    }
  }

  function prevStep() {
    currentStep.value -= 1;
  }

  // ---------------------------- 第5步汇总展示 ----------------------------

  function optionLabel(options, value) {
    return options.find((o) => o.value === value)?.label || '-';
  }

  const summary = computed(() => ({
    activityLabel: optionLabel(ACTIVITY_OPTIONS, wizardForm.base.activityCode),
    templateName: selectedTemplate.value?.templateName || '-',
    taskType: selectedTemplate.value?.taskType || '-',
    taskGroupLabel: optionLabel(TASK_GROUP_OPTIONS, wizardForm.base.taskGroup),
    audienceLabel: optionLabel(TARGET_AUDIENCE_OPTIONS, wizardForm.audience.targetAudience),
    limitLabel: optionLabel(LIMIT_TYPE_OPTIONS, wizardForm.limit.limitType),
    timeText: wizardForm.audience.longTerm
      ? '长期有效'
      : wizardForm.audience.timeRange.length === 2
        ? wizardForm.audience.timeRange.join(' ~ ')
        : '-',
    ruleConfigPreview: buildRuleAndUiConfig().ruleConfig,
    uiConfigPreview: buildRuleAndUiConfig().uiConfig,
  }));

  // ---------------------------- 提交：拆解组装主子表 DTO ----------------------------

  // 按所选模板 schema 拆分参数值：image_upload 归入 uiConfig，其余归入 ruleConfig（拆分语义由共享函数统一）
  function buildRuleAndUiConfig() {
    const { ruleValues, uiValues } = splitSchemaValues(schemaParams.value, wizardForm.ruleParams);
    return {
      ruleConfig: { taskType: selectedTemplate.value?.taskType, ...ruleValues },
      uiConfig: { badge: wizardForm.audience.badge, ...uiValues },
    };
  }

  function buildSubmitData() {
    const { base, limit, audience, display, prizeLadders } = wizardForm;
    const { ruleConfig, uiConfig } = buildRuleAndUiConfig();
    return {
      // 1. 主表信息 (t_task_config / t_task_template)
      taskConfig: {
        activityCode: base.activityCode,
        templateCode: base.templateCode,
        taskName: base.taskName,
        triggerEvent: base.triggerEvent,
        taskGroup: base.taskGroup,
        taskDesc: display.taskDesc,
        ruleDesc: display.ruleDesc,
        targetAudience: audience.targetAudience,
        startTime: audience.longTerm ? null : audience.timeRange[0] || null,
        endTime: audience.longTerm ? null : audience.timeRange[1] || null,
        sortWeight: audience.sortWeight,
        actionUrl: audience.actionUrl,
        limitType: limit.limitType,
        limitCount: limit.limitCount,
        uiConfig,
        ruleConfig,
      },
      // 2. 子表信息 (t_task_prize_mapping)
      prizeMappingList: prizeLadders.map((ladder, index) => ({
        stageLevel: index + 1,
        stageCondition: ladder.stageCondition,
        prizeCode: ladder.prizeCode,
        prizeMode: ladder.prizeMode,
        prizeValue: ladder.prizeValue,
      })),
    };
  }

  async function onSubmit() {
    try {
      await formRef.value.validate();
      const submitData = buildSubmitData();
      console.log('【任务配置向导 · 正式提交】主子表 DTO：', submitData);
      message.success('已按主子表 DTO 规范组装提交数据，请在控制台查看');
    } catch (e) {
      message.error('表单校验未通过，请检查各步骤必填项');
    }
  }

  function onSaveDraft() {
    console.log('【任务配置向导 · 保存草稿】当前表单快照：', JSON.parse(JSON.stringify(wizardForm)));
    message.success('草稿已保存（mock）');
  }
</script>
