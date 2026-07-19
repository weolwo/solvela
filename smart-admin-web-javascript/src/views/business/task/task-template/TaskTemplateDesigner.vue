<!--
  * 任务模板设计器（面向开发/超管，通过菜单权限只对技术角色开放）
  * 左侧：模板基础信息 + rule_script(QLExpress) + ui_schema(JSON) 双 Monaco 编辑器
  * 右侧：ui_schema 实时解析 -> SchemaFormRenderer 所见即所得预览 + rule_config/ui_config 拆分预览
  *
  * @Author:    alaric
  * @Date:      2026-07-19
-->
<template>
  <a-card :bordered="false">
    <a-row :gutter="16">
      <!-- ==================== 左侧：模板规则与代码编辑 ==================== -->
      <a-col :span="14">
        <a-space direction="vertical" class="w-full" :size="16">
          <a-card size="small" title="模板基础信息">
            <a-form ref="formRef" :model="designer" layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="模板编码 template_code" :name="['base', 'templateCode']" :rules="FORM_RULES.templateCode">
                    <a-input v-model:value="designer.base.templateCode" placeholder="如 DAILY_SIGN_TPL" :maxlength="64" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="模板名称 template_name" :name="['base', 'templateName']" :rules="FORM_RULES.templateName">
                    <a-input v-model:value="designer.base.templateName" placeholder="如 每日签到" :maxlength="128" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item
                    label="任务类型 task_type"
                    :name="['base', 'taskType']"
                    :rules="FORM_RULES.taskType"
                    extra="决定后端累计逻辑与阶梯达标条件的单位"
                  >
                    <a-select v-model:value="designer.base.taskType" :options="TASK_TYPE_OPTIONS" placeholder="请选择任务类型" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="默认触发事件 trigger_event" :name="['base', 'triggerEvent']">
                    <a-select
                      v-model:value="designer.base.triggerEvent"
                      :options="TRIGGER_EVENT_OPTIONS"
                      placeholder="模板建议的默认事件，向导中可覆盖"
                      allow-clear
                    />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
          </a-card>

          <!-- 核心交互区：全局工具栏 -->
          <a-flex justify="space-between" align="center" class="bg-gray-50 p-2 rounded">
            <a-typography-text type="secondary" class="text-xs"> <InfoCircleOutlined /> 编辑器默认折叠，点击下方标题展开。 </a-typography-text>
            <a-space>
              <a-typography-text class="text-sm">编辑器主题：</a-typography-text>
              <a-switch v-model:checked="isDarkTheme" checked-children="暗黑" un-checked-children="明亮" />
            </a-space>
          </a-flex>

          <!-- 手风琴面板：编辑器区域 -->
          <a-collapse v-model:activeKey="activePanels" class="bg-white">
            <!-- 1. Rule Script 编辑器 -->
            <a-collapse-panel key="script">
              <template #header>
                <a-space>
                  <span class="font-medium">底层规则脚本 rule_script</span>
                  <a-tag color="orange">运营不可见</a-tag>
                </a-space>
              </template>
              <template #extra>
                <a-button type="link" size="small" @click.stop="isScriptLarge = !isScriptLarge">
                  {{ isScriptLarge ? '收缩视图' : '放大视图' }}
                </a-button>
              </template>
              <vue-monaco-editor
                v-model:value="designer.ruleScript"
                :language="RULE_SCRIPT_LANGUAGE"
                :theme="currentTheme"
                :height="isScriptLarge ? EDITOR_HEIGHT_LARGE : EDITOR_HEIGHT_NORMAL"
                :options="MONACO_OPTIONS"
              />
            </a-collapse-panel>

            <!-- 2. UI Schema 编辑器 -->
            <a-collapse-panel key="schema">
              <template #header>
                <a-space>
                  <span class="font-medium">动态表单配置 ui_schema</span>
                  <!-- 强校验状态前置，折叠时也能看到报错 -->
                  <a-tag :color="schemaParse.ok ? 'green' : 'red'">
                    <CheckCircleOutlined v-if="schemaParse.ok" />
                    <CloseCircleOutlined v-else />
                    {{ schemaParse.ok ? 'JSON 有效' : 'JSON 错误' }}
                  </a-tag>
                </a-space>
              </template>
              <template #extra>
                <a-space @click.stop>
                  <a-button type="link" size="small" @click="formatJson">一键格式化</a-button>
                  <a-divider type="vertical" />
                  <a-button type="link" size="small" @click="isSchemaLarge = !isSchemaLarge">
                    {{ isSchemaLarge ? '收缩视图' : '放大视图' }}
                  </a-button>
                </a-space>
              </template>
              <vue-monaco-editor
                v-model:value="designer.uiSchemaText"
                :language="SCHEMA_LANGUAGE"
                :theme="currentTheme"
                :height="isSchemaLarge ? EDITOR_HEIGHT_LARGE : EDITOR_HEIGHT_NORMAL"
                :options="MONACO_OPTIONS"
              />
            </a-collapse-panel>
          </a-collapse>

          <a-flex justify="end" class="mt-4">
            <a-space>
              <a-button @click="onSaveDraft">暂存草稿</a-button>
              <!-- 阻断保存：不仅 disable，还提供 tooltip 解释原因 -->
              <a-tooltip :title="schemaParse.ok ? '' : '请先修复 ui_schema 的 JSON 错误'">
                <!-- disabled 按钮不触发鼠标事件，必须包一层 span 让 tooltip 能弹出 -->
                <span><a-button type="primary" :disabled="!schemaParse.ok" :loading="saveLoading" @click="onSave">保存模板</a-button></span>
              </a-tooltip>
            </a-space>
          </a-flex>
        </a-space>
      </a-col>

      <!-- ==================== 右侧：所见即所得实时预览 ==================== -->
      <a-col :span="10">
        <a-space direction="vertical" class="w-full" :size="16">
          <a-card size="small">
            <template #title>
              <a-space>
                <span>管理端动态表单 实时预览</span>
              </a-space>
            </template>
            <a-alert v-if="!schemaParse.ok" type="error" show-icon message="ui_schema JSON 解析失败" :description="schemaParse.error" class="mb-4" />
            <a-form v-else layout="vertical" :model="designer.previewValues">
              <SchemaFormRenderer v-model="designer.previewValues" :schema-params="previewParams" />
              <a-divider dashed />
              <a-typography-text type="secondary" class="text-xs">
                💡 提示：在上方表单中点击交互，即可测试 visibleWhen 显隐联动逻辑。
              </a-typography-text>
            </a-form>
          </a-card>

          <a-card size="small" title="DTO 数据拆分预览">
            <a-space direction="vertical" class="w-full" :size="8">
              <a-typography-text type="secondary">rule_config (注入底层引擎)</a-typography-text>
              <JsonViewer :value="configPreview.ruleConfig" :expand-depth="2" copyable boxed sort />
              <a-typography-text type="secondary">ui_config (用于 C 端展示)</a-typography-text>
              <JsonViewer :value="configPreview.uiConfig" :expand-depth="2" copyable boxed sort />
            </a-space>
          </a-card>
        </a-space>
      </a-col>
    </a-row>
  </a-card>
