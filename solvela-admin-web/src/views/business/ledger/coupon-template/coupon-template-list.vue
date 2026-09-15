<!--
  * 券模板
  *
  * 🔴 这个页面最要紧的一件事：让运营明白「编辑 = 新增一版，而且不影响已发出去的券」。
  *
  * 券的规则是【发券那一刻快照】进 t_member_coupon 的，核销读的是会员券行，
  * 根本不读这张模板表。所以：
  *   · 改模板不会让用户手里的券变值 —— 这正是它必须不可变的原因。
  *     如果核销读模板当前值，运营把「满100减20」改成「满200减20」，
  *     用户手里那张券就在他不知情的时候贬值了。那不是显示问题，是资损与信任问题；
  *   · 也因此改了模板【只对之后发的券生效】，运营常常会误以为立刻全场生效，
  *     所以页面上必须把这句话说出来。
  *
  * 服务端同样没有 update、没有 delete，只有 save（新增下一版）和 disable。
  *
  * @Author:    alaric
  * @Date:      2026-09-15
  * @Copyright  weolwo
-->
<template>
  <a-alert type="info" show-icon class="mb-4">
    <template #message> 模板<b>按版本不可变</b>：保存不会改动当前这一版，而是新增下一版。 </template>
    <template #description>
      发券时会把规则<b>快照</b>到用户的券上，核销读的是那份快照。所以改模板
      <b>只影响之后发出去的券</b>，已经在用户手里的券规则不变 —— 否则用户的券会在他不知情时贬值。
      老版本因此必须留着，只能「停用」不能删除。
    </template>
  </a-alert>

  <!--
    体检：会发券但没有模板的配置点。

    🔴 发券侧找不到模板时是【照发】的，只是规则列全空 —— 因为拒发会在运行期
       把一个在架商品变成兑换必失败（库里就有这种商品：兑换凭证类的券本来
       就没有「减多少」这回事）。但降级如果没人看得见，就成了这个项目一直在
       骂的那种「不报错，只是没生效」：券照发、用户照收，直到有人拿它去抵扣
       才发现减不出钱。日志里那条 ERROR 要有人去翻才看得到，所以摆在这里。
  -->
  <a-alert v-if="gaps.length" type="error" show-icon class="mb-4">
    <template #message>
      有 {{ gaps.length }} 个配置点会发券，但<b>没有券模板</b> —— 它们发出去的券没有任何规则，用不了。
    </template>
    <template #description>
      <div v-for="gap in gaps" :key="gap.sourceType + gap.sourceCode" class="text-sm">
        <a-tag :color="gap.sourceType === 'PRIZE' ? 'purple' : 'blue'">
          {{ gap.sourceType === 'PRIZE' ? '奖品配置' : '商城商品' }}
        </a-tag>
        <b>{{ gap.sourceName }}</b>
        <span class="text-slate-500">（{{ gap.sourceCode }}）</span>
        → 券编码 <code>{{ gap.couponCode }}</code>
      </div>
      <div class="mt-2 text-xs text-slate-500">
        ⚠️ 如果它发的是<b>兑换凭证</b>（比如视频会员年卡）而不是折扣券，那本来就没有「减多少」这回事，
        可以不管 —— 但要知道它在这张单子上。
      </div>
    </template>
  </a-alert>

  <a-card size="small" :bordered="false" :bodyStyle="{ padding: '16px' }">
    <a-row justify="end" class="mb-3">
      <a-button type="primary" @click="onAdd">
        <template #icon><PlusOutlined /></template>
        新增券模板
      </a-button>
    </a-row>

    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      :loading="tableLoading"
      rowKey="couponCode"
      :pagination="false"
      bordered
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'version'">
          <a-tag color="blue">v{{ record.version }}</a-tag>
        </template>

        <template v-if="column.dataIndex === 'rule'">
          <!-- 把「减多少」拼成一句人话。此前这条规则唯一活着的地方是券的名字 -->
          <span>{{ describeRule(record) }}</span>
        </template>

        <template v-if="column.dataIndex === 'deductTarget'">
          <a-tag :color="record.deductTarget === 'SCORE' ? 'gold' : 'green'">
            {{ DEDUCT_TARGET_LABEL[record.deductTarget] ?? record.deductTarget }}
          </a-tag>
        </template>

        <template v-if="column.dataIndex === 'scopeType'">
          {{ SCOPE_TYPE_LABEL[record.scopeType] ?? record.scopeType }}
          <a-tooltip v-if="record.scopeRefs" :title="record.scopeRefs">
            <a-tag class="ml-1">明细</a-tag>
          </a-tooltip>
        </template>

        <template v-if="column.dataIndex === 'validity'">
          {{ describeValidity(record) }}
        </template>

        <template v-if="column.dataIndex === 'action'">
          <a-space>
            <a @click="onEdit(record)">基于此版新建</a>
            <a @click="onViewVersions(record)">历史版本</a>
            <a-popconfirm
              title="停用后不再用它发新券，但已经发出去的券照常能用（它们带着自己的规则快照）。确定？"
              @confirm="onDisable(record)"
            >
              <a class="text-red-500">停用</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>

  <!-- 新增 / 基于某版新建 -->
  <a-drawer
    :title="editingBase ? `新增版本：${form.couponCode}` : '新增券模板'"
    :width="760"
    :open="drawerOpen"
    :maskClosable="false"
    :destroyOnClose="true"
    @close="drawerOpen = false"
  >
    <a-alert
      type="warning"
      show-icon
      class="mb-4"
      message="保存会新增一个版本，不会改动任何已有版本，也不会改变已经发出去的券。"
    />

    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="券编码" name="couponCode">
        <a-input v-model:value="form.couponCode" placeholder="如 0ZXXLZ0RZ1" :disabled="Boolean(editingBase)" />
        <div class="mt-1 text-xs text-slate-500">
          发券时引用它。若这张券同时是抽奖奖品，<b>必须和奖品配置的 prize_code 一致</b>，
          否则发出去的券匹配不到模板。
        </div>
      </a-form-item>

      <a-form-item label="券名称" name="couponName">
        <a-input v-model:value="form.couponName" placeholder="如 满100减20优惠券" />
        <div class="mt-1 text-xs text-slate-500">用户在券包里看到的就是这个名字。</div>
      </a-form-item>

      <a-form-item label="抵扣方式" name="discountType">
        <a-radio-group v-model:value="form.discountType" :options="DISCOUNT_TYPE_OPTIONS" />
      </a-form-item>

      <a-form-item :label="form.discountType === 'PERCENT' ? '折扣率(%)' : '抵扣金额'" name="discountValue">
        <a-input-number
          v-model:value="form.discountValue"
          :min="0.01"
          :max="form.discountType === 'PERCENT' ? 99 : undefined"
          :precision="2"
          style="width: 100%"
          :placeholder="form.discountType === 'PERCENT' ? '20 表示减 20%' : '如 20 表示减 20'"
        />
        <div v-if="form.discountType === 'PERCENT'" class="mt-1 text-xs text-slate-500">
          填 20 表示<b>减 20%</b>（即打 8 折）。上限 99 —— 100 是免单，不该用券表达。
        </div>
      </a-form-item>

      <a-form-item label="最低消费门槛" name="minAmount">
        <a-input-number v-model:value="form.minAmount" :min="0" :precision="2" style="width: 100%" />
        <div class="mt-1 text-xs text-slate-500">0 表示无门槛。订单金额低于它这张券用不了。</div>
      </a-form-item>

      <a-form-item label="最高抵扣" name="maxDiscount">
        <a-input-number v-model:value="form.maxDiscount" :min="0.01" :precision="2" style="width: 100%" />
        <!--
          🔴 这是整个券模块最容易造成资损、也最容易被忽略的一条。
             不封顶的「8 折」碰上一台 7999 的手机就是减 1600 ——
             不报错、不告警，只是那一单少收了 1600 块。
        -->
        <div v-if="form.discountType === 'PERCENT'" class="mt-1 text-xs text-red-500">
          百分比券<b>必须封顶</b>：不设上限的「8 折」碰上一台 7999 的手机就是减 1600，
          系统不会报错，只是那一单少收了 1600。
        </div>
        <div v-else class="mt-1 text-xs text-slate-500">固定金额券可不填。</div>
      </a-form-item>

      <a-form-item label="抵扣对象" name="deductTarget">
        <a-radio-group v-model:value="form.deductTarget" :options="DEDUCT_TARGET_OPTIONS" />
        <div class="mt-1 text-xs text-slate-500">
          🔴 现金和积分<b>不可比</b>：1 积分 ≠ 1 元，而汇率是业务定义、还会变。
          所以一张券只抵一种，试算时也不会跨类比「哪张更划算」。
        </div>
      </a-form-item>

      <a-form-item label="适用范围" name="scopeType">
        <a-select v-model:value="form.scopeType" :options="SCOPE_TYPE_OPTIONS" />
      </a-form-item>

      <a-form-item v-if="form.scopeType && form.scopeType !== 'ALL'" label="范围明细" name="scopeRefs">
        <a-select v-model:value="scopeRefList" mode="tags" placeholder="回车添加，如商品 id / 类目编码" />
        <div class="mt-1 text-xs text-slate-500">
          ⚠️ 尽量用「指定类目」而不是「指定商品」：绑商品 id 的话，每上一个新商品都要
          回头改所有相关的券，而漏改<b>不报错</b> —— 只是那张券在新商品上莫名其妙用不了。
        </div>
      </a-form-item>

      <a-form-item label="有效期方式" name="validityMode">
        <!--
          做成二选一的单选，而不是两个都放出来让人填：两个都填的话
          「哪个说了算」就会变成一个没人记得的约定。服务端也会拦。
        -->
        <a-radio-group v-model:value="validityMode">
          <a-radio value="DAYS">发券后 N 天过期</a-radio>
          <a-radio value="FIXED">固定失效时间</a-radio>
        </a-radio-group>
      </a-form-item>

      <a-form-item v-if="validityMode === 'DAYS'" label="有效天数" name="validDays">
        <a-input-number v-model:value="form.validDays" :min="1" :precision="0" style="width: 100%" />
      </a-form-item>

      <a-form-item v-else label="失效时间" name="validEndTime">
        <a-date-picker
          v-model:value="form.validEndTime"
          show-time
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
        <div class="mt-1 text-xs text-slate-500">用于活动券：不管什么时候发的，到这个点一起失效。</div>
      </a-form-item>

      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" placeholder="给运营自己看的" />
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="drawerOpen = false">取消</a-button>
        <a-button type="primary" :loading="submitting" @click="onSubmit">保存为新版本</a-button>
      </a-space>
    </template>
  </a-drawer>

  <!-- 历史版本 -->
  <a-drawer :title="`历史版本：${versionCode}`" :width="680" :open="versionOpen" @close="versionOpen = false">
    <a-alert
      type="info"
      show-icon
      class="mb-4"
      message="老版本必须留着：用户拿着一张两年前发的券来问「为什么只减了 20」，答案只在这里。"
    />
    <a-timeline>
      <a-timeline-item v-for="item in versionList" :key="item.version" :color="item.status === 1 ? 'blue' : 'gray'">
        <p>
          <a-tag :color="item.status === 1 ? 'blue' : 'default'">v{{ item.version }}</a-tag>
          <span class="text-xs text-slate-500">{{ item.createTime }}</span>
          <a-tag v-if="item.status !== 1" color="default" class="ml-1">已停用</a-tag>
        </p>
        <p class="font-medium">{{ item.couponName }}</p>
        <p class="text-sm text-slate-600">{{ describeRule(item) }} · {{ describeValidity(item) }}</p>
        <p v-if="item.remark" class="text-xs text-slate-500">{{ item.remark }}</p>
      </a-timeline-item>
    </a-timeline>
    <a-empty v-if="versionList.length === 0" description="没有历史版本" />
  </a-drawer>
