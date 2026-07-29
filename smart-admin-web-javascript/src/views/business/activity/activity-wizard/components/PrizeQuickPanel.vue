<!--
  * 奖品快速创建面板（活动创建向导右侧）
  *
  * 解决的问题：新建活动的资产大库必然是空的，而抽奖/彩票的玩法配置都要从资产大库引入奖项 ——
  * 运营建完活动进到第二步，只会看到「本活动的资产大库为空，请先到『奖品配置』创建」，
  * 然后不得不跳出向导。这个面板把那一步就地做掉。
  *
  * 两种模式：
  * ① 草稿态（activityCode 为空，向导第一步）：奖品在本地攒着，随活动一起提交给
  *    /activityConfig/wizard/create 一次事务落库 —— 活动都还没有，奖品挂不上去。
  * ② 直连态（activityCode 有值，向导第二步 / 继续配置）：活动已存在，直接调 /prizeConfig/add。
  *
  * 交互范式对齐彩票的 IssueConsole「➕ 新增 → 行内表单 → 保存」，那套已经浏览器实测过。
  *
  * @Author:    alaric
  * @Date:      2026-07-29
-->
<template>
  <div class="flex flex-col h-full">
    <div class="flex items-center justify-between mb-3">
      <div>
        <span class="text-sm font-bold">🎁 活动奖品</span>
        <span class="text-xs text-slate-400 ml-2">共 {{ prizeList.length }} 个</span>
      </div>
      <a-button size="small" type="primary" ghost :disabled="adding" @click="startAdd">➕ 新增奖品</a-button>
    </div>

    <a-alert v-if="!activityCode" type="info" show-icon banner class="mb-3">
      <template #message>
        <span class="text-xs">奖品会随活动一起创建。抽奖/彩票玩法需要先有奖品才能配置，建议在此一并建好。</span>
      </template>
    </a-alert>

    <!-- 新增表单：行内展开，不弹窗 —— 奖品只有 4 个要填的字段，弹窗的开销大过收益 -->
    <a-card v-if="adding" size="small" class="mb-3 border-blue-200 bg-blue-50/40">
      <a-form ref="formRef" :model="draft" :rules="rules" layout="vertical" size="small">
        <a-form-item label="奖品名称" name="prizeName" class="mb-2">
          <a-input v-model:value="draft.prizeName" placeholder="如：iPhone 15 Pro" :maxlength="64" />
        </a-form-item>

        <div class="grid grid-cols-2 gap-2">
          <a-form-item label="资产类型" name="prizeType" class="mb-2">
            <a-select v-model:value="draft.prizeType" :options="PRIZE_TYPE_OPTIONS" placeholder="请选择" @change="onTypeChange" />
          </a-form-item>
          <a-form-item label="奖励价值" name="prizeValue" class="mb-2">
            <a-input-number v-model:value="draft.prizeValue" class="w-full" :min="0" :precision="2" placeholder="0.00" />
          </a-form-item>
        </div>

        <!--
          优惠配置承载预算与风控，是 NOT NULL；且服务端会重算「它的资产类型必须与奖品一致」——
          积分奖品挂了优惠券的配置，用户实际会收到一张券，所以这里只能按类型级联，不能自由选。
        -->
        <a-form-item label="优惠配置（承载预算与风控）" name="promotionConfigId" class="mb-2">
          <a-select
            v-model:value="draft.promotionConfigId"
            :options="filteredPromotionOptions"
            :loading="promotionLoading"
            :placeholder="draft.prizeType ? '请选择优惠配置' : '请先选择资产类型'"
            :disabled="!draft.prizeType"
          />
          <div v-if="draft.prizeType && !filteredPromotionOptions.length && !promotionLoading" class="text-xs text-red-500 mt-1">
            该资产类型下暂无可用的优惠配置，请先到「风控管理 › 优惠配置」创建一个，否则奖品发不出去。
          </div>
        </a-form-item>

        <a-form-item label="审批模式" name="approveMode" class="mb-2">
          <a-radio-group v-model:value="draft.approveMode" size="small">
            <a-radio-button :value="0">自动免审</a-radio-button>
            <a-radio-button :value="1">人工审批</a-radio-button>
          </a-radio-group>
        </a-form-item>

        <div class="flex justify-end gap-2 mt-2">
          <a-button size="small" @click="cancelAdd">取消</a-button>
          <a-button size="small" type="primary" :loading="saving" @click="confirmAdd">确认添加</a-button>
        </div>
      </a-form>
    </a-card>

    <!-- 奖品列表：数量多时在面板内滚动，不把整页撑长 -->
    <div class="flex-1 overflow-y-auto pr-1" style="max-height: 420px">
      <a-empty v-if="!prizeList.length" :image="simpleImage" class="py-8">
        <template #description>
          <span class="text-xs text-slate-400">还没有奖品，点右上角「新增奖品」</span>
        </template>
      </a-empty>

      <div
        v-for="(p, idx) in prizeList"
        :key="p.prizeCode"
        class="flex items-center gap-2 px-3 py-2 mb-2 bg-white border border-slate-200 rounded hover:border-blue-300"
      >
        <span class="text-lg">{{ typeIcon(p.prizeType) }}</span>
        <div class="flex-1 min-w-0">
          <div class="text-sm font-medium truncate">{{ p.prizeName }}</div>
          <div class="text-[11px] text-slate-400 font-mono truncate">
            {{ p.prizeCode }} · {{ p.prizeType }} · ¥{{ p.prizeValue }}
            <a-tag v-if="p.approveMode === 1" color="orange" class="ml-1 scale-90">需审批</a-tag>
          </div>
        </div>
        <!-- 已落库的奖品不允许在这里删：它可能已被奖池/奖级引用，删除要走「奖品配置」的完整校验 -->
        <a-button v-if="!activityCode" type="link" size="small" danger class="px-1" @click="removeDraft(idx)">移除</a-button>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { Empty, message } from 'ant-design-vue';
  import { prizeConfigApi } from '/@/api/business/prize/prize-config/prize-config-api';
  import { promotionConfigApi } from '/@/api/business/risk/promotion-config/promotion-config-api';
  import { PRIZE_TYPE_OPTIONS } from '/@/constants/business/prize/prize-config/prize-config-const';
  import { smartSentry } from '/@/lib/smart-sentry';

  const props = defineProps({
    /**
     * 活动编码。
     * 为空 = 草稿态（向导第一步，活动尚未创建，奖品本地攒着）；
     * 有值 = 直连态（活动已存在，新增即落库）。
     */
    activityCode: { type: String, default: '' },
  });

  const emit = defineEmits(['change']);

  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

  const PRIZE_TYPE_ICON = { SCORE: '⭐', BALANCE: '💰', COUPON: '🎟️', PHYSICAL: '📦', LOTTERY: '🎰', CUSTOM: '🎨' };
  const typeIcon = (t) => PRIZE_TYPE_ICON[t] || '🎁';

  const prizeList = ref([]);
  const adding = ref(false);
  const saving = ref(false);
  const formRef = ref();

  const draft = reactive({
    prizeCode: '',
    prizeName: '',
    prizeType: undefined,
    prizeValue: undefined,
    promotionConfigId: undefined,
    approveMode: 0,
  });

  const rules = {
    prizeName: [{ required: true, message: '请输入奖品名称' }],
    prizeType: [{ required: true, message: '请选择资产类型' }],
    prizeValue: [{ required: true, message: '请输入奖励价值', type: 'number' }],
    promotionConfigId: [{ required: true, message: '请选择优惠配置', type: 'number' }],
    approveMode: [{ required: true, message: '请选择审批模式', type: 'number' }],
  };

  // ---------------------------- 优惠配置：一次拉全量，本地按类型分组 ----------------------------

  const promotionOptions = ref([]);
  const promotionLoading = ref(false);

  async function loadPromotionOptions() {
    promotionLoading.value = true;
    try {
      const res = await promotionConfigApi.optionList();
      promotionOptions.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      promotionLoading.value = false;
    }
  }

  // 配置总量不大，一次拉全量本地过滤，省掉「切一次类型打一次接口」
  const filteredPromotionOptions = computed(() =>
    promotionOptions.value
      .filter((p) => p.prizeType === draft.prizeType)
      .map((p) => ({
        value: p.id,
        label: `${p.promoName}｜预算 ${p.usedAmount}/${p.totalAmount === '-1.0000' ? '不限' : p.totalAmount}`,
      }))
  );

  // 换类型时清掉已选的优惠配置：留着上一个类型的选项，服务端会以「类型不一致」打回来
  function onTypeChange() {
    draft.promotionConfigId = undefined;
  }

  // ---------------------------- 新增 ----------------------------

  async function startAdd() {
    Object.assign(draft, {
      prizeCode: '',
      prizeName: '',
      prizeType: undefined,
      prizeValue: undefined,
      promotionConfigId: undefined,
      approveMode: 0,
    });
    adding.value = true;
    // 编码不让运营手打：它是随机码，手打没有意义还容易撞车（铁律 8）
    try {
      const res = await prizeConfigApi.generateCode();
      draft.prizeCode = res.data;
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  function cancelAdd() {
    adding.value = false;
  }

  async function confirmAdd() {
    try {
      await formRef.value.validate();
    } catch (e) {
      return;
    }
    const item = { ...draft };

    if (!props.activityCode) {
      // 草稿态：本地攒着，随活动一起提交
      prizeList.value.push(item);
      adding.value = false;
      emit('change', prizeList.value);
      return;
    }

    // 直连态：活动已存在，直接落库
    saving.value = true;
    try {
      await prizeConfigApi.add({ ...item, activityCode: props.activityCode, status: 1 });
      message.success('奖品已创建');
      adding.value = false;
      await loadExisting();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  function removeDraft(idx) {
    prizeList.value.splice(idx, 1);
    emit('change', prizeList.value);
  }

  // ---------------------------- 直连态：回显该活动已有的奖品 ----------------------------

  async function loadExisting() {
    if (!props.activityCode) {
      return;
    }
    try {
      const res = await prizeConfigApi.optionList(props.activityCode);
      prizeList.value = (res.data || []).map((p) => ({
        prizeCode: p.prizeCode,
        prizeName: p.prizeName,
        prizeType: p.prizeType,
        prizeValue: p.prizeValue,
        approveMode: p.approveMode,
      }));
      emit('change', prizeList.value);
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  // 活动编码变化（草稿态 → 直连态，或换了活动）时重新回显
  watch(() => props.activityCode, loadExisting);

  onMounted(() => {
    loadPromotionOptions();
    loadExisting();
  });

  defineExpose({
    /** 供向导提交时取草稿态的奖品列表 */
    getPrizeList: () => prizeList.value,
  });
</script>
