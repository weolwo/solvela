<!--
  * 通知模板
  *
  * 🔴 这个页面最要紧的一件事：让运营明白「编辑 = 新增一版」。
  *
  * t_member_notification 只存「模板编码 + 版本号 + 参数」，正文是读的时候现渲染的。
  * 如果原地改模板，所有历史通知的显示会被追溯篡改 —— 用户 1 月收到的
  * 「恭喜获得 100 积分」，6 月改完模板就变成另一句话。在金额/奖品类消息上
  * 这是事故级的：用户截图的和现在显示的对不上。
  *
  * 所以服务端压根没有 update 接口，只有 save（新增下一版）。页面上的措辞
  * 必须跟着说清楚，否则运营会疑惑版本号为什么一直涨。
  *
  * @Author:    alaric
  * @Date:      2026-09-14
  * @Copyright  weolwo
-->
<template>
  <a-alert type="info" show-icon class="mb-4">
    <template #message>
      模板<b>按版本不可变</b>：保存不会改动当前这一版，而是新增下一版。
    </template>
    <template #description>
      已经发出去的通知永远按<b>当时那一版</b>渲染，所以改模板不会影响历史消息的措辞。
      老版本因此必须留着 —— 这也是只能「停用」不能删除的原因。
    </template>
  </a-alert>

  <a-card size="small" :bordered="false" :bodyStyle="{ padding: '16px' }">
    <a-row justify="end" class="mb-3">
      <a-button type="primary" @click="onAdd">
        <template #icon><PlusOutlined /></template>
        新增模板
      </a-button>
    </a-row>

    <a-table
      size="small"
      :dataSource="tableData"
      :columns="columns"
      :loading="tableLoading"
      rowKey="templateCode"
      :pagination="false"
      bordered
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'version'">
          <a-tag color="blue">v{{ record.version }}</a-tag>
        </template>

        <template v-if="column.dataIndex === 'category'">
          {{ CATEGORY_LABEL[record.category] ?? record.category }}
        </template>

        <template v-if="column.dataIndex === 'paramKeys'">
          <!-- 占位符清单：改名会让老调用方漏传，用户就会看到字面的 ${xxx} -->
          <a-tag v-for="key in parseKeys(record.paramKeys)" :key="key">{{ key }}</a-tag>
        </template>

        <template v-if="column.dataIndex === 'action'">
          <a-space>
            <a @click="onEdit(record)">基于此版新建</a>
            <a @click="onViewVersions(record)">历史版本</a>
            <a-popconfirm
              title="停用后不再用它发新通知，但历史消息仍按它渲染。确定？"
              @confirm="onDisable(record)"
            >
              <a class="text-red-500">停用</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>

  <!-- 新增 / 基于某版新建 -->
  <a-drawer
    :title="form.templateCode ? `新增版本：${form.templateCode}` : '新增模板'"
    :width="720"
    :open="drawerOpen"
    :maskClosable="false"
    :destroyOnClose="true"
    @close="drawerOpen = false"
  >
    <a-alert type="warning" show-icon class="mb-4" message="保存会新增一个版本，不会改动任何已有版本。" />

    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
      <a-form-item label="模板编码" name="templateCode">
        <!--
          编码是代码引用的（NotificationTemplateEnum），新建已有编码 = 加一版；
          编一个代码里没有的编码出来，那个模板永远不会被任何业务方用到。
        -->
        <a-select
          v-model:value="form.templateCode"
          :options="TEMPLATE_CODE_OPTIONS"
          placeholder="选择要新增版本的模板"
          :disabled="Boolean(editingBase)"
        />
        <div class="mt-1 text-xs text-slate-500">
          编码由代码引用，只能从这个列表里选。选一个代码里没有的，那个模板永远不会被发送。
        </div>
      </a-form-item>

      <a-form-item label="分类" name="category">
        <a-select v-model:value="form.category" :options="CATEGORY_OPTIONS" placeholder="选择分类" />
        <div class="mt-1 text-xs text-slate-500">
          只影响 C 端 tab 分组与免打扰粒度。SYSTEM 用户关不掉。
        </div>
      </a-form-item>

      <a-form-item label="标题模板" name="titleTemplate">
        <a-input v-model:value="form.titleTemplate" placeholder="如：恭喜您中奖啦" />
      </a-form-item>

      <a-form-item label="正文模板" name="contentTemplate">
        <a-textarea v-model:value="form.contentTemplate" :rows="5" placeholder="用 ${key} 表示占位符" />
        <div class="mt-1 text-xs text-slate-500">
          占位符写成 <code>${'{'}key{'}'}</code>。🔴 <b>解析不到的占位符会原样显示给用户</b>，
          所以别写业务方没传的 key。
        </div>
      </a-form-item>

      <a-form-item label="占位符清单" name="paramKeys">
        <a-select v-model:value="paramKeyList" mode="tags" placeholder="回车添加，如 prizeName" />
        <div class="mt-1 text-xs text-slate-500">
          发送时按它校验。漏了只告警不拦截 —— 拦了的话，加一个占位符就会让所有老调用方发不出通知。
        </div>
      </a-form-item>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="drawerOpen = false">取消</a-button>
        <a-button type="primary" @click="onSubmit">保存为新版本</a-button>
      </a-space>
    </template>
  </a-drawer>

  <!-- 历史版本 -->
  <a-drawer :title="`历史版本：${versionCode}`" :width="640" :open="versionOpen" @close="versionOpen = false">
    <a-timeline>
      <a-timeline-item v-for="item in versionList" :key="item.version" :color="item.status === 1 ? 'blue' : 'gray'">
        <p>
          <a-tag :color="item.status === 1 ? 'blue' : 'default'">v{{ item.version }}</a-tag>
          <span class="text-xs text-slate-500">{{ item.createTime }}</span>
          <a-tag v-if="item.status !== 1" color="default" class="ml-1">已停用</a-tag>
        </p>
        <p class="font-medium">{{ item.titleTemplate }}</p>
        <p class="text-sm text-slate-600 whitespace-pre-wrap">{{ item.contentTemplate }}</p>
      </a-timeline-item>
    </a-timeline>
    <a-empty v-if="versionList.length === 0" description="没有历史版本" />
  </a-drawer>