</template>

<script setup>
  import { computed, reactive, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import { taskApi } from '/@/api/business/task/task-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined } from '@ant-design/icons-vue';
  import { VueMonacoEditor } from '@guolao/vue-monaco-editor';
  import SchemaFormRenderer from '../task-wizard/SchemaFormRenderer.vue';
  import {
    TASK_TYPE_OPTIONS,
    TRIGGER_EVENT_OPTIONS,
    TASK_TEMPLATES,
    buildDefaultRuleParams,
    splitSchemaValues,
  } from '../task-wizard/task-wizard-const';

  // ---------------------------- 编辑器高级交互状态 ----------------------------

  // 手风琴折叠状态：默认均为空数组 []（即两个编辑器都折叠）
  const activePanels = ref([]);

  // 放大视图控制
  const isScriptLarge = ref(false);
  const isSchemaLarge = ref(false);

  // 主题切换
  const isDarkTheme = ref(true);
  const currentTheme = computed(() => (isDarkTheme.value ? 'vs-dark' : 'vs'));

  const RULE_SCRIPT_LANGUAGE = 'java';
  const SCHEMA_LANGUAGE = 'json';
  // vue-monaco-editor 的 height 原样进 style，必须带 px 单位的字符串（纯数字会被浏览器忽略导致容器塌陷）
  const EDITOR_HEIGHT_NORMAL = '300px';
  const EDITOR_HEIGHT_LARGE = '600px';
  const MONACO_OPTIONS = {
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    fontSize: 13,
    tabSize: 2,
    formatOnPaste: true, // 开启粘贴自动格式化（需要语言支持）
  };

  // ---------------------------- 默认值与单一状态源 ----------------------------

  const DEFAULT_TEMPLATE = TASK_TEMPLATES[0];
  const DEFAULT_UI_SCHEMA_TEXT = JSON.stringify(DEFAULT_TEMPLATE.uiSchema, null, 2);
  const DEFAULT_RULE_SCRIPT = [
    '// 事件: DAILY_SIGN；向导 rule_config 中的参数（如 targetDays、allowRepair）作为变量注入',
    "if (event.type != 'DAILY_SIGN') return false;",
    '// 连续性判定: 昨日未签且未补签则重置进度',
    'if (!ctx.signedYesterday && !ctx.repaired) { record.currentMetric = 0; }',
    'record.currentMetric = record.currentMetric + 1;',
    'return record.currentMetric >= targetDays;',
  ].join('\n');

  const designer = reactive({
    base: {
      templateCode: DEFAULT_TEMPLATE.templateCode,
      templateName: DEFAULT_TEMPLATE.templateName,
      taskType: DEFAULT_TEMPLATE.taskType,
      triggerEvent: undefined,
    },
    ruleScript: DEFAULT_RULE_SCRIPT,
    uiSchemaText: DEFAULT_UI_SCHEMA_TEXT,
    previewValues: {},
  });

  const formRef = ref();
  const FORM_RULES = {
    templateCode: [{ required: true, message: '请输入模板编码' }],
    templateName: [{ required: true, message: '请输入模板名称' }],
    taskType: [{ required: true, message: '请选择任务类型' }],
  };

  // ---------------------------- 强校验与防呆逻辑 ----------------------------

  // JSON 解析与强校验
  const schemaParse = computed(() => {
    try {
      const parsed = JSON.parse(designer.uiSchemaText);
      return { ok: true, schema: parsed, error: null };
    } catch (e) {
      return { ok: false, schema: null, error: e.message };
    }
  });

  // 一键格式化 JSON
  function formatJson() {
    if (!schemaParse.value.ok) {
      message.error('JSON 存在语法错误，无法格式化！请先检查红线标记处。');
      return;
    }
    designer.uiSchemaText = JSON.stringify(schemaParse.value.schema, null, 2);
    message.success('JSON 格式化成功');
  }

  const previewParams = computed(() =>
    schemaParse.value.ok && Array.isArray(schemaParse.value.schema?.params) ? schemaParse.value.schema.params : []
  );

  watch(
    () => JSON.stringify(previewParams.value),
    () => {
      designer.previewValues = buildDefaultRuleParams({ uiSchema: { params: previewParams.value } });
    },
    { immediate: true }
  );

  const configPreview = computed(() => {
    const { ruleValues, uiValues } = splitSchemaValues(previewParams.value, designer.previewValues);
    return {
      ruleConfig: { taskType: designer.base.taskType, ...ruleValues },
      uiConfig: uiValues,
    };
  });

  // ---------------------------- 提交保存 ----------------------------

  const saveLoading = ref(false);

  async function onSave() {
    // 校验失败与接口失败分开处理：校验不过不进入 loading
    try {
      await formRef.value.validate();
    } catch (e) {
      message.error('基础信息校验未通过，请检查必填项');
      return;
    }
    saveLoading.value = true;
    try {
      const templateDto = {
        ...designer.base,
        uiSchema: schemaParse.value.schema,
        ruleScript: designer.ruleScript,
      };
      await taskApi.saveTaskTemplate(templateDto);
      message.success('模板保存成功');
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saveLoading.value = false;
    }
  }

  function onSaveDraft() {
    console.log('【任务模板设计器 · 暂存草稿】当前快照：', JSON.parse(JSON.stringify(designer)));
    message.success('草稿已暂存（mock，快照见控制台）');
  }
</script>
