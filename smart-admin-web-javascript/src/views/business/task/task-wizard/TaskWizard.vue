<!--
  * 任务配置向导（第一阶段骨架：步骤框架 + 第1/4/5步 + 主子表 DTO 组装）
  * 第2步 ui_schema 动态表单、第3步阶梯搭建 以 a-empty 占位，下一阶段接入
  *
  * @Author:    alaric
  * @Date:      2026-07-19
-->
<template>
  <a-card :bordered="false">
    <!-- ==================== 提交成功页：替代整个向导，同时天然杜绝重复提交 ====================
         内嵌于活动创建向导时不展示本页：外壳有自己的完成页，两个成功页叠在一起会让运营
         以为流程结束了两次。此时改由 emit('saved') 通知外壳推进步骤。 -->
    <a-result
      v-if="submitResult.submitted && !embedded"
      status="success"
      :title="isEditMode ? '任务配置更新成功' : '任务配置提交成功'"
    >
      <template #subTitle>
        任务「{{ submitResult.taskName }}」{{ isEditMode ? '已更新' : '已创建' }}，归属活动：{{ submitResult.activityLabel }}
      </template>
      <template #extra>
        <a-space direction="vertical" align="center" :size="12">
          <a-space>
            <a-button type="primary" @click="goTaskList">返回任务列表</a-button>
            <!-- 「再建一个」只在新建流里有意义：编辑完接着建新任务是两回事 -->
            <a-button v-if="!isEditMode" @click="createNextTask">在同一活动下再建一个</a-button>
          </a-space>
          <a-button type="link" @click="viewCreatedTask">{{ isEditMode ? '查看这条任务' : '查看刚创建的任务' }}</a-button>
        </a-space>
      </template>
    </a-result>

    <template v-else>
    <!-- 草稿恢复提示：浏览器崩溃/强制关闭等未走离开拦截的情况下兜底 -->
    <a-alert v-if="pendingDraft" type="warning" show-icon class="mb-4">
      <template #message>
        发现上次未提交的配置草稿（暂存于 {{ pendingDraft.savedAt }}，任务名称
        {{ pendingDraft.wizardForm?.base?.taskName || '未填写' }}）
      </template>
      <template #action>
        <a-space>
          <a-button size="small" type="primary" @click="restoreDraft">恢复草稿</a-button>
          <a-button size="small" @click="discardDraft">丢弃</a-button>
        </a-space>
      </template>
    </a-alert>

    <!-- 编辑态给个明确横幅：向导长得和新建一模一样，不说清楚很容易以为自己在建新任务 -->
    <a-alert v-if="isEditMode" type="info" show-icon class="mb-4">
      <template #message>
        正在编辑任务「{{ wizardForm.base.taskName || '—' }}」（ID {{ editId }}）。
        保存后覆盖原配置，奖励阶梯整体替换；<b>归属活动不可更改</b>。
        已在跑的任务记录按接取时的快照执行，不受本次修改影响。
      </template>
    </a-alert>

    <a-flex justify="space-between" align="center" class="mb-6">
      <a-steps :current="currentStep" :items="WIZARD_STEP_ITEMS" size="small" class="flex-1" />
      <a-typography-text v-if="draftSavedAt" type="secondary" class="ml-4 text-xs whitespace-nowrap">
        草稿已暂存 {{ draftSavedAt }}
      </a-typography-text>
    </a-flex>

    <a-form ref="formRef" :model="wizardForm" layout="vertical">
      <!-- ==================== 第1步：模板与基础信息 ==================== -->
      <div v-show="currentStep === WIZARD_STEP.BASE">
        <a-card size="small" title="选择任务模板" class="mb-4" :loading="templateLoading">
          <a-empty
            v-if="!templateLoading && taskTemplates.length === 0"
            description="还没有任何任务模板，请先到「任务模板设计器」配置一个"
          />
          <a-row :gutter="[12, 12]">
            <a-col :span="12" v-for="tpl in taskTemplates" :key="tpl.templateCode">
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
                  <template #description>{{ tpl.description }}</template>
                </a-card-meta>
              </a-card>
            </a-col>
          </a-row>
        </a-card>

        <a-card size="small" title="基础信息">
          <!-- 内嵌时活动由外壳锁定：置灰而不是隐藏，让运营看得见这个任务归在哪个活动下 -->
          <a-form-item
            label="所属活动大类 activity_code"
            :name="['base', 'activityCode']"
            :rules="FORM_RULES.activityCode"
            :extra="activityExtraTip"
          >
            <!--
              编辑态锁死归属活动：奖励阶梯里的 prize_code 是按活动隔离的，
              换活动等于把这些奖品全部作废，那是重建一个任务而不是改配置。服务端也会忽略传入值
            -->
            <a-select
              v-model:value="wizardForm.base.activityCode"
              :options="activityOptions"
              :loading="activityLoading"
              :disabled="activityLocked"
              placeholder="请选择所属活动"
              show-search
              option-filter-prop="label"
              :allow-clear="!activityLocked"
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
                <a-select
                  v-model:value="wizardForm.base.triggerEvent"
                  :options="eventOptions"
                  :loading="eventLoading"
                  placeholder="请选择触发事件"
                />
                <div v-if="selectedEvent?.bizIdRequired" class="text-xs text-orange-600! mt-1">
                  ⚠️ 该事件要求上游上报时必须携带业务单号（eventBizId），否则无法防重
                </div>
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
          <!--
            只在 DAILY / WEEKLY 下出现：
            ONCE 语义就是终身一轮；UNLIMITED 是「轮次不限」，让它生效会变成「无限制+限1次=终身一次」，
            与它自己的名字矛盾 —— 界面上不该让人填一个填了也不生效的值
          -->
          <a-form-item
            v-if="isRoundLimitSupported"
            label="周期内可完成轮数 limit_count"
            :name="['limit', 'limitCount']"
          >
            <a-input-number v-model:value="wizardForm.limit.limitCount" :min="1" />
            <div class="text-xs text-gray-400! mt-1">
              一个周期内最多可完成几轮任务、领几次奖。完成一轮后自动开下一轮，用满为止
            </div>
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
            <!-- 没选活动 / 该活动下还没配奖品时，先把话说清楚，别让运营对着一个空下拉猜 -->
            <a-alert
              v-if="!wizardForm.base.activityCode"
              type="warning"
              show-icon
              message="请先在第 1 步选择所属活动 —— 奖励只能从该活动下已配置的奖品里挑。"
            />
            <a-alert v-else-if="!prizeLoading && prizeOptions.length === 0" type="warning" show-icon>
              <template #message>
                当前活动「{{ summary.activityLabel }}」下还没有配置奖品，请先到「活动中心 › 奖品配置」建好奖品再回来配阶梯。
              </template>
            </a-alert>
            <PrizeLadderBuilder
              v-model="wizardForm.prizeLadders"
              :condition-unit="stageConditionUnit"
              :prize-options="prizeOptions"
              :prize-loading="prizeLoading"
            />
          </a-space>
        </a-card>
      </div>

      <!-- ==================== 第4步：受众与时间 ==================== -->
      <div v-show="currentStep === WIZARD_STEP.AUDIENCE">
        <a-card size="small" title="受众与时间" class="mb-4">
          <a-form-item label="目标人群 target_audience" :name="['audience', 'targetAudience']">
            <a-radio-group v-model:value="wizardForm.audience.targetAudience" :options="TARGET_AUDIENCE_OPTIONS" option-type="button" />
            <div v-if="wizardForm.audience.targetAudience !== TARGET_AUDIENCE_ENUM.ALL" class="text-xs text-orange-600! mt-1">
              ⚠️ 选择非「全部会员」后，<b>上游上报事件时必须携带会员属性 isNewMember</b>，
              否则该任务收到的事件会被丢弃（丢弃原因可在任务记录的「事件流水」里看到）。
              请与埋点开发确认后再使用。
            </div>
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="生效时间" :name="['audience', 'timeRange']" :rules="FORM_RULES.timeRange">
                <a-range-picker
                  v-model:value="wizardForm.audience.timeRange"
                  :show-time="RANGE_SHOW_TIME"
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
            extra="用户点击任务卡片弹出的详细活动规则说明（如：同一设备限领1次等），支持富文本排版，内容以 HTML 存储"
          >
            <SmartRichEditor v-model="wizardForm.display.ruleDesc" />
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
            <a-descriptions-item label="详细规则说明" :span="2">
              <!-- 富文本内容由本后台运营录入，此处仅回显自己刚填写的内容 -->
              <div v-if="wizardForm.display.ruleDesc" class="max-h-40 overflow-auto" v-html="wizardForm.display.ruleDesc"></div>
              <span v-else>-</span>
            </a-descriptions-item>
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
              <a-button type="primary" :loading="submitLoading" @click="onSubmit">正式提交</a-button>
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
    </template>
  </a-card>
