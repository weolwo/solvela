<!--
  首页图表：活动参与趋势 + 各玩法类型参与人数

  口径见 docs/首页图表-口径确认清单.md，界面上也把口径印出来（与大屏原型 v2 一致），
  避免运营按自己的理解解读数字。

  ⚠️ 新增 series 类型必须去 /@/lib/echarts.js 补注册 ——
     漏注册的表现是「空白图表且控制台不报错」，没有任何显式信号。
     本组件用到的 line / bar 已在那里注册。
-->
<template>
  <DefaultHomeCard title="活动参与统计" icon="BarChartOutlined" extra=" ">
    <template #extra>
      <a-radio-group v-model:value="days" size="small" button-style="solid" @change="loadData">
        <a-radio-button :value="7">近 7 天</a-radio-button>
        <a-radio-button :value="30">近 30 天</a-radio-button>
      </a-radio-group>
    </template>

    <a-spin :spinning="loading">
      <div ref="trendRef" class="chart-box"></div>
      <div ref="typeRef" class="chart-box chart-box-short"></div>
      <div class="chart-caliber">
        参与 = 按会员去重的<b>人数</b>（非人次）。抽奖含未中奖与库存不足（有「谢谢参与」兜底）；
        任务只算有效推进（flow_type=1），被丢弃的事件不计；统计范围为已启用的活动。
        <br />
        ⚠️ 任务侧参与量天然偏小：重复上报被幂等拦下时不写流水，那部分无法统计。
        各玩法人数<b>不可相加</b>：同一个人参与多种玩法会被各算一次。
      </div>
    </a-spin>
  </DefaultHomeCard>
</template>

<script setup>
  import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue';
  import echarts from '/@/lib/echarts';
  import DefaultHomeCard from '/@/views/system/home/components/default-home-card.vue';
  import { marketingStatApi } from '/@/api/business/stat/marketing-stat-api';
  import { smartSentry } from '/@/lib/smart-sentry';

  // 与后端 GAMEPLAY_TYPES 保持一致；BASIC 没有参与行为，不出现在图上
  const TYPE_META = [
    { type: 'DRAW', label: '奖池抽奖', color: '#f59e0b' },
    { type: 'TASK', label: '任务驱动', color: '#8b5cf6' },
    { type: 'LOTTERY', label: 'FPE彩票', color: '#06b6d4' },
  ];

  const days = ref(7);
  const loading = ref(false);
  const trendRef = ref(null);
  const typeRef = ref(null);
  const trendChart = shallowRef(null);
  const typeChart = shallowRef(null);
  let ro = null;

  // ⚠️ 后端数值经 JsonConfig 可能序列化成字符串，一律 Number() 归一化后再进图表，
  //    否则 echarts 会把字符串当类目轴处理，图形静默画歪
  const num = (v) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  };

  function renderTrend(dateList, trendList) {
    if (!trendChart.value) return;
    const series = TYPE_META.map((meta) => ({
      name: meta.label,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      itemStyle: { color: meta.color },
      data: dateList.map((d) => {
        const hit = trendList.find((t) => t.statDate === d && t.activityType === meta.type);
        return num(hit && hit.memberCount);
      }),
    }));
    trendChart.value.setOption(
      {
        tooltip: { trigger: 'axis' },
        legend: { data: TYPE_META.map((m) => m.label), top: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
        grid: { top: 32, right: 16, bottom: 24, left: 40 },
        xAxis: { type: 'category', boundaryGap: false, data: dateList, axisLabel: { fontSize: 11 } },
        yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
        series,
      },
      true
    );
  }

  function renderByType(byTypeList) {
    if (!typeChart.value) return;
    // 横向柱状图：刻意不用饼图——各玩法人数会跨类型重复计数，画成饼图等于承诺「占比」，而它们加起来不等于总人数
    const rows = TYPE_META.map((meta) => {
      const hit = byTypeList.find((b) => b.activityType === meta.type);
      return { label: meta.label, value: num(hit && hit.memberCount), color: meta.color };
    });
    typeChart.value.setOption(
      {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}：{c} 人' },
        grid: { top: 8, right: 32, bottom: 8, left: 70, containLabel: false },
        xAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
        yAxis: { type: 'category', data: rows.map((r) => r.label), axisLabel: { fontSize: 12 }, axisTick: { show: false } },
        series: [
          {
            type: 'bar',
            barWidth: 14,
            data: rows.map((r) => ({ value: r.value, itemStyle: { color: r.color, borderRadius: [0, 4, 4, 0] } })),
            label: { show: true, position: 'right', formatter: '{c} 人', fontSize: 11 },
          },
        ],
      },
      true
    );
  }

  // 保留最近一次数据：容器晚于数据拿到尺寸时要补画
  const lastData = shallowRef(null);

  function applyData(data) {
    if (!ensureCharts()) return;
    renderTrend(data.dateList || [], data.trendList || []);
    renderByType(data.byTypeList || []);
  }

  async function loadData() {
    loading.value = true;
    try {
      const res = await marketingStatApi.participation(days.value);
      lastData.value = res.data || {};
      applyData(lastData.value);
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  function resize() {
    trendChart.value && trendChart.value.resize();
    typeChart.value && typeChart.value.resize();
  }

  /*
   * ⚠️ 不能在 onMounted 里直接 echarts.init：
   * 卡片可能还在 a-spin / 栅格布局里没拿到尺寸，此时 init 会告警
   * 「Can't get DOM width or height」并把 0 尺寸锁死，表现为图表一片空白。
   * 改为等容器真的有尺寸了再初始化，并在尺寸变化时 resize。
   */
  function ensureCharts() {
    if (!trendRef.value || !typeRef.value) return false;
    if (!trendRef.value.clientWidth || !trendRef.value.clientHeight) return false;
    if (!trendChart.value) trendChart.value = echarts.init(trendRef.value);
    if (!typeChart.value) typeChart.value = echarts.init(typeRef.value);
    return true;
  }

  onMounted(() => {
    // 卡片在折叠/栅格变化时尺寸会变，只监听 window.resize 不够
    if (typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(() => {
        if (ensureCharts()) {
          resize();
          // 首次拿到尺寸时数据可能已经回来了，补画一次
          if (lastData.value) applyData(lastData.value);
        }
      });
      ro.observe(trendRef.value);
    }
    window.addEventListener('resize', resize);
    ensureCharts();
    loadData();
  });

  onBeforeUnmount(() => {
    ro && ro.disconnect();
    window.removeEventListener('resize', resize);
    trendChart.value && trendChart.value.dispose();
    typeChart.value && typeChart.value.dispose();
  });
</script>

<style lang="less" scoped>
  .chart-box {
    width: 100%;
    height: 220px;
  }

  .chart-box-short {
    height: 130px;
  }

  .chart-caliber {
    padding: 8px 4px 0;
    font-size: 11px;
    line-height: 1.6;
    color: #8c8c8c;
    border-top: 1px solid #f0f0f0;
  }
</style>
