<!--
  * 登录风控 - 名单（黑 / 灰 / 白）
  *
  * 【🔴 豁免面刻意很窄】白名单上的东西恰恰是攻击者最想拿到的。所以页面顶上那条说明
  * 不是客套：IP 白名单只跳过 IP 维度的计数与限流，会员白名单只跳过矩阵与打分 ——
  * 全局滑块、密码冷却、设备封禁一个都不豁免。不写出来的话，客服会以为
  * 「加了白名单就万事大吉」，然后把一个被盗的账号加进去。
  *
  * 【🔴 撤销不删行】列表默认只看有效的，勾上「含已撤销」能看到历史连撤销人一起。
  * 删行的话，下一个人看到的是「这个 IP 从来没被封过」—— 而他正要判断「上次是不是误封」。
  *
  * 【🔴 身份维度输明文、存摘要】库里存的是 PiiHasher 的摘要，列表里显示的也是那串 hex。
  * 看着不友好，但「列表页能读出所有被封用户的手机号」是更糟的事。
  * 要确认是谁，用查询框输明文反查（服务端算摘要再匹配）。
  *
  * @Date 2026-10-16
-->
<template>
  <a-alert type="warning" show-icon style="margin-bottom: 12px" message="白名单不是万能钥匙">
    <template #description>
      <b>IP 白名单</b>只跳过 IP 维度的计数与限流；<b>会员白名单</b>只跳过处置矩阵与打分。
      全局滑块、密码冷却、设备封禁、黑名单<b>一个都不豁免</b> ——
      一个公司出口背后照样可能坐着攻击者。
      <br />
      会员维度<b>只允许白名单</b>：封号请用会员状态（冻结），名单里再放一份就有了两个答案。
    </template>
  </a-alert>

  <!---------- 查询 begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="类型" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.listType" :options="RISK_LIST_TYPE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="维度" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.dimension" :options="RISK_LIST_DIMENSION_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="值" class="solvela-query-form-item">
        <a-input style="width: 180px" v-model:value="queryForm.listValue" placeholder="IP / 网段 / 会员号" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="手机号/邮箱" class="solvela-query-form-item">
        <!-- 输明文，服务端算摘要再查 —— 客服手上只有明文，算 hash 不是人干的事 -->
        <a-input style="width: 200px" v-model:value="queryForm.identity" placeholder="输明文，服务端算摘要" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item class="solvela-query-form-item">
        <a-checkbox v-model:checked="includeRevoked">看已撤销的</a-checkbox>
      </a-form-item>
      <QueryActions @search="onSearch" @reset="resetQuery" />
    </a-row>
  </a-form>
  <!---------- 查询 end ----------->

  <a-card size="small" :bordered="false" :hoverable="true" style="margin-top: 12px">
    <template #title>
      <a-button v-privilege="'memberRisk:list'" type="primary" @click="openAdd">新增名单</a-button>
    </template>

    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      :pagination="false"
      :loading="tableLoading"
      rowKey="id"
      bordered
    >
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'listType'">
          <a-tag :color="metaOf(RISK_LIST_TYPE_ENUM, text).color">
            {{ metaOf(RISK_LIST_TYPE_ENUM, text).desc }}
          </a-tag>
          <a-tag :color="metaOf(RISK_LIST_DIMENSION_ENUM, record.dimension).color">
            {{ metaOf(RISK_LIST_DIMENSION_ENUM, record.dimension).desc }}
          </a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'listValue'">
          <!-- 摘要很长，撑不开就截断；完整值在 title 里 -->
          <span :title="text" class="cell-value">{{ text }}</span>
        </template>

        <template v-else-if="column.dataIndex === 'score'">
          <span v-if="record.score !== null && record.score !== undefined">+{{ record.score }} 分</span>
          <span v-else class="cell-sub">—</span>
        </template>

        <template v-else-if="column.dataIndex === 'expireTime'">
          <span v-if="text">{{ text }}</span>
          <span v-else class="cell-sub">长期</span>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="metaOf(RISK_LIST_STATUS_ENUM, text).color">
            {{ metaOf(RISK_LIST_STATUS_ENUM, text).desc }}
          </a-tag>
          <!-- 撤销信息跟状态挤在一列：它只在已撤销时才有意义 -->
          <div v-if="record.revokeOperator" class="cell-sub">
            {{ record.revokeOperator }}：{{ record.revokeReason }}
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a
            v-if="record.status === RISK_LIST_STATUS_ENUM.VALID.value"
            v-privilege="'memberRisk:list'"
            @click="openRevoke(record)"
          >
            撤销
          </a>
          <span v-else class="cell-sub">—</span>
        </template>
      </template>
    </a-table>

    <TablePagination v-model:pageNum="queryForm.pageNum" v-model:pageSize="queryForm.pageSize" :total="total" @change="queryData" />
  </a-card>

  <!---------- 新增 begin ----------->
  <a-modal v-model:open="addOpen" title="新增名单" :confirmLoading="saving" @ok="doAdd" width="640">
    <a-form :labelCol="{ span: 6 }">
      <a-form-item label="类型">
        <a-radio-group v-model:value="addForm.listType" :options="RISK_LIST_TYPE_OPTIONS" />
      </a-form-item>

      <a-form-item label="维度">
        <a-radio-group v-model:value="addForm.dimension" :options="RISK_LIST_DIMENSION_OPTIONS" />
        <div v-if="memberDimension" class="cell-sub">
          会员维度只允许白名单，而且<b>必须填到期时间</b>：它的用途是审核账号、压测账号，
          都是「这一阵子」的事。
        </div>
      </a-form-item>

      <a-form-item v-if="identityDimension" label="手机号 / 邮箱">
        <a-input v-model:value="addForm.identity" placeholder="明文，服务端算摘要再存" />
        <div class="cell-sub">🔴 服务端会先规范化再算摘要 —— 和登录那一侧算出来的必须是同一个</div>
      </a-form-item>
      <a-form-item v-else label="值">
        <a-input
          v-model:value="addForm.listValue"
          :placeholder="addForm.dimension === RISK_LIST_DIMENSION_ENUM.IP_RANGE.value
            ? '网段，如 203.0.113.0/24'
            : addForm.dimension === RISK_LIST_DIMENSION_ENUM.MEMBER.value
              ? '会员号'
              : 'IP'"
        />
        <div class="cell-sub">格式解不开会直接报错 —— 不会留一条「永远不命中」的名单在列表里</div>
      </a-form-item>

      <a-form-item v-if="greyType" label="命中加分">
        <a-input-number v-model:value="addForm.score" :min="1" :max="1000" style="width: 140px" />
        <div class="cell-sub">只有灰名单有分：黑名单直接拒绝、白名单直接放宽，分数在那两档上没有意义</div>
      </a-form-item>

      <a-form-item label="到期时间">
        <a-date-picker
          v-model:value="addForm.expireTime"
          show-time
          valueFormat="YYYY-MM-DD HH:mm:ss"
          style="width: 220px"
        />
        <div class="cell-sub">
          不填默认 30 天。🔴 IP 会被回收、重新分配 —— 永久黑名单迟早误伤一个无辜的新住户
        </div>
      </a-form-item>

      <a-form-item label="原因">
        <a-textarea v-model:value="addForm.reason" :rows="3" placeholder="给下一个看到它的人的人话" />
      </a-form-item>
    </a-form>
  </a-modal>
  <!---------- 新增 end ----------->

  <!---------- 撤销 begin ----------->
  <a-modal v-model:open="revokeOpen" title="撤销名单" :confirmLoading="saving" @ok="doRevoke">
    <a-form :labelCol="{ span: 5 }">
      <a-form-item label="名单">
        <span>{{ revokeTarget?.listValue }}</span>
      </a-form-item>
      <a-form-item label="撤销原因">
        <a-textarea v-model:value="revokeReason" :rows="3" placeholder="必填" />
        <div class="cell-sub">「上次为什么解的」和「为什么加的」一样重要 —— 这一行不会被删掉</div>
      </a-form-item>
    </a-form>
  </a-modal>
  <!---------- 撤销 end ----------->
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { memberRiskApi } from '/@/api/business/member/member-risk-api';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';
  import {
    RISK_LIST_DIMENSION_ENUM,
    RISK_LIST_DIMENSION_OPTIONS,
    RISK_LIST_STATUS_ENUM,
    RISK_LIST_TYPE_ENUM,
    RISK_LIST_TYPE_OPTIONS,
    riskMetaOf,
  } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const columns = ref([
    { title: '类型 / 维度', dataIndex: 'listType', width: 160 },
    { title: '值', dataIndex: 'listValue', width: 260, ellipsis: true },
    { title: '分', dataIndex: 'score', width: 80 },
    { title: '原因', dataIndex: 'reason', width: 200, ellipsis: true },
    { title: '到期', dataIndex: 'expireTime', width: 160 },
    { title: '新增人', dataIndex: 'operator', width: 100 },
    { title: '状态', dataIndex: 'status', width: 180 },
    { title: '操作', dataIndex: 'action', width: 80, fixed: 'right' },
  ]);

  const queryFormDefault = () => ({
    listType: undefined,
    dimension: undefined,
    listValue: '',
    identity: '',
    pageNum: 1,
    pageSize: 10,
  });

  const queryForm = reactive(queryFormDefault());
  /** 默认只看有效的：客服日常要的是「现在谁被封着」 */
  const includeRevoked = ref(false);
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  function metaOf(enumObj, value) {
    return riskMetaOf(enumObj, value, { desc: value, color: 'default' });
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      // 不传 status = 只看有效；要看历史就显式不限（传 undefined 会被后端兜成「只看有效」，
      // 所以这里靠「含已撤销」开关切换：开了就查已撤销那一档，关了查有效那一档）
      const res = await memberRiskApi.queryListPage({
        ...queryForm,
        status: includeRevoked.value ? RISK_LIST_STATUS_ENUM.REVOKED.value : undefined,
      });
      tableData.value = res.list;
      total.value = res.total;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function resetQuery() {
    Object.assign(queryForm, queryFormDefault());
    includeRevoked.value = false;
    queryData();
  }

  onMounted(queryData);

  // ---------------------------- 新增 ----------------------------

  const addOpen = ref(false);
  const saving = ref(false);
  const addForm = reactive({
    listType: RISK_LIST_TYPE_ENUM.BLACK.value,
    dimension: RISK_LIST_DIMENSION_ENUM.IP.value,
    listValue: '',
    identity: '',
    score: undefined,
    reason: '',
    expireTime: undefined,
  });

  const greyType = computed(() => addForm.listType === RISK_LIST_TYPE_ENUM.GREY.value);
  const memberDimension = computed(
    () => addForm.dimension === RISK_LIST_DIMENSION_ENUM.MEMBER.value,
  );
  const identityDimension = computed(
    () => addForm.dimension === RISK_LIST_DIMENSION_ENUM.IDENTITY.value,
  );

  function openAdd() {
    Object.assign(addForm, {
      listType: RISK_LIST_TYPE_ENUM.BLACK.value,
      dimension: RISK_LIST_DIMENSION_ENUM.IP.value,
      listValue: '',
      identity: '',
      score: undefined,
      reason: '',
      expireTime: undefined,
    });
    addOpen.value = true;
  }

  async function doAdd() {
    saving.value = true;
    try {
      /*
       * 🔴 只把「这一档用得上」的字段送出去：灰名单才带 score，身份维度才带 identity。
       * 都带的话，后端那几条语义校验（「黑白名单不许有分」）会把一个前端默认值当成用户填的。
       */
      await memberRiskApi.addList({
        listType: addForm.listType,
        dimension: addForm.dimension,
        listValue: identityDimension.value ? undefined : addForm.listValue,
        identity: identityDimension.value ? addForm.identity : undefined,
        score: greyType.value ? addForm.score : undefined,
        reason: addForm.reason,
        expireTime: addForm.expireTime,
      });
      message.success('已新增，最多 30 秒在所有实例上生效');
      addOpen.value = false;
      await queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  // ---------------------------- 撤销 ----------------------------

  const revokeOpen = ref(false);
  const revokeTarget = ref(null);
  const revokeReason = ref('');

  function openRevoke(record) {
    revokeTarget.value = record;
    revokeReason.value = '';
    revokeOpen.value = true;
  }

  async function doRevoke() {
    saving.value = true;
    try {
      const res = await memberRiskApi.revokeList({
        id: revokeTarget.value.id,
        reason: revokeReason.value,
      });
      message.success(res ?? '已撤销');
      revokeOpen.value = false;
      await queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }
</script>

<style scoped>
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  /* 身份摘要是 64 位 hex，等宽字体才看得清前后几位 */
  .cell-value {
    font-family: var(--font-family-monospace, monospace);
    font-size: 12px;
  }
</style>
