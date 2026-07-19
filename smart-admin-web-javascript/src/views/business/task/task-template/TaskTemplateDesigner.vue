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

          <a-card size="small">
            <template #title>
              <a-space>
                <span>底层规则脚本 rule_script</span>
                <a-tag color="red">运营不可见</a-tag>
                <a-typography-text type="secondary" class="text-xs font-normal">
                  QLExpress · 向导收集的 rule_config 参数将作为变量注入本脚本
                </a-typography-text>
              </a-space>
            </template>
            <vue-monaco-editor
              v-model:value="designer.ruleScript"
              :language="RULE_SCRIPT_LANGUAGE"
              :theme="EDITOR_THEME"
              :height="RULE_EDITOR_HEIGHT"
              :options="MONACO_OPTIONS"
            />
          </a-card>

          <a-card size="small">
            <template #title>
              <a-space>
                <span>动态表单配置 ui_schema</span>
                <a-tag :color="schemaParse.ok ? 'green' : 'red'">{{ schemaParse.ok ? 'JSON 有效' : 'JSON 错误' }}</a-tag>
                <a-typography-text type="secondary" class="text-xs font-normal">
                  params[] 驱动向导第2步表单，右侧实时预览
                </a-typography-text>
              </a-space>
            </template>
            <vue-monaco-editor
              v-model:value="designer.uiSchemaText"
              :language="SCHEMA_LANGUAGE"
              :theme="EDITOR_THEME"
              :height="SCHEMA_EDITOR_HEIGHT"
              :options="MONACO_OPTIONS"
            />
          </a-card>

          <a-flex justify="end">
            <a-space>
              <a-button @click="onSaveDraft">暂存草稿</a-button>
              <a-button type="primary" :disabled="!schemaParse.ok" @click="onSave">保存模板</a-button>
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
                <span>实时预览 · 管理端动态表单</span>
                <a-tag color="blue">{{ designer.base.taskType }}</a-tag>
              </a-space>
            </template>
            <a-alert
              v-if="!schemaParse.ok"
              type="error"
              show-icon
              message="ui_schema JSON 解析失败，修正左侧内容后自动恢复预览"
              :description="schemaParse.error"
            />
            <a-form v-else layout="vertical" :model="designer.previewValues">
              <SchemaFormRenderer v-model="designer.previewValues" :schema-params="previewParams" />
              <a-typography-text type="secondary" class="text-xs">
                ↑ 可直接交互，验证 visibleWhen 联动与 image_upload 是否符合预期
              </a-typography-text>
            </a-form>
          </a-card>

          <a-card size="small" title="提交时的参数拆分预览">
            <a-space direction="vertical" class="w-full" :size="8">
              <a-typography-text type="secondary">rule_config（taskType + 非图片参数，注入 rule_script）</a-typography-text>
              <JsonViewer :value="configPreview.ruleConfig" :expand-depth="2" copyable boxed sort />
              <a-typography-text type="secondary">ui_config（image_upload 参数，仅供 C 端渲染）</a-typography-text>
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
  import { VueMonacoEditor } from '@guolao/vue-monaco-editor';
  import SchemaFormRenderer from '../task-wizard/SchemaFormRenderer.vue';
  import {
    TASK_TYPE_OPTIONS,
    TRIGGER_EVENT_OPTIONS,
    TASK_TEMPLATES,
    buildDefaultRuleParams,
    splitSchemaValues,
  } from '../task-wizard/task-wizard-const';

  // ---------------------------- 编辑器常量 ----------------------------

  // QLExpress 语法与 Java 高度近似，借用 java 语言的高亮与括号匹配
  const RULE_SCRIPT_LANGUAGE = 'java';
  // json 语言可让 Monaco 自带 JSON 语法校验与格式化
  const SCHEMA_LANGUAGE = 'json';
  const EDITOR_THEME = 'vs-dark';
  const RULE_EDITOR_HEIGHT = 300;
  const SCHEMA_EDITOR_HEIGHT = 380;
  const MONACO_OPTIONS = {
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    fontSize: 13,
    tabSize: 2,
  };

  // ---------------------------- 默认值（取自共享 mock 模板，保证与向导数据同源） ----------------------------

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

  // ---------------------------- 单一状态源 ----------------------------

  const designer = reactive({
    base: {
      templateCode: DEFAULT_TEMPLATE.templateCode,
      templateName: DEFAULT_TEMPLATE.templateName,
      taskType: DEFAULT_TEMPLATE.taskType,
      triggerEvent: undefined,
    },
    ruleScript: DEFAULT_RULE_SCRIPT,
    uiSchemaText: DEFAULT_UI_SCHEMA_TEXT,
    // 右侧预览表单的交互值，供配置人员实时测试联动
    previewValues: {},
  });

  const formRef = ref();

  const FORM_RULES = {
    templateCode: [{ required: true, message: '请输入模板编码' }],
    templateName: [{ required: true, message: '请输入模板名称' }],
    taskType: [{ required: true, message: '请选择任务类型' }],
  };

  // ---------------------------- ui_schema 实时解析（容错，不崩溃） ----------------------------

  const schemaParse = computed(() => {
    try {
      return { ok: true, schema: JSON.parse(designer.uiSchemaText), error: null };
    } catch (e) {
      return { ok: false, schema: null, error: e.message };
    }
  });

  const previewParams = computed(() =>
    schemaParse.value.ok && Array.isArray(schemaParse.value.schema?.params) ? schemaParse.value.schema.params : []
  );

  // 参数结构变化时按 schema default 重建预览初值；解析失败期间保留上次可交互状态
  watch(
    () => JSON.stringify(previewParams.value),
    () => {
      designer.previewValues = buildDefaultRuleParams({ uiSchema: { params: previewParams.value } });
    },
    { immediate: true }
  );

  // 与向导提交共用同一拆分函数：预览即最终提交语义
  const configPreview = computed(() => {
    const { ruleValues, uiValues } = splitSchemaValues(previewParams.value, designer.previewValues);
    return {
      ruleConfig: { taskType: designer.base.taskType, ...ruleValues },
      uiConfig: uiValues,
    };
  });

  // ---------------------------- 保存（mock：组装模板 DTO 打印） ----------------------------

  async function onSave() {
    try {
      await formRef.value.validate();
      const templateDto = {
        ...designer.base,
        uiSchema: schemaParse.value.schema,
        ruleScript: designer.ruleScript,
      };
      console.log('【任务模板设计器 · 保存模板】t_task_template DTO：', templateDto);
      message.success('模板 DTO 已组装，请在控制台查看（mock 保存）');
    } catch (e) {
      message.error('基础信息校验未通过，请检查必填项');
    }
  }

  function onSaveDraft() {
    console.log('【任务模板设计器 · 暂存草稿】当前快照：', JSON.parse(JSON.stringify(designer)));
    message.success('草稿已暂存（mock）');
  }
</script>
