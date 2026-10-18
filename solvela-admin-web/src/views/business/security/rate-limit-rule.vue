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
  * 【🔴 弹窗里那几条约束是当场算的，不是等服务端拒绝】
  * 「突发 ≤ 3 ≤ 5 ≤ 10」与「亚线性」是 RateLimitRuleAdminService.RuleValues.validate() 的规则，
  * 这里按同一条式子实时算一遍标在那一行上，并把每个窗口折算成「次 / 分」显示 ——
  * 「亚线性」这个词解释三行，不如四个折算值并排摆着直观。服务端那份仍然是说了算的，
  * 这里只是别让人把四个数填完、点了保存，才看见一条红字。
  *
  * @Date 2026-10-18
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
  <a-modal
    v-model:open="editOpen"
    :title="editing ? '调整阈值' : '新增接口覆盖'"
    :width="720"
    :maskClosable="false"
    :destroyOnClose="true"
  >
    <!-- 🔴 在改哪一行要摆在最上面：标题只放得下「调整阈值」四个字，而人要确认的是「我在改哪个接口的哪个维度」 -->
    <div class="modal-target">
      <template v-if="editing">
        <a-tag :color="editing.targetType === RATE_LIMIT_TARGET_TYPE_ENUM.SCENE.value ? 'blue' : 'purple'">
          {{ editing.targetType === RATE_LIMIT_TARGET_TYPE_ENUM.SCENE.value ? '场景' : '接口' }}
        </a-tag>
        <span class="modal-target-name">{{ editing.target }}</span>
        <a-tag :color="metaOf(RATE_LIMIT_DIMENSION_ENUM, editing.dimension).color">
          {{ metaOf(RATE_LIMIT_DIMENSION_ENUM, editing.dimension).desc }} 维度
        </a-tag>
        <span v-if="editing.targetType === RATE_LIMIT_TARGET_TYPE_ENUM.SCENE.value" class="modal-target-note">
          改的是整个场景 —— 这个场景下所有没被单独覆盖的接口都会跟着变
        </span>
      </template>
      <span v-else class="modal-target-note">给单个热点接口配一组与所属场景不同的阈值；没覆盖的接口继续走场景行。</span>
    </div>

    <a-form ref="formRef" :model="form" :rules="formRules" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <template v-if="!editing">
        <a-form-item label="接口" name="endpointKey">
          <a-select
            v-model:value="form.endpointKey"
            show-search
            allow-clear
            option-filter-prop="label"
            :options="routeOptions"
            placeholder="输关键字筛选 —— 只能从网关上报的路由清单里选"
            @change="onEndpointChange"
          />
        </a-form-item>
        <a-form-item label="维度" name="dimension">
          <a-radio-group v-model:value="form.dimension" button-style="solid" @change="prefillFromScene">
            <a-radio-button
              v-for="item in RATE_LIMIT_DIMENSION_OPTIONS"
              :key="item.value"
              :value="item.value"
              :disabled="coveredDimensions.includes(item.value)"
            >
              {{ item.label }}
            </a-radio-button>
          </a-radio-group>
          <div v-if="coveredDimensions.length" class="form-hint">灰掉的维度这个接口已经有覆盖行了 —— 回表里改那一行</div>
        </a-form-item>
      </template>

      <a-form-item label="窗口阈值" :validate-status="limitsError ? 'error' : ''" :help="limitsError">
        <!-- 四个数字横着摆、各自带小标题与「次 / 分」折算：亚线性解释三行，不如把折算值并排放出来 -->
        <a-row :gutter="8">
          <a-col v-for="item in LIMIT_FIELDS" :key="item.key" :span="6">
            <div class="field-cap">{{ item.label }}</div>
            <a-input-number v-model:value="form[item.key]" :min="1" :max="999999" :precision="0" style="width: 100%" />
            <div class="field-rate">{{ rateText(item) }}</div>
          </a-col>
        </a-row>
        <div class="form-hint">
          要满足 突发 ≤ 3 分钟 ≤ 5 分钟 ≤ 10 分钟，而且<b>每分钟允许量随窗口变长而变小</b>（亚线性）——
          否则长窗口永远不会先触发，配了等于没配。
        </div>
      </a-form-item>

      <a-form-item label="长窗口" name="limitLong">
        <a-space>
          <a-select
            v-model:value="form.longWindow"
            allow-clear
            placeholder="不设"
            style="width: 110px"
            :options="LONG_WINDOW_OPTIONS"
            @change="onLongWindowChange"
          />
          <a-input-number
            v-model:value="form.limitLong"
            :min="1"
            :precision="0"
            placeholder="阈值"
            :disabled="!form.longWindow"
            style="width: 140px"
          />
          <span v-if="form.longWindow" class="field-cap">次 / {{ longWindowLabel }}</span>
        </a-space>
        <div class="form-hint">抓「每 10 分钟都卡在阈值下、慢慢滴」的脚本。阈值可以比 10 分钟的还小（比如注册：一天只许 2 次）</div>
      </a-form-item>

      <a-form-item label="档位" name="mode">
        <a-radio-group v-model:value="form.mode" button-style="solid" option-type="button" :options="RATE_LIMIT_MODE_OPTIONS" />
        <div class="form-hint">{{ MODE_HINT[form.mode] }}</div>
      </a-form-item>

      <!-- 🔴 切到生效是这个弹窗里唯一会立刻挡住真人的操作，它要有一句明确的提醒 -->
      <a-alert
        v-if="turningOnEnforce"
        type="warning"
        show-icon
        message="切到「生效」之后，超过阈值的请求会被真的挡下，并按阶梯罚（60 秒起，最长 8 小时）。"
        description="拿不准就先存成「陪跑」几天，到安全事件里看一眼「本来会罚」的量级 —— 那些就是会被挡住的人。"
      />
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="editOpen = false">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="!!limitsError" @click="doSave">保存</a-button>
      </a-space>
    </template>
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

  /** 四个窗口阈值。minutes 用来算「次 / 分」；突发是令牌桶的桶容量，没有窗口 */
  const LIMIT_FIELDS = [
    { key: 'burst', label: '突发', minutes: 0 },
    { key: 'limit3m', label: '3 分钟', minutes: 3 },
    { key: 'limit5m', label: '5 分钟', minutes: 5 },
    { key: 'limit10m', label: '10 分钟', minutes: 10 },
  ];

  /** 选中档位之后那一句「它到底会怎样」—— 光有「陪跑」两个字，运营会当成「关了」 */
  const MODE_HINT = {
    [RATE_LIMIT_MODE_ENUM.OFF.value]: '这个维度不再计数，等于这条规则不存在。',
    [RATE_LIMIT_MODE_ENUM.SHADOW.value]: '照常计数，只在日志与安全事件里记「本来会罚」，不挡人 —— 新阈值先陪跑几天再生效。',
    [RATE_LIMIT_MODE_ENUM.ENFORCE.value]: '超过阈值的请求会被真的挡下，并按阶梯罚（60 秒 → … → 8 小时）。',
  };

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
  const formRef = ref();
  /** 正在调整的那一行；新增接口覆盖时为 null */
  const editing = ref(null);
  /** 打开弹窗时的档位：只有「本来不是生效、现在要切成生效」才需要那条警告 */
  const originalMode = ref(undefined);
  const form = reactive({});

  const formRules = {
    endpointKey: [{ required: true, message: '从清单里选一个接口' }],
    dimension: [{ required: true, message: '选一个维度' }],
    mode: [{ required: true, message: '选一个档位' }],
    limitLong: [
      {
        validator: () =>
          !form.longWindow || form.limitLong >= 1
            ? Promise.resolve()
            : Promise.reject('选了长窗口就要填它的阈值 —— 两个要么都填、要么都不填'),
      },
    ],
  };

  /** 这个接口已经有覆盖行的维度：后端会拒绝重复，所以这里直接灰掉 */
  const coveredDimensions = computed(() =>
    rules.value
      .filter((r) => r.targetType === RATE_LIMIT_TARGET_TYPE_ENUM.ENDPOINT.value && r.target === form.endpointKey)
      .map((r) => r.dimension),
  );

  const longWindowLabel = computed(() => LONG_WINDOW_OPTIONS.find((o) => o.value === form.longWindow)?.label || '');

  const turningOnEnforce = computed(
    () => form.mode === RATE_LIMIT_MODE_ENUM.ENFORCE.value && originalMode.value !== RATE_LIMIT_MODE_ENUM.ENFORCE.value,
  );

  /**
   * 与 RateLimitRuleAdminService.RuleValues.validate() 同一条式子，在这里先算一遍。
   * 🔴 服务端那份才是准的 —— 这里只是别让人填完四个数、点了保存，才看见一条红字。
   */
  const limitsError = computed(() => {
    const { burst, limit3m, limit5m, limit10m } = form;
    const all = [burst, limit3m, limit5m, limit10m];
    if (all.some((n) => n === null || n === undefined || n === '')) {
      return '四个阈值都要填';
    }
    if (all.some((n) => n < 1)) {
      return '阈值至少是 1';
    }
    if (!(burst <= limit3m && limit3m <= limit5m && limit5m <= limit10m)) {
      return '要满足 突发 ≤ 3 分钟 ≤ 5 分钟 ≤ 10 分钟：短窗口比长窗口还宽，长窗口就永远先触发';
    }
    if (limit5m * 3 >= limit3m * 5) {
      return `5 分钟最多 ${Math.ceil((limit3m * 5) / 3) - 1}（要小于 3 分钟的 5/3 倍），否则 5 分钟窗口永远不会先触发`;
    }
    if (limit10m >= limit5m * 2) {
      return `10 分钟最多 ${limit5m * 2 - 1}（要小于 5 分钟的 2 倍），否则 10 分钟窗口永远不会先触发`;
    }
    return '';
  });

  function rateText(field) {
    const value = form[field.key];
    if (field.minutes === 0) {
      return '瞬时桶容量';
    }
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    return `${(value / field.minutes).toFixed(1)} 次 / 分`;
  }

  function openEdit(record) {
    editing.value = record;
    originalMode.value = record.mode;
    Object.assign(form, {
      endpointKey: undefined,
      dimension: record.dimension,
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
    // 新增的覆盖行从「陪跑」起步，所以一进来没有那条警告；人自己选了生效才弹
    originalMode.value = RATE_LIMIT_MODE_ENUM.SHADOW.value;
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

  /** 换接口：该接口已覆盖的维度要灰掉，选中的那个正好被灰掉就换一个没被占的 */
  function onEndpointChange() {
    if (coveredDimensions.value.includes(form.dimension)) {
      form.dimension = RATE_LIMIT_DIMENSION_OPTIONS.map((o) => o.value).find((v) => !coveredDimensions.value.includes(v));
    }
    prefillFromScene();
  }

  /**
   * 带入这个接口所属场景、同一维度此刻的阈值。
   *
   * 🔴 覆盖行的起点应该是「它正在被套用的那组数」，而不是一组写死的 5/10/15/25：
   * 人来这里是因为某个接口要比场景更紧（或更松），先看见现在是多少，才知道该往哪边挪。
   */
  function prefillFromScene() {
    const scene = routeScene(form.endpointKey);
    if (!scene) {
      return;
    }
    const sceneRule = rules.value.find(
      (r) => r.targetType === RATE_LIMIT_TARGET_TYPE_ENUM.SCENE.value && r.target === scene && r.dimension === form.dimension,
    );
    if (!sceneRule) {
      return;
    }
    Object.assign(form, {
      burst: sceneRule.burst,
      limit3m: sceneRule.limit3m,
      limit5m: sceneRule.limit5m,
      limit10m: sceneRule.limit10m,
      longWindow: sceneRule.longWindow || undefined,
      limitLong: sceneRule.limitLong ?? undefined,
    });
  }

  /** 清掉长窗口时一并清掉它的阈值：后端要求两个要么都填、要么都不填 */
  function onLongWindowChange() {
    if (!form.longWindow) {
      form.limitLong = undefined;
    }
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
    try {
      await formRef.value.validate();
    } catch {
      // 校验没过：错误已经标在对应的字段上，不必再弹一遍
      return;
    }
    if (limitsError.value) {
      return;
    }
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

  /* 弹窗顶上那条「在改哪一行」 */
  .modal-target {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin-bottom: 16px;
    padding: 8px 12px;
    background: #fafafa;
    border-radius: 6px;
  }

  .modal-target-name {
    font-family: var(--font-family-monospace, monospace);
    font-weight: 600;
  }

  .modal-target-note {
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  .form-hint {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  /* 阈值格子的小标题与「次 / 分」折算 */
  .field-cap {
    font-size: 12px;
    line-height: 20px;
    color: rgba(0, 0, 0, 0.65);
  }

  .field-rate {
    font-size: 12px;
    line-height: 18px;
    color: rgba(0, 0, 0, 0.35);
  }
</style>
