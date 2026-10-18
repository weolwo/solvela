<!--
  * 安全中心 - 名单（黑 / 灰 / 白）。网关与登录风控共用一份
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
  * 【🔴 新增弹窗里不合法的组合是选不出来的，不是选了之后被拒绝】
  * 「会员维度没有黑名单」「灰名单必须有分」「会员名单必须填到期」这几条是 SecurityListAdminService
  * 的语义校验。页面把它们做成联动（会员维度时黑名单灰掉、灰名单才出现分、会员维度时「长期」灰掉），
  * 人就不会先填完一屏、再被一条红字打回来。服务端那份仍然是说了算的。
  *
  * @Date 2026-10-18
-->
<template>
  <a-alert type="warning" show-icon style="margin-bottom: 12px" message="白名单不是万能钥匙">
    <template #description>
      <b>IP 白名单</b>只跳过 IP 维度的计数与限流；<b>会员白名单</b>只跳过处置矩阵与打分。
      全局滑块、密码冷却、设备封禁、黑名单<b>一个都不豁免</b> ——
      一个公司出口背后照样可能坐着攻击者。
      <br />
      会员维度<b>没有黑名单</b>：封号请用会员状态（冻结），名单里再放一份就有了两个答案。
      <b>会员灰名单</b>：可信设备按新设备算、兑换等资产操作每次都要二次验证、网关限流阈值减半；对会员本人不可见。
    </template>
  </a-alert>

  <!---------- 查询 begin ----------->
  <a-form class="solvela-query-form">
    <a-row class="solvela-query-form-row">
      <a-form-item label="类型" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.listType" :options="SECURITY_LIST_TYPE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="维度" class="solvela-query-form-item">
        <a-select style="width: 120px" v-model:value="queryForm.dimension" :options="SECURITY_LIST_DIMENSION_OPTIONS" placeholder="全部" allow-clear />
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
      <a-button v-privilege="'security:list'" type="primary" @click="openAdd">新增名单</a-button>
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
          <a-tag :color="metaOf(SECURITY_LIST_TYPE_ENUM, text).color">
            {{ metaOf(SECURITY_LIST_TYPE_ENUM, text).desc }}
          </a-tag>
          <a-tag :color="metaOf(SECURITY_LIST_DIMENSION_ENUM, record.dimension).color">
            {{ metaOf(SECURITY_LIST_DIMENSION_ENUM, record.dimension).desc }}
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

        <template v-else-if="column.dataIndex === 'sourceRule'">
          <a-tag v-if="text" :color="metaOf(SECURITY_LIST_SOURCE_RULE_ENUM, text).color">
            {{ metaOf(SECURITY_LIST_SOURCE_RULE_ENUM, text).desc }}
          </a-tag>
          <span v-else class="cell-sub">人工</span>
        </template>

        <template v-else-if="column.dataIndex === 'hitCount'">
          <!-- 命中次数：自动条目重复命中时累加；被挡：封禁期间被网关安全闸直接挡掉的请求数（这笔成本账） -->
          <div>命中 {{ record.hitCount ?? 0 }} 次</div>
          <div class="cell-sub">被挡 {{ record.blockedCount ?? 0 }} 次</div>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="metaOf(SECURITY_LIST_STATUS_ENUM, text).color">
            {{ metaOf(SECURITY_LIST_STATUS_ENUM, text).desc }}
          </a-tag>
          <!-- 撤销信息跟状态挤在一列：它只在已撤销时才有意义 -->
          <div v-if="record.revokeOperator" class="cell-sub">
            {{ record.revokeOperator }}：{{ record.revokeReason }}
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a
            v-if="record.status === SECURITY_LIST_STATUS_ENUM.VALID.value"
            v-privilege="'security:list'"
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
  <a-modal v-model:open="addOpen" title="新增名单" :width="680" :maskClosable="false" :destroyOnClose="true">
    <!-- 这一条随着选择变，人点保存之前能先读一遍「我到底在加什么」 -->
    <div class="modal-target">
      <a-tag :color="metaOf(SECURITY_LIST_TYPE_ENUM, addForm.listType).color">
        {{ metaOf(SECURITY_LIST_TYPE_ENUM, addForm.listType).desc }}
      </a-tag>
      <a-tag :color="metaOf(SECURITY_LIST_DIMENSION_ENUM, addForm.dimension).color">
        {{ metaOf(SECURITY_LIST_DIMENSION_ENUM, addForm.dimension).desc }}
      </a-tag>
      <span class="modal-target-note">{{ EFFECT_HINT[addForm.listType] }}</span>
    </div>

    <a-form ref="addFormRef" :model="addForm" :rules="addRules" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <a-form-item label="维度" name="dimension">
        <a-radio-group v-model:value="addForm.dimension" button-style="solid" @change="onDimensionChange">
          <a-radio-button v-for="item in SECURITY_LIST_DIMENSION_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-radio-button>
        </a-radio-group>
        <div v-if="memberDimension" class="form-hint">
          会员维度只有白、灰两种（没有黑名单 —— 封号请用会员状态），而且<b>必须填到期时间</b>：
          白名单的用途是审核账号、压测账号，灰名单是「这阵子多盯着点」—— 都是「这一阵子」的事。
        </div>
      </a-form-item>

      <a-form-item label="类型" name="listType">
        <a-radio-group v-model:value="addForm.listType" button-style="solid" @change="onListTypeChange">
          <a-radio-button
            v-for="item in SECURITY_LIST_TYPE_OPTIONS"
            :key="item.value"
            :value="item.value"
            :disabled="memberDimension && item.value === SECURITY_LIST_TYPE_ENUM.BLACK.value"
          >
            {{ item.label }}
          </a-radio-button>
        </a-radio-group>
      </a-form-item>

      <a-form-item v-if="identityDimension" label="手机号 / 邮箱" name="identity">
        <a-input v-model:value="addForm.identity" placeholder="明文，服务端算摘要再存" />
        <div class="form-hint">🔴 服务端会先规范化再算摘要 —— 和登录那一侧算出来的必须是同一个</div>
      </a-form-item>
      <a-form-item v-else label="值" name="listValue">
        <a-input v-model:value="addForm.listValue" :placeholder="VALUE_PLACEHOLDER[addForm.dimension]" />
        <div class="form-hint">格式解不开会直接报错 —— 不会留一条「永远不命中」的名单在列表里</div>
      </a-form-item>

      <a-form-item v-if="greyType" label="命中加分" name="score">
        <a-input-number v-model:value="addForm.score" :min="1" :max="1000" :precision="0" style="width: 140px" />
        <div class="form-hint">
          只有灰名单有分：黑名单直接拒绝、白名单直接放宽，分数在那两档上没有意义。
          分档线：前置 ≥ 30 出滑块，后置 ≥ 40 要 OTP、≥ 80 换通道。
        </div>
      </a-form-item>

      <a-form-item label="到期" name="expireTime">
        <!-- 🔴 常用时长做成按钮：一条名单该挂多久是个「几天」的判断，不该每次都去日历上点一个时刻 -->
        <a-radio-group v-model:value="expirePreset" button-style="solid" size="small" @change="onExpirePresetChange">
          <a-radio-button v-for="item in EXPIRE_PRESETS" :key="item.key" :value="item.key">{{ item.label }}</a-radio-button>
          <a-radio-button value="CUSTOM">自定义</a-radio-button>
          <a-radio-button value="NEVER" :disabled="memberDimension">长期</a-radio-button>
        </a-radio-group>
        <div v-if="expirePreset === 'CUSTOM'" style="margin-top: 8px">
          <a-date-picker
            v-model:value="addForm.expireTime"
            show-time
            valueFormat="YYYY-MM-DD HH:mm:ss"
            style="width: 240px"
            placeholder="到期时刻"
          />
        </div>
        <div class="form-hint">
          <template v-if="addForm.expireTime">到期：{{ addForm.expireTime }}</template>
          <template v-else>长期有效。🔴 IP 会被回收、重新分配 —— 永久黑名单迟早误伤一个无辜的新住户</template>
        </div>
      </a-form-item>

      <a-form-item label="原因" name="reason">
        <a-textarea
          v-model:value="addForm.reason"
          :rows="3"
          :maxlength="128"
          show-count
          placeholder="给下一个看到它的人的人话：为什么封、从哪看出来的"
        />
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="addOpen = false">取消</a-button>
        <a-button type="primary" :loading="saving" @click="doAdd">新增</a-button>
      </a-space>
    </template>
  </a-modal>
  <!---------- 新增 end ----------->

  <!---------- 撤销 begin ----------->
  <a-modal v-model:open="revokeOpen" title="撤销名单" :width="560" :maskClosable="false" :destroyOnClose="true">
    <div class="modal-target">
      <a-tag :color="metaOf(SECURITY_LIST_TYPE_ENUM, revokeTarget?.listType).color">
        {{ metaOf(SECURITY_LIST_TYPE_ENUM, revokeTarget?.listType).desc }}
      </a-tag>
      <span class="modal-target-name">{{ revokeTarget?.listValue }}</span>
      <span class="modal-target-note">撤销不删行：这一条连同撤销人、原因一起留在列表里（勾「看已撤销的」能查到）</span>
    </div>

    <a-form ref="revokeFormRef" :model="revokeForm" :rules="revokeRules" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <a-form-item label="当初为什么加">
        <span class="cell-sub">{{ revokeTarget?.reason || '—' }}</span>
      </a-form-item>
      <a-form-item label="撤销原因" name="reason">
        <a-textarea v-model:value="revokeForm.reason" :rows="3" :maxlength="128" show-count placeholder="必填" />
        <div class="form-hint">「上次为什么解的」和「为什么加的」一样重要 —— 这一行不会被删掉</div>
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="revokeOpen = false">取消</a-button>
        <a-button type="primary" danger :loading="saving" @click="doRevoke">撤销</a-button>
      </a-space>
    </template>
  </a-modal>
  <!---------- 撤销 end ----------->
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import dayjs from 'dayjs';
  import { message } from 'ant-design-vue';
  import { securityApi } from '/@/api/business/security/security-api';
  import QueryActions from '/@/components/framework/query-actions/index.vue';
  import TablePagination from '/@/components/framework/table-pagination/index.vue';
  import {
    SECURITY_LIST_DIMENSION_ENUM,
    SECURITY_LIST_DIMENSION_OPTIONS,
    SECURITY_LIST_SOURCE_RULE_ENUM,
    SECURITY_LIST_STATUS_ENUM,
    SECURITY_LIST_TYPE_ENUM,
    SECURITY_LIST_TYPE_OPTIONS,
  } from '/@/constants/business/security/security-const';
  import { riskMetaOf } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const columns = ref([
    { title: '类型 / 维度', dataIndex: 'listType', width: 160 },
    { title: '值', dataIndex: 'listValue', width: 260, ellipsis: true },
    { title: '分', dataIndex: 'score', width: 80 },
    { title: '原因', dataIndex: 'reason', width: 200, ellipsis: true },
    { title: '到期', dataIndex: 'expireTime', width: 160 },
    { title: '来源', dataIndex: 'sourceRule', width: 130 },
    { title: '命中 / 被挡', dataIndex: 'hitCount', width: 120 },
    { title: '新增人', dataIndex: 'operator', width: 100 },
    { title: '状态', dataIndex: 'status', width: 180 },
    { title: '操作', dataIndex: 'action', width: 80, fixed: 'right' },
  ]);

  /** 选中这一档会发生什么。「黑 / 灰 / 白」三个字本身不说明它命中之后干了什么 */
  const EFFECT_HINT = {
    [SECURITY_LIST_TYPE_ENUM.BLACK.value]: '命中即拒绝：网关安全闸直接挡掉，登录风控也当场判死。',
    [SECURITY_LIST_TYPE_ENUM.GREY.value]: '命中加分：更容易触发滑块 / OTP；会员灰名单另外还要每次二次验证、限流阈值减半。',
    [SECURITY_LIST_TYPE_ENUM.WHITE.value]: '只放宽这一个维度的计数与打分，其余处置（全局滑块、密码冷却、设备封禁）照常。',
  };

  const VALUE_PLACEHOLDER = {
    [SECURITY_LIST_DIMENSION_ENUM.IP.value]: 'IP，如 203.0.113.5',
    [SECURITY_LIST_DIMENSION_ENUM.IP_RANGE.value]: '网段，如 203.0.113.0/24',
    [SECURITY_LIST_DIMENSION_ENUM.MEMBER.value]: '会员号',
  };

  /** 常用时长。天数对齐服务端不填时的默认（30 天） */
  const EXPIRE_PRESETS = [
    { key: 'D1', label: '1 天', days: 1 },
    { key: 'D7', label: '7 天', days: 7 },
    { key: 'D30', label: '30 天', days: 30 },
    { key: 'D90', label: '90 天', days: 90 },
  ];

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
      const res = await securityApi.queryListPage({
        ...queryForm,
        status: includeRevoked.value ? SECURITY_LIST_STATUS_ENUM.REVOKED.value : undefined,
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
  const addFormRef = ref();
  /** 到期的快捷档：EXPIRE_PRESETS 的 key、CUSTOM（自己挑时刻）或 NEVER（长期） */
  const expirePreset = ref('D30');
  const addForm = reactive({
    listType: SECURITY_LIST_TYPE_ENUM.BLACK.value,
    dimension: SECURITY_LIST_DIMENSION_ENUM.IP.value,
    listValue: '',
    identity: '',
    score: undefined,
    reason: '',
    expireTime: undefined,
  });

  const greyType = computed(() => addForm.listType === SECURITY_LIST_TYPE_ENUM.GREY.value);
  const memberDimension = computed(() => addForm.dimension === SECURITY_LIST_DIMENSION_ENUM.MEMBER.value);
  const identityDimension = computed(() => addForm.dimension === SECURITY_LIST_DIMENSION_ENUM.IDENTITY.value);

  /**
   * 值的形状。与后端 IpRange.literalBytes 同一条判据（只认十六进制数字、`.`、`:`，可带 /前缀），
   * 故意宽松 —— 比服务端严的前端校验会把一个合法的 IPv6 挡在门外，那比晚一步报错更糟。
   */
  function validateListValue() {
    const value = (addForm.listValue || '').trim();
    if (!value) {
      return Promise.reject('这一项必填');
    }
    if (addForm.dimension === SECURITY_LIST_DIMENSION_ENUM.MEMBER.value) {
      return /^\d+$/.test(value) ? Promise.resolve() : Promise.reject('会员维度的值是会员号（纯数字）');
    }
    const [addr, prefix, ...rest] = value.split('/');
    if (rest.length > 0 || !/^[0-9a-fA-F.:]+$/.test(addr)) {
      return Promise.reject('不是 IP 字面量 —— 这里不接受域名，解不开的值会变成一条永远不命中的名单');
    }
    if (addForm.dimension === SECURITY_LIST_DIMENSION_ENUM.IP_RANGE.value && !/^\d{1,3}$/.test(prefix || '')) {
      return Promise.reject('网段要带前缀长度，如 203.0.113.0/24');
    }
    if (prefix !== undefined && !/^\d{1,3}$/.test(prefix)) {
      return Promise.reject('前缀长度不合法');
    }
    return Promise.resolve();
  }

  const addRules = {
    dimension: [{ required: true, message: '选一个维度' }],
    listType: [{ required: true, message: '选一个类型' }],
    listValue: [{ validator: validateListValue }],
    identity: [{ required: true, message: '输手机号或邮箱的明文，服务端算摘要再存' }],
    score: [{ required: true, message: '灰名单必须给分 —— 不给分的灰名单命中了也什么都不会发生' }],
    reason: [{ required: true, message: '写明为什么进名单 —— 这句话是给下一个看到它的人的' }],
    expireTime: [
      {
        validator: () => {
          if (!addForm.expireTime) {
            return memberDimension.value
              ? Promise.reject('会员名单必须填到期时间，不允许长期')
              : Promise.resolve();
          }
          return dayjs(addForm.expireTime).isAfter(dayjs())
            ? Promise.resolve()
            : Promise.reject('到期时间不能是过去 —— 那条名单加完立刻就失效了');
        },
      },
    ],
  };

  function applyExpirePreset() {
    const preset = EXPIRE_PRESETS.find((item) => item.key === expirePreset.value);
    if (preset) {
      addForm.expireTime = dayjs().add(preset.days, 'day').format('YYYY-MM-DD HH:mm:ss');
    } else if (expirePreset.value === 'NEVER') {
      addForm.expireTime = undefined;
    }
    // CUSTOM 保留人自己挑的那个时刻，不覆盖
  }

  function onExpirePresetChange() {
    applyExpirePreset();
  }

  /** 换维度：会员维度没有黑名单，也不许长期 —— 这两条在这里当场纠正，而不是等保存被拒 */
  function onDimensionChange() {
    if (memberDimension.value) {
      if (addForm.listType === SECURITY_LIST_TYPE_ENUM.BLACK.value) {
        addForm.listType = SECURITY_LIST_TYPE_ENUM.GREY.value;
      }
      if (expirePreset.value === 'NEVER') {
        expirePreset.value = 'D30';
        applyExpirePreset();
      }
    }
    addForm.listValue = '';
    addForm.identity = '';
    addFormRef.value?.clearValidate(['listValue', 'identity', 'listType', 'expireTime']);
  }

  /** 换类型：分只在灰名单上有意义，切走就清掉（带着一个前端默认值提交会被后端当成「人填的」） */
  function onListTypeChange() {
    addForm.score = greyType.value ? addForm.score ?? 30 : undefined;
    addFormRef.value?.clearValidate(['score']);
  }

  function openAdd() {
    Object.assign(addForm, {
      listType: SECURITY_LIST_TYPE_ENUM.BLACK.value,
      dimension: SECURITY_LIST_DIMENSION_ENUM.IP.value,
      listValue: '',
      identity: '',
      score: undefined,
      reason: '',
      expireTime: undefined,
    });
    expirePreset.value = 'D30';
    applyExpirePreset();
    addOpen.value = true;
  }

  async function doAdd() {
    try {
      await addFormRef.value.validate();
    } catch {
      // 校验没过：错误已经标在对应的字段上
      return;
    }
    saving.value = true;
    try {
      /*
       * 🔴 只把「这一档用得上」的字段送出去：灰名单才带 score，身份维度才带 identity。
       * 都带的话，后端那几条语义校验（「黑白名单不许有分」）会把一个前端默认值当成用户填的。
       */
      await securityApi.addList({
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
  const revokeFormRef = ref();
  const revokeTarget = ref(null);
  const revokeForm = reactive({ reason: '' });

  const revokeRules = {
    reason: [{ required: true, message: '撤销必须写原因' }],
  };

  function openRevoke(record) {
    revokeTarget.value = record;
    revokeForm.reason = '';
    revokeOpen.value = true;
  }

  async function doRevoke() {
    try {
      await revokeFormRef.value.validate();
    } catch {
      return;
    }
    saving.value = true;
    try {
      const res = await securityApi.revokeList({
        id: revokeTarget.value.id,
        reason: revokeForm.reason,
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

  /* 弹窗顶上那条「在加 / 在撤什么」 */
  .modal-target {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-bottom: 16px;
    padding: 8px 12px;
    background: #fafafa;
    border-radius: 6px;
  }

  .modal-target-name {
    font-family: var(--font-family-monospace, monospace);
    font-weight: 600;
    word-break: break-all;
  }

  .modal-target-note {
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  .form-hint {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }
</style>
