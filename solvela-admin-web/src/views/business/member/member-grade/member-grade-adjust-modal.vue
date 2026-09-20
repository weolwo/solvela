<!--
  * 人工调级
  *
  * 🔴 这是整个等级体系里唯一一条「人能直接改结果」的路，所以页面上必须把
  *    它的边界说清楚 —— 尤其是「调完不改成长值」这一条：
  *    运营以为自己送了一个永久白金，实际期末结算会把人放回该在的档，
  *    到时候用户投诉的是运营，不是系统。
  *
  * @Author:    alaric
  * @Date:      2026-09-18
-->
<template>
  <a-modal
    title="人工调级"
    :width="520"
    :open="visibleFlag"
    :mask-closable="false"
    :destroy-on-close="true"
    :confirm-loading="submitting"
    ok-text="确认调整"
    @ok="onSubmit"
    @cancel="onClose"
  >
    <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <a-form-item label="会员">
        <div>{{ record.memberName || '—' }}</div>
        <div class="sub">会员号 {{ record.memberId }}</div>
      </a-form-item>

      <a-form-item label="当前等级">
        <a-tag>{{ record.gradeName || `等级 ${record.currentGrade}` }}</a-tag>
        <span class="sub">本周期成长值 {{ record.currentPeriodValue ?? 0 }}</span>
      </a-form-item>

      <a-form-item label="目标等级" required>
        <a-select v-model:value="form.newGrade" :options="gradeOptions" placeholder="请选择" style="width: 100%" />
      </a-form-item>

      <a-form-item label="调整原因" required>
        <a-textarea
          v-model:value="form.reason"
          :rows="3"
          :maxlength="255"
          show-count
          placeholder="写清楚为什么调。半年后审计问起来，这句话就是唯一的答案"
        />
      </a-form-item>
    </a-form>

    <!--
      这段不是客套话。不写的话，运营会把人工调级当成「发权益」用，
      而它在期末结算那里根本留不住。
    -->
    <a-alert type="warning" show-icon>
      <template #message>调级不会改成长值</template>
      <template #description>
        <div>· 往上调：本周期有效，期末结算会按他<b>真实的</b>成长值重判，届时可能掉回去；</div>
        <div>· 往下调：他会停在这里，直到自己攒够成长值再升上来；</div>
        <div>· 这条记录会带着你的账号写进变更留痕，谁都能查到。</div>
      </template>
    </a-alert>
  </a-modal>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { memberGradeApi } from '/@/api/business/member/member-grade-api';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const emits = defineEmits(['reloadList']);

  const visibleFlag = ref(false);
  const submitting = ref(false);
  const levels = ref([]);

  const record = reactive({
    memberId: null,
    memberName: '',
    currentGrade: null,
    gradeName: '',
    periodValue: 0,
  });

  const form = reactive({
    newGrade: undefined,
    reason: '',
  });

  /**
   * 只给启用中的档，且把当前等级排掉。
   * 留着当前等级只会换来一次「该会员已经是 X，无需调整」的报错 ——
   * 那条报错是服务端的兜底，不该让运营在页面上撞见。
   */
  const gradeOptions = computed(() =>
    levels.value
      .filter((item) => item.status === 1 && item.gradeCode !== record.currentGrade)
      .map((item) => ({ value: item.gradeCode, label: `${item.gradeName}（等级 ${item.gradeCode}）` }))
  );

  async function show(row) {
    Object.assign(record, {
      memberId: row.memberId,
      memberName: row.memberName,
      currentGrade: row.currentGrade,
      gradeName: row.gradeName,
      periodValue: row.periodValue,
    });
    form.newGrade = undefined;
    form.reason = '';
    visibleFlag.value = true;
    try {
      levels.value = (await memberGradeApi.listConfig()) || [];
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  function onClose() {
    visibleFlag.value = false;
  }

  async function onSubmit() {
    // 前端先拦一道，省掉一次往返；服务端那道才是防线
    if (form.newGrade === undefined || form.newGrade === null) {
      message.warning('请选择目标等级');
      return;
    }
    if (!form.reason.trim()) {
      message.warning('请填写调整原因');
      return;
    }
    submitting.value = true;
    try {
      await memberGradeApi.adjustGrade({
        memberId: record.memberId,
        newGrade: form.newGrade,
        reason: form.reason.trim(),
      });
      message.success('已调整');
      visibleFlag.value = false;
      emits('reloadList');
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      submitting.value = false;
    }
  }

  defineExpose({ show });
</script>

<style scoped lang="less">
  .sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
    margin-left: 8px;
  }
</style>
