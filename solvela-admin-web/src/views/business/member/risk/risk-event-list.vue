<!--
  * 风控 - 决策流水
  *
  * 【2026-10-16 起这张表里混着两条链路】注册也接进了同一个引擎。所以第一个筛选器是「场景」，
  * 列表第一列也是它 —— 「这个 IP 在干什么」的答案，取决于那是 10 次注册还是 10 次登录。
  *
  * 【它回答客服最常被问的那句话】「我为什么要验证码 / 为什么登不上」——
  * 查到那一次尝试，点开看命中了哪几条规则、依据是什么。
  *
  * 【这个页面之前不存在】登录日志的 member_id 是 NOT NULL，所以**查无此人的尝试一行都不留**，
  * 而那恰恰是撞库最常见的样子。流水表允许没有会员，按身份摘要聚合。
  *
  * 【它也是「陪跑 → 生效」唯一的依据】勾上「只看结论不一致」，看到的就是
  * 「把这些规则切到生效会多打扰的那些人」。
  *
  * 【🔴 手机号 / 邮箱输明文，列表里显示摘要】客服说得出口的是手机号，
  * 所以筛选框收明文、服务端算成摘要再查。而列表不回明文 ——
  * 这个页面会被截图、会进浏览器缓存，PII 不该从这里漏出去。
  *
  * @Date 2026-10-16
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.memberId" placeholder="精确匹配" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="手机号/邮箱" class="solvela-query-form-item">
        <a-input
          style="width: 200px"
          v-model:value="queryForm.identity"
          placeholder="输明文，服务端算摘要"
          allow-clear
          @press-enter="onSearch"
        />
      </a-form-item>
      <a-form-item label="IP" class="solvela-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.clientIp" placeholder="精确匹配" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="设备号" class="solvela-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.deviceId" placeholder="精确匹配" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="场景" class="solvela-query-form-item">
        <a-select
          style="width: 120px"
          v-model:value="queryForm.eventType"
          :options="RISK_EVENT_TYPE_OPTIONS"
          placeholder="全部"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="结论" class="solvela-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.decision" :options="RISK_DECISION_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="结局" class="solvela-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.outcome" :options="RISK_OUTCOME_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="timeRange" show-time :presets="defaultTimeRanges" style="width: 330px" @change="onChangeTime" />
      </a-form-item>
      <a-form-item class="solvela-query-form-item">
        <a-checkbox v-model:checked="queryForm.shadowDiffOnly">只看结论不一致</a-checkbox>
      </a-form-item>
      <QueryActions @search="onSearch" @reset="resetQuery" />
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-alert
    v-if="queryForm.shadowDiffOnly"
    type="warning"
    show-icon
    style="margin-bottom: 12px"
    message="这些是「生效结论」与「陪跑结论」不一致的尝试"
    description="也就是说：把还在陪跑的那些规则切到生效，这批人会被多打扰一次。切档位之前就看这个数。"
  />

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
        <template v-if="column.dataIndex === 'eventType'">
          <a-tag :color="eventTypeMeta(text).color">{{ eventTypeMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'who'">
          <div v-if="record.memberId">{{ record.memberId }}</div>
          <!--
            没有会员号正是这张表存在的理由之一 —— 标出来，而不是显示一个空格
          -->
          <a-tag v-else color="volcano">查无此人</a-tag>
          <div class="cell-sub" :title="record.identityHashHex">{{ shortHash(record.identityHashHex) }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'decision'">
          <a-tag :color="decisionMeta(text).color">{{ decisionMeta(text).desc }}</a-tag>
          <!--
            两个结论不一致时把陪跑那个也显示出来 —— 它是「切生效会怎样」的直接答案
          -->
          <div v-if="record.shadowDecision !== text" class="cell-sub">
            陪跑：{{ decisionMeta(record.shadowDecision).desc }}
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'outcome'">
          <a-tag :color="outcomeMeta(text).color">{{ outcomeMeta(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'coordinate'">
          <!--
            矩阵的三个维度。它们是「为什么落在这一格」的全部答案，
            所以直接显示在列表里 —— 不用点进详情才看得到
          -->
          <a-tag v-if="record.deviceTrust !== null && record.deviceTrust !== undefined" :color="trustMeta(record.deviceTrust).color">
            {{ trustMeta(record.deviceTrust).desc }}
          </a-tag>
          <a-tag v-if="record.ipFamiliar !== null && record.ipFamiliar !== undefined" :color="familiarMeta(record.ipFamiliar).color">
            {{ familiarMeta(record.ipFamiliar).desc }}
          </a-tag>
          <a-tag v-if="record.dormant === 1" color="orange">沉睡</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'score'">
          <span>{{ (record.preScore ?? 0) + (record.postScore ?? 0) }}</span>
          <div v-if="record.shadowScore !== (record.preScore ?? 0) + (record.postScore ?? 0)" class="cell-sub">
            陪跑：{{ record.shadowScore }}
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'clientIp'">
          <div>{{ record.clientIp || '—' }}</div>
          <div class="cell-sub">{{ record.ipRegion }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a @click="openDetail(record)">命中明细</a>
        </template>
      </template>
    </a-table>

    <TablePagination v-model:pageNum="queryForm.pageNum" v-model:pageSize="queryForm.pageSize" :total="total" @change="queryData" />
  </a-card>

  <!---------- 命中明细 begin ----------->
  <a-drawer v-model:open="detailOpen" title="这一次尝试，风控判了什么" width="720">
    <a-spin :spinning="detailLoading">
      <a-descriptions v-if="detail.event" size="small" bordered :column="2" style="margin-bottom: 16px">
        <a-descriptions-item label="结论">
          <a-tag :color="decisionMeta(detail.event.decision).color">{{ decisionMeta(detail.event.decision).desc }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="结局">
          <a-tag :color="outcomeMeta(detail.event.outcome).color">{{ outcomeMeta(detail.event.outcome).desc }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="登录方式">
          {{ loginTypeMeta(detail.event.loginType).desc }}
        </a-descriptions-item>
        <a-descriptions-item label="核身通道">
          {{ detail.event.verifyChannel ? channelMeta(detail.event.verifyChannel).desc : '—' }}
        </a-descriptions-item>
        <a-descriptions-item label="得分（前置+后置）">
          {{ (detail.event.preScore ?? 0) + (detail.event.postScore ?? 0) }}
          <span class="cell-sub">（陪跑 {{ detail.event.shadowScore }}）</span>
        </a-descriptions-item>
        <a-descriptions-item label="风控耗时">{{ detail.event.costMs ?? '—' }} ms</a-descriptions-item>
        <a-descriptions-item label="设备号" :span="2">{{ detail.event.deviceId || '—' }}</a-descriptions-item>
        <a-descriptions-item label="链路ID" :span="2">
          <!-- 用户截图报障时，凭它一次定位到日志 -->
          {{ detail.event.traceId || '—' }}
        </a-descriptions-item>
      </a-descriptions>

      <a-alert
        v-if="!detail.hits || detail.hits.length === 0"
        type="success"
        show-icon
        message="一条规则都没命中 —— 这次尝试一路顺畅"
      />
      <a-table
        v-else
        size="small"
        :dataSource="detail.hits"
        :columns="hitColumns"
        :pagination="false"
        rowKey="id"
        bordered
      >
        <template #bodyCell="{ column, text, record }">
          <template v-if="column.dataIndex === 'ruleCode'">
            <div>{{ record.ruleName || record.ruleCode }}</div>
            <div class="cell-sub">{{ record.ruleCode }}</div>
          </template>
          <template v-else-if="column.dataIndex === 'mode'">
            <a-tag :color="modeMeta(text).color">{{ modeMeta(text).desc }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'effect'">
            <a-tag v-if="record.decision" :color="decisionMeta(record.decision).color">
              {{ decisionMeta(record.decision).desc }}
            </a-tag>
            <span v-else-if="record.score">+{{ record.score }} 分</span>
            <span v-else class="cell-sub">—</span>
          </template>
          <template v-else-if="column.dataIndex === 'detail'">
            <!--
              🔴 error 的那几行要标出来：它的意思是「这条规则这次没判出来」（特征取不到），
              而不是「它判了、没命中」。不标的话这两件事在页面上长得一模一样，
              于是「某条规则其实一直没在工作」永远不会被发现
            -->
            <a-tag v-if="record.error === 1" color="red">取不到特征</a-tag>
            <span>{{ record.detail }}</span>
          </template>
        </template>
      </a-table>

      <a-alert
        v-if="hasShadowHit"
        type="info"
        show-icon
        style="margin-top: 12px"
        message="带「陪跑」标签的命中没有影响这次登录"
        description="它们只是被记下来了。把对应规则切到「生效」之后，同样的情况就会真的产生处置。"
      />
    </a-spin>
  </a-drawer>
  <!---------- 命中明细 end ----------->
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { memberRiskApi } from '/@/api/business/member/member-risk-api';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    RISK_DECISION_ENUM,
    RISK_DECISION_OPTIONS,
    RISK_DEVICE_TRUST_ENUM,
    RISK_EVENT_TYPE_ENUM,
    RISK_EVENT_TYPE_OPTIONS,
    RISK_IP_FAMILIAR_ENUM,
    RISK_LOGIN_TYPE_ENUM,
    RISK_MODE_ENUM,
    RISK_OUTCOME_ENUM,
    RISK_OUTCOME_OPTIONS,
    RISK_VERIFY_CHANNEL_ENUM,
    riskMetaOf,
  } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';

  const columns = ref([
    { title: '场景', dataIndex: 'eventType', width: 90 },
    { title: '会员 / 身份摘要', dataIndex: 'who', width: 160 },
    { title: '结论', dataIndex: 'decision', width: 120 },
    { title: '结局', dataIndex: 'outcome', width: 110 },
    { title: '矩阵坐标', dataIndex: 'coordinate', width: 220 },
    { title: '得分', dataIndex: 'score', width: 90 },
    { title: 'IP / 归属地', dataIndex: 'clientIp', width: 190 },
    { title: '时间', dataIndex: 'createTime', width: 170, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 90, fixed: 'right' },
  ]);

  const hitColumns = ref([
    { title: '规则', dataIndex: 'ruleCode', width: 190 },
    { title: '档位', dataIndex: 'mode', width: 80 },
    { title: '给出什么', dataIndex: 'effect', width: 110 },
    { title: '命中依据', dataIndex: 'detail' },
  ]);

  function decisionMeta(value) {
    return riskMetaOf(RISK_DECISION_ENUM, value, { desc: value ?? '—', color: 'default' });
  }

  function outcomeMeta(value) {
    return riskMetaOf(RISK_OUTCOME_ENUM, value, { desc: value ?? '—', color: 'default' });
  }

  function modeMeta(value) {
    return riskMetaOf(RISK_MODE_ENUM, value, { desc: value ?? '—', color: 'default' });
  }

  function trustMeta(value) {
    return riskMetaOf(RISK_DEVICE_TRUST_ENUM, value, { desc: value, color: 'default' });
  }

  function familiarMeta(value) {
    return riskMetaOf(RISK_IP_FAMILIAR_ENUM, value, { desc: value, color: 'default' });
  }

  function loginTypeMeta(value) {
    return riskMetaOf(RISK_LOGIN_TYPE_ENUM, value, { desc: '—' });
  }

  function channelMeta(value) {
    return riskMetaOf(RISK_VERIFY_CHANNEL_ENUM, value, { desc: '—' });
  }

  function shortHash(hash) {
    return hash ? `${hash.slice(0, 12)}…` : '—';
  }

  // ---------------------------- 查询 ----------------------------

  /**
   * 时间范围<b>默认当天</b>。
   *
   * 不给默认值的话服务端会兜最近 7 天，但页面上的时间框是空的 ——
   * 看的人不知道自己正在看多久的数据。这张表按月分区，而且是整个系统里增长最快的表之一。
   */
  const queryFormState = {
    memberId: undefined,
    identity: undefined,
    clientIp: undefined,
    deviceId: undefined,
    eventType: undefined,
    decision: undefined,
    outcome: undefined,
    shadowDiffOnly: false,
    startTime: undefined,
    endTime: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const timeRange = ref([]);
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  function eventTypeMeta(value) {
    return riskMetaOf(RISK_EVENT_TYPE_ENUM, value, { desc: value, color: 'default' });
  }

  function onChangeTime(dates, dateStrings) {
    queryForm.startTime = dateStrings[0] || undefined;
    queryForm.endTime = dateStrings[1] || undefined;
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    timeRange.value = [];
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await memberRiskApi.queryEventPage(queryForm);
      tableData.value = res.list;
      total.value = res.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 命中明细 ----------------------------

  const detailOpen = ref(false);
  const detailLoading = ref(false);
  const detail = reactive({ event: null, hits: [] });

  const hasShadowHit = computed(
    () => (detail.hits || []).some((h) => h.mode === RISK_MODE_ENUM.SHADOW.value),
  );

  async function openDetail(record) {
    detailOpen.value = true;
    detailLoading.value = true;
    detail.event = null;
    detail.hits = [];
    try {
      // 🔴 createTime 必须一起传：表按月分区，只按 id 查不裁剪分区
      const res = await memberRiskApi.eventDetail({ id: record.id, createTime: record.createTime });
      detail.event = res?.event ?? null;
      detail.hits = res?.hits ?? [];
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      detailLoading.value = false;
    }
  }
</script>

<style scoped>
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }
</style>
