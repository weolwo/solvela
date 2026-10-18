<!--
  * 风控 - 规则
  *
  * 【一条规则只属于一个场景】2026-10-16 注册也接进了同一个引擎，于是这张表里混着
  * 登录与注册两条链路。所以第一列就是「场景」—— 看的人必须先知道自己在调哪条链路的松紧，
  * 两边的正常量级差着一个数量级（注册的阈值是「一小时 10 次」，登录是「一小时 60 次」）。
  *
  * 【这个页面是整套风控唯一的旋钮】规则的逻辑在代码里（一条规则一个 Java 类），
  * 这里能调的只有「松紧」：档位、分值、参数。编码、阶段、类型都是只读的 ——
  * 让人在页面上改它们等于在页面上改代码。所以它们在弹窗里不占表单行，只在顶上那条里摆着。
  *
  * 【🔴 全局滑块置顶】GLOBAL_CAPTCHA 的档位就是它的开关：被攻击时切成「生效」，
  * 30 秒内所有密码登录都要过滑块。这是应急档，所以它必须在第一眼看得到的位置，
  * 而不是混在二十条规则中间让人去找。
  *
  * 【为什么要显示「陪跑」是什么意思】陪跑 = 规则照常跑、命中照常记，但不影响结论。
  * 不解释的话，运营会以为陪跑就是「关了」，然后直接切生效 ——
  * 而陪跑攒下的那批数据正是用来判断「切过去会多打扰多少人」的。
  *
  * 【参数是 JSON】所以用 JSON 编辑器而不是裸 textarea：少一个逗号在 textarea 里看不出来，
  * 在这里是一眼的事。形状对不对仍由服务端用这条规则自己的校验判（分级要阈值递增、矩阵不许配成拒绝……）。
  *
  * @Date 2026-10-18