</template>

<script setup>
  import { ref, reactive, computed, onMounted } from 'vue';
  import { message } from 'ant-design-vue';
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { couponTemplateApi } from '/@/api/business/ledger/coupon-template-api';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  /** 与后端 CouponDiscountTypeEnum 对齐 */
  const DISCOUNT_TYPE_LABEL = { FIXED: '固定金额', PERCENT: '百分比' };
  /** 与后端 CouponDeductTargetEnum 对齐 */
  const DEDUCT_TARGET_LABEL = { CASH: '现金', SCORE: '积分' };
  /** 与后端 CouponScopeTypeEnum 对齐 */
  const SCOPE_TYPE_LABEL = {
    ALL: '全场通用',
    COMMODITY: '指定商品',
    CATEGORY: '指定类目',
    EXTERNAL: '外部场景（如充话费）',
  };

  const toOptions = (labelMap) => Object.entries(labelMap).map(([value, label]) => ({ value, label }));

  const DISCOUNT_TYPE_OPTIONS = toOptions(DISCOUNT_TYPE_LABEL);
  const DEDUCT_TARGET_OPTIONS = toOptions(DEDUCT_TARGET_LABEL);
  const SCOPE_TYPE_OPTIONS = toOptions(SCOPE_TYPE_LABEL);

  const columns = [
    { title: '券编码', dataIndex: 'couponCode', width: 150 },
    { title: '当前版本', dataIndex: 'version', width: 90 },
    { title: '券名称', dataIndex: 'couponName', width: 180 },
    { title: '规则', dataIndex: 'rule' },
    { title: '抵扣', dataIndex: 'deductTarget', width: 90 },
    { title: '适用范围', dataIndex: 'scopeType', width: 150 },
    { title: '有效期', dataIndex: 'validity', width: 170 },
    { title: '操作', dataIndex: 'action', width: 230 },
  ];

  const tableData = ref([]);
  const tableLoading = ref(false);

  async function loadList() {
    tableLoading.value = true;
    try {
      tableData.value = (await couponTemplateApi.list()) || [];
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      tableLoading.value = false;
    }
  }

  /** 会发券但没模板的配置点。见模板顶部那段红字 */
  const gaps = ref([]);

  async function loadGaps() {
    try {
      gaps.value = (await couponTemplateApi.missing()) || [];
    } catch (err) {
      // 体检失败不该挡住主列表 —— 它是提示，不是这个页面的主体
      solvelaSentry.captureError(err);
      gaps.value = [];
    }
  }

  onMounted(() => {
    void loadList();
    void loadGaps();
  });

  /**
   * 把规则拼成一句人话。
   *
   * 🔴 这一列存在本身就是这个阶段的意义：在有 t_coupon_template 之前，
   * 「满100减20」这条规则在系统里唯一活着的地方是【券的名字字符串】。
   */
  function describeRule(record) {
    const target = DEDUCT_TARGET_LABEL[record.deductTarget] ?? '';
    const threshold = Number(record.minAmount) > 0 ? `满 ${record.minAmount} ` : '无门槛 ';

    if (record.discountType === 'PERCENT') {
      const cap = record.maxDiscount ? `，最高减 ${record.maxDiscount} ${target}` : '';
      return `${threshold}减 ${record.discountValue}%${cap}`;
    }
    return `${threshold}减 ${record.discountValue} ${target}`;
  }

  function describeValidity(record) {
    if (record.validDays) {
      return `发券后 ${record.validDays} 天`;
    }
    if (record.validEndTime) {
      return `至 ${record.validEndTime}`;
    }
    // 服务端拦着不让两个都不填，所以这行理论上看不到。真看到了说明是脏数据
    return '未设置（异常）';
  }

  // ------------------------ 新增 / 新建版本 ------------------------

  const drawerOpen = ref(false);
  const submitting = ref(false);
  const formRef = ref();
  const editingBase = ref(null);
  const scopeRefList = ref([]);
  const validityMode = ref('DAYS');

  const formDefault = {
    couponCode: undefined,
    couponName: undefined,
    discountType: 'FIXED',
    discountValue: undefined,
    minAmount: 0,
    maxDiscount: undefined,
    deductTarget: 'CASH',
    scopeType: 'ALL',
    scopeRefs: undefined,
    validDays: undefined,
    validEndTime: undefined,
    remark: undefined,
    status: 1,
  };

  const form = reactive({ ...formDefault });

  const rules = computed(() => ({
    couponCode: [{ required: true, message: '券编码 必填' }],
    couponName: [{ required: true, message: '券名称 必填' }],
    discountType: [{ required: true, message: '抵扣方式 必选' }],
    discountValue: [{ required: true, message: '抵扣值 必填' }],
    deductTarget: [{ required: true, message: '抵扣对象 必选' }],
    scopeType: [{ required: true, message: '适用范围 必选' }],
    // 百分比券必须封顶 —— 和服务端 CouponTemplateService.validate 是同一条规则。
    // 前端拦是为了当场说清楚原因，服务端拦是因为前端拦不住绕过页面的调用
    maxDiscount: [{ required: form.discountType === 'PERCENT', message: '百分比券必须填「最高抵扣」' }],
    validDays: [{ required: validityMode.value === 'DAYS', message: '有效天数 必填' }],
    validEndTime: [{ required: validityMode.value === 'FIXED', message: '失效时间 必填' }],
  }));

  function onAdd() {
    Object.assign(form, formDefault);
    editingBase.value = null;
    scopeRefList.value = [];
    validityMode.value = 'DAYS';
    drawerOpen.value = true;
  }

  /** 「基于此版新建」：把当前版本带进来当草稿，运营改完保存就是下一版 */
  function onEdit(record) {
    Object.assign(form, formDefault, {
      couponCode: record.couponCode,
      couponName: record.couponName,
      discountType: record.discountType,
      discountValue: toNumber(record.discountValue),
      minAmount: toNumber(record.minAmount) ?? 0,
      maxDiscount: toNumber(record.maxDiscount),
      deductTarget: record.deductTarget,
      scopeType: record.scopeType,
      validDays: record.validDays,
      validEndTime: record.validEndTime,
      remark: record.remark,
    });
    editingBase.value = record;
    scopeRefList.value = parseRefs(record.scopeRefs);
    validityMode.value = record.validEndTime ? 'FIXED' : 'DAYS';
    drawerOpen.value = true;
  }

  function toNumber(raw) {
    return raw === null || raw === undefined || raw === '' ? undefined : Number(raw);
  }

  /** scope_refs 是 json 文本。解析失败按空处理 —— 为一列展示把整页卡住不值得 */
  function parseRefs(raw) {
    if (!raw) {
      return [];
    }
    try {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }

  async function onSubmit() {
    try {
      await formRef.value.validateFields();
    } catch (err) {
      message.error('参数验证错误，请仔细填写表单数据!');
      return;
    }

    submitting.value = true;
    SolvelaLoading.show();
    try {
      const params = {
        ...form,
        // 有效期两种表达【二选一】。没选中的那个必须显式清空，
        // 否则「基于此版新建」时切换了方式，旧值会跟着一起提上去，服务端直接拒
        validDays: validityMode.value === 'DAYS' ? form.validDays : null,
        validEndTime: validityMode.value === 'FIXED' ? form.validEndTime : null,
        scopeRefs: form.scopeType !== 'ALL' && scopeRefList.value.length ? JSON.stringify(scopeRefList.value) : null,
        // 版本号刻意不传：由服务端算「当前最大版本 + 1」。
        // 前端传的话，两个人同时编辑就会撞版本号
        version: undefined,
      };
      const version = await couponTemplateApi.save(params);
      message.success(`已保存为 v${version}（只对之后发出去的券生效）`);
      drawerOpen.value = false;
      await loadList();
      // 刚补的模板可能正好填上了体检里的某个洞
      await loadGaps();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      submitting.value = false;
      SolvelaLoading.hide();
    }
  }

  async function onDisable(record) {
    SolvelaLoading.show();
    try {
      await couponTemplateApi.disable(record.couponCode, record.version);
      message.success('已停用');
      await loadList();
      // 停掉最后一个启用版 = 又多了一个没有模板的发券点
      await loadGaps();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ------------------------ 历史版本 ------------------------

  const versionOpen = ref(false);
  const versionCode = ref('');
  const versionList = ref([]);

  async function onViewVersions(record) {
    versionCode.value = record.couponCode;
    versionOpen.value = true;
    try {
      versionList.value = (await couponTemplateApi.versions(record.couponCode)) || [];
    } catch (err) {
      solvelaSentry.captureError(err);
      versionList.value = [];
    }
  }
</script>
