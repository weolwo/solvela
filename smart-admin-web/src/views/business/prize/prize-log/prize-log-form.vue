<!--
  * 奖励记录 —— 人工补发
  *
  * 补发不是「改一条旧流水」，而是往前追加一条新的发奖流水，它本身就是一次真实发放的起点。
  *
  * 🔴 两步才算发出去：
  *   ① 本弹窗新建一条 approve_status=1（待审批）的记录 —— /prizeLog/add 是纯 insert，不派发任何东西；
  *   ② 回列表点「通过」走 /prizeLog/approve/{id}，那里才会跑 PrizeDispatchHandler.doDispatch，
  *      生成提案、扣预算、最后由账务落到钱包/券/发货单。
  * 所以这里把审批状态锁死成「待审批」，不给选 —— 落 0-无需审批只会造出一条永远不到账的死记录。
  *
  * @Author:    alaric
  * @Date:      2026-08-15
-->
<template>
  <a-modal
    title="人工补发奖励"
    :width="720"
    :open="visibleFlag"
    @cancel="onClose"
    :maskClosable="false"
    :destroyOnClose="true"
    :confirmLoading="saving"
  >
    <a-alert type="warning" show-icon class="mb-4">
      <template #message>
        <span class="text-xs leading-relaxed">
          补发会新建一条<b>待审批</b>的发奖记录，此刻还<b>不会</b>发出任何东西。
          回到列表点「通过」才会真正走派发链路（生成提案 → 扣减优惠配置预算 → 账务到账）。
        </span>
      </template>
    </a-alert>

    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <!-- 收会员号：账号可改，按名字补发迟早补给错的人 -->
      <a-form-item label="会员号" name="memberId">
        <a-input-number style="width: 100%" v-model:value="form.memberId" placeholder="补发给谁，填 10 位会员号" :controls="false" :precision="0" />
      </a-form-item>

      <a-form-item label="归属活动" name="activityCode">
        <a-select
          v-model:value="form.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          placeholder="请选择归属活动"
          show-search
          option-filter-prop="label"
          @change="onActivityChange"
        />
      </a-form-item>

      <!-- 奖品选定后，名称/类型/奖级/体值全部按奖品配置带出：这些字段是发放时的快照，
           手打极易和 t_prize_config 对不上，后面对账就成了一笔糊涂账 -->
      <a-form-item label="奖品" name="prizeCode">
        <a-select
          v-model:value="form.prizeCode"
          :options="prizeOptions"
          :loading="prizeLoading"
          :disabled="!form.activityCode"
          :placeholder="form.activityCode ? '请选择奖品' : '请先选择归属活动'"
          show-search
          option-filter-prop="label"
          @change="onPrizeChange"
        />
        <div v-if="form.activityCode && !prizeLoading && prizeOptions.length === 0" class="mt-1 text-xs text-orange-500">
          该活动下还没有启用中的奖品，请先到「活动中心 › 奖品配置」创建
        </div>
      </a-form-item>

      <!-- 选中奖品后的快照预览：让运营在提交前确认「补的到底是什么」 -->
      <a-form-item v-if="selectedPrize" :wrapper-col="{ offset: 5, span: 17 }">
        <div class="rounded-lg border border-solid border-slate-200 bg-slate-50/70 px-3 py-2">
          <a-descriptions size="small" :column="2" :label-style="{ color: '#94a3b8' }">
            <a-descriptions-item label="奖品名称">{{ form.prizeName }}</a-descriptions-item>
            <a-descriptions-item label="奖励类型">
              <a-tag>{{ prizeTypeOf(form.prizeType) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="奖品奖级">{{ form.prizeLevel }}</a-descriptions-item>
            <a-descriptions-item label="奖品编码">{{ form.prizeCode }}</a-descriptions-item>
          </a-descriptions>
        </div>
      </a-form-item>

      <a-form-item label="奖励体值" name="prizeValue">
        <a-input v-model:value="form.prizeValue" placeholder="积分数 / 券ID 等，默认取奖品配置的奖励价值" allow-clear />
        <div class="mt-1 text-xs text-slate-400">已按奖品配置带出，确需补发不同数额时才改它</div>
      </a-form-item>

      <a-form-item label="过期时间" name="validUntil">
        <a-date-picker
          v-model:value="form.validUntil"
          show-time
          valueFormat="YYYY-MM-DD HH:mm:ss"
          class="w-full"
          placeholder="不填表示不过期"
        />
      </a-form-item>

      <a-form-item label="补发原因" name="remark">
        <a-textarea v-model:value="form.remark" :rows="2" :maxlength="200" show-count placeholder="如：客诉赔付 #12345 / 活动配置失误导致漏发" />
      </a-form-item>

      <a-form-item label="幂等单号">
        <a-input v-model:value="form.externalBizNo" disabled />
        <div class="mt-1 text-xs text-slate-400">
          自动生成并落在唯一索引上，重复提交同一单号会被库直接挡下，避免手抖补两次
        </div>
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="onClose">取消</a-button>
        <a-button type="primary" :loading="saving" @click="onSubmit">提交待审批</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script setup>
  import { computed, nextTick, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { prizeLogApi } from '/@/api/business/prize/prize-log/prize-log-api';
  import { prizeConfigApi } from '/@/api/business/prize/prize-config/prize-config-api';
  import { activityConfigApi } from '/@/api/business/activity/activity-config/activity-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { PRIZE_TYPE_ENUM } from '/@/constants/business/prize/prize-config/prize-config-const';
  import { APPROVE_STATUS_ENUM, DISPATCH_STATUS_ENUM } from '/@/constants/business/prize/prize-log/prize-log-const';

  const emits = defineEmits(['reloadList']);

  const visibleFlag = ref(false);
  const saving = ref(false);
  const formRef = ref();

  function prizeTypeOf(value) {
    return Object.values(PRIZE_TYPE_ENUM).find((i) => i.value === value)?.desc || value || '-';
  }

  /**
   * 幂等单号：落在 t_prize_log 的 uk_external_biz 唯一索引上。
   * 每次打开弹窗生成一个，重复提交同一个会被数据库挡下 ——
   * 这条索引本来就是 PrizeDispatchHandler 用来防跨系统重复派发的，补发同样该受它保护。
   */
  function buildExternalBizNo() {
    const ts = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
    const rand = Math.random().toString(36).slice(2, 8).toUpperCase();
    return `MANUAL${ts}${rand}`;
  }

  const formDefault = {
    tenantId: '0',
    memberId: undefined,
    activityCode: undefined,
    prizeCode: undefined,
    prizeName: undefined,
    prizeType: undefined,
    prizeLevel: 0,
    prizeValue: undefined,
    validUntil: undefined,
    remark: undefined,
    externalBizNo: undefined,
    // 见文件头注释：这两个值刻意写死，不给运营选
    approveStatus: APPROVE_STATUS_ENUM.PENDING.value,
    status: DISPATCH_STATUS_ENUM.WAITING.value,
  };

  const form = reactive({ ...formDefault });

  const rules = {
    memberId: [{ required: true, message: '会员号 必填' }],
    activityCode: [{ required: true, message: '归属活动 必选' }],
    prizeCode: [{ required: true, message: '奖品 必选' }],
    prizeValue: [{ required: true, message: '奖励体值 必填' }],
    remark: [{ required: true, message: '补发原因 必填，这条记录将来是对账依据' }],
  };

  // ---------------------------- 活动 / 奖品下拉 ----------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);
  const prizeAllList = ref([]);
  const prizeLoading = ref(false);

  const prizeOptions = computed(() =>
    prizeAllList.value.map((item) => ({
      value: item.prizeCode,
      label: `${item.prizeName}（${item.prizeCode}）`,
    }))
  );

  const selectedPrize = computed(() => prizeAllList.value.find((i) => i.prizeCode === form.prizeCode));

  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList();
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      activityLoading.value = false;
    }
  }

  async function loadPrizeOptions(activityCode) {
    if (!activityCode) {
      prizeAllList.value = [];
      return;
    }
    prizeLoading.value = true;
    try {
      const res = await prizeConfigApi.optionList(activityCode);
      prizeAllList.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      prizeLoading.value = false;
    }
  }

  // 换活动就得换一批候选奖品，已选的一律清掉：跨活动的 prize_code 发不出去
  async function onActivityChange(activityCode) {
    form.prizeCode = undefined;
    clearPrizeSnapshot();
    await loadPrizeOptions(activityCode);
  }

  function clearPrizeSnapshot() {
    form.prizeName = undefined;
    form.prizeType = undefined;
    form.prizeLevel = 0;
    form.prizeValue = undefined;
  }

  // 选中奖品后把快照字段整套带出来
  function onPrizeChange() {
    const prize = selectedPrize.value;
    if (!prize) {
      clearPrizeSnapshot();
      return;
    }
    form.prizeName = prize.prizeName;
    form.prizeType = prize.prizeType;
    form.prizeLevel = prize.prizeLevel ?? 0;
    // prize_value 在奖品配置里是 decimal，流水表里是 varchar，统一转字符串再送
    form.prizeValue = prize.prizeValue == null ? undefined : String(prize.prizeValue);
    form.tenantId = prize.tenantId || '0';
  }

  // ---------------------------- 显示 / 提交 ----------------------------

  function show() {
    Object.assign(form, formDefault);
    form.externalBizNo = buildExternalBizNo();
    prizeAllList.value = [];
    loadActivityOptions();
    visibleFlag.value = true;
    nextTick(() => formRef.value?.clearValidate());
  }

  function onClose() {
    Object.assign(form, formDefault);
    visibleFlag.value = false;
  }

  async function onSubmit() {
    try {
      await formRef.value.validateFields();
    } catch (e) {
      message.error('参数验证错误，请仔细填写表单数据!');
      return;
    }
    saving.value = true;
    try {
      await prizeLogApi.add({ ...form });
      message.success('已生成待审批的补发记录，请在列表中点「通过」完成发放');
      emits('reloadList');
      onClose();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  defineExpose({ show });
</script>