-->
<template>
  <a-alert
    type="info"
    show-icon
    style="margin-bottom: 12px"
    message="规则的逻辑在代码里，这里只能调松紧"
  >
    <template #description>
      档位的含义：<b>关闭</b> = 连特征都不取；<b>陪跑</b> = 照常执行、照常记命中，但
      <b>不影响用户</b>（攒数据用，是判断「切生效会多打扰多少人」的唯一依据）；<b>生效</b> = 参与决策。
      改动会记进数据变更留痕。
    </template>
  </a-alert>

  <!---------- 应急档置顶 begin ----------->
  <a-card v-if="globalCaptcha" size="small" :bordered="false" :hoverable="true" style="margin-bottom: 12px">
    <a-row align="middle" :gutter="12">
      <a-col flex="none">
        <a-tag :color="modeMeta(globalCaptcha.mode).color" style="font-size: 14px; padding: 2px 10px">
          全局滑块：{{ modeMeta(globalCaptcha.mode).desc }}
        </a-tag>
      </a-col>
      <a-col flex="auto" class="cell-sub">
        开着时<b>所有密码登录都要过滑块</b>，「熟悉网络免滑块」不起作用。被攻击时把它切成「生效」即可，
        最多 30 秒在所有实例上生效。
      </a-col>
      <a-col flex="none">
        <a-button v-privilege="'security:rule'" size="small" type="primary" @click="openEdit(globalCaptcha)">
          切换
        </a-button>
      </a-col>
    </a-row>
  </a-card>
  <!---------- 应急档置顶 end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      :pagination="false"
      :loading="tableLoading"
      rowKey="id"
      bordered
    >
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'ruleCode'">
          <div>{{ record.ruleName }}</div>
          <div class="cell-sub">{{ record.ruleCode }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'eventType'">
          <a-tag :color="eventTypeMeta(text).color">{{ eventTypeMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'phase'">
          <a-tag :color="phaseMeta(text).color">{{ phaseMeta(text).desc }}</a-tag>
          <a-tag :color="typeMeta(record.ruleType).color">{{ typeMeta(record.ruleType).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'mode'">
          <a-tag :color="modeMeta(text).color">{{ modeMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'effect'">
          <!--
            强规则给出哪一档、打分规则加几分 —— 同一列里表达两种东西，
            因为运营关心的是同一个问题：「它命中了会怎样」
          -->
          <a-tag v-if="record.directDecision" :color="decisionMeta(record.directDecision).color">
            {{ decisionMeta(record.directDecision).desc }}
          </a-tag>
          <span v-else-if="record.ruleType === RISK_RULE_TYPE_ENUM.SCORE.value">+{{ record.score }} 分</span>
          <span v-else class="cell-sub">看参数</span>
          <div v-if="record.ruleGroup" class="cell-sub">同组取最大：{{ record.ruleGroup }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'bypassByTrust'">
          <span v-if="text === 1">跳过</span>
          <span v-else class="cell-sub">照常执行</span>
        </template>

        <template v-else-if="column.dataIndex === 'params'">
          <span v-if="!record.params" class="cell-sub">用代码里的默认值</span>
          <a v-else :title="record.params" @click="openEdit(record)">{{ shorten(record.params) }}</a>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a v-privilege="'security:rule'" @click="openEdit(record)">调整</a>
        </template>
      </template>
    </a-table>
  </a-card>

  <!---------- 调整 begin ----------->
  <a-modal v-model:open="editOpen" title="调整规则" :width="720" :maskClosable="false" :destroyOnClose="true">
    <!-- 编码、场景、阶段、类型都是只读的，占不着表单行 —— 摆在顶上，让人先确认「我在调哪一条」 -->
    <div class="modal-target">
      <div class="modal-target-line">
        <span class="modal-target-name">{{ editForm.ruleName }}</span>
        <span class="cell-mono">{{ editForm.ruleCode }}</span>
        <a-tag :color="eventTypeMeta(editForm.eventType).color">{{ eventTypeMeta(editForm.eventType).desc }}</a-tag>
        <a-tag :color="phaseMeta(editForm.phase).color">{{ phaseMeta(editForm.phase).desc }}</a-tag>
        <a-tag :color="typeMeta(editForm.ruleType).color">{{ typeMeta(editForm.ruleType).desc }}</a-tag>
      </div>
      <div v-if="editForm.description" class="modal-target-note">{{ editForm.description }}</div>
      <div class="modal-target-note">编码与阶段、类型只读 —— 它们与代码里的实现一一对应，在页面上改它们等于在页面上改代码。</div>
    </div>

    <a-form ref="formRef" :model="editForm" :label-col="{ span: 4 }" :wrapper-col="{ span: 19 }">
      <a-form-item label="档位">
        <a-radio-group v-model:value="editForm.mode" button-style="solid" option-type="button" :options="RISK_MODE_OPTIONS" />
        <div class="form-hint">{{ MODE_HINT[editForm.mode] }}</div>
      </a-form-item>

      <!--
        🔴 从陪跑切到生效是这个页面上唯一会立刻影响线上用户的操作，
        所以它要有一句明确的提醒 —— 而不是保存成功之后才发现人人都在验证码
      -->
      <a-alert
        v-if="editForm.mode === RISK_MODE_ENUM.ENFORCE.value && originalMode !== RISK_MODE_ENUM.ENFORCE.value"
        type="warning"
        show-icon
        style="margin-bottom: 12px"
        message="切到「生效」之后，命中这条规则的人立刻会受到处置。"
        description="切之前建议先在决策流水里勾上「只看结论不一致」看一眼：那些就是会被多打扰的人。"
      />
      <a-alert
        v-if="editForm.mode === RISK_MODE_ENUM.OFF.value"
        type="error"
        show-icon
        style="margin-bottom: 12px"
        message="「关闭」会让这条规则连特征都不取 —— 既不拦人，也不再留下任何命中记录。"
        description="只想停止影响用户、但继续攒数据的话，选「陪跑」。"
      />

      <a-form-item v-if="editForm.ruleType === RISK_RULE_TYPE_ENUM.SCORE.value" label="命中加分">
        <a-input-number v-model:value="editForm.score" :min="0" :max="1000" :precision="0" style="width: 140px" />
        <div class="form-hint">分档线：前置 ≥ 30 出滑块，后置 ≥ 40 要 OTP、≥ 80 换通道</div>
      </a-form-item>

      <a-form-item label="参数" :validate-status="paramsError ? 'error' : ''" :help="paramsError">
        <div class="params-bar">
          <span class="form-hint" style="margin: 0">JSON；留空表示用代码里的默认值</span>
          <a-space size="small">
            <a-button size="small" :disabled="!editForm.params" @click="formatParams">格式化</a-button>
            <a-button size="small" :disabled="!editForm.params" @click="editForm.params = ''">清空（回默认值）</a-button>
          </a-space>
        </div>
        <SolvelaCodeEditor v-model:value="editForm.params" language="json" theme="vs" height="200px" />
        <div class="form-hint">
          保存时会用<b>这条规则自己的</b>校验过一遍（分级要阈值递增、矩阵不许配成拒绝……）。
          形状不对会直接报错，不会存进去之后让规则悄悄失效。
        </div>
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="editOpen = false">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="!!paramsError" @click="doSave">保存</a-button>
      </a-space>
    </template>
  </a-modal>
  <!---------- 调整 end ----------->
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { memberRiskApi } from '/@/api/business/member/member-risk-api';
  import SolvelaCodeEditor from '/@/components/business/code-editor/SolvelaCodeEditor.vue';
  import {
    RISK_DECISION_ENUM,
    RISK_EVENT_TYPE_ENUM,
    RISK_MODE_ENUM,
    RISK_MODE_OPTIONS,
    RISK_PHASE_ENUM,
    RISK_RULE_TYPE_ENUM,
    riskMetaOf,
  } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  /** 选中档位之后那一句「它到底会怎样」—— 光有「陪跑」两个字，运营会当成「关了」 */
  const MODE_HINT = {
    [RISK_MODE_ENUM.OFF.value]: '连特征都不取：不拦人，也不再留下任何命中记录。',
    [RISK_MODE_ENUM.SHADOW.value]: '照常执行、照常记命中，但不影响结论 —— 攒的就是「切生效会多打扰多少人」那份数据。',
    [RISK_MODE_ENUM.ENFORCE.value]: '参与决策：命中的人立刻会受到处置。',
  };

  const columns = ref([
    { title: '场景', dataIndex: 'eventType', width: 80 },
    { title: '规则', dataIndex: 'ruleCode', width: 220 },
    { title: '阶段 / 类型', dataIndex: 'phase', width: 150 },
    { title: '档位', dataIndex: 'mode', width: 90 },
    { title: '命中会怎样', dataIndex: 'effect', width: 160 },
    { title: '可信设备', dataIndex: 'bypassByTrust', width: 100 },
    { title: '参数', dataIndex: 'params', width: 260, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 80, fixed: 'right' },
  ]);

  const tableLoading = ref(false);
  const tableData = ref([]);

  /** 应急档那张卡片 */
  const globalCaptcha = computed(() => tableData.value.find((r) => r.ruleCode === 'GLOBAL_CAPTCHA'));

  function modeMeta(value) {
    return riskMetaOf(RISK_MODE_ENUM, value, { desc: value, color: 'default' });
  }

  function eventTypeMeta(value) {
    return riskMetaOf(RISK_EVENT_TYPE_ENUM, value, { desc: value, color: 'default' });
  }

  function phaseMeta(value) {
    return riskMetaOf(RISK_PHASE_ENUM, value, { desc: value, color: 'default' });
  }

  function typeMeta(value) {
    return riskMetaOf(RISK_RULE_TYPE_ENUM, value, { desc: value, color: 'default' });
  }

  function decisionMeta(value) {
    return riskMetaOf(RISK_DECISION_ENUM, value, { desc: value, color: 'default' });
  }

  function shorten(text) {
    return text && text.length > 60 ? `${text.slice(0, 60)}…` : text;
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      tableData.value = await memberRiskApi.listRules();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 调整 ----------------------------

  const editOpen = ref(false);
  const saving = ref(false);
  const formRef = ref();
  const originalMode = ref(undefined);
  const editForm = reactive({
    id: undefined,
    ruleCode: '',
    ruleName: '',
    ruleType: undefined,
    eventType: undefined,
    phase: undefined,
    description: '',
    mode: undefined,
    score: 0,
    params: '',
  });

  /** JSON 语法错在这里就说，不用等服务端 —— 少一个逗号在 textarea 里看不出来 */
  const paramsError = computed(() => {
    const text = (editForm.params || '').trim();
    if (!text) {
      return '';
    }
    try {
      JSON.parse(text);
      return '';
    } catch (e) {
      return `JSON 解不开：${e.message}`;
    }
  });

  function formatParams() {
    try {
      editForm.params = JSON.stringify(JSON.parse(editForm.params), null, 2);
    } catch {
      // 解不开就不动它：错误已经标在「参数」那一行上
    }
  }

  function openEdit(record) {
    Object.assign(editForm, {
      id: record.id,
      ruleCode: record.ruleCode,
      ruleName: record.ruleName,
      ruleType: record.ruleType,
      eventType: record.eventType,
      phase: record.phase,
      description: record.description,
      mode: record.mode,
      score: record.score ?? 0,
      params: record.params || '',
    });
    originalMode.value = record.mode;
    editOpen.value = true;
  }

  async function doSave() {
    if (paramsError.value) {
      return;
    }
    saving.value = true;
    try {
      await memberRiskApi.updateRule({
        id: editForm.id,
        mode: editForm.mode,
        score: editForm.score ?? 0,
        // 空串是「清空参数」的意思，与 undefined（不改）不是一回事，所以照原样传
        params: editForm.params,
      });
      message.success('已保存，最多 30 秒在所有实例上生效');
      editOpen.value = false;
      await queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }
</script>

<style scoped>
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .cell-mono {
    font-family: var(--font-family-monospace, monospace);
    font-size: 12px;
    color: rgba(0, 0, 0, 0.45);
  }

  /* 弹窗顶上那条「在调哪一条」 */
  .modal-target {
    margin-bottom: 16px;
    padding: 8px 12px;
    background: #fafafa;
    border-radius: 6px;
  }

  .modal-target-line {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
  }

  .modal-target-name {
    font-weight: 600;
  }

  .modal-target-note {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  .form-hint {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  .params-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 6px;
  }
</style>
