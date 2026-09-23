<!--
  * 等级配置
  *
  * 【为什么等级是配置不是枚举】加一档是这类体系最常见的运营动作。
  * 写死在代码里的话每加一档要发版，而运营加档的节奏是按季度的。
  *
  * 【🔴 没有删除】删掉一档之后，正停在那一档上的会员会挂在一个配置里已经不存在的
  * 等级号上 —— 页面显示不出名字，判级也解释不了。要下线一档就停用它。
  *
  * @Author:    alaric
  * @Date:      2026-09-18
-->
<template>
  <a-card size="small" :bordered="false" :hoverable="true">
    <a-alert type="warning" show-icon class="mb-3">
      <template #message>权益不在这里配</template>
      <template #description>
        等级权益已拆到<b>独立的 t_grade_privilege 表</b>（一列 varchar 装不下图标、跳转、多语言）。 ⚠️ 它<b>仍然只是展示</b> —— 真正的权益靠「任务人群
        = 等级≥N」「脚本 member_gradeAtLeast」「商城等级价」实现。
      </template>
    </a-alert>

    <a-alert type="info" show-icon class="mb-3">
      <template #message>改门槛不会当场影响已有会员</template>
      <template #description>
        升级只在成长值入账时判且<b>只升不降</b>，降级只在期末结算发生 ——
        所以调高门槛之后，已经在高等级上的人会在本周期结束时才自然掉下来。这是刻意的： 一改配置就当场降一批人，是会上新闻的那种事故。
      </template>
    </a-alert>

    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <a-button v-privilege="'memberGrade:config'" type="primary" size="small" @click="openForm(null)">新增一档</a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table size="small" :dataSource="tableData" :columns="columns" rowKey="id" bordered :loading="tableLoading" :pagination="false">
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'gradeName'">
          <span class="name">{{ record.gradeName }}</span>
          <a-tag class="ml-1">等级 {{ record.gradeCode }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <!--
            🔴 等级 0 的开关禁掉：它是新会员的落点，也是判级函数的兜底。
            服务端也拦了这一条，这里禁用只是别让运营白点一次。
          -->
          <a-switch
            v-privilege="'memberGrade:config'"
            :checked="record.status === 1"
            :disabled="record.gradeCode === 0"
            :loading="switchingId === record.id"
            @change="(checked) => onToggle(record, checked)"
          />
          <a-tooltip v-if="record.gradeCode === 0" title="最低档是新会员的落点，不能停用">
            <span class="cell-sub ml-1">不可停用</span>
          </a-tooltip>
        </template>

        <template v-else-if="column.dataIndex === 'pointsDiscount'">
          <!--
            🔴 这一列显示「8.8 折」而不是「88」。
            运营填的是 88，但他要确认的是「白金打几折」——
            让他自己把 88 换算成 8.8 折，是把一次心算放在一个会花钱的确认动作上。
          -->
          <span v-if="record.pointsDiscount == null || record.pointsDiscount >= 100" class="cell-sub">不打折</span>
          <a-tag v-else color="red">{{ record.pointsDiscount / 10 }} 折</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a-button v-privilege="'memberGrade:config'" type="link" size="small" @click="openForm(record)">编辑</a-button>
        </template>

        <template v-else>{{ text ?? '—' }}</template>
      </template>
    </a-table>
  </a-card>

  <a-modal
    :title="form.id ? '编辑等级' : '新增等级'"
    :width="520"
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
          等级号建完就不该再改：它是 t_member_growth.level 的取值，
          改掉等于把一批会员的等级悄悄换成了另一档。
        -->
        <a-input-number v-model:value="form.gradeCode" :min="0" :disabled="!!form.id" style="width: 100%" placeholder="0 起，数字越大越高" />
        <div v-if="form.id" class="cell-sub">等级号建成后不可修改 —— 会员表里存的就是它</div>
      </a-form-item>
      <a-form-item label="等级名" required>
        <a-input v-model:value="form.gradeName" :maxlength="32" placeholder="如：金卡会员" />
      </a-form-item>
      <a-form-item label="成长值门槛" required>
        <a-input-number v-model:value="form.threshold" :min="0" :disabled="form.gradeCode === 0" style="width: 100%" />
        <div class="cell-sub">必须随等级递增。等级 0 的门槛固定为 0</div>
      </a-form-item>
      <!--
        🔴 这一项和上面几项不是一类：等级名、图标改了只是显示变了，
        这个改了【下一次兑换就按新折扣扣分】，全场参与等级折扣的商品一起变。
        所以提示语里要把「会真的少收钱」说出来，而不是只写一句「1-100」。
      -->
      <a-form-item label="商城积分折扣">
        <a-input-number
          v-model:value="form.pointsDiscount"
          :min="1"
          :max="100"
          :disabled="form.gradeCode === 0"
          style="width: 100%"
          placeholder="留空 = 不打折"
        />
        <div class="cell-sub">
          填 88 = 8.8 折，100 或留空 = 不打折。<b>保存后立即对全场生效</b>，
          个别商品可在商品编辑里单独退出。只打积分，不打现金。
        </div>
        <div v-if="form.gradeCode === 0" class="cell-sub">
          等级 0 不能打折 —— 新会员就享优惠的话，升级这件事本身就不值钱了
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { memberGradeApi } from '/@/api/business/member/member-grade-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';

  const columns = ref([
    { title: '等级', dataIndex: 'gradeName', width: 200 },
    { title: '成长值门槛', dataIndex: 'threshold', width: 130 },
    { title: '商城积分折扣', dataIndex: 'pointsDiscount', width: 130 },
    { title: '状态', dataIndex: 'status', width: 140 },
    { title: '操作', dataIndex: 'action', width: 90 },
  ]);

  const tableLoading = ref(false);
  const tableData = ref([]);
  const switchingId = ref(null);
  const formVisible = ref(false);
  const submitting = ref(false);

  const formState = {
    id: null,
    gradeCode: undefined,
    gradeName: '',
    threshold: undefined,
    // undefined 而不是 100：留空是「没配」，与「明确配了不打折」在后台看起来不同
    pointsDiscount: undefined,
  };
  const form = reactive({ ...formState });

  async function queryData() {
    tableLoading.value = true;
    try {
      tableData.value = (await memberGradeApi.listConfig()) || [];
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
      message.warning('请填写等级');
      return;
    }
    if (!form.gradeName || !form.gradeName.trim()) {
      message.warning('请填写等级名');
      return;
    }
    // 等级 0 的门槛固定为 0：输入框是禁用的，这里补上值，别让它以 undefined 提交
    const threshold = form.gradeCode === 0 ? 0 : form.threshold;
    if (threshold === undefined || threshold === null) {
      message.warning('请填写成长值门槛');
      return;
    }
    submitting.value = true;
    try {
      /*
       * ⚠️ 等级 0 的折扣强制清空。输入框是禁用的，但用户可能先填了 88 再把等级改成 0 ——
       *    那时框里的值还在，提交上去会被服务端打回，而错误提示离操作已经隔了一步。
       */
      const pointsDiscount = form.gradeCode === 0 ? null : (form.pointsDiscount ?? null);
      await memberGradeApi.saveConfig({ ...form, threshold, pointsDiscount });
      message.success('已保存');
      formVisible.value = false;
      queryData();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      submitting.value = false;
    }
  }

  function onToggle(record, checked) {
    if (checked) {
      requestToggle(record, 1);
      return;
    }
    /*
      停用一档要先说清影响面：这些人的 current_grade 列不会变，他们会一直停在一个
      配置里已经没有的等级上，直到期末结算按新配置重判 —— 那是一个周期以后的事。
    */
    Modal.confirm({
      title: `停用「${record.gradeName}」？`,
      content: '正停在这一档的会员不会立刻掉级，他们会一直挂在这个等级上，直到本周期结束时按新配置重判。',
      okText: '停用',
      cancelText: '取消',
      onOk: () => requestToggle(record, 0),
    });
  }

  async function requestToggle(record, status) {
    switchingId.value = record.id;
    try {
      await memberGradeApi.updateConfigStatus(record.id, status);
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
