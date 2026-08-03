<!--
  发出价值趋势（按资产类型堆叠）

  堆叠柱而不是折线：一眼看出「总量里到底是哪种资产在真发」——
  实测里常见的形状是只有券那一段有高度，其余三种恒为 0，折线会挤在一起看不出来。

  ⚠️ 纵轴是「已发出价值」，只算 status=1 的记录。
     「已发出」不等于「已到账」（方案 §2.2），措辞不能混。
-->
<template>
  <div ref="chartRef" class="trend-chart"></div>
</template>

<script setup>
  import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
  import echarts from '/@/lib/echarts';
  import { PRIZE_TYPE_ENUM } from '/@/constants/business/prize/prize-config/prize-config-const';

  const props = defineProps({
    dateList: { type: Array, default: () => [] },
    trendList: { type: Array, default: () => [] },
  });

  // 与后端 PrizeTypeEnum 对齐；颜色固定，切活动时同一种资产不会换色
  const ASSET_COLOR = {
    COUPON: '#10b981',
    SCORE: '#3b82f6',
    BALANCE: '#f59e0b',
    PHYSICAL: '#ec4899',
    LOTTERY: '#8b5cf6',
    CUSTOM: '#94a3b8',
  };

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
    const types = [...new Set(props.trendList.map((t) => t.prizeType))];
    const series = types.map((type) => ({
      name: assetLabel(type),
      type: 'bar',
      stack: 'value',
      barMaxWidth: 28,
      itemStyle: { color: ASSET_COLOR[type] || '#94a3b8' },
      data: props.dateList.map((d) => {
        const hit = props.trendList.find((t) => t.statDate === d && t.prizeType === type);
        return num(hit && hit.issuedValue);
      }),
    }));
    chart.value.setOption(
      {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: { top: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
        grid: { top: 32, right: 16, bottom: 24, left: 52 },
        xAxis: { type: 'category', data: props.dateList, axisLabel: { fontSize: 11 } },
        yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
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
