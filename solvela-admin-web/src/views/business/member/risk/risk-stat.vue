<!--
  * 登录风控 - 看板
  *
  * 【它回答的是「阈值该往哪边调」】流水页回答「这个人为什么被拦」；
  * 而「要不要把某条规则切生效、全局滑块什么时候关掉」只能看聚合 ——
  * 打扰了多少人、拦下了多少、生效与陪跑差了多少。
  *
  * 【🔴 「结论不一致」是这块看板上最该先看的数】它就是「把陪跑规则切生效会多打扰多少人」。
  * 陪跑这件事全部的意义就在这个数上：规则照常跑、命中照常记，但不影响结论 ——
  * 攒下来的差额就是切过去的代价。
  *
  * 【🔴 第二个该看的是「取特征失败」】一条规则在列表上写着「生效中」，而它每次都取不到特征，
  * 实际什么也没拦。那种静默失效只有这一列看得见。
  *
  * 【刻意不画图】这里用的是表格和进度条，不引图表库：
  * 四组数里真正要「看趋势」的只有结论分布，而它一天一行、三十行以内一眼扫得完。
  * 为此引一个几百 KB 的图表库，换来的是一张更好看但信息量相同的图。
  *
  * @Date 2026-10-16
-->
<template>
  <!---------- 查询 begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="时间" class="solvela-query-form-item">
        <a-range-picker
          v-model:value="timeRange"
          show-time
          :presets="defaultTimeRanges"
          style="width: 330px"
          @change="onChangeTime"
        />
      </a-form-item>
      <QueryActions @search="queryData" @reset="resetQuery" />
    </a-row>
  </a-form>
  <!---------- 查询 end ----------->

  <a-spin :spinning="loading">
    <!---------- 概览 begin ----------->
    <a-row :gutter="12" style="margin-bottom: 12px">
      <a-col :span="6">
        <a-card size="small" :bordered="false" :hoverable="true">
          <a-statistic title="登录尝试" :value="stat.totalCount ?? 0" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small" :bordered="false" :hoverable="true">
          <a-statistic title="要了验证码（打扰）" :value="stat.otpFunnel?.challenged ?? 0" />
          <div class="cell-sub">其中验过：{{ stat.otpFunnel?.verified ?? 0 }}（{{ otpRate }}）</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small" :bordered="false" :hoverable="true">
          <!--
            🔴 这个数就是「切生效会多打扰多少人」。所以它要和「尝试总数」摆在一起看 ——
            单独一个绝对值说明不了多少
          -->
          <a-statistic title="结论不一致（切生效的代价）" :value="stat.shadowDiffCount ?? 0" />
          <div class="cell-sub">占尝试 {{ shadowDiffRate }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card size="small" :bordered="false" :hoverable="true">
          <a-statistic
            title="取特征失败的命中"
            :value="errorHitCount"
            :value-style="errorHitCount > 0 ? { color: '#cf1322' } : undefined"
          />
          <div class="cell-sub">不为 0 说明有规则写着「生效」却其实没在工作</div>
        </a-card>
      </a-col>
    </a-row>
    <!---------- 概览 end ----------->

    <a-row :gutter="12">
      <!---------- 每日结论分布 begin ----------->
      <a-col :span="12">
        <a-card size="small" :bordered="false" :hoverable="true" title="每日结论分布">
          <a-table
            size="small"
            :dataSource="decisionRows"
            :columns="decisionColumns"
            :pagination="false"
            rowKey="day"
            bordered
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex !== 'day'">
                <span v-if="record[column.dataIndex]">{{ record[column.dataIndex] }}</span>
                <span v-else class="cell-sub">—</span>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
      <!---------- 每日结论分布 end ----------->

      <!---------- 矩阵各格 begin ----------->
      <a-col :span="12">
        <a-card size="small" :bordered="false" :hoverable="true" title="处置矩阵各格命中">
          <template #extra>
            <span class="cell-sub">命中最多的那一格，配置最该被盯着</span>
          </template>
          <a-table
            size="small"
            :dataSource="stat.matrixCells ?? []"
            :columns="matrixColumns"
            :pagination="false"
            :rowKey="(r) => `${r.deviceTrust}-${r.ipFamiliar}-${r.dormant}`"
            bordered
          >
            <template #bodyCell="{ column, text, record }">
              <template v-if="column.dataIndex === 'deviceTrust'">
                <a-tag :color="metaOf(RISK_DEVICE_TRUST_ENUM, text).color">
                  {{ metaOf(RISK_DEVICE_TRUST_ENUM, text).desc }}
                </a-tag>
                <a-tag :color="metaOf(RISK_IP_FAMILIAR_ENUM, record.ipFamiliar).color">
                  {{ metaOf(RISK_IP_FAMILIAR_ENUM, record.ipFamiliar).desc }}
                </a-tag>
                <a-tag :color="record.dormant === 1 ? 'volcano' : 'green'">
                  {{ record.dormant === 1 ? '沉睡' : '活跃' }}
                </a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
      <!---------- 矩阵各格 end ----------->
    </a-row>

    <!---------- 规则命中 begin ----------->
    <a-card size="small" :bordered="false" :hoverable="true" title="按规则的命中" style="margin-top: 12px">
      <a-table
        size="small"
        :dataSource="stat.ruleHits ?? []"
        :columns="ruleColumns"
        :pagination="false"
        :rowKey="(r) => `${r.ruleCode}-${r.mode}`"
        bordered
      >
        <template #bodyCell="{ column, text, record }">
          <template v-if="column.dataIndex === 'mode'">
            <a-tag :color="metaOf(RISK_MODE_ENUM, text).color">
              {{ metaOf(RISK_MODE_ENUM, text).desc }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'errorCount'">
            <!-- 不为 0 要显眼：那条规则其实没在工作，而它在规则页上写着「生效中」 -->
            <span v-if="record.errorCount > 0" class="cell-error">{{ record.errorCount }}</span>
            <span v-else class="cell-sub">0</span>
          </template>
        </template>
      </a-table>
    </a-card>
    <!---------- 规则命中 end ----------->
  </a-spin>
</template>

<script setup>
  import { computed, onMounted, ref } from 'vue';
  import { memberRiskApi } from '/@/api/business/member/member-risk-api';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    RISK_DECISION_ENUM,
    RISK_DEVICE_TRUST_ENUM,
    RISK_IP_FAMILIAR_ENUM,
    RISK_MODE_ENUM,
    riskMetaOf,
  } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import QueryActions from '/@/components/framework/query-actions/index.vue';

  const loading = ref(false);
  const stat = ref({});
  const timeRange = ref([]);
  const queryForm = ref({ startTime: undefined, endTime: undefined });

  function metaOf(enumObj, value) {
    return riskMetaOf(enumObj, value, { desc: value, color: 'default' });
  }

  function onChangeTime(_, dateStrings) {
    queryForm.value.startTime = dateStrings?.[0] || undefined;
    queryForm.value.endTime = dateStrings?.[1] || undefined;
  }

  async function queryData() {
    loading.value = true;
    try {
      stat.value = await memberRiskApi.stat(queryForm.value);
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  function resetQuery() {
    timeRange.value = [];
    queryForm.value = { startTime: undefined, endTime: undefined };
    queryData();
  }

  onMounted(queryData);

  // ---------------------------- 概览 ----------------------------

  function percent(part, whole) {
    if (!whole) {
      return '—';
    }
    return `${((part / whole) * 100).toFixed(1)}%`;
  }

  /** 要了码的人后来验过的比例。低得异常说明在打扰的是真人，而他们过不去 */
  const otpRate = computed(() =>
    percent(stat.value.otpFunnel?.verified, stat.value.otpFunnel?.challenged),
  );

  const shadowDiffRate = computed(() =>
    percent(stat.value.shadowDiffCount, stat.value.totalCount),
  );

  const errorHitCount = computed(() =>
    (stat.value.ruleHits ?? []).reduce((sum, r) => sum + (r.errorCount ?? 0), 0),
  );

  // ---------------------------- 每日结论分布 ----------------------------

  /** 结论横着铺成列：一天一行，扫一眼就能看出哪天突然多了一片 OTP */
  const decisionColumns = computed(() => [
    { title: '日期', dataIndex: 'day', width: 110 },
    ...Object.values(RISK_DECISION_ENUM).map((item) => ({
      title: item.desc,
      dataIndex: `d${item.value}`,
      width: 80,
    })),
  ]);

  const decisionRows = computed(() => {
    const byDay = new Map();
    for (const row of stat.value.decisionDaily ?? []) {
      const day = String(row.day);
      if (!byDay.has(day)) {
        byDay.set(day, { day });
      }
      byDay.get(day)[`d${row.decision}`] = row.count;
    }
    return [...byDay.values()];
  });

  // ---------------------------- 表头 ----------------------------

  const matrixColumns = ref([
    { title: '设备 / 网络 / 活跃', dataIndex: 'deviceTrust', width: 260 },
    { title: '尝试数', dataIndex: 'count', width: 90 },
  ]);

  const ruleColumns = ref([
    { title: '规则', dataIndex: 'ruleCode', width: 200 },
    { title: '当时档位', dataIndex: 'mode', width: 100 },
    { title: '命中数', dataIndex: 'count', width: 100 },
    { title: '其中取特征失败', dataIndex: 'errorCount', width: 140 },
  ]);
</script>

<style scoped>
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .cell-error {
    color: #cf1322;
    font-weight: 600;
  }
</style>
