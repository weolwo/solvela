<!--
  * 安全中心 - 蜜罐与关键字（网关方案 §5）
  *
  * 【蜜罐路径】真实客户端永远不会调用的路径（扫描器才会探的那些）。命中 → IP 与设备各罚 24 小时，挡所有接口。
  * 三道保险：跨站发起的不罚、太多不同 IP 同时命中就熔断（多半是我们自己出了问题）、与真实接口重叠的不启用（网关启动时直接拒绝）。
  *
  * 【关键字规则】请求里的探测特征（sleep(5)、union select、../../ ……）。🔴 每条保存前都要过一份「正常用户的输入」
  * 反例集（带括号的商品名、地址、昵称……），命中任何一条就存不进去 —— 写宽了的规则会把真人封一天。
  * 密码、验证码类字段不参与匹配。
  *
  * 【熔断状态】窗口内命中的不同 IP 数超过阈值时显示「熔断中」：此时只记录、不处罚（关键字规则熔断时直接放行）。
  * 这里不能「重置熔断」—— 它是窗口滑过去自己解除的，强行清掉等于在它最可能是误伤的时候恢复处罚。
  *
  * 【蜜罐账号】库里真实存在、但从不对外使用的账号。有人拿它来登录，说明我们的用户名单泄露了。
  * 🔴 邮箱不进代码与种子，只能在这里建；留痕里只记投放位置，不记邮箱。
  *
  * @Date 2026-10-17
