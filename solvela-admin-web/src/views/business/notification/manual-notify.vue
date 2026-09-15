<!--
  * 人工发送站内信
  *
  * 补的是管理端一个真实缺口：此前只有「公告」（广播给所有人）和「通知模板」
  * （定义系统触发的措辞），没有「发给某个人」—— 客服想给一个会员补一句说明，
  * 只能去改库。
  *
  * 🔴 三者的关系要在页面上说清楚，否则运营会一直问「公告和模板为什么没关联」：
  *   · 模板   —— 系统自动触发的通知长什么样（中奖、发货…）
  *   · 公告   —— 运营手写、广播给人群，一条内容一行
  *   · 人工发送（本页）—— 运营手写或套模板，定向给几个人，一人一条
  *
  * @Author:    alaric
  * @Date:      2026-09-15
  * @Copyright  weolwo
-->
<template>
  <a-alert type="info" show-icon class="mb-4">
    <template #message>这里是<b>定向</b>发送：一个收件人一条站内信。</template>
    <template #description>
      要发给所有人请用「公告管理」—— 那是一条内容一行，而这里是一人一条。
      人一多就是在用写扩散做广播，所以单次收件人上限 {{ MAX_RECIPIENTS }} 个，超了会被拒绝。
      <br />
      发出去的每一条都记着操作人（<code>create_by</code>），事后查得到是谁发的。
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
          <span v-if="recipientCount > 0" :class="recipientCount > MAX_RECIPIENTS ? 'text-red-500' : 'text-slate-500'">
            当前 {{ recipientCount }} 个
            <template v-if="recipientCount > MAX_RECIPIENTS">（已超上限 {{ MAX_RECIPIENTS }}）</template>
          </span>
        </div>
      </a-form-item>

      <a-form-item label="内容来源" name="mode">
        <a-radio-group v-model:value="form.mode" button-style="solid">
          <a-radio-button value="MANUAL">自定义文本</a-radio-button>
          <a-radio-button value="TEMPLATE">套用已有模板</a-radio-button>
        </a-radio-group>
        <div class="mt-1 text-xs text-slate-500">
          自定义文本走 <code>MANUAL</code> 模板，正文由你现填 —— 它是唯一一个
          正文不写死在模板里的。套用模板适合「补发一条中奖通知」这类场景。
        </div>
      </a-form-item>

      <!-- ---------------- 自定义文本 ---------------- -->
      <template v-if="form.mode === 'MANUAL'">
        <a-form-item label="标题" name="title">
          <a-input v-model:value="form.title" placeholder="如：关于您反馈问题的处理结果" />
        </a-form-item>
        <a-form-item label="正文" name="content">
          <a-textarea v-model:value="form.content" :rows="6" placeholder="直接写给用户看的话" />
          <div class="mt-1 text-xs text-slate-500">
            ⚠️ <b>自定义文本归在「系统通知」，用户关不掉。</b>
            人工触达通常是针对某个人的事（工单答复、补偿说明），这是刻意的 ——
            但请不要拿它群发营销文案。
          </div>
        </a-form-item>
      </template>

      <!-- ---------------- 套用模板 ---------------- -->
      <template v-else>
        <a-form-item label="模板" name="templateCode">
          <a-select
            v-model:value="form.templateCode"
            :options="templateOptions"
            placeholder="选择一个已启用的模板"
            @change="onTemplateChange"
          />
        </a-form-item>

        <a-form-item v-if="templateParams.length > 0" label="占位符">
          <div v-for="key in templateParams" :key="key" class="mb-2">
            <a-input v-model:value="form.params[key]" :addon-before="key" :placeholder="`填 \${${key}} 的值`" />
          </div>
          <div class="mt-1 text-xs text-slate-500">
            🔴 漏填不会拦下来，但那个位置会<b>原样显示</b> <code>${'{'}xxx{'}'}</code> 给用户看。
          </div>
        </a-form-item>
        <a-form-item v-else-if="form.templateCode" label="占位符">
          <span class="text-slate-400">这个模板没有占位符</span>
        </a-form-item>
      </template>

      <a-form-item label="业务单号" name="bizRefId">
        <a-input v-model:value="form.bizRefId" placeholder="可空。客服一般填工单号，方便事后对账" />
      </a-form-item>

      <a-form-item :wrapper-col="{ offset: 4 }">
        <a-space>
          <a-button type="primary" :loading="sending" @click="onSend">发送</a-button>
          <a-button @click="onReset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-card>

  <!--
    结果逐个报成败，不给一个笼统的「成功」——
    一次发 50 个人其中 3 个没发出去，只说「成功」的话运营永远不知道那 3 个人没收到。
  -->
  <a-card v-if="result !== null" size="small" :bordered="false" class="mt-4" title="发送结果">
    <a-result
      :status="result.failed.length === 0 ? 'success' : 'warning'"
      :title="`成功 ${result.sent} 条${result.failed.length > 0 ? `，失败 ${result.failed.length} 条` : ''}`"
    >
      <template v-if="result.failed.length > 0" #subTitle>
        以下会员没收到（原因见服务端日志，通常是模板没配或被免打扰拦下）：
        <div class="mt-2">
          <a-tag v-for="id in result.failed" :key="id" color="red">{{ id }}</a-tag>
        </div>
      </template>
    </a-result>
  </a-card>
