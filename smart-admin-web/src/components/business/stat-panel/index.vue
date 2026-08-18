<!--
  * 统计面板外壳（财务中心四个页面共用）
  *
  * 它只管三件所有页面都一样的事：折叠、时间范围、体检告警。
  * 具体有哪些指标由各页面用默认插槽自己填 —— 四个页面要看的东西完全不同，
  * 抽成配置反而会变成一个谁也读不懂的通用组件。
  *
  * ⚠️ 面板的时间范围<b>和下面列表的筛选是两回事</b>，刻意不联动：
  * 面板回答的是「这段时间整体发生了什么」，跟着某个会员名/状态筛之后它就不再是概览了。
  * 页面上用一行小字明说这一点 —— 筛了却不生效，比不筛更让人困惑。
  *
  * ⚠️ 默认当天。但要注意：并不是面板上每个数都是当天的 ——
  * 券库存、发货积压、钱包余额这类**存量**指标一律是全量，
  * 因为把积压限制在今天，压了三天的单子会正好从页面上消失，
  * 而那恰恰是这些页面唯一需要有人动手的东西。存量卡片自己会标「全量」。
  *
  * @Author:    alaric
  * @Date:      2026-08-18
-->
<template>
  <div class="smart-stat-toggle">
    <a-button type="link" size="small" class="smart-stat-toggle-btn" @click="open = !open">
      <template #icon>
        <DownOutlined v-if="open" />
        <RightOutlined v-else />
      </template>
      {{ title }}
    </a-button>
    <span v-if="!open" class="smart-stat-summary">
      <slot name="summary"></slot>
    </span>
    <div class="smart-stat-range">
      <a-range-picker v-model:value="range" :presets="defaultTimeRanges" :allow-clear="false" size="small" style="width: 240px" @change="emitChange" />
    </div>
  </div>

  <div v-show="open">
    <div class="smart-stat-note">
      统计范围：{{ rangeText }}（默认当天）。本面板不受下方列表筛选影响；标了「全量」的卡片也不受时间范围影响
    </div>

    <a-alert v-for="(issue, idx) in issueList || []" :key="idx" type="error" show-icon class="mb-2">
      <template #message>
        <span class="text-xs">{{ issue }}</span>
      </template>
    </a-alert>

    <slot></slot>
  </div>
</template>

<script setup>
  import { computed, onMounted, ref, watch } from 'vue';
  import dayjs from 'dayjs';
  import { DownOutlined, RightOutlined } from '@ant-design/icons-vue';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';

  const props = defineProps({
    // 折叠条上的标题
    title: { type: String, default: '统计' },
    // 体检告警，服务端算好的整句话，前端不做二次加工
    issueList: { type: Array, default: () => [] },
    // localStorage 里记折叠状态用的 key，各页面传自己的，别共用一个
    storageKey: { type: String, required: true },
  });

  const emit = defineEmits(['change']);

  const DATE_FORMAT = 'YYYY-MM-DD';

  // 默认当天：进页面第一眼看到的就是今天的数
  const range = ref([dayjs(), dayjs()]);

  const rangeText = computed(() => {
    const [begin, end] = range.value || [];
    if (!begin || !end) {
      return '当天';
    }
    const beginText = begin.format(DATE_FORMAT);
    const endText = end.format(DATE_FORMAT);
    return beginText === endText ? beginText : `${beginText} ~ ${endText}`;
  });

  /**
   * 往外抛 { statDateBegin, statDateEnd }，与后端 LedgerStatForm 一一对应。
   * 结束日是**含当天**的，服务端 SQL 里按 < 次日零点处理，这里不用自己加一天。
   */
  function emitChange() {
    const [begin, end] = range.value || [];
    emit('change', {
      statDateBegin: begin ? begin.format(DATE_FORMAT) : undefined,
      statDateEnd: end ? end.format(DATE_FORMAT) : undefined,
    });
  }

  // 由外壳负责首次加载：各页面只要监听 change 一处即可，不用在 onMounted 里再拼一次日期
  onMounted(emitChange);

  defineExpose({ reload: emitChange });

  /*
   * 折叠状态记在 localStorage，读写都用 try/catch 包住 ——
   * 隐私模式下 localStorage 会抛异常，不能让折叠状态把页面初始化带崩。
   */
  const open = ref(readOpen());

  function readOpen() {
    try {
      return localStorage.getItem(`stat-panel:${props.storageKey}`) !== '0';
    } catch (e) {
      return true;
    }
  }

  watch(open, (value) => {
    try {
      localStorage.setItem(`stat-panel:${props.storageKey}`, value ? '1' : '0');
    } catch (e) {
      // 存不了就算了，折叠状态不值得打断用户
    }
  });
</script>
