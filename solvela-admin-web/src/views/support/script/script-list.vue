<!--
  * 脚本管理
  *
  * 定位：脚本的**只读台账 + 挂载入口**。
  *
  * 🔴 本页<b>不能编辑脚本内容</b>，这是设计不是遗漏。脚本的权威在后端项目的
  *    resources/scripts/*.ql 里，走 git 管理 + 发版 —— 换来的是「语法错误在应用启动时
  *    就暴露、坏脚本进不了生产」，代价是运营改不了脚本。这个取舍已确认。
  *
  * 本页要回答的两个问题：
  *   ① 现在有哪些脚本、各自什么契约（列表）
  *   ② 改这个脚本会影响哪些业务对象（详情里的「引用关系」）
  * 第 ② 个是 t_script_ref 这张表存在的唯一理由，所以它在详情页占最大篇幅。
  *
  * @Date 2026-08-23
-->
<template>
  <a-card size="small">
    <a-alert type="info" show-icon class="mb-3">
      <template #message>脚本内容不在这里编辑</template>
      <template #description>
        脚本的权威在后端项目 <code>resources/scripts/</code> 目录下，走 git 管理 + 发版。
        这样语法错误在应用启动时就会暴露，坏脚本进不了生产。本页只能查看内容与维护挂载关系。
      </template>
    </a-alert>

    <!---------- 筛选 ----------->
    <a-space wrap class="mb-3">
      <a-select
        v-model:value="queryForm.domain"
        style="width: 150px"
        placeholder="全部业务域"
        allow-clear
        :options="domainOptions"
        @change="filterData"
      />
      <a-select
        v-model:value="queryForm.scene"
        style="width: 190px"
        placeholder="全部场景"
        allow-clear
        :options="sceneOptions"
        @change="filterData"
      />
      <a-input-search v-model:value="queryForm.keyword" style="width: 240px" placeholder="按编码或名称搜索" allow-clear @change="filterData" />
      <a-checkbox v-model:checked="queryForm.onlyUnused" @change="filterData">只看没被引用的</a-checkbox>
      <a-button @click="queryList">刷新</a-button>
      <span class="list-total">共 {{ tableData.length }} / {{ allData.length }} 个脚本</span>
    </a-space>

    <!---------- 列表 ----------->
    <a-table rowKey="scriptCode" :dataSource="tableData" :columns="tableColumns" :loading="tableLoading" :pagination="false" size="small" bordered>
      <template #bodyCell="{ record, column }">
        <template v-if="column.dataIndex === 'scriptName'">
          <a @click="openDetail(record)">{{ record.scriptName }}</a>
          <div class="cell-sub">{{ record.scriptCode }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'sceneTitle'">
          <a-tag color="blue">{{ record.sceneTitle || record.scene }}</a-tag>
          <div class="cell-sub">返回 {{ record.returnType }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'refCount'">
          <!-- 0 引用不是错误，只是说明它还没被挂上去，可以安全删掉 -->
          <a-tag v-if="record.refCount > 0" color="green">{{ record.refCount }} 处引用</a-tag>
          <a-tag v-else>未被引用</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag v-if="record.status === 1" color="green">正常</a-tag>
          <a-tooltip v-else title="文件已从项目里删除，但记录保留（可能还有引用指着它）">
            <a-tag color="red">文件已删除</a-tag>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'operate'">
          <a @click="openDetail(record)">查看</a>
        </template>
      </template>
    </a-table>

    <!---------- 详情抽屉 ----------->
    <a-drawer v-model:open="detailOpen" :title="detail.scriptName || '脚本详情'" width="900" :destroy-on-close="true">
      <a-spin :spinning="detailLoading">
        <a-descriptions :column="2" size="small" bordered class="mb-4">
          <a-descriptions-item label="脚本编码">{{ detail.scriptCode }}</a-descriptions-item>
          <a-descriptions-item label="版本">v{{ detail.version }}</a-descriptions-item>
          <a-descriptions-item label="业务域">{{ detail.domainTitle || detail.domain }}</a-descriptions-item>
          <a-descriptions-item label="场景">{{ detail.sceneTitle || detail.scene }}</a-descriptions-item>
          <a-descriptions-item label="返回类型">{{ detail.returnType }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{ detail.updateTime }}</a-descriptions-item>
          <a-descriptions-item label="文件路径" :span="2">
            <code>{{ detail.filePath }}</code>
          </a-descriptions-item>
          <a-descriptions-item label="用途" :span="2">{{ detail.description }}</a-descriptions-item>
        </a-descriptions>

        <!-- 引用关系排在脚本内容之前：t_script_ref 这张表就是为「改它会影响谁」存在的 -->
        <a-divider orientation="left">
          引用关系
          <a-tooltip title="改这个脚本会影响下面这些业务对象。这是脚本引用表存在的唯一理由">
            <QuestionCircleOutlined class="hint-icon" />
          </a-tooltip>
        </a-divider>

        <a-table rowKey="id" :dataSource="refs" :columns="refColumns" :loading="refsLoading" :pagination="false" size="small" bordered>
          <template #emptyText>
            <div class="empty-hint">还没有任何业务对象引用它 —— 可以安全地改或删</div>
          </template>
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'refPointTitle'">
              {{ record.refPointTitle || `${record.refType} / ${record.refSlot}` }}
            </template>
            <template v-else-if="column.dataIndex === 'operate'">
              <a-popconfirm title="摘除后该业务对象将不再执行此脚本，确认？" @confirm="unbind(record)">
                <a class="danger-link">摘除</a>
              </a-popconfirm>
            </template>
          </template>
        </a-table>

        <!-- 挂载表单：挂载点只列出与本脚本场景兼容的，避免选了才被后端拒绝 -->
        <a-form layout="inline" class="bind-form">
          <a-form-item label="挂载点">
            <a-select v-model:value="bindForm.refPoint" style="width: 220px" placeholder="选择挂载点" :options="compatiblePointOptions" />
          </a-form-item>
          <a-form-item label="业务对象编码">
            <a-input v-model:value="bindForm.refId" style="width: 220px" :placeholder="refIdPlaceholder" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="bindLoading" @click="bind">挂载</a-button>
          </a-form-item>
        </a-form>
        <div v-if="compatiblePointOptions.length === 0" class="no-point-hint">
          没有挂载点接受「{{ detail.sceneTitle || detail.scene }}」这个场景的脚本
        </div>

        <a-divider orientation="left">
          脚本内容
          <a-tag color="orange">只读</a-tag>
        </a-divider>
        <SolvelaCodeEditor :value="detail.content || ''" language="java" theme="vs-dark" height="360px" :readOnly="true" />

        <a-divider orientation="left">本场景可用的变量</a-divider>
        <a-table rowKey="name" :dataSource="sceneParams" :columns="paramColumns" :pagination="false" size="small" bordered>
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'required'">
              <a-tag :color="record.required ? 'red' : 'default'">{{ record.required ? '必填' : '可选' }}</a-tag>
            </template>
          </template>
        </a-table>
      </a-spin>
    </a-drawer>
  </a-card>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { scriptApi } from '/@/api/support/script-api.js';
  import SolvelaCodeEditor from '/@/components/business/code-editor/SolvelaCodeEditor.vue';

  // ---------------------------- 列表 ----------------------------

  const tableColumns = reactive([
    { title: '脚本', dataIndex: 'scriptName', width: 260 },
    { title: '业务域', dataIndex: 'domainTitle', width: 100 },
    { title: '场景', dataIndex: 'sceneTitle', width: 160 },
    { title: '用途', dataIndex: 'description', ellipsis: true },
    { title: '版本', dataIndex: 'version', width: 70 },
    { title: '引用', dataIndex: 'refCount', width: 100 },
    { title: '状态', dataIndex: 'status', width: 110 },
    { title: '操作', dataIndex: 'operate', width: 70 },
  ]);

  const tableLoading = ref(false);
  const allData = ref([]);
  const tableData = ref([]);
  const domainOptions = ref([]);
  const sceneOptions = ref([]);

  const queryForm = reactive({
    domain: undefined,
    scene: undefined,
    keyword: '',
    onlyUnused: false,
  });

  onMounted(() => {
    queryList();
    loadRefPoints();
    loadScenes();
  });

  async function queryList() {
    try {
      tableLoading.value = true;
      const res = await scriptApi.list();
      allData.value = res.data || [];
      domainOptions.value = distinct(allData.value, 'domain', 'domainTitle');
      sceneOptions.value = distinct(allData.value, 'scene', 'sceneTitle');
      filterData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function distinct(list, valueKey, labelKey) {
    const seen = new Map();
    list.forEach((item) => {
      if (item[valueKey] && !seen.has(item[valueKey])) {
        seen.set(item[valueKey], { value: item[valueKey], label: item[labelKey] || item[valueKey] });
      }
    });
    return [...seen.values()];
  }

  function filterData() {
    const keyword = queryForm.keyword.trim().toLowerCase();
    tableData.value = allData.value.filter((item) => {
      if (queryForm.domain && item.domain !== queryForm.domain) return false;
      if (queryForm.scene && item.scene !== queryForm.scene) return false;
      if (queryForm.onlyUnused && item.refCount > 0) return false;
      if (!keyword) return true;
      return item.scriptCode.toLowerCase().includes(keyword) || (item.scriptName || '').toLowerCase().includes(keyword);
    });
  }

  // ---------------------------- 详情 ----------------------------

  const detailOpen = ref(false);
  const detailLoading = ref(false);
  const detail = ref({});

  async function openDetail(record) {
    detailOpen.value = true;
    detail.value = { ...record };
    resetBindForm();
    try {
      detailLoading.value = true;
      const res = await scriptApi.detail(record.scriptCode);
      detail.value = res.data || {};
      await loadRefs();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      detailLoading.value = false;
    }
  }

  // ---------------------------- 引用关系 ----------------------------

  const refColumns = reactive([
    { title: '挂载点', dataIndex: 'refPointTitle', width: 200 },
    { title: '业务对象编码', dataIndex: 'refId' },
    { title: '更新时间', dataIndex: 'updateTime', width: 180 },
    { title: '操作', dataIndex: 'operate', width: 80 },
  ]);

  const refs = ref([]);
  const refsLoading = ref(false);

  async function loadRefs() {
    if (!detail.value.scriptCode) return;
    try {
      refsLoading.value = true;
      const res = await scriptApi.refs(detail.value.scriptCode);
      refs.value = res.data || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      refsLoading.value = false;
    }
  }

  // ---------------------------- 挂载 / 摘除 ----------------------------

  const refPoints = ref([]);
  const bindLoading = ref(false);
  const bindForm = reactive({ refPoint: undefined, refId: '' });

  async function loadRefPoints() {
    try {
      const res = await scriptApi.refPointList();
      refPoints.value = res.data || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  // 后端挂载时会强校验场景一致，这里先过滤只是为了不让人选了才被拒
  const compatiblePointOptions = computed(() =>
    refPoints.value.filter((point) => point.expectedScene === detail.value.scene).map((point) => ({ value: point.refPoint, label: point.title }))
  );

  const refIdPlaceholder = computed(() => {
    const hit = refPoints.value.find((point) => point.refPoint === bindForm.refPoint);
    if (!hit) return '先选挂载点';
    return { PRIZE_POOL: '奖池编码', TASK_TEMPLATE: '模板编码', ACTIVITY: '活动编码' }[hit.refType] || '业务对象编码';
  });

  function resetBindForm() {
    bindForm.refPoint = undefined;
    bindForm.refId = '';
    refs.value = [];
  }

  async function bind() {
    if (!bindForm.refPoint || !bindForm.refId.trim()) {
      message.warning('请选择挂载点并填写业务对象编码');
      return;
    }
    try {
      bindLoading.value = true;
      await scriptApi.bind({
        refPoint: bindForm.refPoint,
        refId: bindForm.refId.trim(),
        scriptCode: detail.value.scriptCode,
      });
      message.success('挂载成功');
      bindForm.refId = '';
      await loadRefs();
      await queryList();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      bindLoading.value = false;
    }
  }

  async function unbind(record) {
    try {
      await scriptApi.unbind(record.refPoint, record.refId);
      message.success('已摘除');
      await loadRefs();
      await queryList();
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  // ---------------------------- 场景变量 ----------------------------

  const paramColumns = reactive([
    { title: '变量名', dataIndex: 'name', width: 180 },
    { title: '类型', dataIndex: 'type', width: 140 },
    { title: '必填', dataIndex: 'required', width: 80 },
    { title: '说明', dataIndex: 'description' },
  ]);

  const scenes = ref([]);

  async function loadScenes() {
    try {
      const res = await scriptApi.sceneList();
      scenes.value = res.data || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  const sceneParams = computed(() => {
    const hit = scenes.value.find((scene) => scene.scene === detail.value.scene);
    return hit ? hit.params : [];
  });
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

  .hint-icon {
    color: rgba(0, 0, 0, 0.45);
    font-size: 13px;
    margin-left: 4px;
  }

  .empty-hint {
    color: rgba(0, 0, 0, 0.45);
    padding: 12px 0;
  }

  .bind-form {
    margin-top: 12px;
  }

  .no-point-hint {
    margin-top: 8px;
    color: #d46b08;
    font-size: 12px;
  }

  .danger-link {
    color: #cf1322;
  }
</style>
