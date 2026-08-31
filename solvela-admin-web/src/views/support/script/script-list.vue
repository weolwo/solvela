<!--
  * 脚本管理
  *
  * 定位：脚本的**编写、发布、挂载**入口。
  *
  * 脚本的权威在 t_script：一行 = 一个版本，同一编码至多一行激活。
  * 本地 resources/scripts/ 退回到开发工作区的位置，运行期不读它。
  *
  * 🔴 页面把「保存」和「激活」摆成两个动作，是刻意的：
  *    保存只加一个版本、线上不变；激活才改变线上行为。
  *    合成一步的话，「我先存一下待会儿再看」会直接改掉生产逻辑。
  *
  * 🔴 激活与回滚是同一个按钮 —— 都是把 active 标记挪到某一行上。
  *    所以不存在「回滚功能没测过」这回事：每次发布都在测它。
  *
  * 本页要回答三个问题：
  *   ① 现在有哪些脚本、各自什么契约（列表）
  *   ② 这个脚本改过几版、现在跑的是哪一版（详情里的「版本」）
  *   ③ 改这个脚本会影响哪些业务对象（详情里的「引用关系」）
  * 第 ③ 个是 t_script_ref 这张表存在的唯一理由。
  *
  * @Date 2026-08-31
-->
<template>
  <a-card size="small">
    <!---------- 筛选 ----------->
    <a-space wrap class="mb-3">
      <a-button type="primary" v-privilege="'script:edit'" @click="openEditor(null)">
        <template #icon><PlusOutlined /></template>
        新增脚本
      </a-button>
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
          <div class="cell-sub">{{ record.scriptCode }}<SolvelaCopyIcon :value="record.scriptCode" /></div>
        </template>
        <template v-else-if="column.dataIndex === 'sceneTitle'">
          <a-tag color="blue">{{ record.sceneTitle || record.scene }}</a-tag>
          <div class="cell-sub">返回 {{ record.returnType }}</div>
        </template>
        <template v-else-if="column.dataIndex === 'version'">
          <!-- 没有激活版本不是小事：挂载点指着它，一被触发就报错 -->
          <a-tag v-if="record.active" color="green">v{{ record.version }} 生效中</a-tag>
          <a-tooltip v-else title="没有任何版本处于激活状态。挂载点指着它的话，一被触发就会报错">
            <a-tag color="red">未激活</a-tag>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'refCount'">
          <!-- 0 引用不是错误，只是说明它还没被挂上去 -->
          <a-tag v-if="record.refCount > 0" color="green">{{ record.refCount }} 处引用</a-tag>
          <a-tag v-else>未被引用</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'operate'">
          <a-space>
            <a @click="openDetail(record)">查看</a>
            <a v-privilege="'script:edit'" @click="editFromActive(record)">改一版</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <!---------- 详情抽屉 ----------->
    <a-drawer v-model:open="detailOpen" :title="detail.scriptName || '脚本详情'" width="1000" :destroy-on-close="true">
      <a-spin :spinning="detailLoading">
        <a-descriptions :column="2" size="small" bordered class="mb-4">
          <a-descriptions-item label="脚本编码">
            {{ detail.scriptCode }}<SolvelaCopyIcon :value="detail.scriptCode" />
          </a-descriptions-item>
          <a-descriptions-item label="生效版本">
            <a-tag v-if="activeVersion" color="green">v{{ activeVersion.version }}</a-tag>
            <a-tag v-else color="red">未激活</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="业务域">{{ detail.domainTitle || detail.domain }}</a-descriptions-item>
          <a-descriptions-item label="场景">{{ detail.sceneTitle || detail.scene }}</a-descriptions-item>
          <a-descriptions-item label="返回类型">{{ detail.returnType }}</a-descriptions-item>
          <a-descriptions-item label="版本数">{{ versions.length }}</a-descriptions-item>
          <a-descriptions-item label="用途" :span="2">{{ detail.description }}</a-descriptions-item>
        </a-descriptions>

        <!-- 引用关系排在最前：t_script_ref 就是为「改它会影响谁」存在的 -->
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
              <a-tooltip v-if="isUnwired(record.refPoint)" title="引擎里没有代码会执行这个槽位，挂着也不会生效">
                <a-tag color="orange">未接入</a-tag>
              </a-tooltip>
              <div v-if="record.refKey" class="cell-sub">键：{{ record.refKey }}<SolvelaCopyIcon :value="record.refKey" /></div>
            </template>
            <template v-else-if="column.dataIndex === 'operate'">
              <a-popconfirm title="摘除后该业务对象将不再执行此脚本，确认？" @confirm="unbind(record)">
                <a class="danger-link" v-privilege="'script:bind'">摘除</a>
              </a-popconfirm>
            </template>
          </template>
        </a-table>

        <!-- 挂载表单：挂载点只列出与本脚本场景兼容的，避免选了才被后端拒绝 -->
        <a-form layout="inline" class="bind-form">
          <a-form-item label="挂载点">
            <a-select
              v-model:value="bindForm.refPoint"
              style="width: 220px"
              placeholder="选择挂载点"
              :options="compatiblePointOptions"
              @change="onPointChange"
            />
          </a-form-item>
          <a-form-item :label="ownerLabel">
            <!--
              下拉而不是手输：编码是 10 位随机串，靠人记或去别的页面复制都是白白的出错机会。
              仍然 show-search + 可清空，编码多了要能搜。
            -->
            <a-select
              v-model:value="bindForm.refId"
              style="width: 260px"
              show-search
              allow-clear
              :loading="candidateLoading"
              :disabled="!bindForm.refPoint"
              :options="candidateOptions"
              :filter-option="filterCandidate"
              :placeholder="bindForm.refPoint ? '搜索名称或编码' : '先选挂载点'"
            />
          </a-form-item>
          <!-- 多值槽位才出现：单值槽位传了键后端会直接拒 -->
          <a-form-item v-if="selectedPoint?.keyed" :label="selectedPoint.keyTitle">
            <a-input v-model:value="bindForm.refKey" style="width: 180px" :placeholder="selectedPoint.keyTitle" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="bindLoading" v-privilege="'script:bind'" @click="bind">挂载</a-button>
          </a-form-item>
        </a-form>
        <div v-if="compatiblePointOptions.length === 0" class="no-point-hint">
          没有挂载点接受「{{ detail.sceneTitle || detail.scene }}」这个场景的脚本
        </div>

        <!---------- 版本 ----------->
        <a-divider orientation="left">
          版本
          <a-tooltip title="激活与回滚是同一个动作：把「生效中」挪到另一版上">
            <QuestionCircleOutlined class="hint-icon" />
          </a-tooltip>
        </a-divider>

        <a-table
          rowKey="id"
          :dataSource="versions"
          :columns="versionColumns"
          :loading="versionsLoading"
          :pagination="false"
          size="small"
          bordered
          :customRow="(record) => ({ onClick: () => (viewingVersionId = record.id) })"
          :rowClassName="(record) => (record.id === viewingVersionId ? 'row-viewing' : '')"
        >
          <template #bodyCell="{ record, column }">
            <template v-if="column.dataIndex === 'version'">
              <a-tag v-if="record.active" color="green">v{{ record.version }} 生效中</a-tag>
              <span v-else>v{{ record.version }}</span>
            </template>
            <template v-else-if="column.dataIndex === 'source'">
              <a-tag>{{ record.source === 'FILE' ? '项目文件' : '后台录入' }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'operate'">
              <a-space>
                <a @click.stop="viewingVersionId = record.id">看内容</a>
                <a-popconfirm
                  v-if="!record.active"
                  :title="`把线上切到 v${record.version}，${activeVersion ? `当前生效的 v${activeVersion.version} 会退成历史版本` : ''}。确认？`"
                  @confirm="activate(record)"
                >
                  <a v-privilege="'script:publish'" @click.stop>{{ isRollback(record) ? '回滚到这一版' : '激活' }}</a>
                </a-popconfirm>
                <a v-privilege="'script:edit'" @click.stop="openEditor(record)">基于它改一版</a>
              </a-space>
            </template>
          </template>
        </a-table>

        <a-divider orientation="left">
          脚本内容
          <a-tag v-if="viewingVersion" :color="viewingVersion.active ? 'green' : 'default'">
            v{{ viewingVersion.version }}{{ viewingVersion.active ? ' 生效中' : '' }}
          </a-tag>
        </a-divider>
        <SolvelaCodeEditor :value="viewingVersion?.content || ''" language="java" theme="vs-dark" height="360px" :readOnly="true" />

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

    <ScriptEditModal ref="editModal" :scenes="scenes" @saved="onSaved" />
  </a-card>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { PlusOutlined, QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { scriptApi } from '/@/api/support/script-api.js';
  import SolvelaCodeEditor from '/@/components/business/code-editor/SolvelaCodeEditor.vue';
  import ScriptEditModal from './components/script-edit-modal.vue';
  import SolvelaCopyIcon from '/@/components/framework/solvela-copy-icon/index.vue';

  // ---------------------------- 列表 ----------------------------

  const tableColumns = reactive([
    { title: '脚本', dataIndex: 'scriptName', width: 260 },
    { title: '业务域', dataIndex: 'domainTitle', width: 100 },
    { title: '场景', dataIndex: 'sceneTitle', width: 160 },
    { title: '用途', dataIndex: 'description', ellipsis: true },
    { title: '线上版本', dataIndex: 'version', width: 120 },
    { title: '引用', dataIndex: 'refCount', width: 100 },
    { title: '操作', dataIndex: 'operate', width: 130 },
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
      allData.value = res || [];
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
      await loadVersions(record.scriptCode);
      await loadRefs();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      detailLoading.value = false;
    }
  }

  // ---------------------------- 版本 ----------------------------

  const versionColumns = reactive([
    { title: '版本', dataIndex: 'version', width: 120 },
    { title: '改动说明', dataIndex: 'changeLog', ellipsis: true },
    { title: '来源', dataIndex: 'source', width: 100 },
    { title: '创建人', dataIndex: 'createBy', width: 120 },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    { title: '操作', dataIndex: 'operate', width: 240 },
  ]);

  const versions = ref([]);
  const versionsLoading = ref(false);
  /** 内容区当前展示的是哪一版。默认看生效中的那一版 */
  const viewingVersionId = ref(null);

  const activeVersion = computed(() => versions.value.find((item) => item.active));

  const viewingVersion = computed(() => versions.value.find((item) => item.id === viewingVersionId.value));

  /** 版本号比生效中的小 = 往回退，措辞上要说「回滚」而不是「激活」 */
  function isRollback(record) {
    return activeVersion.value && record.version < activeVersion.value.version;
  }

  async function loadVersions(scriptCode) {
    try {
      versionsLoading.value = true;
      const res = await scriptApi.versions(scriptCode);
      versions.value = res || [];
      const active = versions.value.find((item) => item.active);
      viewingVersionId.value = (active || versions.value[0])?.id || null;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      versionsLoading.value = false;
    }
  }

  async function activate(record) {
    try {
      await scriptApi.activate(record.id);
      // 后端各进程的本地缓存有 TTL，别把「已生效」说成瞬时的
      message.success(`已切到 v${record.version}，各服务最多 10 秒内生效`);
      await loadVersions(detail.value.scriptCode);
      await queryList();
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  // ---------------------------- 编辑 ----------------------------

  const editModal = ref();

  function openEditor(source) {
    editModal.value.open(source);
  }

  /** 列表页的「改一版」：先把生效版本的内容取回来，再打开编辑器 */
  async function editFromActive(record) {
    try {
      const list = await scriptApi.versions(record.scriptCode);
      const base = (list || []).find((item) => item.active) || (list || [])[0];
      if (!base) {
        message.warning('这个脚本还没有任何版本');
        return;
      }
      openEditor(base);
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  async function onSaved() {
    await queryList();
    if (detailOpen.value && detail.value.scriptCode) {
      await loadVersions(detail.value.scriptCode);
    }
  }

  // ---------------------------- 引用关系 ----------------------------

  const refColumns = reactive([
    { title: '挂载点', dataIndex: 'refPointTitle', width: 220 },
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
      refs.value = res || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      refsLoading.value = false;
    }
  }

  // ---------------------------- 挂载 / 摘除 ----------------------------

  const refPoints = ref([]);
  const bindLoading = ref(false);
  const bindForm = reactive({ refPoint: undefined, refId: undefined, refKey: '' });

  async function loadRefPoints() {
    try {
      const res = await scriptApi.refPointList();
      refPoints.value = res || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  /*
   * 挂载点下拉。两层过滤，含义不同：
   *   ① 场景不兼容的直接不列出来 —— 后端会强校验，列出来只会让人选了才被拒；
   *   ② 🔴 未接入的（wired=false）列出来但禁选 —— 引擎里没有代码会执行那个槽位，
   *      挂上去不报错、也永远不生效。藏起来会让人以为「怎么少了一个」，
   *      标着「未接入」并禁选，才说得清「它存在，但还不能用」。
   *      与抽奖算法里那个未实现的选项是同一个处理方式。
   */
  const compatiblePointOptions = computed(() =>
    refPoints.value
      .filter((point) => point.expectedScene === detail.value.scene)
      .map((point) => ({
        value: point.refPoint,
        label: point.wired === false ? `${point.title}（未接入）` : point.title,
        disabled: point.wired === false,
      }))
  );

  const selectedPoint = computed(() => refPoints.value.find((point) => point.refPoint === bindForm.refPoint));

  /** 表单标签跟着挂载点走：说「抽奖配置」比说「业务对象编码」清楚得多 */
  const ownerLabel = computed(() => (selectedPoint.value ? selectedPoint.value.ownerTitle || '业务对象' : '业务对象'));

  function isUnwired(refPoint) {
    const hit = refPoints.value.find((point) => point.refPoint === refPoint);
    return hit ? hit.wired === false : false;
  }

  // ---------------------------- 候选业务对象 ----------------------------

  const candidates = ref([]);
  const candidateLoading = ref(false);

  const candidateOptions = computed(() =>
    candidates.value.map((item) => ({
      value: item.code,
      label: `${item.name}（${item.code}）${item.remark ? ' · ' + item.remark : ''}`,
    }))
  );

  /** 名称和编码都能搜：运营记名字，开发记编码 */
  function filterCandidate(input, option) {
    return (option.label || '').toLowerCase().includes(String(input).toLowerCase());
  }

  async function onPointChange() {
    // 换挂载点等于换了对象类型，上一个选中的编码在新类型里没有意义
    bindForm.refId = undefined;
    candidates.value = [];
    if (!bindForm.refPoint) {
      return;
    }
    try {
      candidateLoading.value = true;
      candidates.value = (await scriptApi.refCandidateList(bindForm.refPoint)) || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      candidateLoading.value = false;
    }
  }

  function resetBindForm() {
    bindForm.refPoint = undefined;
    bindForm.refId = undefined;
    bindForm.refKey = '';
    candidates.value = [];
    refs.value = [];
  }

  async function bind() {
    if (!bindForm.refPoint || !bindForm.refId) {
      message.warning('请选择挂载点与业务对象');
      return;
    }
    if (selectedPoint.value?.keyed && !bindForm.refKey.trim()) {
      message.warning(`「${selectedPoint.value.title}」按${selectedPoint.value.keyTitle}分组，必须填`);
      return;
    }
    try {
      bindLoading.value = true;
      await scriptApi.bind({
        refPoint: bindForm.refPoint,
        refId: bindForm.refId,
        refKey: selectedPoint.value?.keyed ? bindForm.refKey.trim() : undefined,
        scriptCode: detail.value.scriptCode,
      });
      message.success('挂载成功');
      bindForm.refId = undefined;
      bindForm.refKey = '';
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
      await scriptApi.unbind(record.refPoint, record.refId, record.refKey);
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
      scenes.value = res || [];
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

  :deep(.row-viewing) {
    background-color: #e6f4ff;
  }
</style>
