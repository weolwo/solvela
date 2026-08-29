<!--
  * 脚本引擎-函数文档
  *
  * 后端把所有 @ScriptFunction 暴露的函数按业务域吐过来，这里只做展示与检索。
  * 函数名前缀（member_ / mall_ / task_ ...）是后端按 Handler 声明的 domain 自动加的，
  * 脚本里必须写全限定名。
-->
<template>
  <a-card size="small">
    <a-space style="margin-bottom: 10px">
      <a-select
        v-model:value="queryForm.domain"
        style="width: 160px"
        placeholder="全部业务域"
        allow-clear
        :options="domainOptions"
        @change="filterData"
      />
      <a-input-search v-model:value="queryForm.keyword" style="width: 260px" placeholder="按函数名或描述搜索" allow-clear @change="filterData" />
      <span class="doc-total">共 {{ tableData.length }} 个函数</span>
    </a-space>

    <a-table rowKey="functionName" :dataSource="tableData" :columns="tableColumns" :loading="tableLoading" :pagination="false" size="small" bordered>
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'domainTitle'">
          <a-tag color="blue">{{ record.domainTitle }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'signature'">
          <a-typography-text code copyable>{{ record.signature }}</a-typography-text>
        </template>
        <template v-else-if="column.dataIndex === 'params'">
          <span v-if="!record.params || record.params.length === 0">-</span>
          <a-tag v-for="param in record.params" :key="param">{{ param }}</a-tag>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { scriptengineAPI } from '/@/api/support/scriptengine-api.js';

  // ----------------------- 表格列 --------------------------------------
  const tableColumns = reactive([
    {
      title: '业务域',
      dataIndex: 'domainTitle',
      width: 100,
    },
    {
      title: '脚本调用签名',
      dataIndex: 'signature',
      width: 280,
    },
    {
      title: '描述',
      dataIndex: 'description',
    },
    {
      title: '返回类型',
      dataIndex: 'returnType',
      width: 100,
    },
    {
      title: '参数',
      dataIndex: 'params',
      width: 220,
    },
    {
      title: 'Java 类',
      dataIndex: 'className',
      width: 160,
    },
    {
      title: 'Java 方法',
      dataIndex: 'methodName',
      width: 140,
    },
  ]);

  const tableLoading = ref(false);
  const tableData = ref([]);
  const allData = ref([]);
  const domainOptions = ref([]);

  const queryForm = reactive({
    domain: undefined,
    keyword: '',
  });

  onMounted(() => {
    queryList();
  });

  async function queryList() {
    try {
      tableLoading.value = true;
      const result = await scriptengineAPI.queryScriptDoc();
      allData.value = result || [];
      domainOptions.value = buildDomainOptions(allData.value);
      filterData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function buildDomainOptions(list) {
    const seen = new Map();
    list.forEach((item) => {
      if (!seen.has(item.domain)) {
        seen.set(item.domain, { value: item.domain, label: item.domainTitle });
      }
    });
    return [...seen.values()];
  }

  function filterData() {
    const keyword = queryForm.keyword.trim().toLowerCase();
    tableData.value = allData.value.filter((item) => {
      if (queryForm.domain && item.domain !== queryForm.domain) {
        return false;
      }
      if (!keyword) {
        return true;
      }
      return item.functionName.toLowerCase().includes(keyword) || (item.description || '').toLowerCase().includes(keyword);
    });
  }
</script>

<style scoped lang="less">
  .doc-total {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }
</style>
