<!--
  * 公告
  *
  * 公告与通知模板是两套东西，这个页面要让运营分清：
  *   · 模板按版本不可变（改一次加一版），因为历史通知指向版本号；
  *   · 公告可以原地改，因为正文就存在自己那一行里。
  *
  * 🔴 但改一条【已发布】的公告要谨慎：已经读过它的用户不会再收到提醒
  *    （游标已经越过它了）。要让所有人重新看到，应该发一条新公告。
  *
  * @Author:    alaric
  * @Date:      2026-09-14
  * @Copyright  weolwo
-->
<template>
  <a-card size="small" :bordered="false" :bodyStyle="{ padding: '16px' }">
    <a-row justify="end" class="mb-3">
      <a-button type="primary" @click="onAdd">
        <template #icon><PlusOutlined /></template>
        发布公告
      </a-button>
    </a-row>

    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      :loading="tableLoading"
      rowKey="id"
      :pagination="false"
      bordered
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'forceAck'">
          <a-tag v-if="record.forceAck === 1" color="red">强制确认</a-tag>
          <span v-else class="text-slate-400">普通</span>
        </template>

        <template v-if="column.dataIndex === 'category'">
          {{ CATEGORY_LABEL[record.category] ?? record.category }}
        </template>

        <template v-if="column.dataIndex === 'audienceType'">
          {{ AUDIENCE_LABEL[record.audienceType] ?? record.audienceType }}
        </template>

        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? '已发布' : '已下架' }}
          </a-tag>
        </template>

        <template v-if="column.dataIndex === 'action'">
          <a-space>
            <a @click="onEdit(record)">编辑</a>
            <a v-if="record.forceAck === 1" @click="onViewAck(record)">确认情况</a>
            <a-popconfirm title="下架后用户看不到，确认记录仍保留。确定？" @confirm="onOffline(record)">
              <a v-if="record.status === 1">下架</a>
            </a-popconfirm>
            <a-popconfirm
              title="⚠️ 物理删除，连同确认记录一起没。强制确认公告的确认记录是合规留痕，确定？"
              @confirm="onDelete(record)"
            >
              <a class="text-red-500">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>

  <a-drawer
    :title="form.id ? '编辑公告' : '发布公告'"
    :width="720"
    :open="drawerOpen"
    :maskClosable="false"
    :destroyOnClose="true"
    @close="drawerOpen = false"
  >
    <a-alert
      v-if="form.id && form.status === 1"
      type="warning"
      show-icon
      class="mb-4"
      message="这条公告已经发布过"
      description="已经读过它的用户不会因为你改了内容再收到提醒 —— 他们的已读游标已经越过它了。要让所有人重新看到，请发一条新公告。"
    />

    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <a-form-item label="标题" name="title">
        <a-input v-model:value="form.title" placeholder="公告标题" />
      </a-form-item>

      <a-form-item label="正文" name="content">
        <!-- 公告不用模板：它是运营手写的一整段成品，没有占位符要替换 -->
        <a-textarea v-model:value="form.content" :rows="6" placeholder="公告正文，直接下发给用户" />
      </a-form-item>

      <a-form-item label="分类" name="category">
        <a-select v-model:value="form.category" :options="CATEGORY_OPTIONS" placeholder="选择分类" />
      </a-form-item>

      <a-form-item label="强制确认" name="forceAck">
        <a-switch v-model:checked="forceAckOn" />
        <div class="mt-1 text-xs text-slate-500">
          开启后用户进 App 就弹窗，<b>必须点「我已阅读并知悉」才能继续</b>，并留下确认记录。
          <br />
          🔴 服务条款变更、停机维护这类才用它。普通公告开了会非常扰民 ——
          而且<b>没确认就每次进来都弹</b>，用户躲不掉。
        </div>
      </a-form-item>

      <a-form-item label="人群" name="audienceType">
        <a-select v-model:value="form.audienceType" :options="AUDIENCE_OPTIONS" />
        <div class="mt-1 text-xs text-slate-500">
          存的是<b>规则</b>不是名单 —— 名单一旦物化，行数就变成「用户数 × 公告数」。
        </div>
      </a-form-item>

      <a-form-item v-if="form.audienceType !== 'ALL'" label="注册时间界线" name="audienceBoundary">
        <a-date-picker v-model:value="audienceBoundary" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
      </a-form-item>

      <a-form-item label="生效时间" name="publishTime">
        <a-date-picker v-model:value="form.publishTime" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        <div class="mt-1 text-xs text-slate-500">未到这个时间用户看不到，可以提前排期。</div>
      </a-form-item>

      <a-form-item label="失效时间" name="expireTime">
        <a-date-picker v-model:value="form.expireTime" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        <div class="mt-1 text-xs text-slate-500">
          🔴 <b>必填</b>。过期后不再参与未读计算，也是归档的依据 ——
          留空的话这条公告会永远占着用户的红点。不填默认 30 天后。
        </div>
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="drawerOpen = false">取消</a-button>
        <a-button type="primary" @click="onSubmit">保存</a-button>
      </a-space>
    </template>
  </a-drawer>

  <a-modal v-model:open="ackOpen" title="确认情况" :footer="null">
    <a-statistic title="已确认人数" :value="ackCount" />
    <!--
      🔴 刻意只给分子。分母（当前命中人群的会员数）是动态的：新注册用户会进来，
      所以「覆盖率」永远不会停在 100%。给一个会一直往下掉的百分比，
      运营会以为出了问题。
    -->
    <a-alert type="info" show-icon class="mt-4">
      <template #message>这里只有已确认人数，没有覆盖率百分比。</template>
      <template #description>
        目标人群是<b>动态的</b> —— 新注册用户会不断进入分母，所以百分比只会一直往下掉。
        <br />
        也查不到「谁还没确认」：系统只记录确认过的人（否则一条公告就要给全体用户建一行）。
        不过不需要 —— <b>没确认的用户每次进 App 都会再弹一次</b>，机制自带催办。
      </template>
    </a-alert>
  </a-modal>
