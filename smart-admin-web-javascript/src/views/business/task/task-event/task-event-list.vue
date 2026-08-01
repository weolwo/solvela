<!--
  * 任务事件注册表
  *
  * 事件是**开放集合**，此前写死在前端常量里，加一个事件要改前端 + 改 DDL 注释 + 可能改后端枚举。
  * 现在加事件 = 在这里加一行 + 上游埋点，代码一行都不用动。
  *
  * @Author:    alaric
  * @Date:      2026-08-01
-->
<template>
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="事件编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.eventCode" placeholder="如 ORDER_PAID" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="展示名" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.eventName" placeholder="如 订单支付" allow-clear @pressEnter="onSearch" />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 140px" v-model:value="queryForm.status" :options="STATUS_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="smart-margin-left10">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-alert type="info" show-icon class="mb-3">
      <template #message>
        事件是任务的<b>输入</b>：上游埋点调 <code>POST /taskEvent/report</code> 上报，任务引擎据此推进进度。
        新增一个事件只需在这里加一行 + 上游埋点，<b>前端与后端都不用改代码</b>。
      </template>
    </a-alert>

    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <a-button @click="showForm()" type="primary" size="small" v-privilege="'taskEventDef:add'">
          <template #icon><PlusOutlined /></template>
          注册事件
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.TASK_EVENT" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1400 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'eventCode'">
          <a-typography-text copyable strong>{{ text }}</a-typography-text>
        </template>

        <template v-if="column.dataIndex === 'bizIdRequired'">
          <!-- 这一列是给对接方看的：为 1 时上游必须传业务单号，否则服务端只能按事件日兜底，
               对订单类事件那意味着「一天只算一笔」 -->
          <a-tag v-if="text === 1" color="orange">必须带单号</a-tag>
          <a-tag v-else color="default">按天兜底</a-tag>
        </template>

        <template v-if="column.dataIndex === 'metricSource'">
          <span v-if="!text || text === 'NONE'" class="text-gray-400!">计次（不取金额）</span>
          <a-tag v-else color="blue">payload.{{ text }}</a-tag>
        </template>

        <template v-if="column.dataIndex === 'isHighFrequency'">
          <a-tag v-if="text === 1" color="purple">高频</a-tag>
          <span v-else class="text-gray-400!">—</span>
        </template>

        <template v-if="column.dataIndex === 'discardLogFlag'">
          <a-tooltip>
            <template #title>
              开启时，未推进进度的事件会连同原因一起写入流水，是客诉自证的依据；
              高频事件建议关闭，否则不匹配的事件会把流水表写爆。
            </template>
            <a-tag :color="text === 1 ? 'green' : 'default'">{{ text === 1 ? '记录' : '不记录' }}</a-tag>
          </a-tooltip>
        </template>

        <template v-if="column.dataIndex === 'payloadSchema'">
          <a-button v-if="text" type="link" size="small" @click="showSchema(record)">查看字段</a-button>
          <span v-else class="text-gray-400!">—</span>
        </template>

        <template v-if="column.dataIndex === 'status'">
          <a-switch
            :checked="text === 1"
            :loading="switchingId === record.id"
            checked-children="启用"
            un-checked-children="停用"
            @change="(checked) => onToggleStatus(record, checked)"
          />
        </template>

        <template v-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="showForm(record)" type="link" v-privilege="'taskEventDef:update'">编辑</a-button>
            <a-button @click="onDelete(record)" danger type="link" v-privilege="'taskEventDef:delete'">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
      <a-pagination
        showSizeChanger
        showQuickJumper
        show-less-items
        :pageSizeOptions="PAGE_SIZE_OPTIONS"
        :defaultPageSize="queryForm.pageSize"
        v-model:current="queryForm.pageNum"
        v-model:pageSize="queryForm.pageSize"
        :total="total"
        @change="queryData"
        @showSizeChange="queryData"
        :show-total="(total) => `共${total}条`"
      />
    </div>
  </a-card>

  <!---------- 新增/编辑弹窗 ----------->
  <a-modal :open="formVisible" :title="formTitle" :width="680" @cancel="closeForm" @ok="onSubmit" :confirmLoading="submitting">
    <a-form ref="formRef" :model="form" :rules="FORM_RULES" :label-col="{ span: 6 }" class="mt-4">
      <a-form-item label="事件编码" name="eventCode">
        <a-input v-model:value="form.eventCode" :disabled="isEdit" placeholder="大写字母/数字/下划线，如 ORDER_PAID" />
        <div v-if="isEdit" class="text-xs text-gray-400! mt-1">
          编码创建后不可改：它是任务配置的引用键，也是上游埋点里硬编码的字符串，改一次所有已配任务当场收不到事件
        </div>
      </a-form-item>
      <a-form-item label="展示名" name="eventName">
        <a-input v-model:value="form.eventName" placeholder="如 订单支付成功" />
      </a-form-item>
      <a-form-item label="必须带业务单号" name="bizIdRequired">
        <a-switch v-model:checked="form.bizIdRequired" :checkedValue="1" :unCheckedValue="0" />
        <div class="text-xs text-gray-400! mt-1">
          有天然单号的事件（订单号）请开启。关闭时服务端按事件自然日兜底幂等，即<b>一天只算一次</b>
        </div>
      </a-form-item>
      <a-form-item label="计量来源" name="metricSource">
        <a-input v-model:value="form.metricSource" placeholder="NONE 表示计次；计额型填 payload 里的字段名，如 payAmount" />
      </a-form-item>
      <a-form-item label="高频事件" name="isHighFrequency">
        <a-switch v-model:checked="form.isHighFrequency" :checkedValue="1" :unCheckedValue="0" />
        <div class="text-xs text-gray-400! mt-1">目前仅作标记，路由优化尚未实现</div>
      </a-form-item>
      <a-form-item label="记录丢弃流水" name="discardLogFlag">
        <a-switch v-model:checked="form.discardLogFlag" :checkedValue="1" :unCheckedValue="0" />
        <div class="text-xs text-gray-400! mt-1">高频事件建议关闭，否则不匹配的事件会把流水表写爆</div>
      </a-form-item>
      <a-form-item label="字段说明 payload_schema" name="payloadSchema">
        <a-textarea v-model:value="form.payloadSchema" :rows="4" placeholder='{"fields":[{"key":"orderId","type":"string","desc":"订单号"}]}' />
      </a-form-item>
      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" placeholder="上游由谁埋点、什么时机触发" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-switch v-model:checked="form.status" :checkedValue="1" :unCheckedValue="0" checked-children="启用" un-checked-children="停用" />
      </a-form-item>
    </a-form>
  </a-modal>

  <!---------- 字段说明查看 ----------->
  <a-modal :open="schemaVisible" title="事件携带字段" :width="600" :footer="null" @cancel="schemaVisible = false">
    <JsonViewer :value="schemaValue" copyable boxed sort />
  </a-modal>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue';
  import { JsonViewer } from 'vue3-json-viewer';
  import 'vue3-json-viewer/dist/index.css';
  import { taskEventApi } from '/@/api/business/task/task-event/task-event-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';

  const STATUS_OPTIONS = [
    { value: 1, label: '启用' },
    { value: 0, label: '停用' },
  ];

  const columns = ref([
    { title: '事件编码', dataIndex: 'eventCode', width: 170, fixed: 'left' },
    { title: '展示名', dataIndex: 'eventName', width: 140, ellipsis: true },
    { title: '幂等要求', dataIndex: 'bizIdRequired', width: 110, align: 'center' },
    { title: '计量来源', dataIndex: 'metricSource', width: 150 },
    { title: '高频', dataIndex: 'isHighFrequency', width: 80, align: 'center' },
    { title: '丢弃流水', dataIndex: 'discardLogFlag', width: 100, align: 'center' },
    { title: '字段说明', dataIndex: 'payloadSchema', width: 100, align: 'center' },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 100, align: 'center' },
    { title: '创建时间', dataIndex: 'createTime', width: 170, showFlag: false },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, showFlag: false },
    { title: '操作', dataIndex: 'action', fixed: 'right', width: 130 },
  ]);

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    eventCode: undefined,
    eventName: undefined,
    status: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const tableData = ref([]);
  const total = ref(0);
  const tableLoading = ref(false);

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    Object.assign(queryForm, queryFormState);
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await taskEventApi.queryPage(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 字段说明 ----------------------------

  const schemaVisible = ref(false);
  const schemaValue = ref({});

  function showSchema(record) {
    try {
      schemaValue.value = JSON.parse(record.payloadSchema);
    } catch (e) {
      // 脏数据单点降级：原样展示字符串，别让一行坏 JSON 把弹窗打不开
      schemaValue.value = { 解析失败: record.payloadSchema };
    }
    schemaVisible.value = true;
  }

  // ---------------------------- 状态开关 ----------------------------

  const switchingId = ref(null);

  async function onToggleStatus(record, checked) {
    switchingId.value = record.id;
    try {
      // 只提交状态变化，其余字段原样回传（update 是整体更新）
      await taskEventApi.update({ ...record, status: checked ? 1 : 0 });
      message.success(checked ? '已启用' : '已停用');
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      switchingId.value = null;
    }
  }

  // ---------------------------- 新增/编辑 ----------------------------

  const formVisible = ref(false);
  const submitting = ref(false);
  const formRef = ref();
  const formDefault = {
    id: undefined,
    eventCode: '',
    eventName: '',
    metricSource: 'NONE',
    payloadSchema: '',
    bizIdRequired: 0,
    isHighFrequency: 0,
    discardLogFlag: 1,
    remark: '',
    status: 1,
  };
  const form = reactive({ ...formDefault });
  const isEdit = computed(() => !!form.id);
  const formTitle = computed(() => (isEdit.value ? '编辑事件' : '注册事件'));

  const FORM_RULES = {
    eventCode: [
      { required: true, message: '请输入事件编码' },
      {
        pattern: /^[A-Z0-9_]{2,64}$/,
        message: '只能是大写字母、数字、下划线，长度 2~64',
      },
    ],
    eventName: [{ required: true, message: '请输入展示名' }],
  };

  function showForm(record) {
    Object.assign(form, formDefault, record || {});
    formVisible.value = true;
  }

  function closeForm() {
    formVisible.value = false;
    formRef.value?.clearValidate();
  }

  async function onSubmit() {
    try {
      await formRef.value.validateFields();
    } catch (e) {
      message.error('请完整填写表单');
      return;
    }
    submitting.value = true;
    try {
      const res = isEdit.value ? await taskEventApi.update({ ...form }) : await taskEventApi.add({ ...form });
      if (!res.ok) {
        // 服务端会给出人话原因（编码重复等），如实透出，不要吞掉
        message.error(res.msg);
        return;
      }
      message.success(isEdit.value ? '已保存' : '已注册');
      closeForm();
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      submitting.value = false;
    }
  }

  // ---------------------------- 删除 ----------------------------

  function onDelete(record) {
    Modal.confirm({
      title: '提示',
      content: `确定要删除事件「${record.eventName}（${record.eventCode}）」吗？仍被任务配置引用时会被拒绝。`,
      okText: '删除',
      okType: 'danger',
      onOk: async () => {
        try {
          const res = await taskEventApi.delete(record.id);
          if (!res.ok) {
            // 删除守卫会告诉运营被几个任务引用、以及应该改用「停用」
            message.error(res.msg);
            return;
          }
          message.success('删除成功');
          await queryData();
        } catch (e) {
          smartSentry.captureError(e);
        }
      },
    });
  }
</script>
