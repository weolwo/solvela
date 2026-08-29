<!--
  * 会员优惠券
  *
  * 只读台账：券由发奖链路发放、由核销链路回写，管理端不提供增删改。
  *
  * 顶部统计里，**发放和核销是两个口径**（发放看 create_time、核销看 used_time），
  * 两个数没有包含关系，不要相减：今天核销的券可能是上个月发的。
  * 用同一个窗口算「今日核销 / 今日发放」得到的必然是个接近 0 的数 ——
  * 那不是核销率低，是口径错了，而它错得很像一条正常的业务结论。
  *
  * ⚠️ 「券库存」那一组是**全量**，不随时间范围变化：压着多少张没用的券是存量问题，
  * 限制在今天只会把它藏起来。
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 23:42:44
  * @Copyright  weolwo
-->
<template>
  <!---------- 优惠券统计 begin ----------->
  <StatPanel title="优惠券统计" storage-key="member-coupon" :issue-list="stat.issueList" @change="loadStat">
    <template #summary>
      本期发放 {{ num(stat.issuedCount) }} 张 · 本期核销 {{ num(stat.usedCount) }} 张
      <span v-if="stat.staleUnusedCount > 0" class="solvela-stat-summary-alert"> · 过期未收口 {{ num(stat.staleUnusedCount) }} 张 </span>
    </template>

    <a-row :gutter="12" class="solvela-stat-row">
      <a-col :span="5">
        <div class="solvela-stat-card is-primary">
          <div class="solvela-stat-card-label">本期发放</div>
          <div class="solvela-stat-card-value">
            {{ num(stat.issuedCount) }}
            <span class="solvela-stat-card-unit">张</span>
          </div>
          <div class="solvela-stat-card-foot">涉及 {{ num(stat.issuedMemberCount) }} 名会员</div>
        </div>
      </a-col>
      <a-col :span="5">
        <!-- ⚠️ 核销走 used_time，和发放不是同一批券，两个数不要相减 -->
        <div class="solvela-stat-card is-success">
          <div class="solvela-stat-card-label">
            本期核销
            <a-tooltip title="按核销时间统计，和「本期发放」不是同一批券——今天核销的券可能是上个月发的，两个数没有包含关系，不要相减">
              <QuestionCircleOutlined class="solvela-stat-card-hint" />
            </a-tooltip>
          </div>
          <div class="solvela-stat-card-value">
            {{ num(stat.usedCount) }}
            <span class="solvela-stat-card-unit">张</span>
          </div>
          <div class="solvela-stat-card-foot">涉及 {{ num(stat.usedMemberCount) }} 名会员</div>
        </div>
      </a-col>
      <a-col :span="5">
        <div class="solvela-stat-card is-muted">
          <div class="solvela-stat-card-label">
            本期发放的核销率
            <a-tooltip
              title="分母是本期发放的这批券，所以是有意义的转化率。但刚发的券天然还没来得及用——时间范围选得越近，这个数越低，不要据此判断券不好用"
            >
              <QuestionCircleOutlined class="solvela-stat-card-hint" />
            </a-tooltip>
          </div>
          <div class="solvela-stat-card-value">{{ percent(stat.issuedUsedRate) }}</div>
          <div class="solvela-stat-card-foot">这批券里已用 {{ num(stat.issuedUsedCount) }} 张</div>
        </div>
      </a-col>
      <a-col :span="5">
        <!-- 存量：压着多少张没用的券，限制在今天就看不见了 -->
        <div class="solvela-stat-card is-muted">
          <div class="solvela-stat-card-label">
            未使用库存
            <span class="solvela-stat-card-tag">全量</span>
            <a-tooltip title="不受时间范围影响。这个数是虚高的——没有过期扫描任务，过了有效期的券也还挂在「未使用」里">
              <QuestionCircleOutlined class="solvela-stat-card-hint" />
            </a-tooltip>
          </div>
          <div class="solvela-stat-card-value">
            {{ num(stat.stockUnusedCount) }}
            <span class="solvela-stat-card-unit">张</span>
          </div>
          <div class="solvela-stat-card-foot">真正还能用 {{ num(realUsableCount) }} 张 · 券总量 {{ num(stat.stockTotalCount) }}</div>
        </div>
      </a-col>
      <a-col :span="4">
        <!-- 全工程没有任何地方把券置为已过期，这批券永远不会自己收口 -->
        <div class="solvela-stat-card" :class="stat.staleUnusedCount > 0 ? 'is-critical' : 'is-muted'">
          <div class="solvela-stat-card-label">
            过期未收口
            <span class="solvela-stat-card-tag">全量</span>
          </div>
          <div class="solvela-stat-card-value">{{ num(stat.staleUnusedCount) }}</div>
          <div class="solvela-stat-card-foot">7 天内到期 {{ num(stat.expiringSoonCount) }} 张</div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="12">
      <a-col :span="12">
        <a-card v-if="stat.couponList && stat.couponList.length > 0" size="small" :bordered="false" class="solvela-stat-block">
          <div class="solvela-stat-block-head">
            券模分布（条形为核销率）
            <span class="solvela-stat-block-sub">按本期发放量 TOP 10</span>
          </div>
          <div v-for="item in stat.couponList" :key="item.couponCode" class="solvela-stat-line">
            <a-tooltip :title="item.couponName ? item.couponName + '（' + item.couponCode + '）' : item.couponCode">
              <div class="solvela-stat-name">{{ item.couponName || item.couponCode }}</div>
            </a-tooltip>
            <div class="solvela-stat-bar-wrap">
              <div class="solvela-stat-bar" :style="{ width: barPercent(item.usedRate) + '%' }"></div>
            </div>
            <div class="solvela-stat-num">
              {{ num(item.issuedCount) }} 张 · {{ percent(item.usedRate) }}
              <span v-if="item.staleCount > 0" class="solvela-stat-alert-text"> · 过期 {{ num(item.staleCount) }} </span>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card v-if="stat.sourceList && stat.sourceList.length > 0" size="small" :bordered="false" class="solvela-stat-block">
          <div class="solvela-stat-block-head">
            来源分布
            <span class="solvela-stat-block-sub">本期发放，取值原样回显（这一列没有枚举约束）</span>
          </div>
          <div v-for="item in stat.sourceList" :key="item.sourceType || 'none'" class="solvela-stat-line">
            <a-tooltip :title="item.sourceType || '（写入侧没写来源）'">
              <div class="solvela-stat-name">{{ item.sourceType || '（未记录）' }}</div>
            </a-tooltip>
            <div class="solvela-stat-bar-wrap">
              <div class="solvela-stat-bar" :style="{ width: barPercent(item.issuedShare) + '%' }"></div>
            </div>
            <div class="solvela-stat-num">{{ num(item.issuedCount) }} 张 · {{ percent(item.issuedShare) }}</div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </StatPanel>
  <!---------- 优惠券统计 end ----------->

  <!---------- 查询表单form begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <!-- 查询键是会员号：member_name 只是展示快照，且身上没有索引 -->
      <a-form-item label="会员号" class="solvela-query-form-item">
        <a-input-number style="width: 200px" v-model:value="queryForm.memberId" placeholder="会员号" :controls="false" :precision="0" />
      </a-form-item>
      <a-form-item label="券模编码" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.couponCode" placeholder="券模编码" />
      </a-form-item>
      <a-form-item label="有效期开始" class="solvela-query-form-item">
        <a-range-picker v-model:value="queryForm.validStartTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeValidStartTime" />
      </a-form-item>
      <a-form-item label="券名称" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.couponName" placeholder="券名称" />
      </a-form-item>
      <a-form-item label="券类型" class="solvela-query-form-item">
        <a-input style="width: 200px" v-model:value="queryForm.couponType" placeholder="券类型" />
      </a-form-item>
      <a-form-item label="状态" class="solvela-query-form-item">
        <a-select style="width: 200px" v-model:value="queryForm.status" :options="COUPON_STATUS_OPTIONS" placeholder="全部" allowClear />
      </a-form-item>
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
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <!---------- 表格操作行 begin ----------->
    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="TABLE_ID_CONST.BUSINESS.MARKETING.MEMBER_COUPON" :refresh="queryData" />
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
    >
      <template #bodyCell="{ text, column }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="couponStatusOf(text).color">{{ couponStatusOf(text).desc }}</a-tag>
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
  </a-card>
