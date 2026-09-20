<!--
  * 等级权益
  *
  * 【🔴 它只是展示】在这里配一条「专享折扣」不会产生任何折扣。真正的权益靠三样
  * 东西实现，一样都不在这个页面上：任务人群 GRADE_GTE_N、脚本 member_gradeAtLeast(n)、
  * 商城价格模型。这张表回答的是另一个问题：用户在等级页看见自己在保什么。
  *
  * 【为什么需要这个页面】原先这张表是拿 SQL 灌进去的，运营改一句权益文案得找人改库。
  * 而权益文案恰恰是最常改的那类内容 —— 它是用户「为什么要保级」的唯一答案。
  *
  * 【为什么这里有删除而等级配置没有】唯一键 (grade_code, privilege_code) 会被停用的行
  * 继续占着。只给停用的话，停掉一条之后想重新加同编码的会撞键，而运营看到的只是
  * 一行「已停用」，不会想到那就是挡住他的东西。删得起是因为这张表没有任何流水指向它。
  *
  * @Author:    alaric
  * @Date:      2026-09-21
-->
<template>
  <a-card size="small" :bordered="false" :hoverable="true">
    <a-alert type="warning" show-icon class="mb-3">
      <template #message>这里配的是「用户看见什么」，不是「用户得到什么」</template>
      <template #description>
        这张表<b>纯展示</b>。在这里新增一条权益<b>不会让它生效</b> —— 真正的权益靠「任务人群 = 等级≥N」「脚本 member_gradeAtLeast(n)」「商城等级价」实现，
        配完这里还要去对应的地方配一次。
      </template>
    </a-alert>

    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <a-button v-privilege="'memberGrade:config'" type="primary" size="small" @click="openForm(null)">新增权益</a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table size="small" :dataSource="tableData" :columns="columns" rowKey="id" bordered :loading="tableLoading" :pagination="false">
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'gradeCode'">
          <span class="name">{{ gradeNameOf(record.gradeCode) }}</span>
          <a-tag class="ml-1">等级 {{ record.gradeCode }}</a-tag>
          <!--
            🔴 挂在一个已经不存在的等级上的权益，C 端永远不会展示它 ——
            不报错、不告警，运营只会觉得「我明明配了」。服务端新增时会拦，
            但等级被删改之后存量数据仍可能变成这样，所以列表上要看得见。
          -->
          <a-tooltip v-if="!gradeExists(record.gradeCode)" title="等级配置里已经没有这一档，这条权益在 C 端不会展示">
            <a-tag color="error" class="ml-1">等级不存在</a-tag>
          </a-tooltip>
        </template>

        <template v-else-if="column.dataIndex === 'privilegeCode'">
          <code class="code">{{ record.privilegeCode }}</code>
        </template>

        <template v-else-if="column.dataIndex === 'actionUrl'">
          <span v-if="record.actionUrl">{{ record.actionUrl }}</span>
          <span v-else class="cell-sub">纯展示，不可点</span>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-switch
            v-privilege="'memberGrade:config'"
            :checked="record.status === 1"
            :loading="switchingId === record.id"
            @change="(checked) => requestToggle(record, checked ? 1 : 0)"
          />
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a-button v-privilege="'memberGrade:config'" type="link" size="small" @click="openForm(record)">编辑</a-button>
          <a-button v-privilege="'memberGrade:config'" type="link" size="small" danger @click="onDelete(record)">删除</a-button>
        </template>

        <template v-else>{{ text ?? '—' }}</template>
      </template>
    </a-table>
  </a-card>

  <a-modal
    :title="form.id ? '编辑权益' : '新增权益'"
    :width="560"
    :open="formVisible"
    :mask-closable="false"
    :destroy-on-close="true"
    :confirm-loading="submitting"
    @ok="onSubmit"
    @cancel="formVisible = false"
  >
    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="等级" required>
        <!--
          下拉而不是数字输入框：手填一个不存在的等级号，这条权益就永远不会
          出现在任何人的等级页上，而页面上完全看不出来。
        -->
        <a-select v-model:value="form.gradeCode" placeholder="选择等级" :options="gradeOptions" style="width: 100%" />
      </a-form-item>
      <a-form-item label="权益编码" required>
        <a-input v-model:value="form.privilegeCode" :maxlength="64" :disabled="!!form.id" placeholder="如 BIRTHDAY_GIFT" />
        <div class="cell-sub">
          大写字母、数字、下划线。<b>没有任何引擎读它</b> —— 写成编码只是为了让「白金和钻石都有生日礼」这件事在数据里看得出来。
        </div>
        <div v-if="form.id" class="cell-sub">编码建成后不可修改 —— 要换编码请删掉重建</div>
      </a-form-item>
      <a-form-item label="权益名" required>
        <a-input v-model:value="form.privilegeName" :maxlength="64" placeholder="直接展示给用户，如：生日礼" />
      </a-form-item>
      <a-form-item label="权益说明">
        <a-textarea v-model:value="form.description" :maxlength="255" :rows="2" placeholder="等级页的第二行小字" />
      </a-form-item>
      <a-form-item label="跳转地址">
        <a-input v-model:value="form.actionUrl" :maxlength="255" placeholder="留空表示纯展示、不可点" />
      </a-form-item>
      <a-form-item label="排序">
        <a-input-number v-model:value="form.sort" :min="0" style="width: 100%" placeholder="越大越靠前" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { memberGradeApi } from '/@/api/business/member/member-grade-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';

  const columns = ref([
    { title: '等级', dataIndex: 'gradeCode', width: 220 },
    { title: '权益编码', dataIndex: 'privilegeCode', width: 170 },
    { title: '权益名', dataIndex: 'privilegeName', width: 150 },
    { title: '说明', dataIndex: 'description' },
    { title: '跳转', dataIndex: 'actionUrl', width: 160 },
    { title: '排序', dataIndex: 'sort', width: 70 },
    { title: '状态', dataIndex: 'status', width: 80 },
    { title: '操作', dataIndex: 'action', width: 120 },
  ]);

  const tableLoading = ref(false);
  const tableData = ref([]);
  const grades = ref([]);
  const switchingId = ref(null);
  const formVisible = ref(false);
  const submitting = ref(false);

  const formState = {
    id: null,
    gradeCode: undefined,
    privilegeCode: '',
    privilegeName: '',
    description: '',
    actionUrl: '',
    sort: 0,
  };
  const form = reactive({ ...formState });

  const gradeOptions = computed(() => grades.value.map((g) => ({ value: g.gradeCode, label: `${g.gradeName}（等级 ${g.gradeCode}）` })));

  function gradeNameOf(gradeCode) {
    const hit = grades.value.find((g) => g.gradeCode === gradeCode);
    return hit ? hit.gradeName : '未知等级';
  }

  function gradeExists(gradeCode) {
    return grades.value.some((g) => g.gradeCode === gradeCode);
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      // 等级表一起拿：列表要显示等级名，新增要用下拉，两处都靠它
      const [privileges, configs] = await Promise.all([memberGradeApi.listPrivilege(), memberGradeApi.listConfig()]);
      tableData.value = privileges || [];
      grades.value = configs || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      tableLoading.value = false;
    }
  }

  function openForm(record) {
    Object.assign(form, formState, record || {});
    formVisible.value = true;
  }

  async function onSubmit() {
    if (form.gradeCode === undefined || form.gradeCode === null) {
      message.warning('请选择等级');
      return;
    }
    const code = (form.privilegeCode || '').trim();
    if (!code) {
      message.warning('请填写权益编码');
      return;
    }
    // 与服务端 @Pattern 同一条规则。前端先拦一次，是为了在光标还在那个框里时就说清楚
    if (!/^[A-Z][A-Z0-9_]*$/.test(code)) {
      message.warning('权益编码只能是大写字母、数字和下划线，且以字母开头');
      return;
    }
    if (!form.privilegeName || !form.privilegeName.trim()) {
      message.warning('请填写权益名');
      return;
    }
    submitting.value = true;
    try {
      await memberGradeApi.savePrivilege({ ...form, privilegeCode: code, sort: form.sort ?? 0 });
      message.success('已保存');
      formVisible.value = false;
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      submitting.value = false;
    }
  }

  function onDelete(record) {
    /*
      说清楚是物理删。运营习惯了「删除 = 软删，找得回来」，而这里找不回来 ——
      同时也要说清为什么不用停用，否则他下次还是会先停用再来问为什么加不回去。
    */
    Modal.confirm({
      title: `删除「${record.privilegeName}」？`,
      content: '这是物理删除，删掉找不回来。只是想暂时下架的话请用停用开关；但注意停用的行仍然占着「同一档 + 同一编码」，想重新加一条同编码的必须先删掉这一条。',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await memberGradeApi.deletePrivilege(record.id);
          message.success('已删除');
          queryData();
        } catch (e) {
          solvelaSentry.captureError(e);
        }
      },
    });
  }

  async function requestToggle(record, status) {
    switchingId.value = record.id;
    try {
      await memberGradeApi.updatePrivilegeStatus(record.id, status);
      message.success('已更新');
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      switchingId.value = null;
    }
  }

  onMounted(queryData);
</script>

<style scoped lang="less">
  .name {
    font-weight: 600;
  }

  .code {
    background: rgba(0, 0, 0, 0.04);
    border-radius: 3px;
    padding: 1px 5px;
    font-size: 12px;
  }

  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .ml-1 {
    margin-left: 6px;
  }

  .mb-3 {
    margin-bottom: 12px;
  }
</style>