-->
<template>
  <a-alert v-if="gate" type="info" show-icon style="margin-bottom: 12px">
    <template #message>
      命中后 IP 与设备各罚 {{ Math.round(gate.banSeconds / 3600) }} 小时；
      熔断：{{ Math.round(gate.breakerWindowSeconds / 60) }} 分钟内超过 {{ gate.breakerDistinctIps }} 个不同 IP 命中同一来源时停止处罚。
      改动 15 秒内在各网关实例生效。
    </template>
  </a-alert>

  <!---------- 蜜罐路径 ----------->
  <a-card size="small" :bordered="false" title="蜜罐路径" style="margin-bottom: 12px">
    <template #extra>
      <a-button v-privilege="'security:rule'" size="small" type="primary" @click="openAdd(SECURITY_GATE_KIND.HONEYPOT)">新增蜜罐路径</a-button>
    </template>
    <a-table size="small" :dataSource="gate?.honeypots || []" :columns="honeypotColumns" :pagination="false" :loading="loading" rowKey="code" bordered>
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'path'">
          <span class="cell-mono">{{ text }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'enabled'">
          <!-- 开关不是按钮，挂不了 v-privilege（那会把它整个删掉），没权限就禁用 -->
          <a-switch :checked="text" :disabled="!$privilege('security:rule')" @change="(v) => setEnabled(record, v)" />
        </template>
        <template v-else-if="column.dataIndex === 'breaker'">
          <a-tag v-if="record.breakerOpen" color="purple">熔断中（{{ record.recentDistinctIps }} 个 IP）</a-tag>
          <span v-else class="cell-sub">正常（窗口内 {{ record.recentDistinctIps }} 个 IP）</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-popconfirm title="删掉这条蜜罐路径？" @confirm="remove(record)">
            <a v-privilege="'security:rule'" class="danger">删除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </a-card>

  <!---------- 关键字规则 ----------->
  <a-card size="small" :bordered="false" title="关键字规则" style="margin-bottom: 12px">
    <template #extra>
      <a-button v-privilege="'security:rule'" size="small" type="primary" @click="openAdd(SECURITY_GATE_KIND.KEYWORD)">新增关键字规则</a-button>
    </template>
    <a-table size="small" :dataSource="gate?.keywords || []" :columns="keywordColumns" :pagination="false" :loading="loading" rowKey="code" bordered>
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'scope'">
          <a-tag :color="text === 'UA' ? 'geekblue' : 'blue'">{{ text === 'UA' ? 'User-Agent' : '请求参数' }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'pattern'">
          <span class="cell-mono">{{ text }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'enabled'">
          <a-switch :checked="text" :disabled="!$privilege('security:rule')" @change="(v) => setEnabled(record, v)" />
        </template>
        <template v-else-if="column.dataIndex === 'breaker'">
          <a-tag v-if="record.breakerOpen" color="purple">熔断中（{{ record.recentDistinctIps }} 个 IP，放行中）</a-tag>
          <span v-else class="cell-sub">正常（窗口内 {{ record.recentDistinctIps }} 个 IP）</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-popconfirm title="删掉这条关键字规则？" @confirm="remove(record)">
            <a v-privilege="'security:rule'" class="danger">删除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </a-card>

  <!---------- 蜜罐账号 ----------->
  <a-card size="small" :bordered="false" title="蜜罐账号">
    <div class="cell-sub" style="margin-bottom: 8px">
      用一个<b>从没被用过</b>的邮箱建一个谁也登不进去的账号，把邮箱放到「可能泄露」的地方。
      有人拿它登录 → IP 与设备罚 24 小时、告警「用户名单可能已泄露」；对方看到的和输错密码一模一样。
      蜜罐账号不出现在会员列表里（按来源 CANARY 筛才看得到）。
    </div>
    <a-form layout="inline">
      <a-form-item label="邮箱">
        <a-input v-model:value="canaryForm.email" style="width: 240px" placeholder="从没注册过的邮箱" />
      </a-form-item>
      <a-form-item label="投放位置">
        <a-input v-model:value="canaryForm.placement" style="width: 280px" placeholder="放在哪了 —— 日后命中时靠它判断是哪里漏的" />
      </a-form-item>
      <a-form-item>
        <a-button v-privilege="'security:list'" type="primary" :loading="saving" @click="createCanary">建蜜罐账号</a-button>
      </a-form-item>
    </a-form>
  </a-card>

  <!---------- 新增 ----------->
  <a-modal v-model:open="addOpen" :title="addKind === SECURITY_GATE_KIND.HONEYPOT ? '新增蜜罐路径' : '新增关键字规则'" :confirmLoading="saving" @ok="doAdd" width="640">
    <a-form :labelCol="{ span: 5 }">
      <a-form-item label="编码">
        <a-input v-model:value="addForm.code" placeholder="大写字母、数字、下划线，如 SQL_SLEEP" />
      </a-form-item>
      <template v-if="addKind === SECURITY_GATE_KIND.HONEYPOT">
        <a-form-item label="路径">
          <a-input v-model:value="addForm.path" placeholder="如 /.env、/wp-admin/**（网关看到的是去掉 /api 之后的路径）" />
          <div class="cell-sub">🔴 与真实接口重叠的路径不会启用（网关启动时直接拒绝）—— 否则真人一调接口就被封一天</div>
        </a-form-item>
      </template>
      <template v-else>
        <a-form-item label="作用在">
          <a-radio-group v-model:value="addForm.scope" :options="SECURITY_KEYWORD_SCOPE_OPTIONS" />
        </a-form-item>
        <a-form-item label="正则">
          <a-input v-model:value="addForm.pattern" placeholder="如 \bsleep\s*\(\s*\d+\s*\)（不区分大小写）" />
          <div class="cell-sub">
            🔴 用<b>带词边界的探测特征</b>，不用裸词：裸的 or( 会命中「Tea or (coffee)」。
            保存时会跑一遍正常输入的反例集，命中任何一条就存不进去。
          </div>
        </a-form-item>
      </template>
    </a-form>
  </a-modal>
</template>

<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { securityApi } from '/@/api/business/security/security-api';
  import { SECURITY_GATE_KIND, SECURITY_KEYWORD_SCOPE_OPTIONS } from '/@/constants/business/security/security-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  const honeypotColumns = [
    { title: '编码', dataIndex: 'code', width: 160 },
    { title: '路径', dataIndex: 'path', width: 260 },
    { title: '启用', dataIndex: 'enabled', width: 80 },
    { title: '熔断', dataIndex: 'breaker', width: 220 },
    { title: '操作', dataIndex: 'action', width: 80 },
  ];

  const keywordColumns = [
    { title: '编码', dataIndex: 'code', width: 160 },
    { title: '作用在', dataIndex: 'scope', width: 110 },
    { title: '正则', dataIndex: 'pattern' },
    { title: '启用', dataIndex: 'enabled', width: 80 },
    { title: '熔断', dataIndex: 'breaker', width: 220 },
    { title: '操作', dataIndex: 'action', width: 80 },
  ];

  const loading = ref(false);
  const saving = ref(false);
  const gate = ref(null);

  async function load() {
    loading.value = true;
    try {
      gate.value = await securityApi.getGate();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  onMounted(load);

  async function setEnabled(record, enabled) {
    try {
      message.success(await securityApi.setGateEnabled({ kind: record.kind, code: record.code, enabled }));
      await load();
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  async function remove(record) {
    try {
      message.success(await securityApi.removeGateEntry({ kind: record.kind, code: record.code }));
      await load();
    } catch (e) {
      solvelaSentry.captureError(e);
    }
  }

  // ---------------------------- 新增 ----------------------------

  const addOpen = ref(false);
  const addKind = ref(SECURITY_GATE_KIND.HONEYPOT);
  const addForm = reactive({ code: '', path: '', scope: 'PARAM', pattern: '' });

  function openAdd(kind) {
    addKind.value = kind;
    Object.assign(addForm, { code: '', path: '', scope: 'PARAM', pattern: '' });
    addOpen.value = true;
  }

  async function doAdd() {
    saving.value = true;
    try {
      const res =
        addKind.value === SECURITY_GATE_KIND.HONEYPOT
          ? await securityApi.addHoneypot({ code: addForm.code, path: addForm.path })
          : await securityApi.addKeyword({ code: addForm.code, scope: addForm.scope, pattern: addForm.pattern });
      message.success(res);
      addOpen.value = false;
      await load();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  // ---------------------------- 蜜罐账号 ----------------------------

  const canaryForm = reactive({ email: '', placement: '' });

  async function createCanary() {
    saving.value = true;
    try {
      message.success(await securityApi.createCanary({ email: canaryForm.email, placement: canaryForm.placement }));
      // 🔴 建完就清掉：邮箱不该在页面上留着
      Object.assign(canaryForm, { email: '', placement: '' });
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }
</script>

<style scoped>
  .cell-sub {
    color: rgba(0, 0, 0, 0.45);
    font-size: 12px;
  }

  .cell-mono {
    font-family: var(--font-family-monospace, monospace);
    font-size: 12px;
  }

  .danger {
    color: #ff4d4f;
  }
</style>
