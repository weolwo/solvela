<!--
  * 安全中心 - 限流规则（网关方案 §6.1、§6.2、§6.5）
  *
  * 【场景 × 维度】每个接口归一个场景（代码里 @RateLimit 标的），每个场景按维度各有一行阈值：
  * 突发（令牌桶）、3 / 5 / 10 分钟窗口，可选长窗口（1h / 1d）。超了就按阶梯罚（60 秒 → … → 8 小时）。
  *
  * 【接口覆盖】个别热点接口可以单独覆盖某个维度：接口**只能从网关上报的路由清单里选**（不许手填 ——
  * 一个 {orderNo} 写成 {id} 的行永远匹配不上，而它在页面上看起来好好的）。路由已经不存在的覆盖行标红「已失效」。
  *
  * 【页头的版本】保存即重发网关快照，15 秒内各实例重载。页头列出已发布的版本与每个网关实例此刻加载的版本 ——
  * 不一致的实例标出来，不用猜改的东西生效没有。
  *
  * 【场景行不能删】每个场景 × 维度都要有一行，缺了网关退回内置默认值；不想要就把档位改成「关闭」。
  *
  * @Date 2026-10-17
-->
<template>
  <a-card size="small" :bordered="false" style="margin-bottom: 12px">
    <div class="versions">
      <span>已发布版本：</span>
      <a-tag v-if="versions.published" color="blue">{{ versions.published }}</a-tag>
      <a-tag v-else color="red">Redis 里没有快照（网关在用内置默认值）</a-tag>
      <span style="margin-left: 16px">网关实例：</span>
      <span v-if="!versions.instances || versions.instances.length === 0" class="cell-sub">一个都没在回报（网关没起来？）</span>
      <a-tag
        v-for="item in versions.instances"
        :key="item.instance"
        :color="item.version === versions.published ? 'green' : 'orange'"
        :title="item.version === versions.published ? '已加载最新版本' : '还没加载到最新版本（15 秒一次）'"
      >
        {{ item.instance }}：{{ item.version || '—' }}{{ item.source === 'BUILTIN' ? '（内置默认值）' : '' }}
      </a-tag>
      <a-button size="small" style="margin-left: 8px" @click="load">刷新</a-button>
    </div>
  </a-card>

  <a-card size="small" :bordered="false">
    <template #title>
      <a-button v-privilege="'security:rule'" type="primary" @click="openAddEndpoint">新增接口覆盖</a-button>
    </template>

    <a-table size="small" :dataSource="rules" :columns="columns" :pagination="false" :loading="loading" rowKey="id" bordered>
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'target'">
          <template v-if="record.targetType === RATE_LIMIT_TARGET_TYPE_ENUM.SCENE.value">
            <a-tag color="blue">场景</a-tag>
            <span>{{ text }}</span>
            <span class="cell-sub">（{{ sceneName(text) }}）</span>
          </template>
          <template v-else>
            <a-tag color="purple">接口</a-tag>
            <span class="cell-mono">{{ text }}</span>
            <a-tag v-if="!routeScene(text)" color="red" style="margin-left: 4px">已失效</a-tag>
            <div v-else class="cell-sub">所属场景：{{ sceneName(routeScene(text)) }}</div>
          </template>
        </template>

        <template v-else-if="column.dataIndex === 'dimension'">
          <a-tag :color="metaOf(RATE_LIMIT_DIMENSION_ENUM, text).color">{{ metaOf(RATE_LIMIT_DIMENSION_ENUM, text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'longWindow'">
          <span v-if="text">{{ text }} ≤ {{ record.limitLong }}</span>
          <span v-else class="cell-sub">—</span>
        </template>

        <template v-else-if="column.dataIndex === 'mode'">
          <a-tag :color="metaOf(RATE_LIMIT_MODE_ENUM, text).color">{{ metaOf(RATE_LIMIT_MODE_ENUM, text).desc }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a v-privilege="'security:rule'" @click="openEdit(record)">调整</a>
          <template v-if="record.targetType === RATE_LIMIT_TARGET_TYPE_ENUM.ENDPOINT.value">
            <a-divider type="vertical" />
            <a-popconfirm title="删掉这条接口覆盖？这个接口回到它所属场景的阈值" @confirm="doDelete(record)">
              <a v-privilege="'security:rule'" class="danger">删除</a>
            </a-popconfirm>
          </template>
        </template>
      </template>
    </a-table>
  </a-card>

  <!---------- 调整 / 新增 begin ----------->
  <a-modal v-model:open="editOpen" :title="editing ? '调整阈值' : '新增接口覆盖'" :confirmLoading="saving" @ok="doSave" width="640">
    <a-form :labelCol="{ span: 6 }">
      <template v-if="!editing">
        <a-form-item label="接口">
          <a-select
            v-model:value="form.endpointKey"
            show-search
            :options="routeOptions"
            placeholder="从网关上报的路由清单里选"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="维度">
          <a-radio-group v-model:value="form.dimension" :options="RATE_LIMIT_DIMENSION_OPTIONS" />
        </a-form-item>
      </template>
      <a-form-item v-else label="规则">
        <span>{{ editing.target }} · {{ metaOf(RATE_LIMIT_DIMENSION_ENUM, editing.dimension).desc }}</span>
      </a-form-item>

      <a-form-item label="突发 / 3 / 5 / 10 分钟">
        <a-space>
          <a-input-number v-model:value="form.burst" :min="1" placeholder="突发" />
          <a-input-number v-model:value="form.limit3m" :min="1" placeholder="3 分钟" />
          <a-input-number v-model:value="form.limit5m" :min="1" placeholder="5 分钟" />
          <a-input-number v-model:value="form.limit10m" :min="1" placeholder="10 分钟" />
        </a-space>
        <div class="cell-sub">
          要满足 突发 ≤ 3 分钟 ≤ 5 分钟 ≤ 10 分钟，而且<b>亚线性</b>（窗口越长每分钟允许得越少）——
          否则长窗口永远不会先触发，配了等于没配。服务端会校验。
        </div>
      </a-form-item>

      <a-form-item label="长窗口（可选）">
        <a-space>
          <a-select v-model:value="form.longWindow" allow-clear placeholder="不设" style="width: 100px" :options="LONG_WINDOW_OPTIONS" />
          <a-input-number v-model:value="form.limitLong" :min="1" placeholder="阈值" :disabled="!form.longWindow" />
        </a-space>
        <div class="cell-sub">抓「每 10 分钟都卡在阈值下、慢慢滴」的脚本。阈值可以比 10 分钟的还小（比如注册：一天只许 2 次）</div>
      </a-form-item>

      <a-form-item label="档位">
        <a-radio-group v-model:value="form.mode" :options="RATE_LIMIT_MODE_OPTIONS" />
        <div class="cell-sub">陪跑：照常计数，只在日志里记「本来会罚」，不挡人 —— 新阈值先陪跑几天再生效</div>
      </a-form-item>
    </a-form>
  </a-modal>
  <!---------- 调整 / 新增 end ----------->
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { securityApi } from '/@/api/business/security/security-api';
  import {
    RATE_LIMIT_DIMENSION_ENUM,
    RATE_LIMIT_DIMENSION_OPTIONS,
    RATE_LIMIT_MODE_ENUM,
    RATE_LIMIT_MODE_OPTIONS,
    RATE_LIMIT_SCENE_ENUM,
    RATE_LIMIT_TARGET_TYPE_ENUM,
  } from '/@/constants/business/security/security-const';
  import { riskMetaOf } from '/@/constants/business/member/risk-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const LONG_WINDOW_OPTIONS = [
    { label: '1 小时', value: '1h' },
    { label: '1 天', value: '1d' },
  ];

  const columns = [
    { title: '目标', dataIndex: 'target', width: 320 },
    { title: '维度', dataIndex: 'dimension', width: 80 },
    { title: '突发', dataIndex: 'burst', width: 70 },
    { title: '3 分钟', dataIndex: 'limit3m', width: 70 },
    { title: '5 分钟', dataIndex: 'limit5m', width: 70 },
    { title: '10 分钟', dataIndex: 'limit10m', width: 80 },
    { title: '长窗口', dataIndex: 'longWindow', width: 110 },
    { title: '档位', dataIndex: 'mode', width: 80 },
    { title: '操作', dataIndex: 'action', width: 120, fixed: 'right' },
  ];

  const loading = ref(false);
  const rules = ref([]);
  const routes = ref([]);
  const versions = ref({ published: null, instances: [] });

  function metaOf(enumObj, value) {
    return riskMetaOf(enumObj, value, { desc: value, color: 'default' });
  }

  function sceneName(scene) {
    return RATE_LIMIT_SCENE_ENUM[scene]?.desc || scene || '—';
  }

  /** 接口所属的场景；接口已不在路由清单里时为 undefined（覆盖行标红「已失效」） */
  function routeScene(endpointKey) {
    return routes.value.find((r) => r.endpointKey === endpointKey)?.scene;
  }

  const routeOptions = computed(() =>
    routes.value.map((r) => ({ label: `${r.endpointKey}（${sceneName(r.scene)}）`, value: r.endpointKey })),
  );

  async function load() {
    loading.value = true;
    try {
      const res = await securityApi.rateLimitPage();
      rules.value = res.rules;
      routes.value = res.routes;
      versions.value = res.versions;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  onMounted(load);

  // ---------------------------- 调整 / 新增 ----------------------------

  const editOpen = ref(false);
  const saving = ref(false);
  /** 正在调整的那一行；新增接口覆盖时为 null */
  const editing = ref(null);
  const form = reactive({});

  function openEdit(record) {
    editing.value = record;
    Object.assign(form, {
      burst: record.burst,
      limit3m: record.limit3m,
      limit5m: record.limit5m,
      limit10m: record.limit10m,
      longWindow: record.longWindow || undefined,
      limitLong: record.limitLong ?? undefined,
      mode: record.mode,
    });
    editOpen.value = true;
  }

  function openAddEndpoint() {
    editing.value = null;
    Object.assign(form, {
      endpointKey: undefined,
      dimension: RATE_LIMIT_DIMENSION_ENUM.IP.value,
      burst: 5,
      limit3m: 10,
      limit5m: 15,
      limit10m: 25,
      longWindow: undefined,
      limitLong: undefined,
      mode: RATE_LIMIT_MODE_ENUM.SHADOW.value,
    });
    editOpen.value = true;
  }

  function values() {
    return {
      burst: form.burst,
      limit3m: form.limit3m,
      limit5m: form.limit5m,
      limit10m: form.limit10m,
      longWindow: form.longWindow || null,
      limitLong: form.longWindow ? form.limitLong : null,
      mode: form.mode,
    };
  }

  async function doSave() {
    saving.value = true;
    try {
      const res = editing.value
        ? await securityApi.updateRateLimitRule({ id: editing.value.id, values: values() })
        : await securityApi.addRateLimitEndpoint({ endpointKey: form.endpointKey, dimension: form.dimension, values: values() });
      message.success(res);
      editOpen.value = false;
      await load();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  async function doDelete(record) {
    try {
      message.success(await securityApi.deleteRateLimitEndpoint({ id: record.id }));
      await load();
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }
</script>

<style scoped>
  .versions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
  }

  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .cell-mono {
    font-family: var(--font-family-monospace, monospace);
    font-size: 12px;
  }

  .danger {
    color: #ff4d4f;
  }
</style>