</template>

<script setup>
  import { ref, reactive, computed, watch, onMounted } from 'vue';
  import { message } from 'ant-design-vue';
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { announcementApi } from '/@/api/business/notification/announcement-api';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const CATEGORY_LABEL = { SYSTEM: '系统', TRADE: '交易物流', MARKETING: '活动营销' };
  const CATEGORY_OPTIONS = Object.entries(CATEGORY_LABEL).map(([value, label]) => ({ value, label }));

  const AUDIENCE_LABEL = {
    ALL: '全体会员',
    REGISTER_BEFORE: '某时间之前注册的',
    REGISTER_AFTER: '某时间之后注册的',
  };
  const AUDIENCE_OPTIONS = Object.entries(AUDIENCE_LABEL).map(([value, label]) => ({ value, label }));

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '标题', dataIndex: 'title' },
    { title: '类型', dataIndex: 'forceAck', width: 100 },
    { title: '分类', dataIndex: 'category', width: 110 },
    { title: '人群', dataIndex: 'audienceType', width: 160 },
    { title: '生效', dataIndex: 'publishTime', width: 170 },
    { title: '失效', dataIndex: 'expireTime', width: 170 },
    { title: '状态', dataIndex: 'status', width: 90 },
    { title: '操作', dataIndex: 'action', width: 230 },
  ];

  const tableData = ref([]);
  const tableLoading = ref(false);

  async function loadList() {
    tableLoading.value = true;
    try {
      tableData.value = (await announcementApi.list()) || [];
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(loadList);

  // ------------------------ 表单 ------------------------

  const drawerOpen = ref(false);
  const formRef = ref();
  const forceAckOn = ref(false);
  const audienceBoundary = ref(undefined);

  const formDefault = {
    id: undefined,
    title: undefined,
    content: undefined,
    category: 'SYSTEM',
    forceAck: 0,
    audienceType: 'ALL',
    audienceRule: undefined,
    publishTime: undefined,
    expireTime: undefined,
    status: 1,
  };

  const form = reactive({ ...formDefault });

  watch(forceAckOn, (on) => {
    form.forceAck = on ? 1 : 0;
  });

  const rules = computed(() => ({
    title: [{ required: true, message: '标题 必填' }],
    content: [{ required: true, message: '正文 必填' }],
    category: [{ required: true, message: '分类 必选' }],
    publishTime: [{ required: true, message: '生效时间 必填' }],
    // 失效时间不设 required：空着就按 30 天兜底（服务端也兜一次）。
    // 设成必填反而会挡住「我就想发个默认 30 天的」这种最常见的情况
  }));

  function onAdd() {
    Object.assign(form, formDefault);
    forceAckOn.value = false;
    audienceBoundary.value = undefined;
    drawerOpen.value = true;
  }

  function onEdit(record) {
    Object.assign(form, formDefault, record);
    forceAckOn.value = record.forceAck === 1;
    audienceBoundary.value = parseBoundary(record.audienceRule, record.audienceType);
    drawerOpen.value = true;
  }

  /** audience_rule 是 json：{"before": "..."} 或 {"after": "..."} */
  function parseBoundary(raw, audienceType) {
    if (!raw || audienceType === 'ALL') {
      return undefined;
    }
    try {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      return audienceType === 'REGISTER_BEFORE' ? parsed.before : parsed.after;
    } catch (e) {
      return undefined;
    }
  }

  async function onSubmit() {
    try {
      await formRef.value.validateFields();
    } catch (err) {
      message.error('参数验证错误，请仔细填写表单数据!');
      return;
    }

    SolvelaLoading.show();
    try {
      const params = { ...form };
      // 人群规则：ALL 不需要规则；另两种把界线拼成 json。
      // 🔴 存规则不存名单 —— 名单一物化，行数就变成「用户数 × 公告数」
      if (form.audienceType === 'ALL') {
        params.audienceRule = null;
      } else if (form.audienceType === 'REGISTER_BEFORE') {
        params.audienceRule = JSON.stringify({ before: audienceBoundary.value });
      } else {
        params.audienceRule = JSON.stringify({ after: audienceBoundary.value });
      }

      await announcementApi.save(params);
      message.success('操作成功');
      drawerOpen.value = false;
      await loadList();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  async function onOffline(record) {
    SolvelaLoading.show();
    try {
      await announcementApi.offline(record.id);
      message.success('已下架');
      await loadList();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  async function onDelete(record) {
    SolvelaLoading.show();
    try {
      await announcementApi.delete(record.id);
      message.success('已删除');
      await loadList();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ------------------------ 确认情况 ------------------------

  const ackOpen = ref(false);
  const ackCount = ref(0);

  async function onViewAck(record) {
    ackOpen.value = true;
    try {
      ackCount.value = (await announcementApi.ackCount(record.id)) || 0;
    } catch (err) {
      solvelaSentry.captureError(err);
      ackCount.value = 0;
    }
  }
</script>
