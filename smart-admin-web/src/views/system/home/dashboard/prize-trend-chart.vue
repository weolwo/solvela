<!--
  发出价值趋势（按资产类型堆叠）

  堆叠柱而不是折线：一眼看出「总量里到底是哪种资产在真发」——
  实测里常见的形状是只有券那一段有高度，其余三种恒为 0，折线会挤在一起看不出来。

  ⚠️ 纵轴是「已发出价值」，只算 status=1 的记录。
     「已发出」不等于「已到账」（方案 §2.2），措辞不能混。

  配色来自 dashboard-theme.js 的固定槽位，堆叠顺序也按槽位来（理由见那里）。
  青、黄两槽对白底不足 3:1，故这张图的读数通道不只有颜色：
  上方 KPI 卡把每种资产的条数与金额逐项列出来了，等价于一张明细表。
-->
<template>
  <div ref="chartRef" class="trend-chart"></div>
</template>

<script setup>
  import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
  import echarts from '/@/lib/echarts';
  import { PRIZE_TYPE_ENUM } from '/@/constants/business/prize/prize-config/prize-config-const';
  import { ASSET_COLOR, ASSET_ORDER, CHROME, axisStyle, legendStyle, splitLineStyle, tooltipStyle } from './dashboard-theme';

  const props = defineProps({
    dateList: { type: Array, default: () => [] },
    trendList: { type: Array, default: () => [] },
  });

  const chartRef = ref(null);
  const chart = shallowRef(null);
  let ro = null;

  // ⚠️ BigDecimal 经 JsonConfig 序列化成字符串（"1020.0000"），必须 Number() 归一化
  const num = (v) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  };

  const assetLabel = (type) => (PRIZE_TYPE_ENUM[type] ? PRIZE_TYPE_ENUM[type].desc : type);

  function render() {
    if (!ensureChart()) return;
    // ⚠️ 堆叠顺序按固定槽位来，不按数值大小排：
    //    ① 颜色跟着资产走，切活动时同一种资产不会换色；
    //    ② 相邻两段的配色正是校验通过的那几对（见 dashboard-theme.js）。
    const present = new Set(props.trendList.map((t) => t.prizeType));
    const types = ASSET_ORDER.filter((t) => present.has(t)).concat([...present].filter((t) => !ASSET_ORDER.includes(t)));

    const series = types.map((type) => ({
      name: assetLabel(type),
      type: 'bar',
      stack: 'value',
      barMaxWidth: 24,
      itemStyle: {
        color: ASSET_COLOR[type] || CHROME.axisLine,
        // 段与段之间留一条底色缝：靠留白分隔，而不是给每段描个边（描边是多出来的、不表意的墨）
        borderColor: CHROME.surface,
        borderWidth: 1,
        // 只有最上面那段圆角，堆叠体才像一根完整的柱子而不是一摞方块
        borderRadius: [0, 0, 0, 0],
      },
      data: props.dateList.map((d) => {
        const hit = props.trendList.find((t) => t.statDate === d && t.prizeType === type);
        return num(hit && hit.issuedValue);
      }),
    }));
    if (series.length) {
      series[series.length - 1].itemStyle.borderRadius = [4, 4, 0, 0];
    }

    chart.value.setOption(
      {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(11,11,11,0.04)' } },
          valueFormatter: (v) => '¥' + num(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }),
          ...tooltipStyle,
        },
        legend: { ...legendStyle, data: series.map((s) => s.name) },
        grid: { top: 34, right: 16, bottom: 24, left: 56 },
        xAxis: { type: 'category', data: props.dateList, ...axisStyle },
        yAxis: {
          type: 'value',
          ...axisStyle,
          ...splitLineStyle,
          axisLine: { show: false },
          axisLabel: { ...axisStyle.axisLabel, formatter: (v) => (v >= 1000 ? v / 1000 + 'k' : v) },
        },
        series,
      },
      true
    );
  }

  /* 容器还没尺寸时 init 会把 0 尺寸锁死（表现为空白图且不报错），必须等它真的有尺寸 */
  function ensureChart() {
    if (!chartRef.value || !chartRef.value.clientWidth || !chartRef.value.clientHeight) return false;
    if (!chart.value) chart.value = echarts.init(chartRef.value);
    return true;
  }

  watch(() => [props.dateList, props.trendList], render, { deep: true });

  onMounted(() => {
    if (typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(() => {
        if (ensureChart()) {
          chart.value.resize();
          render();
        }
      });
      ro.observe(chartRef.value);
    }
    render();
  });

  onBeforeUnmount(() => {
    ro && ro.disconnect();
    chart.value && chart.value.dispose();
  });
</script>

<style lang="less" scoped>
  .trend-chart {
    width: 100%;
    height: 240px;
  }
</style>
