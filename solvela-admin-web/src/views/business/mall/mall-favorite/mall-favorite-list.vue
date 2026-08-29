<!--
  * 商城-商品收藏（统计）
  *
  * 【这个页面只有统计，没有明细列表】一条收藏记录是「某人收藏了某商品」，
  * 逐条看它回答不了任何运营问题 —— 运营要问的是「哪些商品最多人想要」。
  *
  * 【收藏是需求信号，不是销量】想要但还没兑。所以每一行都带上兑换数和库存：
  * 「收藏很多但兑换很少」通常意味着积分定价偏高或长期缺货，那才是可执行的结论，
  * 而单看收藏排行看不出这一层。
  *
  * 【「想要但买不到」单独列出来并放在最前】它是这个页面唯一直接对应一个动作的东西 ——
  * 补货，或者重新上架。收藏榜首很可能正是一个早就卖空的爆款，混在一起就被淹没了。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:34:44
  * @Copyright  weolwo
-->
<template>
  <a-spin :spinning="loading">
    <!---------- 总量 ----------->
    <a-card size="small" :bordered="false" class="mb-3">
      <a-row :gutter="16">
        <a-col :span="4">
          <div class="stat-item">
            <div class="stat-value">{{ stat.totalCount }}</div>
            <div class="stat-label">收藏总数</div>
          </div>
        </a-col>
        <a-col :span="4">
          <div class="stat-item">
            <div class="stat-value">{{ stat.commodityCount }}</div>
            <div class="stat-label">被收藏商品数</div>
          </div>
        </a-col>
        <a-col :span="4">
          <div class="stat-item">
            <div class="stat-value">{{ stat.memberCount }}</div>
            <div class="stat-label">
              收藏人数
              <a-tooltip title="按会员号去重。一个人收藏 20 个商品，收藏数是 20、人数是 1">
                <QuestionCircleOutlined class="stat-help" />
              </a-tooltip>
            </div>
          </div>
        </a-col>
        <a-col :span="6">
          <div class="stat-item">
            <div class="stat-value" :class="stat.unavailableRank.length ? 'is-warn' : ''">
              {{ stat.unavailableRank.length }}
            </div>
            <div class="stat-label">有人想要但买不到</div>
          </div>
        </a-col>
        <a-col :span="6" class="text-right">
          <a-space>
            <span class="stat-label">排行条数</span>
            <a-select v-model:value="rankTopN" :options="TOP_N_OPTIONS" style="width: 90px" @change="loadStat" />
            <a-button size="small" @click="loadStat">
              <template #icon><ReloadOutlined /></template>
              刷新
            </a-button>
          </a-space>
        </a-col>
      </a-row>
    </a-card>

    <!---------- 想要但买不到：放在前面，因为它对应动作 ----------->
    <a-card size="small" :bordered="false" class="mb-3">
      <div class="section-head">
        <span class="section-title">⚠ 有人想要但当前买不到</span>
        <span class="section-tip">商品已下架，或可用库存为 0 —— 这些是最该先处理的</span>
      </div>
      <a-table size="small" row-key="commodityId" :data-source="stat.unavailableRank" :columns="unavailableColumns" :pagination="false" bordered>
        <template #bodyCell="{ index, column, record }">
          <template v-if="column.dataIndex === 'no'">
            <span class="rank-no is-warn-bg">{{ index + 1 }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'commodityName'">
            <div class="commodity-cell">
              <FileThumb v-if="record.coverFileId" :file-id="record.coverFileId" :height="36" />
              <div>
                <div>{{ record.commodityName }}</div>
                <div class="cell-sub">{{ record.commodityCode }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'reason'">
            <a-tag v-if="record.commodityStatus !== COMMODITY_STATUS_ENUM.ON.value" color="default">
              {{ statusDesc(record.commodityStatus) }}
            </a-tag>
            <a-tag v-else color="red">零库存</a-tag>
          </template>
        </template>
        <template #emptyText>
          <span class="cell-sub">很好，被收藏的商品当前都买得到</span>
        </template>
      </a-table>
    </a-card>

    <!---------- 收藏排行 ----------->
    <a-card size="small" :bordered="false">
      <div class="section-head">
        <span class="section-title">收藏排行</span>
        <span class="section-tip">收藏是「想要但还没兑」的信号；收藏高而兑换低，多半是定价偏高或长期缺货</span>
      </div>
      <a-table size="small" row-key="commodityId" :data-source="stat.rank" :columns="rankColumns" :pagination="false" bordered>
        <template #bodyCell="{ index, column, record }">
          <template v-if="column.dataIndex === 'no'">
            <span class="rank-no" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'commodityName'">
            <div class="commodity-cell">
              <FileThumb v-if="record.coverFileId" :file-id="record.coverFileId" :height="36" />
              <div>
                <div>{{ record.commodityName }}</div>
                <div class="cell-sub">{{ record.commodityCode }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'commodityStatus'">
            <a-tag :color="statusMeta(record.commodityStatus).color">{{ statusMeta(record.commodityStatus).desc }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'availableStock'">
            <span :class="record.availableStock > 0 ? '' : 'is-warn'">{{ record.availableStock }}</span>
          </template>
        </template>
        <template #emptyText>
          <span class="cell-sub">还没有任何收藏记录</span>
        </template>
      </a-table>
    </a-card>
  </a-spin>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { QuestionCircleOutlined, ReloadOutlined } from '@ant-design/icons-vue';
  import { mallFavoriteApi } from '/@/api/business/mall/mall-favorite-api';
  import { COMMODITY_STATUS_ENUM } from '/@/constants/business/mall/mall-commodity-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  const TOP_N_OPTIONS = [
    { value: 10, label: '前 10' },
    { value: 20, label: '前 20' },
    { value: 50, label: '前 50' },
  ];

  const rankColumns = ref([
    { title: '#', dataIndex: 'no', width: 50, align: 'center' },
    { title: '商品', dataIndex: 'commodityName' },
    { title: '收藏数', dataIndex: 'favoriteCount', width: 90 },
    { title: '已兑件数', dataIndex: 'soldCount', width: 100 },
    { title: '可用库存', dataIndex: 'availableStock', width: 100 },
    { title: '积分', dataIndex: 'pointsPrice', width: 90 },
    { title: '商品状态', dataIndex: 'commodityStatus', width: 100 },
  ]);

  const unavailableColumns = ref([
    { title: '#', dataIndex: 'no', width: 50, align: 'center' },
    { title: '商品', dataIndex: 'commodityName' },
    { title: '收藏数', dataIndex: 'favoriteCount', width: 90 },
    { title: '原因', dataIndex: 'reason', width: 110 },
    { title: '可用库存', dataIndex: 'availableStock', width: 100 },
    { title: '已兑件数', dataIndex: 'soldCount', width: 100 },
  ]);

  function statusMeta(value) {
    return Object.values(COMMODITY_STATUS_ENUM).find((item) => item.value === value) || { desc: value, color: 'default' };
  }

  function statusDesc(value) {
    return statusMeta(value).desc;
  }

  const loading = ref(false);
  const rankTopN = ref(10);
  const stat = reactive({
    totalCount: 0,
    commodityCount: 0,
    memberCount: 0,
    rank: [],
    unavailableRank: [],
  });

  async function loadStat() {
    loading.value = true;
    try {
      const res = await mallFavoriteApi.queryStat(rankTopN.value);
      Object.assign(stat, res || {});
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  onMounted(loadStat);
</script>

<style scoped lang="less">
  .mb-3 {
    margin-bottom: 12px;
  }

  .text-right {
    text-align: right;
  }

  .stat-item {
    padding: 4px 0;
  }

  .stat-value {
    font-size: 22px;
    font-weight: 600;
    line-height: 1.3;
    color: #1e293b;
  }

  .stat-value.is-warn,
  .is-warn {
    color: #ef4444;
  }

  .stat-label {
    font-size: 12px;
    color: #64748b;
  }

  .stat-help {
    color: #cbd5e1;
  }

  .section-head {
    display: flex;
    gap: 10px;
    align-items: baseline;
    margin-bottom: 10px;
  }

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
  }

  .section-tip,
  .cell-sub {
    font-size: 12px;
    color: #bfbfbf;
  }

  .rank-no {
    display: inline-block;
    width: 20px;
    height: 20px;
    font-size: 12px;
    line-height: 20px;
    color: #64748b;
    text-align: center;
    background: #f1f5f9;
    border-radius: 4px;
  }

  .rank-no.is-top {
    font-weight: 600;
    color: #fff;
    background: #f97316;
  }

  .rank-no.is-warn-bg {
    font-weight: 600;
    color: #fff;
    background: #ef4444;
  }

  .commodity-cell {
    display: flex;
    gap: 8px;
    align-items: center;
  }
</style>
