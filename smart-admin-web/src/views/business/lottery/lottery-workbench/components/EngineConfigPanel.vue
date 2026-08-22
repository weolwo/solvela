<!--
  * 彩票工作台 Tab1：发号引擎配置 + 奖级规则映射
  *
  * 职责：
  * 1. 发号引擎基础定义（名称/发售上限/号码长度），结构锁命中时整体只读。
  * 2. FPE 算号推演台 —— 必须走服务端，前端不可能也不应该持有密钥。
  * 3. 奖级规则设定区 + 中台资产池，支持拖拽与「指派」按钮两条绑定路径。
  *
  * 单一状态源（铁律 4）：整个 Tab 的表单收在一个 reactive(form) 里，
  * 奖级规则的行内编辑也直接改 form.prizeRuleList，不另开副本。
  *
  * @Author:    alaric
  * @Date:      2026-07-27
-->
<template>
  <div class="py-2">
    <!-- ============ 发号引擎基础定义 ============ -->
    <div class="bg-white p-5 rounded-lg border border-slate-200 mb-6 shadow-sm">
      <div class="flex items-center justify-between mb-3">
        <h3 class="section-title title-blue m-0">发号引擎基础定义</h3>
        <a-tag v-if="structureLocked" color="red" class="m-0">🔒 引擎参数已冻结</a-tag>
      </div>

      <a-alert v-if="structureLocked" type="warning" show-icon class="mb-4" :message="lockReason" />

      <a-row :gutter="24">
        <a-col :span="12">
          <a-form layout="vertical">
            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item label="玩法名称 (外显)" required class="mb-3">
                  <a-input v-model:value="form.lotteryName" placeholder="如：618仲夏夜幸运号" @change="emitChange" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item required class="mb-3">
                  <template #label>
                    <span>彩票编码</span>
                    <a-tooltip title="10 位大写字母+数字，全局唯一。创建后不可修改——奖级规则、期号、购彩记录都靠它关联">
                      <QuestionCircleOutlined class="ml-1 text-slate-400" />
                    </a-tooltip>
                  </template>
                  <a-input-group compact>
                    <a-input
                      v-model:value="form.lotteryCode"
                      style="width: calc(100% - 76px)"
                      :disabled="configured"
                      placeholder="如 H88JHKJFNE"
                    />
                    <a-button style="width: 76px" :disabled="configured" @click="onGenerateCode">生成</a-button>
                  </a-input-group>
                </a-form-item>
              </a-col>
            </a-row>

            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item class="mb-3">
                  <template #label>
                    <span>归属活动</span>
                    <a-tooltip title="由顶部「当前活动」决定。一个活动只配一个玩法，奖级规则只能绑定该活动资产大库里的奖品">
                      <QuestionCircleOutlined class="ml-1 text-slate-400" />
                    </a-tooltip>
                  </template>
                  <div class="h-8 flex items-center text-sm">
                    <a-tag color="blue" class="m-0">{{ activityName || '—' }}</a-tag>
                    <span class="text-slate-400 font-mono text-xs ml-2">{{ form.activityCode || '' }}</span>
                  </div>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="单期发售上限 (物理防资损)" required class="mb-3">
                  <a-input-number
                    v-model:value="form.totalCount"
                    :min="1"
                    :max="domain"
                    class="w-full font-bold"
                    :disabled="structureLocked"
                    @change="emitChange"
                  />
                </a-form-item>
              </a-col>
            </a-row>

            <a-row :gutter="12">
              <a-col :span="12">
                <a-form-item required class="mb-0">
                  <template #label>
                    <span>号码长度</span>
                    <a-tooltip title="纯数字。下限 4 位：号码空间需不小于 1 万，太小则号码可被穷举，且分布质量下降">
                      <QuestionCircleOutlined class="ml-1 text-slate-400" />
                    </a-tooltip>
                  </template>
                  <a-input-number
                    v-model:value="form.numberLength"
                    :min="MIN_NUMBER_LENGTH"
                    :max="MAX_NUMBER_LENGTH"
                    class="w-full"
                    :disabled="structureLocked"
                    @change="onLengthChange"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="号码空间" class="mb-0">
                  <div class="h-8 flex items-center text-sm">
                    <span class="font-mono font-bold text-slate-700">{{ domain.toLocaleString() }}</span>
                    <span class="text-slate-400 ml-2">个 (10^{{ form.numberLength }})</span>
                  </div>
                </a-form-item>
              </a-col>
            </a-row>

            <div v-if="capacityError" class="text-red-500 text-xs mt-2 font-bold">❌ {{ capacityError }}</div>
            <div v-else class="text-slate-400 text-xs mt-2">
              发售 {{ (form.totalCount || 0).toLocaleString() }} / 空间 {{ domain.toLocaleString() }}，
              占用 {{ occupancy }}%。空间越宽裕，号码越稀疏、越难被猜中。
            </div>
          </a-form>
        </a-col>

        <!-- ============ FPE 算号推演台 ============ -->
        <a-col :span="12">
          <div class="geek-terminal rounded-lg p-4 h-full flex flex-col justify-center">
            <div class="text-green-500 font-bold mb-3 text-sm"># FPE 算号推演模拟台</div>
            <div class="flex items-center gap-3 text-sm">
              <span class="text-slate-300">输入游标:</span>
              <a-input-number v-model:value="demoSequence" :min="1" :max="form.totalCount || domain" class="w-28" size="small" />
              <span class="text-slate-500 text-xs">第 N 个领号的人</span>
            </div>
            <div class="mt-4 border-t border-green-800 pt-3 flex items-center">
              <span class="text-slate-300 text-sm">🎟️ 密文票号:</span>
              <span class="ml-3 font-mono text-xl text-white tracking-widest font-bold">
                {{ previewLoading ? '· · ·' : previewNumber || '—' }}
              </span>
            </div>
            <div class="text-[11px] text-slate-500 mt-3 leading-relaxed">
              演示号码，实际号码随期号变化。<br />
              与线上发号是同一段代码、同一套密钥派生，所见即所得。
            </div>
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- ============ 奖级规则 + 资产池 ============ -->
    <div class="flex gap-6 items-start">
      <div class="w-7/12 flex flex-col gap-3">
        <div class="flex justify-between items-center mb-1">
          <h3 class="section-title title-green m-0">奖级规则设定区</h3>
          <div class="flex gap-2">
            <a-button type="dashed" size="small" @click="sortRules">🔄 按等级整理</a-button>
            <a-button type="primary" size="small" class="bg-green-600 border-green-600 font-bold" @click="addRule">
              ➕ 新增奖级 (置顶)
            </a-button>
          </div>
        </div>

        <div class="bg-slate-50 p-3 rounded-lg border border-slate-200 max-h-[600px] overflow-y-auto flex flex-col gap-3 shadow-inner">
          <div v-if="form.prizeRuleList.length === 0" class="py-8 text-center text-slate-400 text-sm">
            暂无奖级配置，请点击上方新增
          </div>
          <div
            v-for="rule in form.prizeRuleList"
            :key="rule._key"
            class="rule-card bg-white rounded-md p-4 cursor-pointer relative"
            :class="{ active: activeRuleKey === rule._key, 'drag-over': rule._dragOver }"
            @click="activeRuleKey = rule._key"
            @dragover.prevent="onDragOver($event, rule)"
            @dragleave="rule._dragOver = false"
            @drop="onDrop($event, rule)"
          >
            <div class="flex justify-between items-center mb-3">
              <div class="flex items-center gap-2">
                <a-tag color="purple" class="font-bold text-sm px-2 m-0">奖级</a-tag>
                <a-input-number v-model:value="rule.prizeLevel" :min="1" size="small" class="w-16" @change="emitChange" />
              </div>
              <a-button type="link" danger size="small" class="p-0 h-auto text-xs" @click.stop="removeRule(rule._key)">移除</a-button>
            </div>

            <div class="flex gap-4 mb-3 items-center">
              <div class="w-1/2 flex items-center gap-2">
                <span class="text-xs text-slate-500 whitespace-nowrap">匹配模式</span>
                <a-select
                  v-model:value="rule.matchRule"
                  :options="MATCH_RULE_OPTIONS"
                  size="small"
                  class="flex-1"
                  @change="onMatchRuleChange(rule)"
                />
              </div>
              <div class="w-1/2 flex items-center gap-2">
                <span class="text-xs text-slate-500 whitespace-nowrap">匹配长度</span>
                <a-input-number
                  v-model:value="rule.matchLength"
                  :min="1"
                  :max="form.numberLength"
                  size="small"
                  class="flex-1"
                  :disabled="rule.matchRule === 'EXACT'"
                  @change="emitChange"
                />
              </div>
            </div>

            <div
              class="rounded-md p-2 flex items-center transition-all duration-200 border text-sm"
              :class="rule.prizeCode ? 'border-blue-300 bg-blue-50/60' : 'border-dashed border-slate-300 bg-slate-50'"
            >
              <div v-if="rule.prizeCode" class="flex items-center gap-3 w-full">
                <span class="text-2xl">{{ prizeIcon(prizeTypeOf(rule.prizeCode)) }}</span>
                <div class="flex-1 truncate leading-tight">
                  <div class="font-bold text-blue-900 text-sm">{{ prizeNameOf(rule.prizeCode) }}</div>
                  <div class="text-xs text-blue-500 font-mono mt-0.5">{{ rule.prizeCode }}</div>
                </div>
                <a-button type="text" danger size="small" @click.stop="unbind(rule)">解绑</a-button>
              </div>
              <div v-else class="text-slate-400 flex-1 text-center py-3 text-sm">
                <span v-if="rule._dragOver" class="text-green-500 font-bold">✨ 松开绑定奖品</span>
                <span v-else>📥 拖拽资产入内，或点击右侧【指派】</span>
              </div>
            </div>

            <div v-if="ruleHint(rule)" class="text-[11px] mt-2 text-slate-500">💡 {{ ruleHint(rule) }}</div>
          </div>
        </div>
      </div>

      <div class="w-5/12 flex flex-col gap-3">
        <h3 class="section-title title-green m-0">中台资产池 (拖拽源)</h3>
        <div class="bg-white border border-slate-200 rounded-lg p-3 max-h-[600px] overflow-y-auto flex flex-col gap-2.5 shadow-sm">
          <a-empty v-if="prizeOptions.length === 0" :image="simpleImage">
            <template #description>
              <span class="text-xs text-slate-400">
                {{ form.activityCode ? '该活动的资产大库为空，请先到「奖品配置」新建奖品' : '请先选择归属活动' }}
              </span>
            </template>
          </a-empty>
          <div
            v-for="asset in prizeOptions"
            :key="asset.prizeCode"
            class="asset-meat bg-slate-50 p-2.5 rounded-md flex items-center justify-start gap-3"
            :draggable="true"
            @dragstart="onDragStart($event, asset)"
            @dragend="onDragEnd"
          >
            <div class="w-[68px] shrink-0">
              <a-button
                v-if="activeRulePrizeCode === asset.prizeCode"
                type="primary"
                size="small"
                shape="round"
                class="bg-green-500 border-green-500 w-full font-bold px-0"
              >
                ✅ 已绑
              </a-button>
              <a-button
                v-else
                size="small"
                shape="round"
                class="w-full text-blue-600 border-blue-200 font-bold px-0 bg-white"
                :disabled="!activeRuleKey"
                @click="bindToActive(asset.prizeCode)"
              >
                指派 👈
              </a-button>
            </div>
            <div class="flex items-center gap-2 overflow-hidden">
              <div class="text-2xl bg-white p-1.5 rounded shadow-sm border border-slate-100 shrink-0 leading-none">
                {{ prizeIcon(asset.prizeType) }}
              </div>
              <div class="truncate leading-tight">
                <div class="font-bold text-slate-800 text-sm truncate">{{ asset.prizeName }}</div>
                <div class="text-[11px] text-slate-400 font-mono mt-0.5">
                  {{ asset.prizeCode }} · 价值 {{ asset.prizeValue }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { computed, reactive, ref, watch } from 'vue';
  import { message, Empty } from 'ant-design-vue';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { lotteryWorkbenchApi } from '/src/api/business/lottery/lottery-workbench-api';
  import { prizeConfigApi } from '/src/api/business/prize/prize-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { MIN_NUMBER_LENGTH, MAX_NUMBER_LENGTH, MATCH_RULE_OPTIONS, prizeIcon } from '/@/constants/business/lottery/lottery-const';

  const emit = defineEmits(['change']);
  const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;

  // ---------------------------- 单一状态源 ----------------------------

  function emptyForm() {
    return {
      activityCode: undefined,
      lotteryCode: undefined,
      lotteryName: undefined,
      numberLength: 5,
      totalCount: 100000,
      prizeRuleList: [],
    };
  }

  const form = reactive(emptyForm());
  const configured = ref(false);
  const structureLocked = ref(false);
  const lockReason = ref('');

  // _key 只用于前端行标识，提交前会剔除：prizeLevel 可被运营改成任意值，不能拿它当 v-for 的 key
  let keySeed = 0;
  function nextKey() {
    return `rule_${Date.now()}_${keySeed++}`;
  }

  const activeRuleKey = ref(null);
  const activeRulePrizeCode = computed(() => form.prizeRuleList.find((r) => r._key === activeRuleKey.value)?.prizeCode || '');

  function emitChange() {
    emit('change');
  }

  // ---------------------------- 号码空间 ----------------------------

  const domain = computed(() => Math.pow(10, form.numberLength || MIN_NUMBER_LENGTH));
  const occupancy = computed(() => (((form.totalCount || 0) / domain.value) * 100).toFixed(2));
  const capacityError = computed(() => {
    if (!form.totalCount || !form.numberLength) return '';
    if (form.totalCount > domain.value) {
      return `单期发售上限 ${form.totalCount.toLocaleString()} 超过 ${form.numberLength} 位号码的空间 ${domain.value.toLocaleString()}，请增加号码长度或降低发售量`;
    }
    return '';
  });

  function onLengthChange() {
    // 号码变短后，尾号/首号匹配长度可能超出，顺手夹回去，免得保存时才被服务端拦下
    form.prizeRuleList.forEach((rule) => {
      if (rule.matchRule === 'EXACT') {
        rule.matchLength = form.numberLength;
      } else if (rule.matchLength > form.numberLength) {
        rule.matchLength = form.numberLength;
      }
    });
    refreshPreview();
    emitChange();
  }

  // ---------------------------- FPE 推演（必须走服务端） ----------------------------

  const demoSequence = ref(1);
  const previewNumber = ref('');
  const previewLoading = ref(false);
  let previewTimer = null;

  function refreshPreview() {
    clearTimeout(previewTimer);
    // 密钥由 lotteryCode 参与派生，编码没填时算不出正确结果，不如不显示
    if (!form.lotteryCode || !form.numberLength || !demoSequence.value) {
      previewNumber.value = '';
      return;
    }
    previewTimer = setTimeout(async () => {
      previewLoading.value = true;
      try {
        const res = await lotteryWorkbenchApi.fpePreview({
          lotteryCode: form.lotteryCode,
          numberLength: form.numberLength,
          sequenceNo: demoSequence.value,
        });
        previewNumber.value = res.data;
      } catch (e) {
        previewNumber.value = '';
        smartSentry.captureError(e);
      } finally {
        previewLoading.value = false;
      }
    }, 300);
  }

  watch(() => [form.lotteryCode, demoSequence.value], refreshPreview);

  // ---------------------------- 活动与资产池 ----------------------------

  const activityName = ref('');
  const prizeOptions = ref([]);

  async function loadPrizes() {
    if (!form.activityCode) {
      prizeOptions.value = [];
      return;
    }
    try {
      const res = await prizeConfigApi.optionList(form.activityCode);
      prizeOptions.value = res.data || [];
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  function prizeOf(prizeCode) {
    return prizeOptions.value.find((p) => p.prizeCode === prizeCode);
  }
  function prizeNameOf(prizeCode) {
    return prizeOf(prizeCode)?.prizeName || prizeCode;
  }
  function prizeTypeOf(prizeCode) {
    return prizeOf(prizeCode)?.prizeType;
  }

  // ---------------------------- 奖级规则 ----------------------------

  function addRule() {
    const nextLevel = form.prizeRuleList.length > 0 ? Math.max(...form.prizeRuleList.map((r) => r.prizeLevel || 0)) + 1 : 1;
    const key = nextKey();
    form.prizeRuleList.unshift({
      _key: key,
      _dragOver: false,
      prizeLevel: nextLevel,
      matchRule: 'TAIL',
      matchLength: 1,
      prizeCode: '',
    });
    activeRuleKey.value = key;
    emitChange();
  }

  function removeRule(key) {
    form.prizeRuleList = form.prizeRuleList.filter((r) => r._key !== key);
    if (activeRuleKey.value === key) {
      activeRuleKey.value = form.prizeRuleList[0]?._key || null;
    }
    emitChange();
  }

  function sortRules() {
    form.prizeRuleList.sort((a, b) => (a.prizeLevel || 0) - (b.prizeLevel || 0));
    emitChange();
  }

  function onMatchRuleChange(rule) {
    // EXACT 的匹配长度恒等于号码长度，服务端也会归一，这里同步只是让界面不自相矛盾
    if (rule.matchRule === 'EXACT') {
      rule.matchLength = form.numberLength;
    }
    emitChange();
  }

  function ruleHint(rule) {
    if (!rule.matchRule || !rule.matchLength || !form.numberLength) return '';
    if (rule.matchRule === 'EXACT') {
      return `全号命中，理论中奖率 1/${domain.value.toLocaleString()}`;
    }
    const odds = Math.pow(10, rule.matchLength);
    const label = rule.matchRule === 'TAIL' ? '尾' : '首';
    return `${label} ${rule.matchLength} 位命中，理论中奖率约 1/${odds.toLocaleString()}（每 ${odds.toLocaleString()} 张中 1 张）`;
  }

  function unbind(rule) {
    rule.prizeCode = '';
    emitChange();
  }

  function bindToActive(prizeCode) {
    const rule = form.prizeRuleList.find((r) => r._key === activeRuleKey.value);
    if (!rule) return;
    if (form.prizeRuleList.some((r) => r._key !== rule._key && r.prizeCode === prizeCode)) {
      message.warning('该奖品已被其他奖级绑定，一个奖品只能对应一个奖级');
      return;
    }
    rule.prizeCode = prizeCode;
    message.success('绑定成功');
    emitChange();
  }

  // ---------------------------- 拖拽 ----------------------------

  function onDragStart(event, asset) {
    event.dataTransfer.setData('text/plain', asset.prizeCode);
    event.dataTransfer.effectAllowed = 'copy';
  }
  function onDragOver(event, rule) {
    rule._dragOver = true;
    event.dataTransfer.dropEffect = 'copy';
  }
  function onDrop(event, rule) {
    rule._dragOver = false;
    const prizeCode = event.dataTransfer.getData('text/plain');
    if (!prizeCode) return;
    activeRuleKey.value = rule._key;
    bindToActive(prizeCode);
  }
  function onDragEnd() {
    form.prizeRuleList.forEach((r) => (r._dragOver = false));
  }

  // ---------------------------- 编码 ----------------------------

  async function onGenerateCode() {
    try {
      const res = await lotteryWorkbenchApi.generateCode();
      form.lotteryCode = res.data;
      emitChange();
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  // ---------------------------- 对外接口 ----------------------------

  function setData(vo) {
    Object.assign(form, emptyForm());
    configured.value = !!vo.configured;
    structureLocked.value = !!vo.structureLocked;
    lockReason.value = vo.lockReason || '';

    activityName.value = vo.activityName || '';
    form.activityCode = vo.activityCode || undefined;
    form.lotteryCode = vo.lotteryCode || undefined;
    form.lotteryName = vo.lotteryName || undefined;
    form.numberLength = vo.numberLength || 5;
    form.totalCount = vo.totalCount || 100000;
    form.prizeRuleList = (vo.prizeRuleList || []).map((r) => ({
      _key: nextKey(),
      _dragOver: false,
      prizeLevel: r.prizeLevel,
      matchRule: r.matchRule,
      matchLength: r.matchLength,
      prizeCode: r.prizeCode,
    }));
    activeRuleKey.value = form.prizeRuleList[0]?._key || null;
    demoSequence.value = 1;

    loadPrizes();
    refreshPreview();
  }

  function getData() {
    return {
      activityCode: form.activityCode,
      lotteryCode: form.lotteryCode,
      lotteryName: form.lotteryName,
      numberLength: form.numberLength,
      totalCount: form.totalCount,
      // 剔除 _key / _dragOver 这类纯前端字段，别把 UI 状态提交给服务端
      prizeRuleList: form.prizeRuleList.map((r) => ({
        prizeLevel: r.prizeLevel,
        matchRule: r.matchRule,
        matchLength: r.matchLength,
        prizeCode: r.prizeCode,
      })),
    };
  }

  /**
   * 提交前的防呆校验。只是防呆——服务端会把这些规则全部重算一遍（铁律 2）
   */
  function validate() {
    if (!form.activityCode) return '请选择归属活动';
    if (!form.lotteryCode) return '请填写或生成彩票编码';
    if (!/^[A-Z0-9]{10}$/.test(form.lotteryCode)) return '彩票编码必须是 10 位大写字母或数字的组合，如 H88JHKJFNE';
    if (!form.lotteryName) return '请填写玩法名称';
    if (!form.numberLength || form.numberLength < MIN_NUMBER_LENGTH || form.numberLength > MAX_NUMBER_LENGTH) {
      return `号码长度必须在 ${MIN_NUMBER_LENGTH}~${MAX_NUMBER_LENGTH} 之间`;
    }
    if (capacityError.value) return capacityError.value;

    const levels = new Set();
    const codes = new Set();
    for (const rule of form.prizeRuleList) {
      if (!rule.prizeLevel) return '存在未填写奖级的规则';
      if (levels.has(rule.prizeLevel)) return `奖级重复：${rule.prizeLevel} 级配置了多条规则`;
      levels.add(rule.prizeLevel);
      if (!rule.prizeCode) return `奖级 ${rule.prizeLevel} 尚未绑定奖品`;
      if (codes.has(rule.prizeCode)) return `奖品 ${rule.prizeCode} 被多个奖级重复绑定`;
      codes.add(rule.prizeCode);
      if (rule.matchRule !== 'EXACT' && rule.matchLength > form.numberLength) {
        return `奖级 ${rule.prizeLevel} 的匹配长度超过号码长度，这条规则永远不可能命中`;
      }
    }
    return null;
  }

  defineExpose({ setData, getData, validate });
</script>

<style lang="less" scoped>
  .section-title {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 1rem;
    font-weight: 600;
    color: #0f172a;

    &::before {
      display: block;
      width: 4px;
      height: 16px;
      content: '';
      border-radius: 2px;
    }
  }
  .title-blue::before {
    background-color: #3b82f6;
  }
  .title-green::before {
    background-color: #10b981;
  }

  .geek-terminal {
    font-family: 'Courier New', Courier, monospace;
    color: #4ade80;
    background-color: #1e1e1e;
  }

  .rule-card {
    border: 1px solid #e2e8f0;
    transition: all 0.2s ease;

    &.active {
      background-color: #f8fafc;
      border-color: #3b82f6;
      box-shadow: 0 4px 12px rgb(59 130 246 / 8%);
    }

    &:hover:not(.active) {
      border-color: #cbd5e1;
    }

    &.drag-over {
      z-index: 10;
      background-color: #ecfdf5 !important;
      border-color: #10b981 !important;
      transform: scale(1.01);
    }
  }

  .asset-meat {
    border: 1px solid #e2e8f0;
    cursor: grab;
    transition: all 0.2s;

    &:active {
      cursor: grabbing;
      transform: scale(0.99);
    }

    &:hover {
      border-color: #94a3b8;
      box-shadow: 0 2px 4px rgb(0 0 0 / 5%);
    }
  }
</style>