</template>

<script setup>
  import { ref, reactive, computed, onMounted } from 'vue';
  import { message } from 'ant-design-vue';
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { notificationTemplateApi } from '/@/api/business/notification/notification-template-api';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  /** 与后端 NotificationCategoryEnum 对齐。SYSTEM 不可被用户关闭 */
  const CATEGORY_LABEL = {
    SYSTEM: '系统通知（用户关不掉）',
    TRADE: '交易物流',
    MARKETING: '活动营销',
  };

  const CATEGORY_OPTIONS = Object.entries(CATEGORY_LABEL).map(([value, label]) => ({ value, label }));

  /**
   * 与后端 NotificationTemplateEnum 对齐。
   *
   * 🔴 编码是<b>代码契约</b>，不是自由输入：业务方调 send() 时传的就是这些值。
   * 编一个代码里没有的编码出来，那个模板永远不会被发送 —— 而且不报错。
   */
  const TEMPLATE_CODE_OPTIONS = [
    { value: 'PRIZE_WON', label: 'PRIZE_WON（中奖）' },
    { value: 'DELIVERY_SHIPPED', label: 'DELIVERY_SHIPPED（发货）' },
    { value: 'ORDER_FULFILL_FAILED', label: 'ORDER_FULFILL_FAILED（履约失败，不退积分）' },
    { value: 'ORDER_CANCELLED', label: 'ORDER_CANCELLED（超时取消，积分已退）' },
    { value: 'ACCOUNT_LIMITED', label: 'ACCOUNT_LIMITED（账号受限）' },
    { value: 'COUPON_EXPIRING', label: 'COUPON_EXPIRING（券将过期）' },
  ];

  const columns = [
    { title: '模板编码', dataIndex: 'templateCode', width: 220 },
    { title: '当前版本', dataIndex: 'version', width: 100 },
    { title: '分类', dataIndex: 'category', width: 160 },
    { title: '标题', dataIndex: 'titleTemplate', width: 200 },
    { title: '占位符', dataIndex: 'paramKeys' },
    { title: '操作', dataIndex: 'action', width: 220 },
  ];

  const tableData = ref([]);
  const tableLoading = ref(false);

  async function loadList() {
    tableLoading.value = true;
    try {
      tableData.value = (await notificationTemplateApi.list()) || [];
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      tableLoading.value = false;
    }
  }

  onMounted(loadList);

  /** param_keys 是 json 文本。解析失败按空处理 —— 为一列展示把整页卡住不值得 */
  function parseKeys(raw) {
    if (!raw) {
      return [];
    }
    try {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }

  // ------------------------ 新增 / 新建版本 ------------------------

  const drawerOpen = ref(false);
  const formRef = ref();
  const editingBase = ref(null);
  const paramKeyList = ref([]);

  const formDefault = {
    templateCode: undefined,
    category: undefined,
    titleTemplate: undefined,
    contentTemplate: undefined,
    paramKeys: undefined,
    status: 1,
  };

  const form = reactive({ ...formDefault });

  const rules = computed(() => ({
    templateCode: [{ required: true, message: '模板编码 必选' }],
    category: [{ required: true, message: '分类 必选' }],
    titleTemplate: [{ required: true, message: '标题模板 必填' }],
    contentTemplate: [{ required: true, message: '正文模板 必填' }],
  }));

  function onAdd() {
    Object.assign(form, formDefault);
    editingBase.value = null;
    paramKeyList.value = [];
    drawerOpen.value = true;
  }

  /** 「基于此版新建」：把当前版本的内容带进来当草稿，运营改完保存就是下一版 */
  function onEdit(record) {
    Object.assign(form, formDefault, {
      templateCode: record.templateCode,
      category: record.category,
      titleTemplate: record.titleTemplate,
      contentTemplate: record.contentTemplate,
    });
    editingBase.value = record;
    paramKeyList.value = parseKeys(record.paramKeys);
    drawerOpen.value = true;
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
      // 版本号刻意不传：由服务端算「当前最大版本 + 1」。
      // 前端传的话，两个人同时编辑就会撞版本号
      const params = { ...form, paramKeys: paramKeyList.value.length ? JSON.stringify(paramKeyList.value) : null };
      const version = await notificationTemplateApi.save(params);
      message.success(`已保存为 v${version}`);
      drawerOpen.value = false;
      await loadList();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  async function onDisable(record) {
    SolvelaLoading.show();
    try {
      await notificationTemplateApi.disable(record.templateCode, record.version);
      message.success('已停用');
      await loadList();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  // ------------------------ 历史版本 ------------------------

  const versionOpen = ref(false);
  const versionCode = ref('');
  const versionList = ref([]);

  async function onViewVersions(record) {
    versionCode.value = record.templateCode;
    versionOpen.value = true;
    try {
      versionList.value = (await notificationTemplateApi.versions(record.templateCode)) || [];
    } catch (err) {
      solvelaSentry.captureError(err);
      versionList.value = [];
    }
  }
</script>
