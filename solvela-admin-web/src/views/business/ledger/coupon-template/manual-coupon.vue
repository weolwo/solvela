<!--
  * 人工发券
  *
  * 补的是一个真实缺口：券此前只能由【活动 / 任务 / 商城】发出来。
  * 客服想给一个会员补一张券，只能去改库 —— 而改库既没有规则快照、
  * 也没有通知、更没有留痕。
  *
  * 🔴 这一页和「人工发送站内信」形状一样，但分量不同：
  *    那边发错了是一条消息，这边发错了是钱。所以多了两道闸：
  *      · 工单号必填 —— 它是防重发的唯一依据，库上有唯一索引兜着；
  *      · 券模必须有启用中的模板 —— 没有的话发出去的券没有任何规则，用户永远用不了。
  *
  * @Author:    alaric
  * @Date:      2026-09-15
  * @Copyright  weolwo
-->
<template>
  <a-alert type="warning" show-icon class="mb-4">
    <template #message>这里是<b>直接给用户发钱</b>，不是发消息。</template>
    <template #description>
      单次收件人上限 {{ MAX_RECIPIENTS }} 个、每人最多 {{ MAX_QUANTITY }} 张，超了会被拒绝 ——
      这个入口不能变成「给全体用户发券」的后门（那会绕过活动配置、预算与风控）。
      要发给更多人请走活动配置。
      <br />
      <b>工单号必填</b>：它是防重发的唯一依据，同一个工单号对同一个人重复提交只会发一次。
      每张券都记着操作人，事后查得到是谁发的。
    </template>
  </a-alert>

  <a-card size="small" :bordered="false" :bodyStyle="{ padding: '16px' }">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="收件人" name="recipients">
        <a-textarea
          v-model:value="form.recipients"
          :rows="4"
          placeholder="会员号或会员账号，一行一个（也可以用逗号、空格分隔）"
        />
        <div class="mt-1 text-xs text-slate-500">
          两种都认：纯数字当会员号，其余当会员账号。
          <b>账号查不到会整批拒绝</b> —— 贴 5 个打错一个，静默发给 4 个人你不会发现。
          <span
            v-if="recipientCount > 0"
            :class="recipientCount > MAX_RECIPIENTS ? 'text-red-500' : 'text-slate-500'"
          >
            当前 {{ recipientCount }} 个
            <template v-if="recipientCount > MAX_RECIPIENTS">（已超上限 {{ MAX_RECIPIENTS }}）</template>
          </span>
        </div>
      </a-form-item>

      <a-form-item label="券模板" name="couponCode">
        <!--
          从券模板列表里选，不让手输：手输一个库里没有的编码，服务端会拒，
          但那是在点了「发送」之后 —— 而这一页的每一次点击都对应一批真实的券。
        -->
        <a-select
          v-model:value="form.couponCode"
          :options="couponOptions"
          placeholder="选择要发的券"
          show-search
          option-filter-prop="label"
        />
        <div v-if="chosenCoupon" class="mt-1 text-xs text-slate-500">
          规则：<b>{{ describeRule(chosenCoupon) }}</b> · {{ describeValidity(chosenCoupon) }}
        </div>
        <div v-else class="mt-1 text-xs text-slate-500">
          只列出<b>有启用中模板</b>的券 —— 没有模板的券发出去没有任何规则，用户用不了。
        </div>
      </a-form-item>

      <a-form-item label="每人几张" name="quantity">
        <a-input-number v-model:value="form.quantity" :min="1" :max="MAX_QUANTITY" :precision="0" />
        <span class="ml-2 text-xs text-slate-500">
          上限 {{ MAX_QUANTITY }} 张 ——「每人 1000 张」和「发给 1000 个人」是同一件事
        </span>
      </a-form-item>

      <a-form-item label="工单号" name="bizRefId">
        <a-input v-model:value="form.bizRefId" placeholder="如 T20260915-001" />
        <div class="mt-1 text-xs text-red-500">
          🔴 <b>防重发的唯一依据</b>。同一个工单号对同一个人只会发一次 ——
          网络慢了再点一次也不会多发。换一个工单号就是<b>另一批券</b>，会真的再发一次。
        </div>
      </a-form-item>

      <a-form-item label="发券原因" name="reason">
        <a-textarea v-model:value="form.reason" :rows="2" placeholder="如：就您 9 月 12 日反馈的问题补偿" />
        <div class="mt-1 text-xs text-slate-500">
          会<b>原样显示给用户</b>（随「您收到一张优惠券」那条通知一起）。
          不填的话用户收到的是一张来路不明的券。
        </div>
      </a-form-item>

      <a-form-item :wrapper-col="{ offset: 4 }">
        <a-space>
          <a-button type="primary" :loading="sending" @click="onConfirmSend">发券</a-button>
          <a-button @click="onReset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-card>

  <!--
    结果卡片。发完【不清掉】它 —— 那是这次操作唯一的凭据，
    页面上什么都不留的话，运营只能靠记忆回答「我刚才发成功了吗」。
  -->
  <a-card v-if="result" size="small" :bordered="false" class="mt-4" :bodyStyle="{ padding: '16px' }">
    <a-result
      :status="result.failed.length > 0 ? 'warning' : 'success'"
      :title="`已发出 ${result.granted} 张「${result.couponName}」`"
    >
      <template #subTitle>
        <div v-if="result.skipped.length > 0">
          <!-- 跳过是幂等，不是失败。分不开的话运营会再点一次，而那次是真的重复 -->
          {{ result.skipped.length }} 个会员在工单 {{ sentBizRefId }} 下<b>已经发过</b>，本次跳过。
        </div>
        <div v-if="result.failed.length > 0" class="text-red-500">
          {{ result.failed.length }} 个会员没发成：{{ result.failed.join('、') }}
        </div>
        <div v-if="result.failed.length === 0 && result.skipped.length === 0">
          收件人全部发放成功，每人已收到一条通知。
        </div>
      </template>
      <template v-if="result.failed.length > 0" #extra>
        <a-button @click="onRefillFailed">把失败的填回收件人</a-button>
      </template>
    </a-result>
  </a-card>
