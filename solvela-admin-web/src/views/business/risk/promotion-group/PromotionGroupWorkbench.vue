<!--
  * 优惠配置工作台 —— 一页配完一个分组下所有资产类型的预算与风控
  *
  * 【它省掉的是什么】
  * 预算与风控是按资产类型分开算的（used_amount 混不了积分和元），所以一个活动
  * 有几种奖励类型就得有几条 t_promotion_config。真正的痛点不是「要建 N 条」，
  * 是「N 条里 90% 的字段要重填 N 遍」—— 所以这一页的核心是下面那个「一键铺底」。
  *
  * 【它不改什么】
  * 分组只是配置入口。t_prize_config 关联的仍是具体那条优惠配置，
  * 发奖链路从头到尾不认识分组。
  *
  * @Author:    alaric
  * @Date:      2026-08-30
-->
<template>
  <div class="promotion-workbench">
    <a-card size="small" :bordered="false" :hoverable="true" class="mb-2">
      <template #title>
        <a-button type="link" class="pl-0" @click="goBack">
          <template #icon><ArrowLeftOutlined /></template>
          返回分组列表
        </a-button>
      </template>
      <template #extra>
        <a-space>
          <a-tag v-if="form.groupCode" color="blue">{{ form.groupCode }}</a-tag>
          <a-button type="primary" :loading="saving" @click="onSave">保存</a-button>
        </a-space>
      </template>

      <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="分组名称" required>
              <!--
                刻意没有「关联活动」：优惠配置是可跨活动复用的预算池，
                表上没有也不该有活动关联列。要表达归属，写在名字里。
              -->
              <a-input v-model:value="form.groupName" placeholder="如：2026中秋活动优惠配置" :maxlength="128" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="分组状态">
              <a-switch
                v-model:checked="groupEnabled"
                checked-children="启用"
                un-checked-children="停用"
                @change="onGroupSwitch"
              />
              <span class="ml-3 text-xs text-slate-400">
                {{ groupEnabled ? '各类型可单独控制' : '停用会连带停发组内所有类型' }}
              </span>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="备注" :label-col="{ span: 2 }" :wrapper-col="{ span: 21 }">
          <a-input v-model:value="form.remark" placeholder="选填" :maxlength="512" />
        </a-form-item>
      </a-form>
    </a-card>

    <!--
      一键铺底：这一页真正的价值所在。
      填一份公共风控参数、勾选要生成的类型，一次铺到所有卡上，再单独微调差异项。
      不铺预算 —— 预算是每种类型都不一样的那一个字段，铺过去只会让人以为已经配好了。
    -->
    <a-card size="small" :bordered="false" :hoverable="true" class="mb-2">
      <template #title>
        <span>一键铺底</span>
        <!--
          预算/库存刻意不进铺底：那是每种类型都不同的字段，
          铺一个统一值过去，运营会以为已经配好了而不再逐个确认。
        -->
        <span class="ml-2 text-xs font-normal text-slate-400">公共风控参数一次铺到勾选的类型，预算需逐个填</span>
      </template>
      <a-form layout="inline">
        <a-form-item label="铺到">
          <a-checkbox-group v-model:value="fillTypes">
            <a-checkbox v-for="t in GROUPABLE_PRIZE_TYPES" :key="t.value" :value="t.value">{{ t.desc }}</a-checkbox>
          </a-checkbox-group>
        </a-form-item>
        <a-form-item label="审核层级">
          <a-select v-model:value="fillTemplate.reviewLevel" :options="REVIEW_LEVEL_OPTIONS" style="width: 130px" />
        </a-form-item>
        <a-form-item label="一审阈值">
          <a-input-number v-model:value="fillTemplate.firstReviewThreshold" :min="0" style="width: 110px" />
        </a-form-item>
        <a-form-item label="限制周期">
          <a-select v-model:value="fillTemplate.limitPeriod" :options="LIMIT_PERIOD_OPTIONS" style="width: 120px" />
        </a-form-item>
        <a-form-item v-if="fillTemplate.limitPeriod === LIMIT_PERIOD_ENUM.CUSTOM.value" label="窗口">
          <a-range-picker
            v-model:value="fillRange"
            :show-time="RANGE_SHOW_TIME"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="MM-DD HH:mm"
            style="width: 300px"
          />
        </a-form-item>
        <a-form-item label="单会员限领">
          <a-input-number v-model:value="fillTemplate.identifyLimit" :min="-1" :precision="0" style="width: 100px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" ghost :disabled="!fillTypes.length" @click="applyFill">铺到勾选的类型</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-row :gutter="12">
      <a-col v-for="item in form.itemList" :key="item.prizeType" :xs="24" :sm="12" :xl="6" class="mb-2">
        <PrizeTypeRiskCard
          :item="item"
          :group-disabled="!groupEnabled"
          @configure="onConfigure(item)"
          @toggle="(checked) => onToggleItem(item, checked)"
        />
      </a-col>
    </a-row>
  </div>