</template>
<script setup>
  import { computed, reactive, ref, onMounted } from 'vue';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { memberCouponApi } from '/src/api/business/ledger/member-coupon-api';
  import StatPanel from '/@/components/business/stat-panel/index.vue';
  import { barPercent, num, percent } from '/@/lib/stat-format';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import { TABLE_ID_CONST } from '/@/constants/support/table-id-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import { COUPON_STATUS_OPTIONS, couponStatusOf } from '/src/constants/business/ledger/member-coupon-const';

  // ---------------------------- 统计面板 ----------------------------

  /*
   * 时间范围由 StatPanel 自己管（默认当天），与下面列表的筛选<b>刻意不联动</b>。
   * 券库存那一组指标服务端就是按全量算的，不受这个范围影响。
   */
  const stat = ref({});

  async function loadStat(form) {
    try {
      const res = await memberCouponApi.stat(form);
      stat.value = res || {};
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  /*
   * 真正还能用的券 = 未使用 - 已过有效期还挂在未使用里的。
   * 直接把「未使用」当成可用库存会高估 —— 全工程没有任何地方把券置为已过期。
   */
  const realUsableCount = computed(() => {
    const unused = Number(stat.value.stockUnusedCount || 0);
    const stale = Number(stat.value.staleUnusedCount || 0);
    return unused - stale;
  });

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    {
      title: 'id',
      dataIndex: 'id',
      ellipsis: true,
    },
    {
      title: '会员号',
      dataIndex: 'memberId',
      width: 130,
      ellipsis: true,
    },
    {
      title: '会员账号（快照）',
      dataIndex: 'memberName',
      ellipsis: true,
    },
    {
      title: '券模编码',
      dataIndex: 'couponCode',
      ellipsis: true,
    },
    {
      title: '券类型',
      dataIndex: 'couponType',
      ellipsis: true,
    },
    {
      title: '券名称',
      dataIndex: 'couponName',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      ellipsis: true,
    },
    {
      title: '来源',
      dataIndex: 'sourceType',
      ellipsis: true,
    },
    {
      title: '关联单号',
      dataIndex: 'sourceBizId',
      ellipsis: true,
    },
    {
      title: '有效期开始',
      dataIndex: 'validStartTime',
      ellipsis: true,
    },
    {
      title: '有效期结束',
      dataIndex: 'validEndTime',
      ellipsis: true,
    },
    {
      title: '核销时间',
      dataIndex: 'usedTime',
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
  ]);

  // ---------------------------- 查询数据表单和方法 ----------------------------

  const queryFormState = {
    memberId: undefined, //会员号（关联键）
    couponCode: undefined, //券模编码
    validStartTime: [], //有效期开始
    validStartTimeBegin: undefined, //有效期开始 开始
    validStartTimeEnd: undefined, //有效期开始 结束
    couponName: undefined, //券名称
    couponType: undefined, //券类型
    status: undefined, //状态：0-未使用, 1-已使用, 2-已过期, 3-已作废
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
      let queryResult = await memberCouponApi.queryPage(queryForm);
      tableData.value = queryResult.list;
      total.value = queryResult.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onChangeValidStartTime(dates, dateStrings) {
    queryForm.validStartTimeBegin = dateStrings[0];
    queryForm.validStartTimeEnd = dateStrings[1];
  }

  onMounted(queryData);
</script>
