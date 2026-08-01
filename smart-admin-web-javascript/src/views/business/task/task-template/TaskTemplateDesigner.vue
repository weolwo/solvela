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
    <!-- 草稿恢复提示：浏览器崩溃/强制关闭等未走离开拦截的情况下兜底 -->
    <a-alert v-if="pendingDraft" type="warning" show-icon class="mb-4">
      <template #message>
        发现上次未提交的草稿（暂存于 {{ pendingDraft.savedAt }}，模板编码 {{ pendingDraft.base?.templateCode }}）
      </template>
      <template #action>
        <a-space>
          <a-button size="small" type="primary" @click="restoreDraft">恢复草稿</a-button>
          <a-button size="small" @click="discardDraft">丢弃</a-button>
        </a-space>
      </template>
    </a-alert>

    <a-row :gutter="16">
      <!-- ==================== 左侧：模板规则与代码编辑 ==================== -->
      <a-col :span="14">
        <a-space direction="vertical" class="w-full" :size="16">
          <a-card size="small" title="模板基础信息">
            <a-form ref="formRef" :model="designer" layout="vertical">
              <a-row :gutter="16">
                <a-col :span="12">
                  <!-- 模板编码：10位大写字母+数字，同时也是 upsert 唯一键；填已存在的编码即覆盖该模板 -->
                  <a-form-item label="模板编码 template_code" :name="['base', 'templateCode']" :rules="FORM_RULES.templateCode">
                    <a-input-group compact>
                      <a-input
                        v-model:value="designer.base.templateCode"
                        style="width: calc(100% - 96px)"
                        :maxlength="10"
                        placeholder="如 H88JHKJFNE，或点右侧生成"
                        @change="onTemplateCodeInput"
                      />
                      <a-tooltip title="随机生成一个未被占用的编码（等同于新建一个模板）">
                        <a-button style="width: 96px" :loading="codeGenerating" @click="generateTemplateCode">生成</a-button>
                      </a-tooltip>
                    </a-input-group>
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
                      :options="eventOptions"
                      :loading="eventLoading"
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

          <a-flex justify="space-between" align="center" class="mt-4">
            <!-- 保存状态：服务端保存状态 + 本地草稿时间，改完不用猜存没存 -->
            <a-space :size="12">
              <a-badge v-if="isDirty" status="warning" text="有未保存改动" />
              <a-badge v-else-if="lastSavedAt" status="success" :text="`已保存 · ${lastSavedAt}`" />
              <a-typography-text v-else type="secondary">尚未保存</a-typography-text>
              <a-typography-text v-if="draftSavedAt" type="secondary" class="text-xs">
                本地草稿 {{ draftSavedAt }}
              </a-typography-text>
            </a-space>
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
  import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import { onBeforeRouteLeave } from 'vue-router';
  import dayjs from 'dayjs';
  import { message, Modal } from 'ant-design-vue';
  import { localRead, localRemove, localSave } from '/@/utils/local-util';
  import LocalStorageKeyConst from '/@/constants/local-storage-key-const';
  import { taskApi } from '/@/api/business/task/task-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined } from '@ant-design/icons-vue';
  import { VueMonacoEditor } from '@guolao/vue-monaco-editor';
  import SchemaFormRenderer from '../task-wizard/SchemaFormRenderer.vue';
  import { regular } from '/@/constants/regular-const';
  import {
    TASK_TYPE_OPTIONS,
    toEventOptions,
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

  // 新建模板的起手骨架。icon / desc 会被任务向导的模板卡片读走，是 ui_schema 的约定字段
  const DEFAULT_UI_SCHEMA = {
    version: 1,
    icon: '📅',
    desc: '连续/累计签到达标，支持补签联动配置',
    params: [
      { key: 'targetDays', label: '连续签到目标', widget: 'number', unit: '天', default: 7, min: 1, required: true },
      { key: 'allowRepair', label: '允许补签', widget: 'switch', default: true },
      {
        key: 'repairCost',
        label: '补签消耗积分',
        widget: 'number',
        unit: '积分',
        default: 20,
        min: 0,
        visibleWhen: { field: 'allowRepair', eq: true },
        help: '关闭「允许补签」后此项自动隐藏',
      },
    ],
  };
  const DEFAULT_TEMPLATE = {
    templateName: '每日签到',
    taskType: TASK_TYPE_OPTIONS[1].value,
  };
  const DEFAULT_UI_SCHEMA_TEXT = JSON.stringify(DEFAULT_UI_SCHEMA, null, 2);
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
      // 编码留空，onMounted 拉一个服务端生成的；也可手输已有编码来覆盖那个模板
      templateCode: '',
      templateName: DEFAULT_TEMPLATE.templateName,
      taskType: DEFAULT_TEMPLATE.taskType,
      triggerEvent: undefined,
    },
    ruleScript: DEFAULT_RULE_SCRIPT,
    uiSchemaText: DEFAULT_UI_SCHEMA_TEXT,
    previewValues: {},
  });

  const formRef = ref();

  // ---------------------------- 未保存改动追踪 ----------------------------

  // 模板内容快照：只含真正会入库的内容，previewValues 是预览交互态不参与比对
  const currentSnapshot = computed(() =>
    JSON.stringify({
      base: designer.base,
      ruleScript: designer.ruleScript,
      uiSchemaText: designer.uiSchemaText,
    })
  );
  const savedSnapshot = ref(currentSnapshot.value);
  const lastSavedAt = ref('');
  const isDirty = computed(() => currentSnapshot.value !== savedSnapshot.value);

  // ---------------------------- 模板编码 ----------------------------

  const codeGenerating = ref(false);

  // 手输时统一转大写，省得因为小写被规则拦下来
  function onTemplateCodeInput() {
    if (designer.base.templateCode) {
      designer.base.templateCode = designer.base.templateCode.toUpperCase();
    }
  }

  async function generateTemplateCode() {
    codeGenerating.value = true;
    try {
      const res = await taskApi.generateTemplateCode();
      designer.base.templateCode = res.data;
      formRef.value?.clearValidate(['base', 'templateCode']);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      codeGenerating.value = false;
    }
  }

  /**
   * 进页面时预填一个编码，并把它并入基线
   * 不重置基线的话，人还没动一个字页面就是脏的、离开还要被拦一次
   */
  async function prefillTemplateCode() {
    await generateTemplateCode();
    savedSnapshot.value = currentSnapshot.value;
  }

  const FORM_RULES = {
    templateCode: [
      { required: true, message: '请输入模板编码' },
      { pattern: regular.bizCode, message: regular.bizCodeDesc },
    ],
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
      const res = await taskApi.saveTaskTemplate(templateDto);
      // 保存成功后重置脏标记基准，并清掉本地草稿（内容已在服务端）
      savedSnapshot.value = currentSnapshot.value;
      clearTimeout(draftTimer);
      clearDraft();
      lastSavedAt.value = dayjs().format('HH:mm:ss');
      // 区分新建与覆盖：填了已存在的 templateCode 时明确告知，避免误覆盖线上模板而不自知
      message.success(res.data ? '模板已创建' : '模板已更新（覆盖了同编码的原有配置）');
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saveLoading.value = false;
    }
  }

  // ---------------------------- 未保存改动保护 ----------------------------

  // 站内路由跳转拦截
  onBeforeRouteLeave(() => {
    if (!isDirty.value) {
      return true;
    }
    return new Promise((resolve) => {
      Modal.confirm({
        title: '有未保存的改动',
        content: 'ui_schema / rule_script 的修改尚未保存，离开本页将丢失这些内容。',
        okText: '仍然离开',
        okType: 'danger',
        cancelText: '留在本页',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  });

  // 关闭标签页/刷新拦截（浏览器原生确认框）
  function handleBeforeUnload(event) {
    if (!isDirty.value) {
      return;
    }
    event.preventDefault();
    event.returnValue = '';
  }

  // ---------------------------- 本地草稿：防浏览器崩溃/强制关闭丢失内容 ----------------------------

  const DRAFT_DEBOUNCE_MS = 2000;
  // 检测到的历史草稿，等待用户决定恢复还是丢弃
  const pendingDraft = ref(null);
  const draftSavedAt = ref('');
  let draftTimer = null;

  function writeDraft() {
    const draft = {
      base: { ...designer.base },
      ruleScript: designer.ruleScript,
      uiSchemaText: designer.uiSchemaText,
      savedAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    };
    localSave(LocalStorageKeyConst.TASK_TEMPLATE_DRAFT, JSON.stringify(draft));
    draftSavedAt.value = dayjs(draft.savedAt).format('HH:mm:ss');
  }

  function clearDraft() {
    localRemove(LocalStorageKeyConst.TASK_TEMPLATE_DRAFT);
    draftSavedAt.value = '';
  }

  // 内容变化即自动暂存，不依赖使用者记得手动点
  watch(currentSnapshot, () => {
    // 历史草稿待用户决定期间冻结自动暂存，否则随手一改就会把待恢复的内容覆盖掉
    if (pendingDraft.value) {
      return;
    }
    clearTimeout(draftTimer);
    draftTimer = setTimeout(writeDraft, DRAFT_DEBOUNCE_MS);
  });

  function onSaveDraft() {
    clearTimeout(draftTimer);
    writeDraft();
    message.success(`草稿已暂存到本地 · ${draftSavedAt.value}`);
  }

  function restoreDraft() {
    const draft = pendingDraft.value;
    Object.assign(designer.base, draft.base);
    designer.ruleScript = draft.ruleScript;
    designer.uiSchemaText = draft.uiSchemaText;
    pendingDraft.value = null;
    message.success('草稿已恢复，确认无误后请点「保存模板」提交到服务端');
  }

  function discardDraft() {
    clearDraft();
    pendingDraft.value = null;
    message.success('草稿已丢弃');
  }

  // 触发事件来自服务端注册表（与向导同一个接口），不再是前端写死的常量
  const eventOptions = ref([]);
  const eventLoading = ref(false);

  async function loadEventOptions() {
    eventLoading.value = true;
    try {
      const res = await taskApi.queryEventOptionList();
      eventOptions.value = toEventOptions(res.data);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      eventLoading.value = false;
    }
  }

  onMounted(() => {
    prefillTemplateCode();
    loadEventOptions();

    window.addEventListener('beforeunload', handleBeforeUnload);
    // 检测上次遗留的草稿（浏览器崩溃、强制关闭等未走离开拦截的情况）
    const raw = localRead(LocalStorageKeyConst.TASK_TEMPLATE_DRAFT);
    if (!raw) {
      return;
    }
    try {
      pendingDraft.value = JSON.parse(raw);
    } catch (e) {
      clearDraft();
    }
  });

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload);
    clearTimeout(draftTimer);
  });

</script>
