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
        <!-- 只提供批量禁用，不提供批量启用：
             禁用是「出问题了赶紧全停掉」的止血动作，越快越好；
             而启用要逐个确认配置完备（服务端也会拦），批量放开只会让运营一次性启用一批半成品 -->
        <a-button @click="confirmBatchDisable" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <StopOutlined />
          </template>
          批量禁用
        </a-button>
      </div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.ACTIVITY_CONFIG" :refresh="queryData" />
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

        <!-- 状态直接用开关就地切换，不用再进编辑表单 -->
        <template v-else-if="column.dataIndex === 'status'">
          <a-switch
            :checked="isActivityEnabled(record.status)"
            :loading="statusLoadingId === record.id"
            checked-children="启用"
            un-checked-children="禁用"
            @change="(checked) => onToggleStatus(record, checked)"
          />
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
            <!--
              玩法类活动一律给入口：「编辑」只改活动外壳（名称/时间），进不去玩法配置。
              没有这个按钮时，一个已配置好的抽奖活动在列表里根本没有任何办法回到它的奖池配置。
              未配置时文案用「继续配置」，把待办感做出来。
            -->
            <a-button v-if="hasGameplay(record.activityType)" type="link" @click="resumeWizard(record)">
              {{ configuredMap[record.activityCode] === false ? '继续配置' : '配置玩法' }}
            </a-button>
            <a-button v-else type="link" @click="openUpgrade(record)">升级玩法</a-button>
            <a-button @click="showForm(record)" type="link">编辑</a-button>
            <!-- 不提供删除：活动的生命周期用「启用/禁用」表达。
                 发奖流水按 activity_code 追溯（t_prize_log 上有 (member_name, activity_code) 索引），
                 删掉活动等于把资损追溯链路断在源头。
                 服务端 delete 接口与删除守卫仍保留，作为 API 层兜底。 -->
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
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import ActivityConfigForm from './activity-config-form.vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    ACTIVITY_STATUS_ENUM,
    PLAYABLE_TYPE_LIST,
    activityTypeMeta,
    hasGameplay,
    isActivityEnabled,
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

  // ---------------------------- 启用 / 禁用 ----------------------------

  /** 正在提交状态变更的行 id，用来只给那一行的开关转圈，而不是整表 loading */
  const statusLoadingId = ref(null);

  /**
   * 单个开关切换。
   *
   * 不加二次确认：启用/禁用都是可逆的，而「出问题立刻停掉」正是运营最需要快的动作，
   * 每次弹框会让止血变慢。真正不可逆的操作（删除）已经从这个页面拿掉了。
   *
   * ⚠️ 开关用的是 :checked 而非 v-model —— 服务端可能拒绝（如启用一个没配完的活动），
   * 用 v-model 会让开关先自己跳过去、再被数据刷回来，出现一次视觉回弹。
   * 用 :checked 则状态始终由服务端返回的数据决定。
   */
  async function onToggleStatus(record, checked) {
    const target = checked ? ACTIVITY_STATUS_ENUM.ENABLED.value : ACTIVITY_STATUS_ENUM.DISABLED.value;
    statusLoadingId.value = record.id;
    try {
      await activityConfigApi.updateStatus({ idList: [record.id], status: target });
      message.success(checked ? '已启用' : '已禁用');
      await queryData();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      statusLoadingId.value = null;
    }
  }

  // ---------------------------- 批量禁用 ----------------------------

  // 选择表格行
  const selectedRowKeyList = ref([]);

  function onSelectChange(selectedRowKeys) {
    selectedRowKeyList.value = selectedRowKeys;
  }

  /**
   * 批量禁用保留确认框：单个开关是一次点击一个、看得见结果，
   * 批量则可能一次影响十几个活动，值得让运营停一下确认数量。
   */
  function confirmBatchDisable() {
    Modal.confirm({
      title: '批量禁用',
      content: `确定要禁用选中的 ${selectedRowKeyList.value.length} 个活动吗？禁用后可随时再启用。`,
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
      await activityConfigApi.updateStatus({
        idList: selectedRowKeyList.value,
        status: ACTIVITY_STATUS_ENUM.DISABLED.value,
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
