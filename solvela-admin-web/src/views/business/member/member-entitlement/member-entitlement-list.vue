<!--
  * 等级权益发放配置：生日礼 / 月度券
  *
  * 【🔴 这一页配的东西会真的花钱】与「等级权益」那一页不是一回事：
  * 那一页改的是用户在等级页看见什么（纯文案），这一页改的是【真的发什么出去】。
  * 加一条「每月给所有白金发一张 20 元券」，下一个 job 周期就真的发了。
  * 所以它的权限点是单独的 memberEntitlement:config。
  *
  * 【🔴 没有删除】已生成的待领取记录是已经承诺给用户的东西，删配置会让它们变成
  * 查不到来源的孤儿。要停就停用——停用不影响已生成的，只是不再生成新的。
  *
  * 【🔴 类型建成后不可改】改类型 = 改周期键的口径（yyyy ↔ yyyyMM），
  * 而周期键是幂等键的一半：已发过的记录用旧口径，新口径算出来的键与它们不冲突，
  * 于是同一个周期会再发一次。想换周期就新建一条，把旧的停用。
  *
  * @Author:    alaric
  * @Date:      2026-09-22
-->
<template>
  <a-card size="small" :bordered="false" :hoverable="true">
    <a-alert type="error" show-icon class="mb-3">
      <template #message>这一页配的东西会真的发出去</template>
      <template #description>
        和「等级权益」那一页不同：那一页是<b>写给用户看的文案</b>，这一页是<b>真的发资产</b>。
        保存之后，下一个 job 周期（每天 04:10）就会按等级生成待领取记录，用户在会员中心点一下就领走。
        <b>先确认券模板编码和面额是对的</b>——发出去的东西收不回来。
      </template>
    </a-alert>

    <a-alert type="info" show-icon class="mb-3">
      <template #message>幂等靠唯一键，job 每天扫全量是安全的</template>
      <template #description>
        唯一键是 <code>(会员, 权益, 周期)</code>。生日礼的周期是<code>年</code>，月度券是<code>年月</code>——
        所以每天扫也只会各发一次，而且<b>自带补跑</b>：某天 job 没跑起来，第二天会补上。
        这也是「类型建成后不可改」的原因：改了口径，同一个周期会再发一次。
      </template>
    </a-alert>

    <a-row class="solvela-table-btn-block">
      <div class="solvela-table-operate-block">
        <a-button v-privilege="'memberEntitlement:config'" type="primary" size="small" @click="openForm(null)">新增权益</a-button>
      </div>
      <div class="solvela-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table size="small" :dataSource="tableData" :columns="columns" rowKey="id" bordered :loading="tableLoading" :pagination="false">
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'entitlementName'">
          <span class="name">{{ record.entitlementName }}</span>
          <div class="cell-sub">{{ record.entitlementCode }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'entitlementType'">
          <a-tag :color="record.entitlementType === 'BIRTHDAY' ? 'pink' : 'blue'">
            {{ TYPE_LABEL[record.entitlementType] || record.entitlementType }}
          </a-tag>
          <div class="cell-sub">{{ record.entitlementType === 'BIRTHDAY' ? '一年一次' : '一月一次' }}</div>
        </template>

        <template v-else-if="column.dataIndex === 'minGrade'">
          <span>{{ gradeNameOf(record.minGrade) }}</span>
          <a-tag class="ml-1">≥ {{ record.minGrade }}</a-tag>
        </template>

        <template v-else-if="column.dataIndex === 'asset'">
          <span class="name">{{ record.assetName }}</span>
          <div class="cell-sub">
            {{ record.assetType }}<template v-if="record.assetRef"> · {{ record.assetRef }}</template>
            · ×{{ record.quantity }}<template v-if="record.amount"> · 面额 {{ record.amount }}</template>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <a-switch
            v-privilege="'memberEntitlement:config'"
            :checked="record.status === 1"
            :loading="switchingId === record.id"
            @change="(checked) => onToggle(record, checked)"
          />
        </template>

        <template v-else-if="column.dataIndex === 'action'">
          <a-button v-privilege="'memberEntitlement:config'" type="link" size="small" @click="openForm(record)">编辑</a-button>
        </template>

        <template v-else>{{ text ?? '—' }}</template>
      </template>
    </a-table>
  </a-card>

  <a-modal
    :title="form.id ? '编辑权益' : '新增权益'"
    :width="600"
    :open="formVisible"
    :mask-closable="false"
    :destroy-on-close="true"
    :confirm-loading="submitting"
    @ok="onSubmit"
    @cancel="formVisible = false"
  >
    <a-form :label-col="{ span: 7 }" :wrapper-col="{ span: 15 }">
      <a-form-item label="权益名" required>
        <a-input v-model:value="form.entitlementName" :maxlength="64" placeholder="如：白金生日礼" />
        <div class="cell-sub">会显示给用户，说明<b>为什么给他</b></div>
      </a-form-item>

      <a-form-item label="类型" required>
        <a-select v-model:value="form.entitlementType" :options="TYPE_OPTIONS" :disabled="!!form.id" style="width: 100%" />
        <!--
          建成后禁用：改类型 = 改周期键的口径，会让同一个周期再发一次。
          服务端也会忽略编辑时传来的类型，这里禁用只是别让人白改一次。
        -->
        <div v-if="form.id" class="cell-sub">类型建成后不可修改——改了会让同一个周期再发一次。要换周期请新建一条</div>
      </a-form-item>

      <a-form-item label="最低等级" required>
        <a-select v-model:value="form.minGrade" :options="gradeOptions" style="width: 100%" />
        <div class="cell-sub">判据是「≥」：白金的月度券，钻石会员也有</div>
      </a-form-item>

      <a-form-item label="资产类型" required>
        <a-select v-model:value="form.assetType" :options="ASSET_OPTIONS" style="width: 100%" />
      </a-form-item>

      <a-form-item v-if="form.assetType === 'COUPON'" label="券模板编码" required>
        <a-input v-model:value="form.assetRef" :maxlength="64" placeholder="t_coupon_template.template_code" />
        <div class="cell-sub">🔴 填错不会保存失败，而是<b>领取时发放被拒</b>——发之前先核对一遍</div>
      </a-form-item>

      <a-form-item v-if="form.assetType === 'BALANCE'" label="单份面额" required>
        <a-input-number v-model:value="form.amount" :min="0" :precision="2" style="width: 100%" />
        <div class="cell-sub">实发 = 面额 × 份数</div>
      </a-form-item>

      <a-form-item label="展示名" required>
        <a-input v-model:value="form.assetName" :maxlength="128" placeholder="如：生日 20 元券" />
        <div class="cell-sub">说明<b>给他什么</b>。会直接显示给用户，别拿备注顶替</div>
      </a-form-item>

      <a-form-item label="发放份数" required>
        <a-input-number v-model:value="form.quantity" :min="1" :precision="0" style="width: 100%" />
      </a-form-item>

      <a-form-item label="可领取天数" required>
        <a-input-number v-model:value="form.claimDays" :min="1" :precision="0" style="width: 100%" />
        <div class="cell-sub">
          必须大于 0。待领取不设期限的话会无限堆积——一个从不打开 App 的人会攒下几十条永远不会被领的记录，
          而它们既占预算口径、又让「有多少人享受了权益」这个数字失真
        </div>
      </a-form-item>

      <a-form-item label="备注">
        <a-textarea v-model:value="form.remark" :maxlength="255" :rows="2" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { memberEntitlementApi } from '/@/api/business/member/member-entitlement-api';
  import { memberGradeApi } from '/@/api/business/member/member-grade-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';

  const TYPE_LABEL = { BIRTHDAY: '生日礼', MONTHLY: '月度券' };

  const TYPE_OPTIONS = [
    { value: 'BIRTHDAY', label: '生日礼（一年一次）' },
    { value: 'MONTHLY', label: '月度券（一月一次）' },
  ];

  /*
    只列真正能发的三种。资产类型的全集在 PrizeTypeEnum 里更大（还有实物、标记、彩票），
    但那几种在权益这条路上没有意义：实物要收货地址、标记不是资产、彩票有自己的发号链路。
    列出来只会让人配一个永远发不出去的东西。
  */
  const ASSET_OPTIONS = [
    { value: 'COUPON', label: '优惠券' },
    { value: 'BALANCE', label: '余额' },
    { value: 'SCORE', label: '积分' },
  ];

  const columns = ref([
    { title: '权益', dataIndex: 'entitlementName', width: 180 },
    { title: '类型', dataIndex: 'entitlementType', width: 110 },
    { title: '最低等级', dataIndex: 'minGrade', width: 150 },
    { title: '发什么', dataIndex: 'asset' },
    { title: '可领天数', dataIndex: 'claimDays', width: 90 },
    { title: '状态', dataIndex: 'status', width: 80 },
    { title: '操作', dataIndex: 'action', width: 80 },
  ]);

  const tableLoading = ref(false);
  const tableData = ref([]);
  const grades = ref([]);
  const switchingId = ref(null);
  const formVisible = ref(false);
  const submitting = ref(false);

  const formState = {
    id: null,
    entitlementName: '',
    entitlementType: 'MONTHLY',
    minGrade: 0,
    assetType: 'COUPON',
    assetRef: '',
    assetName: '',
    quantity: 1,
    amount: null,
    claimDays: 30,
    remark: '',
  };
  const form = reactive({ ...formState });

  /*
    等级下拉从后端拉，不在前端写死——等级是配置出来的，运营能加档、改名、停用。
    写死那份改一次就和库里对不上，而症状是「配了个不存在的等级，这条权益谁都收不到」。
  */
  const gradeOptions = computed(() => [
    { value: 0, label: '不限（所有会员）' },
    ...grades.value.filter((g) => g.gradeCode > 0).map((g) => ({ value: g.gradeCode, label: `${g.gradeName}（≥ ${g.gradeCode}）` })),
  ]);

  function gradeNameOf(code) {
    if (code === 0) {
      return '不限';
    }
    const hit = grades.value.find((g) => g.gradeCode === code);
    return hit ? hit.gradeName : `等级 ${code}`;
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const [list, configs] = await Promise.all([memberEntitlementApi.list(), memberGradeApi.listConfig()]);
      tableData.value = list || [];
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
    if (!form.entitlementName?.trim()) {
      message.warning('请填写权益名');
      return;
    }
    if (!form.assetName?.trim()) {
      message.warning('请填写展示名');
      return;
    }
    if (form.assetType === 'COUPON' && !form.assetRef?.trim()) {
      message.warning('优惠券必须填券模板编码');
      return;
    }
    if (form.assetType === 'BALANCE' && !(form.amount > 0)) {
      message.warning('余额必须填单份面额');
      return;
    }
    if (!(form.claimDays > 0)) {
      message.warning('可领取天数必须大于 0，否则生成当天就会过期');
      return;
    }
    submitting.value = true;
    try {
      await memberEntitlementApi.save({ ...form });
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
      停用要说清影响面：已经生成的待领取【仍然可以领】，那是已经承诺给用户的东西。
      不说的话运营会以为停用等于立刻收回，然后在客诉来的时候才发现不是。
    */
    Modal.confirm({
      title: `停用「${record.entitlementName}」？`,
      content: '停用后不再生成新的待领取记录。但【已经生成的仍然可以领取】——那是已经承诺给用户的东西，收回会变成客诉。',
      okText: '停用',
      cancelText: '取消',
      onOk: () => requestToggle(record, 0),
    });
  }

  async function requestToggle(record, status) {
    switchingId.value = record.id;
    try {
      await memberEntitlementApi.updateStatus(record.id, status);
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
