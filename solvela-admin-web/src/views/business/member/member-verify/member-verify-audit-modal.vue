<!--
  * 实名审核弹窗
  *
  * 🔴 这是<b>唯一会显示完整姓名与身份证</b>的地方，明文由单独的 detail 接口现取 ——
  * 列表接口下发的是脱敏值，页面上就算翻遍 DOM 也拿不到全量证件号。
  * 核对证件真伪是这个页面存在的理由，所以审核时必须看到完整信息；
  * 但那是「一次看一条」的动作，和「列表一屏铺开几十条」是完全不同的暴露面。
  *
  * @Copyright  weolwo
-->
<template>
  <a-modal title="实名审核" :width="560" :open="visibleFlag" :mask-closable="false" :destroy-on-close="true" @cancel="onClose">
    <!--
      🔴 v-if 而不是只靠 destroy-on-close：那个是在**关闭动画结束**时才销毁内容，
      而这里渲染的是身份证明文 —— 动画期间（甚至动画因页面不活跃而没跑完时）
      它仍然留在 DOM 里，F12 一翻就能看到。
      挂 v-if 之后，visibleFlag 一变为假，明文当帧就从 DOM 消失，不依赖任何动画。
    -->
    <a-spin v-if="visibleFlag" :spinning="loading">
      <a-descriptions :column="1" size="small" bordered>
        <a-descriptions-item label="会员">
          {{ detail.memberName || '—' }}
          <span class="sub">{{ detail.nickname }}</span>
          <div class="sub">会员号 {{ detail.memberId }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="真实姓名">
          <span class="plain">{{ detail.realName || '—' }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="身份证号">
          <span class="plain">{{ detail.idCard || '—' }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="当前状态">
          <a-tag :color="statusMeta.color">{{ statusMeta.desc }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="提交时间">{{ detail.createTime || '—' }}</a-descriptions-item>
        <a-descriptions-item v-if="detail.failReason" label="上次驳回原因">
          {{ detail.failReason }}
        </a-descriptions-item>
      </a-descriptions>

      <a-alert
        v-if="!auditable"
        type="info"
        show-icon
        class="mt-3"
        message="这条记录不在「认证中」，只能查看"
        description="已认证的记录再审一次会把认证通过时间刷成今天，而那个时间是合规审计用来回答「什么时候通过的」的依据。"
      />

      <div v-else class="reject-block">
        <div class="reject-label">驳回原因</div>
        <a-textarea
          v-model:value="failReason"
          :rows="3"
          :maxlength="255"
          show-count
          placeholder="驳回时必填。这句话会原样展示给用户，写清楚该改什么，否则他只能反复重交"
        />
      </div>
    </a-spin>

    <template #footer>
      <a-space>
        <a-button @click="onClose">关闭</a-button>
        <template v-if="visibleFlag && auditable">
          <a-button danger :loading="rejecting" @click="onReject">驳回</a-button>
          <a-button type="primary" :loading="approving" @click="onApprove">通过</a-button>
        </template>
      </a-space>
    </template>
  </a-modal>
</template>

<script setup>
  import { computed, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { memberVerifyApi } from '/@/api/business/member/member-verify-api';
  import { metaOf, VERIFY_STATUS_ENUM } from '/@/constants/business/member/member-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const emits = defineEmits(['reloadList']);

  const visibleFlag = ref(false);
  const loading = ref(false);
  const approving = ref(false);
  const rejecting = ref(false);
  const failReason = ref('');

  const detail = reactive({
    id: null,
    memberId: null,
    memberName: '',
    nickname: '',
    realName: '',
    idCard: '',
    verifyStatus: null,
    failReason: '',
    createTime: '',
  });

  const statusMeta = computed(() => metaOf(VERIFY_STATUS_ENUM, detail.verifyStatus, VERIFY_STATUS_ENUM.NONE));

  /** 只有「认证中」能审核，服务端也是这么卡的 */
  const auditable = computed(() => detail.verifyStatus === VERIFY_STATUS_ENUM.PENDING.value);

  async function show(record) {
    Object.assign(detail, { id: record.id, verifyStatus: record.verifyStatus });
    failReason.value = '';
    visibleFlag.value = true;
    loading.value = true;
    try {
      // 明文在这里才取：列表里没有，DOM 里也翻不出来
      const res = await memberVerifyApi.detail(record.id);
      Object.assign(detail, res || {});
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  function onClose() {
    // 关掉就把明文从内存里抹掉，别让它留在组件状态里等下次打开时闪一下
    Object.assign(detail, { realName: '', idCard: '', memberName: '', nickname: '' });
    failReason.value = '';
    visibleFlag.value = false;
  }

  function onApprove() {
    Modal.confirm({
      title: '确认通过？',
      content: `确认「${detail.memberName}」提交的实名信息真实有效？通过后会员即可使用需要实名的功能。`,
      okText: '通过',
      cancelText: '取消',
      onOk: requestApprove,
    });
  }

  async function requestApprove() {
    approving.value = true;
    try {
      await memberVerifyApi.approve(detail.id);
      message.success('已通过');
      emits('reloadList');
      onClose();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      approving.value = false;
    }
  }

  async function onReject() {
    const reason = (failReason.value || '').trim();
    if (!reason) {
      // 前端先拦一道：不填原因的话用户只知道失败了、不知道该改什么，只能反复重交
      message.warning('请填写驳回原因，用户会看到这句话');
      return;
    }
    rejecting.value = true;
    try {
      await memberVerifyApi.reject({ id: detail.id, failReason: reason });
      message.success('已驳回');
      emits('reloadList');
      onClose();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      rejecting.value = false;
    }
  }

  defineExpose({ show });
</script>

<style scoped lang="less">
  .sub {
    margin-left: 6px;
    font-size: 12px;
    color: #bfbfbf;
  }

  .plain {
    font-family: Menlo, Consolas, monospace;
    letter-spacing: 0.5px;
  }

  .reject-block {
    margin-top: 16px;
  }

  .reject-label {
    margin-bottom: 6px;
    font-size: 13px;
    color: #64748b;
  }

  .mt-3 {
    margin-top: 12px;
  }
</style>
