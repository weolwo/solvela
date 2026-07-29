<!--
  * 活动配置
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 19:31:49
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="活动编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="活动名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.activityName" placeholder="活动名称" />
      </a-form-item>
      <a-form-item label="创建时间" class="smart-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon>
            <SearchOutlined />
          </template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="smart-margin-left10">
          <template #icon>
            <ReloadOutlined />
          </template>
          重置
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <!---------- 表格操作行 begin ----------->
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block">
        <!-- 主入口：建活动的同时把玩法一起配了，避免建完还要自己找工作台 -->
        <a-button @click="goWizard" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          创建活动向导
        </a-button>
        <a-button @click="showForm" size="small">新建（仅活动）</a-button>
        <a-button @click="confirmBatchDelete" type="primary" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <DeleteOutlined />
          </template>
          批量删除
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>
    <!---------- 表格操作行 end ----------->

    <!---------- 表格 begin ----------->
    <a-table
      size="small"
      :scroll="{ y: 800 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'activityType'">
          <a-tag :color="activityTypeMeta(record.activityType).color" class="m-0">
            {{ activityTypeMeta(record.activityType).icon }} {{ activityTypeMeta(record.activityType).desc }}
          </a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-badge :status="activityStatusMeta(record.status).badge" :text="activityStatusMeta(record.status).desc" />
        </template>

        <!--
          配置完备度：这一列的意义在于把两种情况分开 ——
          「该配而没配」（向导中途退出的空壳活动）与「本来就不用配」（基础活动）。
          合成一个状态时，向导第二步该显示什么、这里该标什么，都无从判断。
        -->
        <template v-else-if="column.dataIndex === 'configured'">
          <span v-if="!hasGameplay(record.activityType)" class="text-slate-400 text-xs">基础活动，无需配置玩法</span>
          <a-tag v-else-if="configuredMap[record.activityCode] === true" color="success" class="m-0">已配置</a-tag>
          <a-tag v-else-if="configuredMap[record.activityCode] === false" color="warning" class="m-0">未配置玩法</a-tag>
          <span v-else class="text-slate-300 text-xs">—</span>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button v-if="hasGameplay(record.activityType) && configuredMap[record.activityCode] === false" type="link" @click="resumeWizard(record)">
              继续配置
            </a-button>
            <a-button v-if="!hasGameplay(record.activityType)" type="link" @click="openUpgrade(record)">升级玩法</a-button>
            <a-button @click="showForm(record)" type="link">编辑</a-button>
            <a-button @click="onDelete(record)" danger type="link">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>
    <!---------- 表格 end ----------->

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

    <ActivityConfigForm ref="formRef" @reloadList="queryData" />

    <!-- 升级玩法：只有基础活动能升，且服务端会二次校验下游玩法表为空 -->
    <a-modal v-model:open="upgradeVisible" title="升级玩法类型" width="560px" ok-text="升级并配置" cancel-text="取消" :confirm-loading="upgrading" @ok="doUpgrade">
      <div class="py-2">
        <div class="mb-4 px-3 py-2 bg-slate-50 rounded border border-slate-200 text-sm">
          <span class="font-bold">{{ upgradeTarget.activityName }}</span>
          <span class="font-mono text-xs text-slate-500 ml-2">{{ upgradeTarget.activityCode }}</span>
        </div>
        <div class="text-sm mb-3">选择要升级到的玩法类型：</div>
        <div class="grid grid-cols-3 gap-3">
          <div
            v-for="t in playableTypes"
            :key="t.value"
            class="upgrade-card p-3 flex flex-col items-center text-center"
            :class="{ active: upgradeType === t.value }"
            @click="upgradeType = t.value"
          >
            <span class="text-2xl mb-1">{{ t.icon }}</span>
            <span class="font-bold text-xs">{{ t.desc }}</span>
          </div>
        </div>
        <div class="mt-4 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2">
          仅<b>基础活动</b>可升级。玩法类活动之间不可互转、也不可降级回基础活动 —— 那会遗弃已配好的玩法配置。
        </div>
      </div>
    </a-modal>
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { useRouter } from 'vue-router';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import ActivityConfigForm from './activity-config-form.vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    PLAYABLE_TYPE_LIST,
    activityStatusMeta,
    activityTypeMeta,
    hasGameplay,
  } from '/@/constants/business/activity/activity-config/activity-config-const';

  // ⚠️ 与 t_menu 里登记的 path 保持一致（v3.42.0.sql），写错不会报错、只会白屏
  const ACTIVITY_WIZARD_PATH = '/activity/activity-wizard';

  const router = useRouter();
  const playableTypes = PLAYABLE_TYPE_LIST;

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: '活动编码',
      dataIndex: 'activityCode',
      ellipsis: true,
    },
    {
      title: '活动名称',
      dataIndex: 'activityName',
      ellipsis: true,
    },
    {
      title: '活动类型',
      dataIndex: 'activityType',
      width: 130,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
    },
    {
      title: '配置完备度',
      dataIndex: 'configured',
      width: 160,
    },
    {
      title: '活动开始时间',
      dataIndex: 'startTime',
      ellipsis: true,
    },
    {
      title: '活动结束时间',
      dataIndex: 'endTime',
      ellipsis: true,
    },
    {
      title: '规则脚本id',
      dataIndex: 'scriptId',
      ellipsis: true,
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      ellipsis: true,
    },
    {
      title: '更新人',
      dataIndex: 'updateBy',
      ellipsis: true,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      ellipsis: true,
    },
    {
      title: '操作',
      dataIndex: 'action',
      fixed: 'right',
      width: 220,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    tenantId: undefined, //租户id
    activityCode: undefined, //活动编码
    activityName: undefined, //活动名称
    createTime: [], //创建时间
    createTimeBegin: undefined, //创建时间 开始
    createTimeEnd: undefined, //创建时间 结束
    pageNum: 1,
    pageSize: 10,
  };
  // 查询表单form
  const queryForm = reactive({ ...queryFormState });
  // 表格加载loading
  const tableLoading = ref(false);
  // 表格数据
  const tableData = ref([]);
  // 总数
  const total = ref(0);

  // 重置查询条件
  function resetQuery() {
    let pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    queryData();
  }

  // 搜索
  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  // 各活动「玩法是否已配置完备」：{ [activityCode]: boolean }
  const configuredMap = ref({});

  // 查询数据
  async function queryData() {
    tableLoading.value = true;
    try {
      let queryResult = await activityConfigApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
      await loadConfiguredStatus();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  /**
   * 批量取完备度：一次请求算完整页，不要每行发一个。
   * 失败时只清空标记（那一列显示「—」），不打断列表本身 —— 完备度是辅助信息，
   * 不该因为它挂了就让运营连活动列表都看不到。
   */
  async function loadConfiguredStatus() {
    const codes = (tableData.value || []).map((r) => r.activityCode).filter(Boolean);
    if (!codes.length) {
      configuredMap.value = {};
      return;
    }
    try {
      const res = await activityConfigApi.configuredStatus(codes);
      configuredMap.value = res.data || {};
    } catch (e) {
      configuredMap.value = {};
      smartSentry.captureError(e);
    }
  }

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  onMounted(queryData);

  // ---------------------------- 添加/修改 ----------------------------
  const formRef = ref();

  function showForm(data) {
    formRef.value.show(data);
  }

  // ---------------------------- 向导入口 ----------------------------

  function goWizard() {
    router.push(ACTIVITY_WIZARD_PATH);
  }

  /**
   * 继续配置「空壳活动」：活动已在库里，直接把向导定位到第二步，别让运营重建一个。
   * 活动名与类型一并带过去，第二步的摘要条要用，省一次详情请求。
   */
  function resumeWizard(record) {
    router.push({
      path: ACTIVITY_WIZARD_PATH,
      query: {
        activityCode: record.activityCode,
        activityType: record.activityType,
        activityName: record.activityName,
        step: '2',
      },
    });
  }

  // ---------------------------- 升级玩法 ----------------------------

  const upgradeVisible = ref(false);
  const upgrading = ref(false);
  const upgradeTarget = ref({});
  const upgradeType = ref('DRAW');

  function openUpgrade(record) {
    upgradeTarget.value = record;
    upgradeType.value = 'DRAW';
    upgradeVisible.value = true;
  }

  async function doUpgrade() {
    upgrading.value = true;
    try {
      await activityConfigApi.upgradeType({ id: upgradeTarget.value.id, targetType: upgradeType.value });
      message.success('已升级，请继续配置玩法');
      upgradeVisible.value = false;
      // 升级后直接送去配玩法：升完就停在列表，运营还得自己再点一次
      resumeWizard({ ...upgradeTarget.value, activityType: upgradeType.value });
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      upgrading.value = false;
    }
  }

  // ---------------------------- 单个删除 ----------------------------

  /**
   * 删除前先问服务端能不能删，把「有 3 个奖池」这种理由在确认框里就摆出来，
   * 而不是等运营点完确认才被拒。服务端 delete 里同样会再拦一次（铁律 2：前端只是防呆）。
   */
  async function onDelete(data) {
    let check;
    try {
      const res = await activityConfigApi.checkDeletable(data.id);
      check = res.data || {};
    } catch (e) {
      smartSentry.captureError(e);
      return;
    }
    if (!check.deletable) {
      Modal.warning({ title: '无法删除', content: check.reason || '该活动存在下游引用' });
      return;
    }
    Modal.confirm({
      title: '提示',
      content: `确定要删除活动「${data.activityName}」吗？`,
      okText: '删除',
      okType: 'danger',
      onOk() {
        requestDelete(data);
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求删除
  async function requestDelete(data) {
    SmartLoading.show();
    try {
      await activityConfigApi.delete(data.id);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }

  // ---------------------------- 批量删除 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  // 批量删除
  function confirmBatchDelete() {
    Modal.confirm({
      title: '提示',
      content: '确定要批量删除这些数据吗?',
      okText: '删除',
      okType: 'danger',
      onOk() {
        requestBatchDelete();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  //请求批量删除
  async function requestBatchDelete() {
    try {
      SmartLoading.show();
      await activityConfigApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>

<style scoped>
  .upgrade-card {
    border: 2px solid #e4e7eb;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    background: #fff;
  }
  .upgrade-card:hover {
    border-color: #91caff;
  }
  .upgrade-card.active {
    border-color: #1677ff;
    background: #e6f2ff;
  }
</style>