</template>
<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { useRoute, useRouter } from 'vue-router';
  import { message } from 'ant-design-vue';
  import { ArrowLeftOutlined } from '@ant-design/icons-vue';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { RANGE_SHOW_TIME } from '/@/constants/date-time-const';
  import { promotionGroupApi } from '/src/api/business/risk/promotion-group-api';
  import { GROUPABLE_PRIZE_TYPES } from '/src/constants/business/risk/promotion-group-const';
  import { PRIZE_TYPE_ENUM } from '/src/constants/business/prize/prize-config-const';
  import {
    LIMIT_PERIOD_ENUM,
    LIMIT_PERIOD_OPTIONS,
    QUOTA_BASED_PRIZE_TYPES,
    REVIEW_LEVEL_ENUM,
    REVIEW_LEVEL_OPTIONS,
    UNLIMITED,
  } from '/src/constants/business/risk/promotion-config-const';
  import PrizeTypeRiskCard from './components/PrizeTypeRiskCard.vue';

  const route = useRoute();
  const router = useRouter();

  const ENABLED = 1;
  const DISABLED = 0;

  const saving = ref(false);

  const form = reactive({
    id: undefined,
    groupCode: undefined,
    groupName: undefined,
    remark: undefined,
    status: ENABLED,
    // 四种可分组类型各一张卡，始终都在。没配过的那几张显示「配置此类型」，
    // 提交时只带 configured 的 —— 服务端会把没提交的类型停用（不是删除）
    itemList: [],
  });

  const groupEnabled = computed({
    get: () => form.status === ENABLED,
    set: (val) => {
      form.status = val ? ENABLED : DISABLED;
    },
  });

  /**
   * 一张空白卡。默认值取的是「最不容易出事」的那一组：
   * 免审 + 终身限制 + 不限次数 + 不设兜底，预算留空强制运营自己填。
   */
  function emptyItem(prizeType) {
    return {
      id: undefined,
      prizeType,
      promoName: undefined,
      // 库存/预算二选一，不适用的那一侧提交时补 -1
      totalQuota: undefined,
      totalAmount: undefined,
      usedQuota: 0,
      usedAmount: 0,
      reviewLevel: REVIEW_LEVEL_ENUM.NONE.value,
      firstReviewThreshold: 0,
      secondReviewThreshold: 0,
      singleMaxQuota: 0,
      singleMaxAmount: 0,
      limitPeriod: LIMIT_PERIOD_ENUM.LIFETIME.value,
      limitStartTime: undefined,
      limitEndTime: undefined,
      identifyLimit: UNLIMITED,
      // 四个暂无消费方的维度：卡片里折叠着，但字段要在 ——
      // 库里有列，不带上去的话编辑既有配置会把原值覆盖成 null
      phoneLimit: UNLIMITED,
      ipLimit: UNLIMITED,
      deviceLimit: UNLIMITED,
      fingerprintLimit: UNLIMITED,
      status: ENABLED,
      configured: false,
    };
  }

  // ---------------------------- 回显 ----------------------------

  async function loadDetail() {
    SolvelaLoading.show();
    try {
      const id = route.query.id ? Number(route.query.id) : undefined;
      const detail = await promotionGroupApi.workbenchDetail(id);
      form.id = detail?.id;
      form.groupCode = detail?.groupCode;
      form.groupName = detail?.groupName;
      form.remark = detail?.remark;
      form.status = detail?.status ?? ENABLED;

      // 服务端只返回「组里已有的」类型，这里补齐成四张卡 ——
      // 页面要能看出「哪几种还没配」，那是配置完备度的一半信息
      const existedMap = Object.fromEntries((detail?.itemList || []).map((i) => [i.prizeType, i]));
      form.itemList = GROUPABLE_PRIZE_TYPES.map((t) => {
        const existed = existedMap[t.value];
        return existed ? { ...emptyItem(t.value), ...existed, configured: true } : emptyItem(t.value);
      });
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  onMounted(loadDetail);

  // ---------------------------- 卡片交互 ----------------------------

  function onConfigure(item) {
    item.configured = true;
    item.status = groupEnabled.value ? ENABLED : DISABLED;
  }

  function onToggleItem(item, checked) {
    item.status = checked ? ENABLED : DISABLED;
  }

  /**
   * 分组开关拨到停用时，把所有卡一起置成停用。
   *
   * 服务端 resolveItemStatus 也会强制这么做，这里同步只是为了让页面立刻显示真实结果 ——
   * 否则运营关了分组、卡片上还写着「发放中」，保存后才发现全变了。
   */
  function onGroupSwitch(checked) {
    if (checked) {
      return;
    }
    form.itemList.forEach((item) => {
      if (item.configured) {
        item.status = DISABLED;
      }
    });
  }

  // ---------------------------- 一键铺底 ----------------------------

  const fillTypes = ref([]);
  const fillTemplate = reactive({
    reviewLevel: REVIEW_LEVEL_ENUM.NONE.value,
    firstReviewThreshold: 0,
    limitPeriod: LIMIT_PERIOD_ENUM.LIFETIME.value,
    limitStartTime: undefined,
    limitEndTime: undefined,
    identifyLimit: UNLIMITED,
  });

  const fillRange = computed({
    get: () => (fillTemplate.limitStartTime && fillTemplate.limitEndTime ? [fillTemplate.limitStartTime, fillTemplate.limitEndTime] : []),
    set: (val) => {
      fillTemplate.limitStartTime = val?.[0];
      fillTemplate.limitEndTime = val?.[1];
    },
  });

  function applyFill() {
    form.itemList
      .filter((item) => fillTypes.value.includes(item.prizeType))
      .forEach((item) => {
        // 铺底顺带把类型纳入本组 —— 勾了却还要再点一次「配置此类型」是多余的一步
        item.configured = true;
        item.reviewLevel = fillTemplate.reviewLevel;
        item.firstReviewThreshold = fillTemplate.firstReviewThreshold;
        item.limitPeriod = fillTemplate.limitPeriod;
        item.limitStartTime = fillTemplate.limitStartTime;
        item.limitEndTime = fillTemplate.limitEndTime;
        item.identifyLimit = fillTemplate.identifyLimit;
        if (item.status == null) {
          item.status = groupEnabled.value ? ENABLED : DISABLED;
        }
        // 🔴 totalQuota / totalAmount 刻意不铺：预算是每种类型都不同的那个字段，
        // 铺一个统一值过去，运营会以为已经配好了而不再逐个确认
      });
    message.success(`已铺到 ${fillTypes.value.length} 种类型，请逐个确认预算`);
  }

  // ---------------------------- 保存 ----------------------------

  function validate() {
    if (!form.groupName) {
      return '分组名称必填';
    }
    for (const item of form.itemList) {
      if (!item.configured) {
        continue;
      }
      const quotaBased = QUOTA_BASED_PRIZE_TYPES.includes(item.prizeType);
      if (quotaBased && item.totalQuota == null) {
        return `${item.prizeType} 的总库存必填（-1 为不限制）`;
      }
      if (!quotaBased && item.totalAmount == null) {
        return `${item.prizeType} 的总预算必填（-1 为不限制）`;
      }
      // 阈值的必填跟着层级走：置灰的框不能同时是必填项，
      // 否则就是「填不了却提示必填」那种死锁
      if (item.reviewLevel !== REVIEW_LEVEL_ENUM.NONE.value && item.firstReviewThreshold == null) {
        return `${item.prizeType} 的一审触发阈值必填`;
      }
      if (item.limitPeriod === LIMIT_PERIOD_ENUM.CUSTOM.value && (!item.limitStartTime || !item.limitEndTime)) {
        return `${item.prizeType} 的限制周期选了「自定义」，必须填写窗口起止时间`;
      }
    }
    return null;
  }

  async function onSave() {
    const error = validate();
    if (error) {
      message.error(error);
      return;
    }
    saving.value = true;
    SolvelaLoading.show();
    try {
      const payload = {
        id: form.id,
        groupName: form.groupName,
        remark: form.remark,
        status: form.status,
        itemList: form.itemList.filter((item) => item.configured).map(toItemPayload),
      };
      await promotionGroupApi.workbenchSave(payload);
      message.success('保存成功');
      // 保存完回列表：留在原地只有一个 toast，看不出到底存进去了什么。
      // 列表上那一行的「配置完备度」会立刻反映本次结果，展开还能逐条核对 ——
      // 那才是真正的保存回执。
      //
      // 顺带解掉一个隐患：新建保存后如果留在原地，form.id 还是空的，
      // 再点一次保存就会又建一个新组，而运营完全看不出来。
      goBack();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      saving.value = false;
      SolvelaLoading.hide();
    }
  }

  /**
   * 组装单条子配置的提交体。
   *
   * 两件必须做的事：
   * ① 不适用的那一侧补 -1（不限制）—— 服务端两个字段不接受 null；
   * ② usedQuota / usedAmount / configured 一律不传 —— 前两个是运行态水位，
   *    只能由发放链路的原子 SQL 维护，末一个是纯前端状态。
   */
  const isPhysical = (item) => item.prizeType === PRIZE_TYPE_ENUM.PHYSICAL.value;

  function toItemPayload(item) {
    const quotaBased = QUOTA_BASED_PRIZE_TYPES.includes(item.prizeType);
    return {
      id: item.id,
      prizeType: item.prizeType,
      promoName: item.promoName,
      totalQuota: quotaBased ? item.totalQuota : UNLIMITED,
      totalAmount: quotaBased ? UNLIMITED : item.totalAmount,
      reviewLevel: item.reviewLevel,
      firstReviewThreshold: item.firstReviewThreshold,
      secondReviewThreshold: item.secondReviewThreshold,
      singleMaxQuota: item.singleMaxQuota,
      // 实物固定送 0（不设兜底）：它的「金额」只是一个参考价值
      // （iPhone 记 7999 是给风控预算一个口径），拿它做单次上限没有意义，
      // 卡片上也不展示这个输入框 —— 展示与提交两边口径必须一致
      singleMaxAmount: isPhysical(item) ? 0 : item.singleMaxAmount,
      limitPeriod: item.limitPeriod,
      limitStartTime: item.limitStartTime,
      limitEndTime: item.limitEndTime,
      identifyLimit: item.identifyLimit,
      phoneLimit: item.phoneLimit,
      ipLimit: item.ipLimit,
      deviceLimit: item.deviceLimit,
      fingerprintLimit: item.fingerprintLimit,
      status: item.status,
    };
  }

  function goBack() {
    router.push({ path: '/risk/promotion-group/list' });
  }
</script>
<style scoped>
  .promotion-workbench {
    padding: 8px;
  }
</style>