</template>

<script setup>
  import { ref, reactive, computed, onMounted } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { couponTemplateApi, manualCouponApi } from '/@/api/business/ledger/coupon-template-api';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  /**
   * 与服务端 CouponManualGrantService 的两个上限对齐。
   *
   * ⚠️ 前端这一道只是为了省一次白跑的请求，<b>服务端那道才是真正的闸</b>。
   * 改这里的数字不会改变服务端的行为。
   */
  const MAX_RECIPIENTS = 200;
  const MAX_QUANTITY = 10;

  const DEDUCT_TARGET_LABEL = { CASH: '现金', SCORE: '积分' };

  const formDefault = {
    recipients: '',
    couponCode: undefined,
    quantity: 1,
    bizRefId: undefined,
    reason: undefined,
  };

  const form = reactive({ ...formDefault });
  const formRef = ref();
  const sending = ref(false);
  const result = ref(null);
  const sentBizRefId = ref('');

  const rules = computed(() => ({
    recipients: [{ required: true, message: '收件人 必填' }],
    couponCode: [{ required: true, message: '券模板 必选' }],
    // 工单号必填不是形式：没有它就没有幂等键，双击一次就多发一批券出去
    bizRefId: [{ required: true, message: '工单号 必填 —— 它是防重发的唯一依据' }],
  }));

  /**
   * 收件人文本 → 两个数组。
   *
   * 纯数字当会员号，其余当会员账号。分隔符给得宽松（换行/逗号/空格/中文逗号）：
   * 运营从各处粘过来的格式五花八门，为此报错是把内部实现的别扭转嫁给使用的人。
   */
  const parsedRecipients = computed(() => {
    const tokens = form.recipients
      .split(/[\s,，;；]+/)
      .map((t) => t.trim())
      .filter((t) => t.length > 0);
    const memberIds = [];
    const memberNames = [];
    for (const token of tokens) {
      if (/^\d+$/.test(token)) {
        memberIds.push(Number(token));
      } else {
        memberNames.push(token);
      }
    }
    return { memberIds, memberNames };
  });

  const recipientCount = computed(
    () => parsedRecipients.value.memberIds.length + parsedRecipients.value.memberNames.length,
  );

  // ------------------------ 券模板 ------------------------

  const templates = ref([]);

  const couponOptions = computed(() =>
    templates.value.map((t) => ({
      value: t.couponCode,
      label: `${t.couponName}（${t.couponCode}）`,
    })),
  );

  const chosenCoupon = computed(
    () => templates.value.find((t) => t.couponCode === form.couponCode) ?? null,
  );

  /** 和券模板列表页同一套措辞 —— 两处显示同一张券必须是同一句话 */
  function describeRule(record) {
    const target = DEDUCT_TARGET_LABEL[record.deductTarget] ?? '';
    const threshold = Number(record.minAmount) > 0 ? `满 ${record.minAmount} ` : '无门槛 ';
    if (record.discountType === 'PERCENT') {
      const cap = record.maxDiscount ? `，最高减 ${record.maxDiscount} ${target}` : '';
      return `${threshold}减 ${record.discountValue}%${cap}`;
    }
    return `${threshold}减 ${record.discountValue} ${target}`;
  }

  function describeValidity(record) {
    if (record.validDays) {
      return `发券后 ${record.validDays} 天有效`;
    }
    return record.validEndTime ? `有效至 ${record.validEndTime}` : '有效期未设置（异常）';
  }

  onMounted(async () => {
    try {
      // list 返回的就是「每个编码的最新启用版」，所以这里天然只列得出有模板的券
      templates.value = (await couponTemplateApi.list()) || [];
    } catch (err) {
      solvelaSentry.captureError(err);
    }
  });

  // ------------------------ 发送 ------------------------

  /**
   * 点「发券」先弹确认，不直接发。
   *
   * 发券是<b>不可逆的对外动作</b> —— 发错了收不回来，而且它对应钱。
   * 确认框把「发给几个人、发什么券、几张」摆出来核对一眼，
   * 同时它本身就是一个明确的分界：见过这个框才算发过。
   */
  async function onConfirmSend() {
    try {
      await formRef.value.validateFields();
    } catch (err) {
      message.error('参数验证错误，请仔细填写表单数据!');
      return;
    }

    const count = recipientCount.value;
    if (count > MAX_RECIPIENTS) {
      message.error(`一次最多 ${MAX_RECIPIENTS} 个收件人，要发给更多人请走活动配置`);
      return;
    }

    const quantity = form.quantity || 1;
    Modal.confirm({
      title: `确认给 ${count} 个会员各发 ${quantity} 张券？`,
      content: `券：${chosenCoupon.value?.couponName ?? form.couponCode}，共 ${count * quantity} 张。
        工单号 ${form.bizRefId}。券发出去收不回来，请核对收件人。`,
      okText: '确认发券',
      cancelText: '再看看',
      onOk: onSend,
    });
  }

  async function onSend() {
    const { memberIds, memberNames } = parsedRecipients.value;

    sending.value = true;
    SolvelaLoading.show();
    try {
      sentBizRefId.value = form.bizRefId;
      result.value = await manualCouponApi.send({
        memberIds,
        memberNames,
        couponCode: form.couponCode,
        quantity: form.quantity || 1,
        bizRefId: form.bizRefId,
        reason: form.reason,
      });

      if (result.value.failed.length === 0) {
        // 全成功就把表单清掉 —— 内容还留在框里是「看着像没发出去」最大的来源。
        // 但【不清结果卡片】，那是唯一的凭据
        message.success(`已发出 ${result.value.granted} 张`);
        clearForm();
      } else {
        // 部分失败时【保留表单】：运营多半要改点什么再发一次，清掉等于让他重填
        message.warning(`发出 ${result.value.granted} 张，${result.value.failed.length} 个会员没发成`);
      }
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
      sending.value = false;
    }
  }

  /** 只清内容，不动结果卡片 —— 发完之后那张卡是唯一的凭据 */
  function clearForm() {
    Object.assign(form, formDefault);
  }

  function onReset() {
    clearForm();
    result.value = null;
  }

  /**
   * 把失败的会员号填回收件人框。
   *
   * ⚠️ 不自动重发，而且<b>工单号保持不变</b> ——
   * 同一个工单号重发只会补上没发成的那几个，已经成功的会被幂等挡掉。
   * 换工单号才是「再发一批」，那是另一件事。
   */
  function onRefillFailed() {
    if (result.value === null || result.value.failed.length === 0) {
      return;
    }
    form.recipients = result.value.failed.join('\n');
    form.bizRefId = sentBizRefId.value;
    message.info(`已填回 ${result.value.failed.length} 个失败的会员号，工单号保持不变`);
  }
</script>