</template>

<script setup>
  import { RANGE_SHOW_TIME } from '/@/constants/date-time-const';
  import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
  import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
  import dayjs from 'dayjs';
  import { message, Modal } from 'ant-design-vue';
  import { localRead, localRemove, localSave } from '/@/utils/local-util';
  import LocalStorageKeyConst from '/@/constants/local-storage-key-const';
  import { taskApi } from '/@/api/business/task/task-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { prizeConfigApi } from '/@/api/business/prize/prize-config/prize-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import SmartRichEditor from '/@/components/framework/wangeditor/index.vue';
  import SchemaFormRenderer from './SchemaFormRenderer.vue';
  import PrizeLadderBuilder from './PrizeLadderBuilder.vue';
  import {
    WIZARD_STEP,
    WIZARD_STEP_ITEMS,
    TASK_CONFIG_LIST_PATH,
    toEventOptions,
    TASK_GROUP_OPTIONS,
    LIMIT_TYPE_ENUM,
    LIMIT_TYPE_OPTIONS,
    TARGET_AUDIENCE_ENUM,
    TARGET_AUDIENCE_OPTIONS,
    STAGE_CONDITION_UNIT,
    isSchemaParamVisible,
    splitSchemaValues,
    buildDefaultRuleParams,
    buildDefaultWizardForm,
    withLadderUid,
  } from './task-wizard-const';

  // ---------------------------- 内嵌契约 ----------------------------

  /*
   * 两种用法：
   * ① 独立入口（默认）：从菜单进入，活动可选，提交后展示自带的成功页与三个出口。
   * ② 内嵌于活动创建向导：活动由外壳锁定并置灰，成功页被抑制，改为 emit('saved') 让外壳推进。
   *
   * ⚠️ 本组件的提交是「走完 5 步一次性提交」——后端 TaskConfigService.wizardSubmit 是单个
   * @Transactional，主表与奖励子表一次性落库，中途没有可保存的半成品。
   * 所以外壳<b>不能</b>持有本组件的保存按钮：那个「中途 save」根本不存在。
   */
  const props = defineProps({
    activityCode: { type: String, default: '' },
    embedded: { type: Boolean, default: false },
  });

  const emit = defineEmits(['saved']);

  // ---------------------------- 单一状态源：向导全部表单输入（初始值统一由常量文件工厂构建） ----------------------------

  const wizardForm = reactive(buildDefaultWizardForm());

  const currentStep = ref(WIZARD_STEP.BASE);
  const formRef = ref();
  const router = useRouter();
  const route = useRoute();

  // ---------------------------- 活动大类与模板：均由服务端下发 ----------------------------

  // 只拉任务类活动（对齐 t_activity_config.activity_type）
  const ACTIVITY_TYPE_TASK = 'TASK';

  const activityOptions = ref([]);
  const activityLoading = ref(false);
  const taskTemplates = ref([]);
  const templateLoading = ref(false);

  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList(ACTIVITY_TYPE_TASK);
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      activityLoading.value = false;
    }
  }

  const eventOptions = ref([]);
  const eventLoading = ref(false);

  /**
   * 触发事件来自服务端注册表 t_task_event，不再是前端写死的常量 ——
   * 加事件只需加一行数据，前端零改动。
   */
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

  // ---------------------------- 奖品下拉（按所属活动过滤） ----------------------------

  const prizeOptions = ref([]);
  const prizeLoading = ref(false);

  /**
   * 奖励阶梯里的 prize_code 必须是**当前活动下已配置的奖品**。
   *
   * 此前这里是个自由文本框（默认值还是写死的 SCORE_100）：拼错、或者填了别的活动的奖品编码，
   * 任务照样能保存，但发奖时按 prize_code 查不到 t_prize_config —— 派发在
   * AFTER_COMMIT 里静默失败，前台完全无感，要翻 t_prize_log.fail_reason 才发现。
   * 改成下拉后运营根本填不出不存在的编码。
   */
  async function loadPrizeOptions(activityCode) {
    if (!activityCode) {
      prizeOptions.value = [];
      return;
    }
    prizeLoading.value = true;
    try {
      const res = await prizeConfigApi.optionList(activityCode);
      prizeOptions.value = (res.data || []).map((item) => ({
        value: item.prizeCode,
        label: `${item.prizeName}（${item.prizeCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      prizeLoading.value = false;
    }
  }

  // 换活动就换一批候选奖品；已选中的若不在新活动下，清掉而不是留着一个跨活动的脏编码
  watch(
    () => wizardForm.base.activityCode,
    async (activityCode) => {
      await loadPrizeOptions(activityCode);
      const valid = new Set(prizeOptions.value.map((o) => o.value));
      wizardForm.prizeLadders = wizardForm.prizeLadders.map((row) =>
        row.prizeCode && !valid.has(row.prizeCode) ? { ...row, prizeCode: undefined } : row
      );
    },
    { immediate: true }
  );

  const selectedEvent = computed(() => eventOptions.value.find((e) => e.value === wizardForm.base.triggerEvent));

  // 只有 DAILY / WEEKLY 受 limit_count 轮次限制，与后端 TaskPeriodResolver.supportsRoundLimit 同一口径
  const isRoundLimitSupported = computed(
    () => wizardForm.limit.limitType === LIMIT_TYPE_ENUM.DAILY || wizardForm.limit.limitType === LIMIT_TYPE_ENUM.WEEKLY
  );

  async function loadTaskTemplates() {
    templateLoading.value = true;
    try {
      const res = await taskApi.queryTemplateOptionList();
      taskTemplates.value = res.data || [];
      // 默认选中第一个模板；已有选中（如恢复草稿）时不覆盖
      if (!wizardForm.base.templateCode && taskTemplates.value.length > 0) {
        wizardForm.base.templateCode = taskTemplates.value[0].templateCode;
      }
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      templateLoading.value = false;
    }
  }

  // ---------------------------- 模板选择与 ruleParams 初始化 ----------------------------

  const selectedTemplate = computed(() => taskTemplates.value.find((t) => t.templateCode === wizardForm.base.templateCode));

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
      // 模板列表还没回来时先不动 ruleParams：否则恢复草稿时会因为 selectedTemplate 暂时为空而把参数值冲成 {}
      if (!selectedTemplate.value) {
        return;
      }
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

  /**
   * 奖励阶梯不是 a-form-item 绑定的字段（它是自绘表格），validateFields 管不到它 ——
   * 这一步此前完全没有校验，阶梯的奖品留空也能一路点到提交。
   * 返回错误文案，没问题时返回空串。
   */
  function validatePrizeLadders() {
    const missing = wizardForm.prizeLadders
      .map((row, index) => (row.prizeCode ? null : index + 1))
      .filter((i) => i !== null);
    if (missing.length) {
      return `第 ${missing.join('、')} 级阶梯还没选奖品`;
    }
    return '';
  }

  async function nextStep() {
    try {
      if (currentStep.value === WIZARD_STEP.PRIZE) {
        const err = validatePrizeLadders();
        if (err) {
          message.error(err);
          return;
        }
      }
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
    activityLabel: optionLabel(activityOptions.value, wizardForm.base.activityCode),
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

  const submitLoading = ref(false);

  // 提交结果：submitted 为 true 时展示成功页，向导表单整体隐藏，从根源杜绝重复提交
  const submitResult = reactive({
    submitted: false,
    taskConfigId: null,
    taskName: '',
    activityLabel: '',
  });

  async function onSubmit() {
    // 阶梯不在 a-form 的管辖范围内，validate() 查不到它 —— 提交前单独兜一道。
    // 走到这里通常已被「下一步」拦过，但草稿恢复可能直接落在汇总页，那条路绕开了逐步校验
    const ladderError = validatePrizeLadders();
    if (ladderError) {
      message.error(ladderError);
      currentStep.value = WIZARD_STEP.PRIZE;
      return;
    }
    // 校验失败与接口失败分开处理：校验不过不进入 loading
    try {
      await formRef.value.validate();
    } catch (e) {
      message.error('表单校验未通过，请检查各步骤必填项');
      return;
    }
    submitLoading.value = true;
    try {
      const payload = buildSubmitData();
      const res = isEditMode.value
        ? await taskApi.updateTaskConfig({ ...payload, id: editId.value })
        : await taskApi.submitTaskConfig(payload);
      // 成功页展示与跳转定位所需信息，需在表单重置前留存
      submitResult.taskConfigId = res.data;
      submitResult.taskName = wizardForm.base.taskName;
      submitResult.activityLabel = summary.value.activityLabel;
      submitResult.submitted = true;
      // 已入库，清掉本地草稿避免下次进来又提示恢复
      clearTimeout(draftTimer);
      clearDraft();
      // 内嵌时成功页被抑制，改由外壳推进到它自己的完成页
      emit('saved', { activityCode: wizardForm.base.activityCode, taskConfigId: res.data });
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      submitLoading.value = false;
    }
  }

  // ---------------------------- 未保存内容保护 ----------------------------

  /*
   * 「有没有改动」的比较基线。
   * 新建时是空表单；编辑时是回显完成后的那一刻 —— 否则一进编辑页就被判成有未保存改动，
   * 点返回必弹「有未提交的配置」。回显结束后由 loadWizardDetail 重置它。
   */
  const baseSnapshot = ref(JSON.stringify(buildDefaultWizardForm()));
  const currentSnapshot = computed(() => JSON.stringify(wizardForm));
  // 已提交成功的内容已入库，不再视为待保存
  const isDirty = computed(() => !submitResult.submitted && currentSnapshot.value !== baseSnapshot.value);

  onBeforeRouteLeave(() => {
    if (!isDirty.value) {
      return true;
    }
    return new Promise((resolve) => {
      Modal.confirm({
        title: '有未提交的配置',
        content: '向导中已填写的内容尚未提交，离开本页将全部丢失。',
        okText: '仍然离开',
        okType: 'danger',
        cancelText: '留在本页',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  });

  function handleBeforeUnload(event) {
    if (!isDirty.value) {
      return;
    }
    event.preventDefault();
    event.returnValue = '';
  }

  // ---------------------------- 本地草稿：连同填写进度一起保留 ----------------------------

  const DRAFT_DEBOUNCE_MS = 2000;
  const pendingDraft = ref(null);
  const draftSavedAt = ref('');
  let draftTimer = null;

  function writeDraft() {
    const draft = {
      wizardForm: JSON.parse(currentSnapshot.value),
      // 向导特有：记录填到第几步，恢复后直接回到当时的位置
      currentStep: currentStep.value,
      savedAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    };
    localSave(LocalStorageKeyConst.TASK_WIZARD_DRAFT, JSON.stringify(draft));
    draftSavedAt.value = dayjs(draft.savedAt).format('HH:mm:ss');
  }

  function clearDraft() {
    localRemove(LocalStorageKeyConst.TASK_WIZARD_DRAFT);
    draftSavedAt.value = '';
  }

  watch(currentSnapshot, () => {
    // 历史草稿待决策期间冻结暂存，避免把待恢复内容覆盖掉；已提交成功的也不再暂存。
    // 编辑态同样不写草稿：草稿是「还没建出来的任务」，把一次编辑存成草稿，
    // 下次进新建向导会被当成未完成的新任务恢复出来，凭空多一条任务
    if (pendingDraft.value || submitResult.submitted || isEditMode.value) {
      return;
    }
    clearTimeout(draftTimer);
    // 表单被清回初始空态，说明运营主动放弃了这次配置，草稿一并清掉
    if (!isDirty.value) {
      clearDraft();
      return;
    }
    draftTimer = setTimeout(writeDraft, DRAFT_DEBOUNCE_MS);
  });

  async function restoreDraft() {
    const draft = pendingDraft.value;
    const { ruleParams, ...rest } = draft.wizardForm;
    Object.assign(wizardForm, rest);
    // 老草稿是在 _uid 这套行标识之前存下的，补齐后表格才有稳定的 row-key
    wizardForm.prizeLadders = withLadderUid(wizardForm.prizeLadders);
    currentStep.value = draft.currentStep ?? WIZARD_STEP.BASE;
    pendingDraft.value = null;
    // templateCode 赋值会触发 watch 用模板默认值重建 ruleParams，必须等它跑完再回填草稿参数
    await nextTick();
    wizardForm.ruleParams = ruleParams;
    message.success('草稿已恢复，可继续配置');
  }

  function discardDraft() {
    clearDraft();
    pendingDraft.value = null;
    message.success('草稿已丢弃');
  }

  // ---------------------------- 编辑态：从列表页带 id 进来，回显既有配置 ----------------------------

  /*
   * 编辑与新建复用同一套向导：任务是主子表 + 模板驱动的动态表单，
   * 再维护一份「编辑专用表单」等于把 ui_schema 渲染、阶梯校验、奖品级联全抄一遍。
   *
   * 与新建的差别只有四处：活动锁死、不走草稿、提交打 update、成功页文案。
   */
  const editId = ref(null);
  const isEditMode = computed(() => !!editId.value);
  const detailLoading = ref(false);

  // 内嵌（活动向导锁定）与编辑（换活动会让阶梯失效）两种情况都不允许改活动
  const activityLocked = computed(() => props.embedded || isEditMode.value);
  const activityExtraTip = computed(() => {
    if (props.embedded) {
      return '由活动创建向导锁定，不可更改';
    }
    if (isEditMode.value) {
      return '编辑时不可更改：换活动会让已配的奖励阶梯全部失效';
    }
    return '原子任务必须归属一个活动大类，C端按大类聚合展示';
  });

  /*
   * 同一路由只是 query 变化时 vue-router 会复用组件而不重新挂载 ——
   * 从「编辑(?id=5)」直接点菜单进「任务配置向导(无 query)」就属于这种情况。
   * 只写 onMounted 的话 editId 会残留，新建流程会把新任务提交成对 5 号的更新。
   */
  watch(
    () => route.query.id,
    (id) => {
      const next = id ? Number(id) : null;
      if (next === editId.value) {
        return;
      }
      editId.value = next;
      // 回到新建态：表单清回初始值，基线也跟着回到空表单
      Object.assign(wizardForm, buildDefaultWizardForm());
      baseSnapshot.value = JSON.stringify(buildDefaultWizardForm());
      currentStep.value = WIZARD_STEP.BASE;
      if (next) {
        loadWizardDetail(next);
      }
    }
  );

  async function loadWizardDetail(id) {
    detailLoading.value = true;
    try {
      const res = await taskApi.queryWizardDetail(id);
      const d = res.data;
      if (!d) {
        message.error('任务配置不存在');
        return;
      }

      Object.assign(wizardForm.base, {
        activityCode: d.activityCode,
        templateCode: d.templateCode,
        taskName: d.taskName,
        triggerEvent: d.triggerEvent,
        taskGroup: d.taskGroup,
      });
      Object.assign(wizardForm.limit, { limitType: d.limitType, limitCount: d.limitCount });
      Object.assign(wizardForm.audience, {
        targetAudience: d.targetAudience,
        // 起止时间两头都空 = 长期有效，与提交时 longTerm 的写法互逆
        longTerm: !d.startTime && !d.endTime,
        timeRange: d.startTime && d.endTime ? [d.startTime, d.endTime] : [],
        sortWeight: d.sortWeight,
        actionUrl: d.actionUrl || '',
        badge: d.badge || '',
      });
      Object.assign(wizardForm.display, { taskDesc: d.taskDesc || '', ruleDesc: d.ruleDesc || '' });
      wizardForm.prizeLadders = withLadderUid(
        (d.prizeMappingList || []).map((item) => ({
          stageCondition: item.stageCondition,
          prizeCode: item.prizeCode,
          prizeMode: item.prizeMode,
          prizeValue: item.prizeValue,
        }))
      );

      // 同 restoreDraft：templateCode 一赋值就会触发 watch 用模板默认值重建 ruleParams，
      // 必须等它跑完再把库里的参数值盖回去，否则回显出来的是模板默认值而不是运营当初填的
      await nextTick();
      wizardForm.ruleParams = { ...(d.ruleConfig || {}), ...(d.uiConfig || {}) };

      // 回显完成才是编辑态的「初始状态」；不重置基线的话，一进来就被判成有未保存改动，
      // 点返回必弹「有未提交的配置」
      await nextTick();
      baseSnapshot.value = JSON.stringify(wizardForm);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      detailLoading.value = false;
    }
  }

  /*
   * 内嵌模式：活动由外壳锁定，写进表单的单一状态源里（铁律 4），
   * 而不是在提交时另找一个地方取值 —— 那样校验、摘要、草稿三处都会看到不一致的活动。
   *
   * onMounted 与 watch 两条路径都要处理：外壳从活动 A 切到活动 B 时若组件没有重新挂载，
   * 只写 onMounted 会让任务错误地挂到上一个活动下（这个错落库后很难发现）。
   */
  watch(
    () => props.activityCode,
    (code) => {
      if (props.embedded && code) {
        wizardForm.base.activityCode = code;
      }
    },
    { immediate: true }
  );

  onMounted(() => {
    // 活动大类与模板都来自服务端，两者互不依赖，并行拉取
    loadActivityOptions();
    loadTaskTemplates();
    loadEventOptions();

    window.addEventListener('beforeunload', handleBeforeUnload);

    // 编辑态：列表页「编辑」带 ?id= 进来，回显既有配置
    const idFromQuery = route.query.id;
    if (idFromQuery) {
      editId.value = Number(idFromQuery);
      loadWizardDetail(editId.value);
      return;
    }

    // 内嵌时不走草稿恢复：草稿是「上次没配完的任务」，与本次向导正在建的活动多半不是一回事，
    // 弹出来只会让运营把无关配置恢复到新活动下
    if (props.embedded) {
      return;
    }
    const raw = localRead(LocalStorageKeyConst.TASK_WIZARD_DRAFT);
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

  // ---------------------------- 成功页三个出口 ----------------------------

  function goTaskList() {
    router.push(TASK_CONFIG_LIST_PATH);
  }

  /**
   * 列表页按任务名称回填查询条件，直接定位刚创建的那条
   */
  function viewCreatedTask() {
    router.push({ path: TASK_CONFIG_LIST_PATH, query: { taskName: submitResult.taskName } });
  }

  /**
   * 同一活动下再建一个：保留活动级配置（活动/生效时间/人群），重置任务级配置
   * 对应运营「一个活动大类下批量配 N 个原子任务」的实际作业模式
   */
  async function createNextTask() {
    const keep = {
      activityCode: wizardForm.base.activityCode,
      timeRange: [...wizardForm.audience.timeRange],
      longTerm: wizardForm.audience.longTerm,
      targetAudience: wizardForm.audience.targetAudience,
    };
    Object.assign(wizardForm, buildDefaultWizardForm());
    wizardForm.base.activityCode = keep.activityCode;
    wizardForm.audience.timeRange = keep.timeRange;
    wizardForm.audience.longTerm = keep.longTerm;
    wizardForm.audience.targetAudience = keep.targetAudience;

    submitResult.submitted = false;
    currentStep.value = WIZARD_STEP.BASE;
    // 表单在成功页期间被卸载，需等重新挂载后再清校验状态
    await nextTick();
    formRef.value?.clearValidate();
    message.success('已保留活动与生效时间，请配置下一个任务');
  }

  function onSaveDraft() {
    console.log('【任务配置向导 · 保存草稿】当前表单快照：', JSON.parse(JSON.stringify(wizardForm)));
    message.success('草稿已保存（mock）');
  }
</script>
