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
  * 【🔴 反例集在弹窗里当场跑】正则一边写、一边拿反例集试，命中哪一条就把那一条摆出来；
  * 旁边还有个试跑框，能拿自己想挡的那串探测特征验一下「它到底命不命中」。
  * 用的是浏览器的正则引擎与 KEYWORD_NORMAL_INPUTS（照抄后端那份）—— 服务端保存时仍会用它自己那份再跑一遍，
  * 那份才是说了算的。这里只是别让人把一条会封真人的规则写完、点了保存，才知道不行。
  *
  * 【熔断状态】窗口内命中的不同 IP 数超过阈值时显示「熔断中」：此时只记录、不处罚（关键字规则熔断时直接放行）。
  * 这里不能「重置熔断」—— 它是窗口滑过去自己解除的，强行清掉等于在它最可能是误伤的时候恢复处罚。
  *
  * 【蜜罐账号】库里真实存在、但从不对外使用的账号。有人拿它来登录，说明我们的用户名单泄露了。
  * 🔴 邮箱不进代码与种子，只能在这里建；留痕里只记投放位置，不记邮箱。
  *
  * @Date 2026-10-18
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
    <div class="cell-sub" style="margin-bottom: 12px">
      用一个<b>从没被用过</b>的邮箱建一个谁也登不进去的账号，把邮箱放到「可能泄露」的地方。
      有人拿它登录 → IP 与设备罚 24 小时、告警「用户名单可能已泄露」；对方看到的和输错密码一模一样。
      蜜罐账号不出现在会员列表里（按来源 CANARY 筛才看得到）。
    </div>
    <a-form ref="canaryFormRef" :model="canaryForm" :rules="canaryRules" layout="inline">
      <a-form-item label="邮箱" name="email">
        <a-input v-model:value="canaryForm.email" style="width: 240px" placeholder="从没注册过的邮箱" />
      </a-form-item>
      <a-form-item label="投放位置" name="placement">
        <a-input v-model:value="canaryForm.placement" style="width: 300px" placeholder="放在哪了 —— 日后命中时靠它判断是哪里漏的" />
      </a-form-item>
      <a-form-item>
        <a-button v-privilege="'security:list'" type="primary" :loading="saving" @click="createCanary">建蜜罐账号</a-button>
      </a-form-item>
    </a-form>
    <div class="form-hint">🔴 建完邮箱就从页面上清掉，留痕里也只记投放位置 —— 它的价值全在「没人见过它」</div>
  </a-card>

  <!---------- 新增 ----------->
  <a-modal
    v-model:open="addOpen"
    :title="isHoneypot ? '新增蜜罐路径' : '新增关键字规则'"
    :width="720"
    :maskClosable="false"
    :destroyOnClose="true"
  >
    <div class="modal-target">
      <span class="modal-target-note">
        <template v-if="isHoneypot">真实客户端永远不会调用的路径。命中 → 那个 IP 与设备各罚 24 小时，挡所有接口。</template>
        <template v-else>请求参数或 UA 里的探测特征。命中 → 同样罚 24 小时，所以宁可漏、不可错。</template>
      </span>
    </div>

    <a-form ref="formRef" :model="addForm" :rules="addRules" :label-col="{ span: 4 }" :wrapper-col="{ span: 19 }">
      <a-form-item label="编码" name="code">
        <a-input v-model:value="addForm.code" placeholder="如 SQL_SLEEP" @change="onCodeInput" />
        <div class="form-hint">大写字母、数字、下划线，最多 32 位（小写会自动转成大写）。它会进日志与安全事件，用来回答「是哪条规则封的」</div>
      </a-form-item>

      <template v-if="isHoneypot">
        <a-form-item label="路径" name="path">
          <a-input v-model:value="addForm.path" placeholder="如 /.env、/wp-admin/**" />
          <div class="form-hint">
            网关看到的是<b>去掉 /api 之后</b>的路径。
            🔴 与真实接口重叠的路径不会启用（网关启动时直接拒绝）—— 否则真人一调接口就被封一天。
          </div>
          <a-alert
            v-if="addForm.path.startsWith('/api')"
            type="warning"
            show-icon
            style="margin-top: 8px"
            message="这条路径以 /api 开头，而网关是在剥掉 /api 之后才匹配的 —— 它多半永远不会命中。"
          />
        </a-form-item>
      </template>

      <template v-else>
        <a-form-item label="作用在" name="scope">
          <a-radio-group v-model:value="addForm.scope" button-style="solid" option-type="button" :options="SECURITY_KEYWORD_SCOPE_OPTIONS" />
        </a-form-item>

        <a-form-item label="正则" name="pattern">
          <a-input v-model:value="addForm.pattern" class="input-mono" placeholder="如 \bsleep\s*\(\s*\d+\s*\)（不区分大小写）" />
          <div class="form-hint">
            🔴 用<b>带词边界的探测特征</b>，不用裸词：裸的 or( 会命中「Floor(落地灯)」「Color(2024)!」。
            密码、验证码类字段不参与匹配。
          </div>
        </a-form-item>

        <!---------- 反例集预检 ----------->
        <a-form-item label="预检" :colon="false">
          <a-alert v-if="!addForm.pattern" type="info" message="填了正则就在这里拿反例集跑一遍。" />
          <a-alert v-else-if="patternSyntaxError" type="error" show-icon :message="`正则写不对：${patternSyntaxError}`" />
          <a-alert v-else-if="normalHits.length" type="error" show-icon message="这条规则命中了正常用户真的会输入的东西 —— 上线的话这样的人会被封 24 小时">
            <template #description>
              <div v-for="hit in normalHits" :key="hit.input" class="hit-row">
                <span class="cell-mono">{{ hit.input }}</span>
                <span class="hit-seg">命中「{{ hit.matched }}」</span>
              </div>
              <div class="form-hint">改写正则：加词边界（\b）、收紧上下文、别用裸词。</div>
            </template>
          </a-alert>
          <a-alert
            v-else
            type="success"
            show-icon
            :message="`本地预检通过：${normalSamples.length} 条正常${isUaScope ? ' UA' : '输入'}一条都没命中`"
            description="服务端保存时还会用它自己那份反例集（连同超时判定）再跑一遍 —— 那份才是说了算的。"
          />
        </a-form-item>

        <!---------- 试跑 ----------->
        <a-form-item label="试一下">
          <a-input
            v-model:value="probeText"
            class="input-mono"
            allow-clear
            placeholder="贴一段想挡的请求（或一段正常输入），看它命不命中"
          />
          <div v-if="probeText && !patternSyntaxError" class="probe-result">
            <a-tag v-if="probeHit" color="red">命中</a-tag>
            <a-tag v-else color="default">不命中</a-tag>
            <span v-if="probeHit" class="cell-mono">「{{ probeHit }}」</span>
          </div>
        </a-form-item>
      </template>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="addOpen = false">取消</a-button>
        <a-button type="primary" :loading="saving" @click="doAdd">新增</a-button>
      </a-space>
    </template>
  </a-modal>
</template>

<script setup>
  import { computed, onMounted, reactive, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { securityApi } from '/@/api/business/security/security-api';
  import {
    KEYWORD_NORMAL_INPUTS,
    KEYWORD_NORMAL_USER_AGENTS,
    SECURITY_GATE_KIND,
    SECURITY_KEYWORD_SCOPE_OPTIONS,
  } from '/@/constants/business/security/security-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';

  /** 后端 KeywordRules.CODE：进日志与 t_security_incident.rule_code（宽 32） */
  const CODE_PATTERN = /^[A-Z0-9_]{1,32}$/;

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
  const formRef = ref();
  const addForm = reactive({ code: '', path: '', scope: 'PARAM', pattern: '' });
  /** 「试一下」框里的那段文本。只在页面上跑，不发给后端 */
  const probeText = ref('');

  const isHoneypot = computed(() => addKind.value === SECURITY_GATE_KIND.HONEYPOT);
  const isUaScope = computed(() => addForm.scope === 'UA');

  /** 编码不能和已有的撞（后端 requireNew 会拒绝，这里先说一声） */
  const existingCodes = computed(() =>
    ((isHoneypot.value ? gate.value?.honeypots : gate.value?.keywords) || []).map((item) => item.code),
  );

  const addRules = {
    code: [
      {
        validator: () => {
          const code = (addForm.code || '').trim().toUpperCase();
          if (!code) {
            return Promise.reject('编码必填');
          }
          if (!CODE_PATTERN.test(code)) {
            return Promise.reject('只能是 1~32 位大写字母、数字、下划线');
          }
          return existingCodes.value.includes(code) ? Promise.reject(`编码已存在：${code}`) : Promise.resolve();
        },
      },
    ],
    path: [
      {
        validator: () => {
          const path = (addForm.path || '').trim();
          if (!path) {
            return Promise.reject('路径必填');
          }
          if (!path.startsWith('/')) {
            return Promise.reject('路径要以 / 开头');
          }
          return path.length > 200 ? Promise.reject('路径最多 200 字') : Promise.resolve();
        },
      },
    ],
    scope: [{ required: true, message: '选一个作用范围' }],
    pattern: [
      {
        validator: () => {
          if (!addForm.pattern) {
            return Promise.reject('正则必填');
          }
          if (addForm.pattern.length > 500) {
            return Promise.reject('正则最多 500 字');
          }
          return patternSyntaxError.value ? Promise.reject(`正则写不对：${patternSyntaxError.value}`) : Promise.resolve();
        },
      },
    ],
  };

  /** 当前这条正则：编译得过给 regex，编译不过给 error */
  const compiled = computed(() => {
    if (!addForm.pattern) {
      return { regex: null, error: '' };
    }
    try {
      // 只带 i：后端是 CASE_INSENSITIVE | UNICODE_CASE，而 JS 的 u 标志对转义更挑剔，
      // 会把一条后端收得下的正则判成语法错 —— 预检宁可宽一点
      return { regex: new RegExp(addForm.pattern, 'i'), error: '' };
    } catch (e) {
      return { regex: null, error: e.message };
    }
  });

  const patternSyntaxError = computed(() => compiled.value.error);

  const normalSamples = computed(() => (isUaScope.value ? KEYWORD_NORMAL_USER_AGENTS : KEYWORD_NORMAL_INPUTS));

  /**
   * 反例集里被这条正则命中的那些。命中就意味着「真人会被封 24 小时」，所以要把命中的那一段也摆出来 ——
   * 光说「命中了 Tea or (coffee)」，人还得自己去数是哪几个字符。
   */
  const normalHits = computed(() => {
    const regex = compiled.value.regex;
    if (!regex) {
      return [];
    }
    const hits = [];
    for (const input of normalSamples.value) {
      const found = input.match(regex);
      if (found) {
        hits.push({ input, matched: found[0] });
      }
    }
    return hits;
  });

  /** 试跑框：命中的那一段，没命中为空 */
  const probeHit = computed(() => {
    const regex = compiled.value.regex;
    if (!regex || !probeText.value) {
      return '';
    }
    return probeText.value.match(regex)?.[0] || '';
  });

  /** 编码统一存大写（后端 normalizeCode 也会做），边输边转，免得人以为自己填的小写能存下去 */
  function onCodeInput() {
    addForm.code = (addForm.code || '').toUpperCase();
  }

  function openAdd(kind) {
    addKind.value = kind;
    Object.assign(addForm, { code: '', path: '', scope: 'PARAM', pattern: '' });
    probeText.value = '';
    addOpen.value = true;
  }

  async function doAdd() {
    try {
      await formRef.value.validate();
    } catch {
      // 校验没过：错误已经标在对应的字段上
      return;
    }
    saving.value = true;
    try {
      const code = addForm.code.trim().toUpperCase();
      const res = isHoneypot.value
        ? await securityApi.addHoneypot({ code, path: addForm.path.trim() })
        : await securityApi.addKeyword({ code, scope: addForm.scope, pattern: addForm.pattern });
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

  const canaryFormRef = ref();
  const canaryForm = reactive({ email: '', placement: '' });

  const canaryRules = {
    email: [
      { required: true, message: '要一个从没注册过的邮箱' },
      { type: 'email', message: '邮箱格式不对' },
    ],
    placement: [{ required: true, message: '必须写投放位置 —— 命中时靠它判断是哪里漏的' }],
  };

  async function createCanary() {
    try {
      await canaryFormRef.value.validate();
    } catch {
      return;
    }
    saving.value = true;
    try {
      message.success(await securityApi.createCanary({ email: canaryForm.email, placement: canaryForm.placement }));
      // 🔴 建完就清掉：邮箱不该在页面上留着
      Object.assign(canaryForm, { email: '', placement: '' });
      canaryFormRef.value?.clearValidate();
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

  .modal-target {
    margin-bottom: 16px;
    padding: 8px 12px;
    background: #fafafa;
    border-radius: 6px;
  }

  .modal-target-note {
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  .form-hint {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.6;
    color: rgba(0, 0, 0, 0.45);
  }

  /* 正则与试跑的输入框：空格、括号、转义都要数得清 */
  .input-mono :deep(input) {
    font-family: var(--font-family-monospace, monospace);
  }

  .hit-row {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    line-height: 20px;
  }

  .hit-seg {
    font-size: 12px;
    color: #cf1322;
  }

  .probe-result {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 6px;
  }
</style>
