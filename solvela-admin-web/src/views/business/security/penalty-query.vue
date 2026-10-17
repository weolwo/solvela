<!--
  * 安全中心 - 处罚查询（网关方案 §6.2）
  *
  * 【精确查，不扫库】处罚以 Redis 为准，库里只有留痕。输一个 IP / 设备号 / 会员号，服务端把它会落到的
  * 每一个处罚键都 GET 一遍：全局处罚（蜜罐 / 关键字 / 蜜罐账号，挡所有接口）、每个场景的限流处罚、设备封禁。
  *
  * 【⚠️ 匿名场景的账号查不了】登录、注册、发码时的「账号」是请求体里的邮箱 / 手机号，键里是网关用自己的密钥
  * 算的摘要，后台算不出来。被罚的人换个网络就能继续，而且最长只封 30 分钟。登录后的账号维度按会员号查。
  *
  * 【解除】只删处罚键；设备封禁不在这里解（真相源是设备表，去设备管理里解封）。
  * 网关各实例的进程内缓存最多再挡几秒。
  *
  * @Date 2026-10-17
-->
<template>
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="IP" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.ip" placeholder="IPv4 / IPv6" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="设备号" class="solvela-query-form-item">
        <a-input style="width: 280px" v-model:value="queryForm.deviceId" placeholder="32 位设备号" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.memberId" placeholder="登录后场景的账号维度" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <QueryActions @search="onSearch" @reset="resetQuery" />
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" style="margin-top: 12px">
    <a-table size="small" :dataSource="rows" :columns="columns" :pagination="false" :loading="loading" rowKey="key" bordered>
      <template #emptyText>
        <span>{{ searched ? '此刻没有在罚的东西' : '输一个 IP / 设备号 / 会员号查询' }}</span>
      </template>
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'kind'">
          <a-tag :color="metaOf(SECURITY_PENALTY_KIND_ENUM, text).color">{{ metaOf(SECURITY_PENALTY_KIND_ENUM, text).desc }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'target'">
          <span v-if="text">{{ text }}</span>
          <span v-else class="cell-sub">所有接口</span>
        </template>
        <template v-else-if="column.dataIndex === 'value'">
          <span class="cell-mono">{{ record.dimension }} · {{ text }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'untilEpochSec'">
          <span v-if="text">{{ formatTime(text) }}</span>
          <span v-else class="cell-sub">没有到期时间</span>
        </template>
        <template v-else-if="column.dataIndex === 'level'">
          <span v-if="text">第 {{ text }} 档</span>
          <span v-else class="cell-sub">—</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <span v-if="record.kind === SECURITY_PENALTY_KIND_ENUM.BANNED.value" class="cell-sub">去设备管理解封</span>
          <a-popconfirm v-else title="解除这条处罚？" @confirm="doRelease(record)">
            <a v-privilege="'security:penalty'">解除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup>
  import { reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import dayjs from 'dayjs';
  import { securityApi } from '/@/api/business/security/security-api';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import { SECURITY_PENALTY_KIND_ENUM } from '/@/constants/business/security/security-const';
  import { riskMetaOf } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const columns = [
    { title: '种类', dataIndex: 'kind', width: 170 },
    { title: '场景 / 接口', dataIndex: 'target', width: 220 },
    { title: '维度 · 值', dataIndex: 'value', width: 300 },
    { title: '到期', dataIndex: 'untilEpochSec', width: 170 },
    { title: '档位', dataIndex: 'level', width: 80 },
    { title: '操作', dataIndex: 'action', width: 120 },
  ];

  const queryFormDefault = () => ({ ip: '', deviceId: '', memberId: '' });
  const queryForm = reactive(queryFormDefault());
  const loading = ref(false);
  const rows = ref([]);
  const searched = ref(false);

  function metaOf(enumObj, value) {
    return riskMetaOf(enumObj, value, { desc: value, color: 'default' });
  }

  function formatTime(epochSec) {
    return dayjs(epochSec * 1000).format('YYYY-MM-DD HH:mm:ss');
  }

  async function onSearch() {
    loading.value = true;
    try {
      rows.value = await securityApi.queryPenalty({
        ip: queryForm.ip || undefined,
        deviceId: queryForm.deviceId || undefined,
        memberId: queryForm.memberId || undefined,
      });
      searched.value = true;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  function resetQuery() {
    Object.assign(queryForm, queryFormDefault());
    rows.value = [];
    searched.value = false;
  }

  async function doRelease(record) {
    try {
      message.success(await securityApi.releasePenalty({ key: record.key }));
      await onSearch();
    } catch (e) {
      solvelaSentry.captureError(e);
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
  }
</style>