</template>

<script setup>
  import { ref, reactive, computed, onMounted } from 'vue';
  import { message } from 'ant-design-vue';
  import { manualNotifyApi } from '/@/api/business/notification/manual-notify-api';
  import { notificationTemplateApi } from '/@/api/business/notification/notification-template-api';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  /** 与后端 NotificationAdminService.MANUAL_MAX_RECIPIENTS 对齐 */
  const MAX_RECIPIENTS = 200;

  const formRef = ref();
  const sending = ref(false);
  const result = ref(null);

  const formDefault = {
    recipients: '',
    mode: 'MANUAL',
    title: undefined,
    content: undefined,
    templateCode: undefined,
    params: {},
    bizRefId: undefined,
  };

  const form = reactive({ ...formDefault, params: {} });

  const rules = computed(() => ({
    recipients: [{ required: true, message: '收件人 必填' }],
    ...(form.mode === 'MANUAL'
      ? {
          title: [{ required: true, message: '标题 必填' }],
          content: [{ required: true, message: '正文 必填' }],
        }
      : { templateCode: [{ required: true, message: '模板 必选' }] }),
  }));

  /**
   * 收件人文本 → 两个数组。
   *
   * 纯数字当会员号，其余当会员账号 —— 会员号是 10 位数字，而账号是字母开头，
   * 两者不会混淆。分隔符给得宽松（换行/逗号/空格/中文逗号）：运营从各处粘过来的
   * 格式五花八门，为此报错是把内部实现的别扭转嫁给使用的人。
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

  // ------------------------ 模板 ------------------------

  const templates = ref([]);

  const templateOptions = computed(() =>
    templates.value
      // MANUAL 不出现在这个下拉里 —— 它对应的是上面那个「自定义文本」单选，
      // 在两个地方都能选到同一个东西只会让人犯嘀咕
      .filter((t) => t.templateCode !== 'MANUAL')
      .map((t) => ({ value: t.templateCode, label: `${t.templateCode}（v${t.version}）` })),
  );

  const templateParams = computed(() => {
    const picked = templates.value.find((t) => t.templateCode === form.templateCode);
    if (!picked || !picked.paramKeys) {
      return [];
    }
    try {
      const parsed = typeof picked.paramKeys === 'string' ? JSON.parse(picked.paramKeys) : picked.paramKeys;
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  });

  function onTemplateChange() {
    // 换模板要清掉上一个模板的参数，否则会把不相干的键一起提交上去
    form.params = {};
  }

  onMounted(async () => {
    try {
      templates.value = (await notificationTemplateApi.list()) || [];
    } catch (err) {
      solvelaSentry.captureError(err);
    }
  });

  // ------------------------ 发送 ------------------------

  async function onSend() {
    try {
      await formRef.value.validateFields();
    } catch (err) {
      message.error('参数验证错误，请仔细填写表单数据!');
      return;
    }

    const { memberIds, memberNames } = parsedRecipients.value;
    if (memberIds.length + memberNames.length > MAX_RECIPIENTS) {
      // 前端先挡一道，省一次白跑的请求。服务端那道才是真正的闸
      message.error(`一次最多 ${MAX_RECIPIENTS} 个收件人，要发给更多人请用「公告管理」`);
      return;
    }

    sending.value = true;
    SolvelaLoading.show();
    try {
      const params =
        form.mode === 'MANUAL' ? { title: form.title, content: form.content } : { ...form.params };

      result.value = await manualNotifyApi.send({
        memberIds,
        memberNames,
        templateCode: form.mode === 'MANUAL' ? 'MANUAL' : form.templateCode,
        params,
        bizRefId: form.bizRefId,
      });

      if (result.value.failed.length === 0) {
        message.success(`已发送 ${result.value.sent} 条`);
      } else {
        message.warning(`成功 ${result.value.sent} 条，失败 ${result.value.failed.length} 条`);
      }
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
      sending.value = false;
    }
  }

  function onReset() {
    Object.assign(form, formDefault);
    form.params = {};
    result.value = null;
  }
</script>
