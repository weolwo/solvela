<!--
  * 意见反馈
  * 
  * @Author:    1024创新实验室：开云
  * @Date:      2022-07-21 21:55:12
  * @Wechat:    zhuda1024 
  * @Email:     lab1024@163.com 
  * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012 
-->
<template>
  <a-card size="small">
    <a-table rowKey="functionName" :dataSource="tableData" :columns="tableColumns" :loading="tableLoading" size="small" bordered>
      <template #bodyCell="{ text, record, index, column }">
        <template v-if="column.dataIndex === 'params'">
          <template>
            <div>
             <a-tag>text</a-tag>
            </div>
          </template>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { scriptengineAPI } from '/@/api/support/scriptengine-api.js';

  // ----------------------- 表格列 --------------------------------------
  const tableColumns = reactive([
    {
      title: '类名',
      dataIndex: 'handlerName',
      width: 80,
    },
    {
      title: '方法名',
      dataIndex: 'functionName',
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
      title: 'Java 类名',
      dataIndex: 'className',
      width: 100,
    },
    {
      title: 'Java 方法名',
      dataIndex: 'methodName',
      width: 150,
    },
    {
      title: '方法参数',
      dataIndex: 'params',
      width: 150,
    },
  ]);

  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  onMounted(() => {
    queryList();
  });

  async function queryList() {
    try {
      tableLoading.value = true;
      const result = await scriptengineAPI.queryScriptDoc();
      tableData.value = result.data;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }
</script>
