<!--
  * 登录风控 - 规则
  *
  * 【这个页面是整套风控唯一的旋钮】规则的逻辑在代码里（一条规则一个 Java 类），
  * 这里能调的只有「松紧」：档位、分值、参数。编码、阶段、类型都是只读的 ——
  * 让人在页面上改它们等于在页面上改代码。
  *
  * 【🔴 全局滑块置顶】GLOBAL_CAPTCHA 的档位就是它的开关：被攻击时切成「生效」，
  * 30 秒内所有密码登录都要过滑块。这是应急档，所以它必须在第一眼看得到的位置，
  * 而不是混在二十条规则中间让人去找。
  *
  * 【为什么要显示「陪跑」是什么意思】陪跑 = 规则照常跑、命中照常记，但不影响结论。
  * 不解释的话，运营会以为陪跑就是「关了」，然后直接切生效 ——
  * 而陪跑攒下的那批数据正是用来判断「切过去会多打扰多少人」的。
  *
  * @Date 2026-10-16
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
        <a-button v-privilege="'memberRisk:update'" size="small" type="primary" @click="openEdit(globalCaptcha)">
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
          <a v-privilege="'memberRisk:update'" @click="openEdit(record)">调整</a>
        </template>
      </template>
    </a-table>
  </a-card>

  <!---------- 调整 begin ----------->
  <a-modal v-model:open="editOpen" :title="`调整：${editForm.ruleName}`" :confirmLoading="saving" @ok="doSave" width="640">
    <a-form :labelCol="{ span: 5 }">
      <a-form-item label="规则编码">
        <span>{{ editForm.ruleCode }}</span>
        <div class="cell-sub">只读 —— 它与代码里的实现一一对应</div>
      </a-form-item>

      <a-form-item label="它在抓什么" v-if="editForm.description">
        <div class="cell-sub">{{ editForm.description }}</div>
      </a-form-item>

      <a-form-item label="档位">
        <a-radio-group v-model:value="editForm.mode" :options="RISK_MODE_OPTIONS" />
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

      <a-form-item label="命中加分" v-if="editForm.ruleType === RISK_RULE_TYPE_ENUM.SCORE.value">
        <a-input-number v-model:value="editForm.score" :min="0" :max="1000" style="width: 140px" />
        <div class="cell-sub">分档线：前置 ≥ 30 出滑块，后置 ≥ 40 要 OTP、≥ 80 换通道</div>
      </a-form-item>

      <a-form-item label="参数">
        <a-textarea v-model:value="editForm.params" :rows="6" placeholder="留空表示用代码里的默认值" />
        <div class="cell-sub">
          保存时会用<b>这条规则自己的</b>校验过一遍（分级要阈值递增、矩阵不许配成拒绝……）。
          形状不对会直接报错，不会存进去之后让规则悄悄失效。
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
  <!---------- 调整 end ----------->
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { memberRiskApi } from '/@/api/business/member/member-risk-api';
  import {
    RISK_DECISION_ENUM,
    RISK_MODE_ENUM,
    RISK_MODE_OPTIONS,
    RISK_PHASE_ENUM,
    RISK_RULE_TYPE_ENUM,
    riskMetaOf,
  } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const columns = ref([
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
  const originalMode = ref(undefined);
  const editForm = reactive({
    id: undefined,
    ruleCode: '',
    ruleName: '',
    ruleType: undefined,
    description: '',
    mode: undefined,
    score: 0,
    params: '',
  });

  function openEdit(record) {
    Object.assign(editForm, {
      id: record.id,
      ruleCode: record.ruleCode,
      ruleName: record.ruleName,
      ruleType: record.ruleType,
      description: record.description,
      mode: record.mode,
      score: record.score ?? 0,
      params: record.params || '',
    });
    originalMode.value = record.mode;
    editOpen.value = true;
  }

  async function doSave() {
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
</style>
