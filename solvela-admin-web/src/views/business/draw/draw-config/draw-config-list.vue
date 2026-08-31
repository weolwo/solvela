<!--
  * 抽奖配置
  *
  * 定位：抽奖<b>玩法这一层</b>的配置，与彩票配置同层。
  *
  * 层级是：活动 → 抽奖配置 → N 个奖池。这一层原本不存在，
  * 于是玩法级参数只能挂到奖池上，脚本更是没地方挂 ——
  * 脚本引擎要一个业务对象编码，而「一次抽奖」当时不是一个对象。
  *
  * 🔴 一个活动只能有一套抽奖，由数据库唯一键保证。
  *
  * 🔴 抽奖编码创建后不可改：脚本挂载点 DRAW_PLAY 引用的就是它，
  *    改了等于把已有挂载指向一个不存在的对象，而挂载表不会跟着动。
  *
  * @Date 2026-08-31
-->
<template>
  <a-card size="small">
    <a-alert type="info" show-icon class="mb-3">
      <template #message>重置周期与抽奖算法住在这里，不在奖池上</template>
      <template #description>
        重置周期决定两件事：奖项「单人限领次数」多久归零，以及统计「本轮已经抽了几次」时从哪天起算。
        奖池只管奖项怎么摆。坑位、概率、库存请到<b>抽奖工作台</b>配。
      </template>
    </a-alert>

    <a-space wrap class="mb-3">
      <a-button type="primary" v-privilege="'drawConfig:add'" @click="openForm(null)">
        <template #icon><PlusOutlined /></template>
        新增抽奖配置
      </a-button>
      <a-input-search v-model:value="keyword" style="width: 260px" placeholder="按活动/抽奖编码或名称搜索" allow-clear @change="filterData" />
      <a-button @click="queryList">刷新</a-button>
      <span class="list-total">共 {{ tableData.length }} / {{ allData.length }} 套</span>
    </a-space>

    <a-table rowKey="id" :dataSource="tableData" :columns="tableColumns" :loading="tableLoading" :pagination="false" size="small" bordered>
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'drawName'">
          <div>{{ record.drawName }}</div>
          <div class="cell-sub font-mono">{{ record.drawCode }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'activityName'">
          <div>{{ record.activityName || '—' }}</div>
          <div class="cell-sub font-mono">{{ record.activityCode }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'resetPeriod'">
          {{ resetPeriodOf(record.resetPeriod) }}
        </template>
        <template v-else-if="column.dataIndex === 'drawMode'">
          <!-- 只有「按概率」是真的，另一种还没实现。界面上必须说出来，否则运营会配一个假开关 -->
          <span>{{ drawModeOf(record.drawMode) }}</span>
          <a-tooltip v-if="record.drawMode === DRAW_MODE_ENUM.STOCK_RATIO.value" title="「按库存比例」尚未实现，当前仍按概率抽">
            <a-tag color="orange" class="ml-1">未实现</a-tag>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'poolCount'">
          <!-- 0 个奖池不是「还没配」那么轻描淡写：这套抽奖现在抽必然失败 -->
          <a-tag v-if="record.poolCount > 0" color="green">{{ record.poolCount }} 个奖池</a-tag>
          <a-tooltip v-else title="这套抽奖没有任何奖池，脚本挑不到池子，抽奖必然失败">
            <a-tag color="red">没有奖池</a-tag>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag v-if="record.status === 1" color="green">开启</a-tag>
          <a-tooltip v-else title="关闭后该活动的抽奖请求会被直接拒绝，脚本不会执行">
            <a-tag color="red">关闭</a-tag>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'operate'">
          <a-space>
            <a v-privilege="'drawConfig:update'" @click="openForm(record)">编辑</a>
            <a-popconfirm
              v-if="record.poolCount === 0"
              title="删除这套抽奖配置？该活动的抽奖将无法进行。"
              @confirm="onDelete(record)"
            >
              <a class="danger-link" v-privilege="'drawConfig:delete'">删除</a>
            </a-popconfirm>
            <a-tooltip v-else title="底下还有奖池，先到工作台处理奖池">
              <span class="disabled-link">删除</span>
            </a-tooltip>
          </a-space>
        </template>
      </template>
    </a-table>

    <!---------- 新增 / 编辑 ----------->
    <a-modal
      v-model:open="formOpen"
      :title="isAdd ? '新增抽奖配置' : `编辑：${form.drawName}`"
      :width="640"
      :maskClosable="false"
      :destroyOnClose="true"
    >
      <a-form :model="form" :label-col="{ span: 6 }">
        <a-form-item label="归属活动" required>
          <a-input v-if="isAdd" v-model:value="form.activityCode" placeholder="活动编码" />
          <span v-else class="readonly-field">
            <span class="font-mono">{{ form.activityCode }}</span>
            <a-tag color="default" class="ml-2">创建后不可修改</a-tag>
          </span>
        </a-form-item>

        <a-form-item label="抽奖编码" required>
          <a-input-group v-if="isAdd" compact>
            <a-input v-model:value="form.drawCode" style="width: calc(100% - 64px)" placeholder="10 位大写字母或数字" />
            <a-button :loading="generating" @click="generateCode">生成</a-button>
          </a-input-group>
          <span v-else class="readonly-field">
            <span class="font-mono">{{ form.drawCode }}</span>
            <a-tag color="default" class="ml-2">创建后不可修改</a-tag>
          </span>
          <div v-if="isAdd" class="form-hint">脚本挂载点「抽奖配置 - 玩法编排」填的就是它</div>
        </a-form-item>

        <a-form-item label="抽奖名称" required>
          <a-input v-model:value="form.drawName" placeholder="给人看的名字" />
        </a-form-item>

        <a-form-item label="重置周期" required>
          <a-select v-model:value="form.resetPeriod" :options="RESET_PERIOD_OPTIONS" placeholder="请选择重置周期" />
          <div class="form-hint">
            决定两件事：奖项「单人限领次数」多久归零，以及统计「本轮已抽几次」时从哪天起算。
            选「活动期间」表示整个活动内累计不重置。
          </div>
        </a-form-item>

        <a-form-item label="抽奖算法">
          <a-select v-model:value="form.drawMode" :options="DRAW_MODE_SELECTABLE_OPTIONS" />
          <div class="form-hint danger-hint">
⚠️ 「按库存比例」还没实现，所以是禁选的 —— 不是藏起来，是让你知道它存在但还不能用。
          </div>
        </a-form-item>

        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="STATUS_OPTIONS" />
          <div class="form-hint">关闭后该活动的抽奖请求会被直接拒绝，脚本不会执行。</div>
        </a-form-item>
      </a-form>

      <template #footer>
        <a-space>
          <a-button @click="formOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="onSubmit">保存</a-button>
        </a-space>
      </template>
    </a-modal>
  </a-card>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { drawConfigApi } from '/@/api/business/draw/draw-config-api';
  import {
    RESET_PERIOD_OPTIONS,
    DRAW_MODE_SELECTABLE_OPTIONS,
    DRAW_MODE_ENUM,
    resetPeriodOf,
    drawModeOf,
  } from '/@/constants/business/draw/prize-pool-config-const';

  const STATUS_OPTIONS = [
    { value: 1, label: '开启' },
    { value: 0, label: '关闭' },
  ];

  const tableColumns = reactive([
    { title: '抽奖', dataIndex: 'drawName', width: 220 },
    { title: '归属活动', dataIndex: 'activityName', width: 220 },
    { title: '重置周期', dataIndex: 'resetPeriod', width: 110 },
    { title: '抽奖算法', dataIndex: 'drawMode', width: 140 },
    { title: '奖池', dataIndex: 'poolCount', width: 110 },
    { title: '状态', dataIndex: 'status', width: 90 },
    { title: '创建人', dataIndex: 'createBy', width: 110 },
    { title: '操作', dataIndex: 'operate', width: 110 },
  ]);

  const tableLoading = ref(false);
  const allData = ref([]);
  const tableData = ref([]);
  const keyword = ref('');

  onMounted(queryList);

  async function queryList() {
    try {
      tableLoading.value = true;
      allData.value = (await drawConfigApi.list()) || [];
      filterData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function filterData() {
    const kw = keyword.value.trim().toLowerCase();
    tableData.value = !kw
      ? allData.value
      : allData.value.filter((item) =>
          [item.drawCode, item.drawName, item.activityCode, item.activityName].some((field) =>
            (field || '').toLowerCase().includes(kw)
          )
        );
  }

  // ---------------------------- 新增 / 编辑 ----------------------------

  const formOpen = ref(false);
  const saving = ref(false);
  const generating = ref(false);
  const isAdd = ref(true);

  const form = reactive({
    id: undefined,
    activityCode: '',
    drawCode: '',
    drawName: '',
    drawMode: DRAW_MODE_ENUM.PROBABILITY.value,
    resetPeriod: 'DAY',
    status: 1,
  });

  function openForm(record) {
    isAdd.value = !record;
    Object.assign(form, {
      id: record?.id,
      activityCode: record?.activityCode || '',
      drawCode: record?.drawCode || '',
      drawName: record?.drawName || '',
      drawMode: record?.drawMode ?? DRAW_MODE_ENUM.PROBABILITY.value,
      resetPeriod: record?.resetPeriod || 'DAY',
      status: record?.status ?? 1,
    });
    formOpen.value = true;
  }

  async function generateCode() {
    try {
      generating.value = true;
      form.drawCode = await drawConfigApi.generateCode();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      generating.value = false;
    }
  }

  async function onSubmit() {
    if (!form.activityCode.trim() || !form.drawCode.trim() || !form.drawName.trim() || !form.resetPeriod) {
      message.warning('活动编码、抽奖编码、名称、重置周期都是必填的');
      return;
    }
    try {
      saving.value = true;
      if (isAdd.value) {
        await drawConfigApi.add({ ...form, id: undefined });
      } else {
        await drawConfigApi.update({ ...form });
      }
      message.success('保存成功');
      formOpen.value = false;
      await queryList();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  async function onDelete(record) {
    try {
      await drawConfigApi.delete(record.id);
      message.success('已删除');
      await queryList();
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }
</script>

<style scoped lang="less">
  .list-total {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .font-mono {
    font-family: Consolas, Monaco, monospace;
  }

  .readonly-field {
    color: rgba(0, 0, 0, 0.85);
  }

  .form-hint {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
    line-height: 18px;
    margin-top: 2px;
  }

  .danger-hint {
    color: #d46b08;
  }

  .danger-link {
    color: #cf1322;
  }

  .disabled-link {
    color: rgba(0, 0, 0, 0.25);
    cursor: not-allowed;
  }

  .mb-3 {
    margin-bottom: 12px;
  }

  .ml-1 {
    margin-left: 4px;
  }

  .ml-2 {
    margin-left: 8px;
  }
</style>
