<!--
  * 优惠配置表
  *
  * 表里 23 列全铺开没人读得完。这里只留 8 列决策信息：
  *   · 预算/库存的四列（总量、已用 × 数量、金额）合成一个「额度用量」进度条列；
  *   · 五个 xxx_limit 合成一个「防刷限制」列，-1（不限）的不显示；
  * 其余字段默认隐藏（可在右上角列设置里调出），或点「详情」看完整分组。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:28:25
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="优惠配置名称" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.promoName" placeholder="优惠配置名称" />
      </a-form-item>
      <a-form-item label="资产类型" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.prizeType" :options="PRIZE_TYPE_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="状态" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="PROMOTION_STATUS_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
      <a-form-item label="创建时间" class="solvela-query-form-item">
        <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
      </a-form-item>
      <!-- 三个防刷次数：按具体次数精确匹配的检索几乎用不上，收进「更多」里 -->
      <template v-if="showMoreQuery">
        <a-form-item label="单会员ID限制" class="solvela-query-form-item">
          <a-input style="width: 200px" v-model:value="queryForm.identifyLimit" placeholder="按次数精确匹配，-1 为不限" />
        </a-form-item>
        <a-form-item label="单手机号限制" class="solvela-query-form-item">
          <a-input style="width: 200px" v-model:value="queryForm.phoneLimit" placeholder="按次数精确匹配，-1 为不限" />
        </a-form-item>
        <a-form-item label="单IP限制" class="solvela-query-form-item">
          <a-input style="width: 200px" v-model:value="queryForm.ipLimit" placeholder="按次数精确匹配，-1 为不限" />
        </a-form-item>
      </template>
      <a-form-item class="solvela-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon>
            <SearchOutlined />
          </template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="solvela-margin-left10">
          <template #icon>
            <ReloadOutlined />
          </template>
          重置
        </a-button>
        <a-button type="link" @click="showMoreQuery = !showMoreQuery">
          {{ showMoreQuery ? '收起' : '更多条件' }}
          <DownOutlined :rotate="showMoreQuery ? 180 : 0" />
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <!---------- 表格操作行 begin ----------->
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <a-button @click="showForm" type="primary" size="small">
          <template #icon>
            <PlusOutlined />
          </template>
          新建
        </a-button>
        <a-button @click="confirmBatchDelete" type="primary" danger size="small" :disabled="selectedRowKeyList.length == 0">
          <template #icon>
            <DeleteOutlined />
          </template>
          批量删除
        </a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.PROMOTION_CONFIG" :refresh="queryData" />
      </div>
    </a-row>
    <!---------- 表格操作行 end ----------->

    <!---------- 表格 begin ----------->
    <a-table
      size="small"
      :scroll="{ x: 1200, y: 800 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="id"
      bordered
      :loading="tableLoading"
      :pagination="false"
      :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="promotionStatusOf(text).color">{{ promotionStatusOf(text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'prizeType'">
          <a-tag :color="prizeTypeOf(text).color">{{ prizeTypeOf(text).desc }}</a-tag>
        </template>

        <!-- 额度用量：券/实物看数量，积分/现金看金额，用同一列表达 -->
        <template v-else-if="column.dataIndex === 'usage'">
          <div v-if="usageOf(record).unlimited" class="text-slate-400">不限制</div>
          <div v-else>
            <a-progress
              :percent="usageOf(record).percent"
              :status="usageOf(record).percent >= 100 ? 'exception' : 'normal'"
              size="small"
              :show-info="false"
            />
            <div class="text-[12px] text-slate-500">{{ usageOf(record).text }}</div>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'reviewLevel'">
          <span>{{ reviewLevelOf(text).desc }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'limitPeriod'">
          <span>{{ limitPeriodOf(text).desc }}</span>
        </template>

        <!-- 防刷限制：五个维度合成一列，-1（不限）的不显示；全不限时给个明确的「无」 -->
        <template v-else-if="column.dataIndex === 'limits'">
          <template v-if="limitsOf(record).length">
            <a-tag v-for="item in limitsOf(record)" :key="item" class="mb-1">{{ item }}</a-tag>
          </template>
          <span v-else class="text-slate-400">无</span>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <div class="solvela-table-operate">
            <a-button @click="showDetail(record)" type="link">详情</a-button>
            <a-button @click="showForm(record)" type="link">编辑</a-button>
            <a-button @click="onDelete(record)" danger type="link">删除</a-button>
          </div>
        </template>
      </template>
    </a-table>
    <!---------- 表格 end ----------->

    <div class="solvela-query-table-page">
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

    <PromotionConfigForm ref="formRef" @reloadList="queryData" />

    <!---------- 详情抽屉：只读，分组与编辑抽屉一致 ----------->
    <a-drawer :title="`优惠配置详情 · ${detail.promoName || ''}`" :width="720" :open="detailVisible" @close="detailVisible = false">
      <a-descriptions title="基本信息" bordered size="small" :column="2" class="mb-6">
        <a-descriptions-item label="配置ID">{{ detail.id }}</a-descriptions-item>
        <a-descriptions-item label="配置名称" :span="2">{{ detail.promoName }}</a-descriptions-item>
        <a-descriptions-item label="资产类型">
          <a-tag :color="prizeTypeOf(detail.prizeType).color">{{ prizeTypeOf(detail.prizeType).desc }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="promotionStatusOf(detail.status).color">{{ promotionStatusOf(detail.status).desc }}</a-tag>
        </a-descriptions-item>
      </a-descriptions>

      <a-descriptions
        :title="isQuotaBased(detail.prizeType) ? '库存控制（按个数）' : '预算控制（按金额）'"
        bordered
        size="small"
        :column="2"
        class="mb-6"
      >
        <template v-if="isQuotaBased(detail.prizeType)">
          <a-descriptions-item label="总库存">{{ totalText(detail.totalQuota) }}</a-descriptions-item>
          <a-descriptions-item label="已消耗库存">{{ detail.usedQuota }}</a-descriptions-item>
        </template>
        <template v-else>
          <a-descriptions-item label="总预算">{{ totalText(detail.totalAmount) }}</a-descriptions-item>
          <a-descriptions-item label="已消耗预算">{{ detail.usedAmount }}</a-descriptions-item>
        </template>
      </a-descriptions>

      <a-descriptions title="审核层级" bordered size="small" :column="2" class="mb-6">
        <a-descriptions-item label="审核层级" :span="2">{{ reviewLevelOf(detail.reviewLevel).desc }}</a-descriptions-item>
        <a-descriptions-item label="一审触发阈值">{{ detail.firstReviewThreshold }}</a-descriptions-item>
        <a-descriptions-item label="二审触发阈值">{{ detail.secondReviewThreshold }}</a-descriptions-item>
      </a-descriptions>

      <a-descriptions title="单次发放兜底" bordered size="small" :column="2" class="mb-6">
        <a-descriptions-item label="单次最大数量">{{ detail.singleMaxQuota }}</a-descriptions-item>
        <a-descriptions-item label="单次最大金额">{{ detail.singleMaxAmount }}</a-descriptions-item>
      </a-descriptions>

      <a-descriptions title="风控与防刷" bordered size="small" :column="2">
        <a-descriptions-item label="限制周期" :span="2">{{ limitPeriodOf(detail.limitPeriod).desc }}</a-descriptions-item>
        <a-descriptions-item label="单会员ID限制">{{ limitText(detail.identifyLimit) }}</a-descriptions-item>
        <a-descriptions-item label="单手机号限制">{{ limitText(detail.phoneLimit) }}</a-descriptions-item>
        <a-descriptions-item label="单IP限制">{{ limitText(detail.ipLimit) }}</a-descriptions-item>
        <a-descriptions-item label="单设备号限制">{{ limitText(detail.deviceLimit) }}</a-descriptions-item>
        <a-descriptions-item label="单端指纹限制">{{ limitText(detail.fingerprintLimit) }}</a-descriptions-item>
        <a-descriptions-item label="互斥规则">{{ detail.mutexRule || '无' }}</a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </a-card>
</template>
<script setup>
  import { reactive, ref, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { promotionConfigApi } from '/src/api/business/risk/promotion-config-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import PromotionConfigForm from './promotion-config-form.vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    PRIZE_TYPE_OPTIONS,
    PROMOTION_STATUS_OPTIONS,
    UNLIMITED,
    isQuotaBased,
    limitPeriodOf,
    prizeTypeOf,
    promotionStatusOf,
    reviewLevelOf,
  } from '/src/constants/business/risk/promotion-config-const';

  // ---------------------------- 查询区展开 ----------------------------

  const showMoreQuery = ref(false);

  // ---------------------------- 额度用量 / 防刷限制的派生展示 ----------------------------

  /**
   * 把「总量 + 已用」压成一个百分比。券/实物看数量，积分/现金看金额 ——
   * 两套字段本来就是二选一的，列表里没必要占四列。
   */
  function usageOf(record) {
    const quotaBased = isQuotaBased(record.prizeType);
    const total = Number(quotaBased ? record.totalQuota : record.totalAmount);
    const used = Number(quotaBased ? record.usedQuota : record.usedAmount) || 0;

    if (total === UNLIMITED || Number.isNaN(total)) {
      return { unlimited: true, percent: 0, text: '' };
    }
    // total 为 0 时算百分比会得到 Infinity/NaN，直接按已用尽处理
    const percent = total > 0 ? Math.min(Math.round((used / total) * 100), 100) : 100;
    return {
      unlimited: false,
      percent,
      text: `${used} / ${total}${quotaBased ? '' : ' 元'}`,
    };
  }

  /**
   * 五个防刷维度合成短标签，-1（不限）的不出现 —— 全是 -1 时返回空数组，
   * 由模板显示成「无」，而不是五个「不限」把列撑爆。
   */
  function limitsOf(record) {
    return [
      { label: '会员', value: record.identifyLimit },
      { label: '手机', value: record.phoneLimit },
      { label: 'IP', value: record.ipLimit },
      { label: '设备', value: record.deviceLimit },
      { label: '指纹', value: record.fingerprintLimit },
    ]
      .filter((i) => i.value != null && Number(i.value) !== UNLIMITED)
      .map((i) => `${i.label} ${i.value}`);
  }

  function limitText(value) {
    return value == null || Number(value) === UNLIMITED ? '不限' : value;
  }

  // 金额列后端是 BigDecimal，序列化后可能是 "-1.0000" 这样的字符串 ——
  // 直接 === -1 比不中，会把「不限制」显示成一个莫名其妙的 -1.0000
  function totalText(value) {
    return value == null || Number(value) === UNLIMITED ? '不限制' : value;
  }

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: '配置ID',
      dataIndex: 'id',
      width: 80,
    },
    {
      title: '优惠配置名称',
      dataIndex: 'promoName',
      width: 160,
      ellipsis: true,
    },
    {
      title: '资产类型',
      dataIndex: 'prizeType',
      width: 100,
    },
    {
      title: '额度用量',
      dataIndex: 'usage',
      width: 160,
    },
    {
      title: '审核层级',
      dataIndex: 'reviewLevel',
      width: 100,
    },
    {
      title: '限制周期',
      dataIndex: 'limitPeriod',
      width: 100,
    },
    {
      title: '防刷限制',
      dataIndex: 'limits',
      width: 200,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
    },
    // ↓ 以下默认隐藏：已并进上面的派生列，需要时在列设置里调出。
    //   创建人/更新人/更新时间不在这里标 —— DEFAULT_HIDDEN_COLUMNS 已统一收口
    {
      title: '总库存',
      dataIndex: 'totalQuota',
      showFlag: false,
    },
    {
      title: '已消耗库存',
      dataIndex: 'usedQuota',
      showFlag: false,
    },
    {
      title: '总预算',
      dataIndex: 'totalAmount',
      showFlag: false,
    },
    {
      title: '已消耗预算',
      dataIndex: 'usedAmount',
      showFlag: false,
    },
    {
      title: '一审触发阈值',
      dataIndex: 'firstReviewThreshold',
      showFlag: false,
    },
    {
      title: '二审触发阈值',
      dataIndex: 'secondReviewThreshold',
      showFlag: false,
    },
    {
      title: '单次最大数量',
      dataIndex: 'singleMaxQuota',
      showFlag: false,
    },
    {
      title: '单次最大金额',
      dataIndex: 'singleMaxAmount',
      showFlag: false,
    },
    {
      title: '创建人',
      dataIndex: 'createBy',
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      showFlag: false,
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
      width: 160,
    },
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    promoName: undefined, //优惠配置名称
    prizeType: undefined, //资产类型：SCORE / BALANCE / COUPON / PHYSICAL
    createTime: [], //创建时间
    createTimeBegin: undefined, //创建时间 开始
    createTimeEnd: undefined, //创建时间 结束
    identifyLimit: undefined, //同周期内，单会员ID最多领取次数 (-1为不限)
    phoneLimit: undefined, //同周期内，单手机号最多领取次数 (-1为不限)
    ipLimit: undefined, //同周期内，单IP地址最多领取次数 (-1为不限)
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
      let queryResult = await promotionConfigApi.queryPage(queryForm);
      tableData.value = queryResult.data.list;
      total.value = queryResult.data.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0];
    queryForm.createTimeEnd = dateStrings[1];
  }

  onMounted(queryData);

  // ---------------------------- 详情 ----------------------------

  const detailVisible = ref(false);
  const detail = ref({});

  function showDetail(data) {
    detail.value = { ...data };
    detailVisible.value = true;
  }

  // ---------------------------- 添加/修改 ----------------------------
  const formRef = ref();

  function showForm(data) {
    formRef.value.show(data);
  }

  // ---------------------------- 单个删除 ----------------------------
  //确认删除
  function onDelete(data) {
    Modal.confirm({
      title: '提示',
      content: '确定要删除选吗?',
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
    SolvelaLoading.show();
    try {
      await promotionConfigApi.delete(data.id);
      message.success('删除成功');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
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
      SolvelaLoading.show();
      await promotionConfigApi.batchDelete(selectedRowKeyList.value);
      message.success('删除成功');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      SolvelaLoading.hide();
    }
  }
</script>
