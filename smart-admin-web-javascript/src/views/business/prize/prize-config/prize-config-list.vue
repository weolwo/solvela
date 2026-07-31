<!--
  * 奖品配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:20:44
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="活动编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.activityCode" placeholder="活动编码" />
      </a-form-item>
      <a-form-item label="奖品编码" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.prizeCode" placeholder="奖品编码" />
      </a-form-item>
      <a-form-item label="奖品级别" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.prizeLevel" placeholder="奖品级别" />
      </a-form-item>
      <a-form-item label="奖品名称" class="smart-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.prizeName" placeholder="奖品名称" />
      </a-form-item>
      <a-form-item label="审批模式" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.approveMode" :options="APPROVE_MODE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="PRIZE_STATUS_OPTIONS" placeholder="全部" allow-clear />
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
        <a-button @click="showForm" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          新建
        </a-button>
        <!-- 只提供批量禁用：奖品可能已被奖池坑位/彩票奖级引用，删掉会让那些引用断链；
             禁用则不再发放，历史流水不受影响 -->
        <a-button @click="confirmBatchDisable" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量禁用
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.PRIZE_CONFIG" :refresh="queryData" />
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
        <!-- 状态：开关就地切换 -->
        <template v-if="column.dataIndex === 'status'">
          <a-switch
            :checked="isPrizeEnabled(record.status)"
            :loading="statusLoadingId === record.id"
            checked-children="启用"
            un-checked-children="禁用"
            @change="(checked) => onToggleStatus(record, checked)"
          />
        </template>

        <!-- 审批模式：开关。开=人工审批（更保守的那一侧放在「开」，符合直觉） -->
        <template v-else-if="column.dataIndex === 'approveMode'">
          <a-switch
            :checked="record.approveMode === 1"
            :loading="approveLoadingId === record.id"
            checked-children="人工审批"
            un-checked-children="自动免审"
            @change="(checked) => onToggleApproveMode(record, checked)"
          />
        </template>

        <!-- 排序权重：行内编辑，点数字即改，失焦提交 -->
        <template v-else-if="column.dataIndex === 'sortWeight'">
          <a-input-number
            :value="sortWeightDraft[record.id] ?? record.sortWeight"
            :min="0"
            :precision="0"
            size="small"
            style="width: 90px"
            @change="(val) => (sortWeightDraft[record.id] = val)"
            @blur="() => commitSortWeight(record)"
            @keyup.enter="(e) => e.target.blur()"
          />
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="smart-table-operate">
            <a-button @click="showForm(record)" type="link">编辑</a-button>
            <!-- 不提供删除：奖品可能已被奖池坑位、彩票奖级、发奖流水引用，
                 删掉会让那些引用断链。需要停用就用状态开关。 -->
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

    <PrizeConfigForm ref="formRef" @reloadList="queryData" />
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { prizeConfigApi } from '/@/api/business/prize/prize-config/prize-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import PrizeConfigForm from './prize-config-form.vue';
  import {
    APPROVE_MODE_OPTIONS,
    PRIZE_STATUS_ENUM,
    PRIZE_STATUS_OPTIONS,
    isPrizeEnabled,
  } from '/@/constants/business/prize/prize-config/prize-config-const';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      showFlag: false, //默认隐藏：运维字段，运营不看
      ellipsis: true,
    },
    {
      title: '活动编码',
      dataIndex: 'activityCode',
      ellipsis: true,
    },
    {
      title: '优惠配置ID',
      dataIndex: 'promotionConfigId',
      showFlag: false, //默认隐藏：运维字段，运营不看
      ellipsis: true,
    },
    {
      title: '资产类型',
      dataIndex: 'prizeType',
      ellipsis: true,
    },
    {
      title: '奖品名称',
      dataIndex: 'prizeName',
      ellipsis: true,
    },
    {
      title: '奖品编码',
      dataIndex: 'prizeCode',
      ellipsis: true,
    },
    {
      title: '奖品级别',
      dataIndex: 'prizeLevel',
      ellipsis: true,
    },
    {
      title: '奖励价值',
      dataIndex: 'prizeValue',
      ellipsis: true,
    },
    {
      title: '审批模式',
      dataIndex: 'approveMode',
      ellipsis: true,
    },
    {
      title: '排序权重',
      dataIndex: 'sortWeight',
      ellipsis: true,
    },
    {
      title: '扩展信息',
      dataIndex: 'ext',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
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
      width: 90,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    tenantId: undefined, //租户ID
    activityCode: undefined, //活动编码
    prizeCode: undefined, //奖品编码
    prizeLevel: undefined, //奖品级别
    prizeName: undefined, //奖品名称
    approveMode: undefined, //审批模式：0-自动免审, 1-人工审批
    status: undefined, //状态：0-停用, 1-启用
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

  // 查询数据
  async function queryData() {
    tableLoading.value = true;
    try {
      let queryResult = await prizeConfigApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(queryData);

  // ---------------------------- 添加/修改 ----------------------------
  const formRef = ref();

  function showForm(data) {
    formRef.value.show(data);
  }

  // ---------------------------- 启用 / 禁用 ----------------------------

  /** 正在提交的行 id，只让那一行的控件转圈 */
  const statusLoadingId = ref(null);
  const approveLoadingId = ref(null);

  async function onToggleStatus(record, checked) {
    const target = checked ? PRIZE_STATUS_ENUM.ENABLED.value : PRIZE_STATUS_ENUM.DISABLED.value;
    statusLoadingId.value = record.id;
    try {
      await prizeConfigApi.updateStatus({ idList: [record.id], status: target });
      message.success(checked ? '已启用' : '已禁用');
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      statusLoadingId.value = null;
    }
  }

  // ---------------------------- 审批模式 / 排序权重：行内改 ----------------------------

  /**
   * 这两个字段走通用的 update 接口，需要回传整行 ——
   * PrizeConfigUpdateForm 的 approveMode 是 @NotNull，且服务端会重校验
   * 「优惠配置的资产类型必须与奖品一致」，少传字段会被判成参数错误。
   */
  function buildUpdatePayload(record, patch) {
    return {
      id: record.id,
      promotionConfigId: record.promotionConfigId,
      prizeType: record.prizeType,
      prizeName: record.prizeName,
      prizeLevel: record.prizeLevel,
      prizeValue: record.prizeValue,
      approveMode: record.approveMode,
      sortWeight: record.sortWeight,
      ext: record.ext,
      status: record.status,
      ...patch,
    };
  }

  async function onToggleApproveMode(record, checked) {
    approveLoadingId.value = record.id;
    try {
      await prizeConfigApi.update(buildUpdatePayload(record, { approveMode: checked ? 1 : 0 }));
      message.success(checked ? '已改为人工审批' : '已改为自动免审');
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      approveLoadingId.value = null;
    }
  }

  /**
   * 排序权重的本地编辑值，key 是行 id。
   *
   * ⚠️ 不从 blur 事件的 e.target.value 取值 —— 那是 DOM 上的字符串，
   * 与 antd InputNumber 的内部状态是两回事，程序化赋值时两者会不一致，
   * 表现是「界面显示改了、请求却没发出去」。改用 @change 维护本地草稿，
   * blur 时只读草稿，不碰 DOM。
   * （本项目已在 a-input-number 与 RangePicker 上踩过同族问题。）
   */
  const sortWeightDraft = reactive({});
  /** 防重：回车会先 blur 再冒泡，不加锁会连发两次请求 */
  const sortWeightSubmitting = new Set();

  async function commitSortWeight(record) {
    if (sortWeightSubmitting.has(record.id)) {
      return;
    }
    const next = sortWeightDraft[record.id];
    // 没动过、清空了、或与服务端值相同：都不发请求
    if (next === undefined || next === null || Number.isNaN(next) || next === record.sortWeight) {
      delete sortWeightDraft[record.id];
      return;
    }
    sortWeightSubmitting.add(record.id);
    try {
      await prizeConfigApi.update(buildUpdatePayload(record, { sortWeight: next }));
      message.success('排序权重已更新');
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      sortWeightSubmitting.delete(record.id);
      // 无论成败都清掉草稿并以服务端数据为准，
      // 避免界面停留在「改了但没存进去」的数字上
      delete sortWeightDraft[record.id];
      await queryData();
    }
  }

  // ---------------------------- 批量禁用 ----------------------------

  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  function confirmBatchDisable() {
    Modal.confirm({
      title: '批量禁用',
      content: `确定要禁用选中的 ${selectedRowKeyList.value.length} 个奖品吗？禁用后不再发放，可随时再启用。`,
      okText: '禁用',
      okType: 'danger',
      onOk() {
        requestBatchDisable();
      },
      cancelText: '取消',
      onCancel() {},
    });
  }

  async function requestBatchDisable() {
    try {
      SmartLoading.show();
      await prizeConfigApi.updateStatus({
        idList: selectedRowKeyList.value,
        status: PRIZE_STATUS_ENUM.DISABLED.value,
      });
      message.success('已批量禁用');
      selectedRowKeyList.value = [];
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      SmartLoading.hide();
    }
  }
</script>
