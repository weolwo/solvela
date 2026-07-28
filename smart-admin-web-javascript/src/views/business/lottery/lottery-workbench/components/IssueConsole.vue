<!--
  * 彩票工作台 Tab2：期号生命周期与开奖大盘
  *
  * 左侧期号列表（创设 / 行内编辑时间 / 删除），右侧开奖操作台。
  *
  * ⚠️ 期号一旦创建，期号标识本身不可修改 —— 它是 FPE 的 tweak
  * （key = HMAC(secret, lotteryCode|issueNo)），改了等于换密钥，
  * 已发号码全部反解失效、签名全废，新号码还可能与历史重复。
  * 所以编辑态只放开时间，期号列固定只读。
  *
  * @Author:    alaric
  * @Date:      2026-07-28
-->
<template>
  <div class="py-2">
    <a-empty v-if="!lotteryCode" class="py-16">
      <template #description>
        <span class="text-sm text-slate-400">请先在「引擎配置与奖级映射」保存一个玩法，再来创设期号</span>
      </template>
    </a-empty>

    <div v-else class="flex gap-5 items-start">
      <!-- ============ 左：期号列表 ============ -->
      <div class="w-8/12 flex flex-col">
        <div class="flex items-center justify-between mb-4">
          <h3 class="section-title title-blue m-0">活动期数与发售列表</h3>
          <div class="flex items-center gap-3">
            <span class="text-xs text-slate-400">💡 点击行任意位置即可选中，在右侧面板开奖</span>
            <a-button type="primary" :disabled="creating" @click="startCreate">➕ 创设新期数</a-button>
          </div>
        </div>

        <!-- 创设表单 -->
        <transition name="expand">
          <div v-if="creating" class="bg-blue-50/50 border border-blue-200 rounded-lg p-4 mb-4 shadow-sm">
            <a-form layout="vertical">
              <a-row :gutter="16">
                <a-col :span="6">
                  <a-form-item class="mb-0" required>
                    <template #label>
                      <span>期号标识</span>
                      <a-tooltip title="它同时是 FPE 算号的盐值，创建后不可修改。建议用可读的期次标识，如 2026_MID_01">
                        <QuestionCircleOutlined class="ml-1 text-slate-400" />
                      </a-tooltip>
                    </template>
                    <a-input v-model:value="createForm.issueNo" placeholder="如 2026_MID_01" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="售卖周期 (起止)" class="mb-0" required>
                    <a-range-picker
                      v-model:value="createForm.saleRange"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      show-time
                      class="w-full"
                      format="MM-DD HH:mm"
                    />
                  </a-form-item>
                </a-col>
                <a-col :span="6">
                  <a-form-item label="计划开奖时间" class="mb-0">
                    <a-date-picker
                      v-model:value="createForm.planDrawTime"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      show-time
                      class="w-full"
                      placeholder="对外承诺的开奖时刻"
                      format="MM-DD HH:mm"
                    />
                  </a-form-item>
                </a-col>
                <a-col :span="4" class="flex items-end">
                  <div class="flex gap-2 w-full">
                    <a-button class="flex-1 px-0" @click="creating = false">取消</a-button>
                    <a-button type="primary" class="flex-1 px-0" :loading="saving" @click="submitCreate">保存</a-button>
                  </div>
                </a-col>
              </a-row>
            </a-form>
          </div>
        </transition>

        <div class="bg-white border border-slate-200 rounded shadow-sm">
          <a-table
            :data-source="issueList"
            :columns="columns"
            :pagination="false"
            :loading="loading"
            row-key="id"
            size="small"
            :custom-row="customRow"
            :row-class-name="() => 'clickable-row'"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'issueNo'">
                <div class="flex items-center gap-1">
                  <span class="font-mono font-bold text-slate-800">{{ record.issueNo }}</span>
                  <a-tag v-if="selectedId === record.id" color="blue" class="m-0 text-[10px] leading-tight py-0">已选中</a-tag>
                </div>
              </template>

              <template v-else-if="column.key === 'times'">
                <div v-if="record._editing" class="flex flex-col gap-1 w-[220px]" @click.stop>
                  <a-range-picker
                    v-model:value="record._editRange"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    show-time
                    size="small"
                    :placeholder="['发售', '截止']"
                    format="MM-DD HH:mm"
                  />
                  <a-date-picker
                    v-model:value="record._editPlanDraw"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    show-time
                    size="small"
                    placeholder="计划开奖时间"
                    class="w-full"
                    format="MM-DD HH:mm"
                  />
                </div>
                <div v-else class="text-[11px] text-slate-600 space-y-0.5">
                  <div class="truncate">
                    <span class="text-slate-400">售卖:</span>
                    {{ fmt(record.saleStartTime) }} ~ {{ fmt(record.saleEndTime) }}
                  </div>
                  <div>
                    <span class="text-slate-400">计划开奖:</span> {{ fmt(record.planDrawTime) || '未设置' }}
                  </div>
                </div>
              </template>

              <template v-else-if="column.key === 'progress'">
                <div class="flex flex-col gap-1 items-start">
                  <a-tag :color="statusColor(record.status)" class="m-0 text-[10px] py-0 leading-tight">
                    {{ statusText(record.status) }}
                  </a-tag>
                  <span class="text-xs font-bold" :class="isSoldOut(record) ? 'text-red-500' : 'text-blue-600'">
                    {{ (record.soldCount || 0).toLocaleString() }} / {{ (totalCount || 0).toLocaleString() }}
                  </span>
                </div>
              </template>

              <template v-else-if="column.key === 'result'">
                <div v-if="record.winningNumber" class="flex flex-col">
                  <span class="font-mono text-red-600 font-bold text-base tracking-widest">{{ record.winningNumber }}</span>
                  <span class="text-slate-400 text-[10px] mt-0.5">开于: {{ fmt(record.settleTime) }}</span>
                </div>
                <span v-else class="text-xs text-slate-400">- 待开奖 -</span>
              </template>

              <template v-else-if="column.key === 'action'">
                <div v-if="record._editing" class="flex gap-1 justify-center" @click.stop>
                  <a-button type="primary" size="small" class="text-[11px] px-2 h-6" @click="saveEdit(record)">保存</a-button>
                  <a-button size="small" class="text-[11px] px-2 h-6" @click="cancelEdit(record)">取消</a-button>
                </div>
                <div v-else-if="record.status === 0" class="flex gap-1 justify-center" @click.stop>
                  <a-button type="link" size="small" class="text-[11px] p-0" @click="startEdit(record)">编辑</a-button>
                  <a-divider type="vertical" class="mx-1" />
                  <a-tooltip :title="record.soldCount > 0 ? '已发出号码的期号不能删除：记录是用户凭证也是开奖依据' : ''">
                    <a-button
                      type="link"
                      danger
                      size="small"
                      class="p-0 text-[11px]"
                      :disabled="record.soldCount > 0"
                      @click="removeIssue(record)"
                    >
                      移除
                    </a-button>
                  </a-tooltip>
                </div>
                <span v-else class="text-xs text-slate-300">-</span>
              </template>
            </template>
          </a-table>
        </div>
      </div>

      <!-- ============ 右：开奖操作台 ============ -->
      <div class="w-4/12 flex flex-col">
        <h3 class="section-title title-red m-0 mb-4">开奖执行操作台</h3>
        <div class="flex-1 rounded-xl border border-red-200 bg-red-50/40 p-5 shadow-sm sticky top-4">
          <div v-if="!selectedIssue" class="py-16 text-center text-slate-400 text-sm">
            👈 请在左侧列表中选择需要操作的期数
          </div>
          <div v-else class="space-y-4">
            <div class="bg-white p-4 rounded-lg border border-red-100 flex flex-col gap-1 shadow-sm">
              <div class="text-xs text-slate-400">目前锁定操作期号：</div>
              <div class="font-mono font-bold text-slate-800 text-lg">{{ selectedIssue.issueNo }}</div>
              <div class="text-xs text-slate-500 mt-1">
                已发 {{ (selectedIssue.soldCount || 0).toLocaleString() }} 张 ·
                {{ statusText(selectedIssue.status) }}
              </div>
            </div>

            <div v-if="selectedIssue.status === 2" class="rounded-lg bg-green-50 p-6 text-center border border-green-200 shadow-sm">
              <div class="text-3xl mb-2">✅</div>
              <div class="text-sm font-bold text-green-700">该期已完成核销</div>
              <div class="text-xs text-green-600 mt-1">开奖结果与时间已在左侧表格直显</div>
            </div>

            <div v-else class="rounded-lg bg-white p-5 shadow-sm border-t-4 border-red-500">
              <div class="text-sm font-bold text-slate-700 mb-2">录入最终开奖号码</div>
              <a-input
                v-model:value="winningNumber"
                size="large"
                class="font-mono tracking-[0.3em] text-center text-2xl h-12 text-red-600 font-bold"
                :maxlength="numberLength"
                placeholder="等待录入"
              />
              <div class="text-[11px] mt-1 font-bold" :class="drawValid ? 'text-green-500' : 'text-red-500'">
                <span v-if="drawValid">✅ 格式合法</span>
                <span v-else>❌ 必须是 {{ numberLength }} 位纯数字</span>
              </div>

              <a-alert type="info" show-icon class="mt-4">
                <template #message>
                  <span class="text-xs">开奖执行将在下一阶段接入</span>
                </template>
                <template #description>
                  <span class="text-[11px] leading-relaxed">
                    核销要按奖级升序逐级认领、分批更新十万级记录，并联动派奖链路，
                    与期号管理不是一个量级的改动，单独作为一个阶段做，避免半成品按钮上线。
                  </span>
                </template>
              </a-alert>

              <a-button type="primary" danger block size="large" class="font-bold tracking-widest mt-4" disabled>
                🚀 执 行 开 奖
              </a-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { computed, ref, watch } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { lotteryIssueApi } from '/@/api/business/lottery/lottery-issue/lottery-issue-api';
  import { smartSentry } from '/@/lib/smart-sentry';

  const props = defineProps({
    lotteryCode: { type: String, default: '' },
    numberLength: { type: Number, default: 5 },
    totalCount: { type: Number, default: 0 },
  });

  const issueList = ref([]);
  const loading = ref(false);
  const saving = ref(false);
  const creating = ref(false);
  const selectedId = ref(null);
  const winningNumber = ref('');

  const createForm = ref({ issueNo: '', saleRange: [], planDrawTime: '' });

  // 每列都给 key：times / progress / result / action 是纯展示列，数据里并不存在同名字段
  const columns = [
    { title: '期号', key: 'issueNo', dataIndex: 'issueNo', width: '20%' },
    { title: '发售与开奖时间', key: 'times', dataIndex: 'times', width: '32%' },
    { title: '售卖进度', key: 'progress', dataIndex: 'progress', width: '16%' },
    { title: '开奖号码', key: 'result', dataIndex: 'result', width: '18%' },
    { title: '操作', key: 'action', dataIndex: 'action', align: 'center', width: '14%' },
  ];

  const selectedIssue = computed(() => issueList.value.find((i) => i.id === selectedId.value));
  const drawValid = computed(
    () => winningNumber.value.length === props.numberLength && /^\d+$/.test(winningNumber.value)
  );

  function fmt(value) {
    return value ? String(value).replace('T', ' ').slice(5, 16) : '';
  }
  function statusText(status) {
    return status === 0 ? '售卖中' : status === 1 ? '核销中' : '已开奖';
  }
  function statusColor(status) {
    return status === 0 ? 'blue' : status === 1 ? 'orange' : 'default';
  }
  function isSoldOut(record) {
    return props.totalCount > 0 && (record.soldCount || 0) >= props.totalCount;
  }

  const customRow = (record) => ({
    onClick: () => {
      if (record._editing) return;
      selectedId.value = record.id;
      winningNumber.value = '';
    },
  });

  // ---------------------------- 加载 ----------------------------

  async function loadList() {
    if (!props.lotteryCode) {
      issueList.value = [];
      return;
    }
    loading.value = true;
    try {
      const res = await lotteryIssueApi.queryPage({
        pageNum: 1,
        pageSize: 200,
        lotteryCode: props.lotteryCode,
      });
      issueList.value = (res.data.list || []).map((item) => ({ ...item, _editing: false }));
      // 选中项在换玩法后可能已不存在，清掉避免右侧面板显示上一个玩法的期号
      if (!issueList.value.some((i) => i.id === selectedId.value)) {
        selectedId.value = null;
      }
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  watch(() => props.lotteryCode, loadList, { immediate: true });

  // ---------------------------- 创设 ----------------------------

  function startCreate() {
    creating.value = true;
    createForm.value = { issueNo: '', saleRange: [], planDrawTime: '' };
  }

  async function submitCreate() {
    const form = createForm.value;
    if (!form.issueNo) {
      message.error('请填写期号标识');
      return;
    }
    if (!form.saleRange || form.saleRange.length !== 2) {
      message.error('请选择售卖周期');
      return;
    }
    saving.value = true;
    try {
      await lotteryIssueApi.add({
        lotteryCode: props.lotteryCode,
        issueNo: form.issueNo,
        saleStartTime: form.saleRange[0],
        saleEndTime: form.saleRange[1],
        planDrawTime: form.planDrawTime || null,
      });
      message.success('期数创设成功');
      creating.value = false;
      await loadList();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  // ---------------------------- 行内编辑 ----------------------------

  function startEdit(record) {
    record._editRange = record.saleStartTime && record.saleEndTime
      ? [normalize(record.saleStartTime), normalize(record.saleEndTime)]
      : [];
    record._editPlanDraw = record.planDrawTime ? normalize(record.planDrawTime) : '';
    record._editing = true;
  }

  // 后端返回的是 ISO 形式（2026-07-28T18:20:13），picker 的 value-format 要的是空格分隔
  function normalize(value) {
    return String(value).replace('T', ' ').slice(0, 19);
  }

  function cancelEdit(record) {
    record._editing = false;
  }

  async function saveEdit(record) {
    if (!record._editRange || record._editRange.length !== 2) {
      message.error('售卖时间不能清空');
      return;
    }
    try {
      await lotteryIssueApi.update({
        id: record.id,
        saleStartTime: record._editRange[0],
        saleEndTime: record._editRange[1],
        planDrawTime: record._editPlanDraw || null,
      });
      message.success('时间节点已更新');
      record._editing = false;
      await loadList();
    } catch (e) {
      smartSentry.captureError(e);
    }
  }

  // ---------------------------- 删除 ----------------------------

  function removeIssue(record) {
    Modal.confirm({
      title: `确认删除期号「${record.issueNo}」？`,
      content: '删除后不可恢复。已发出号码的期号无法删除。',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await lotteryIssueApi.delete(record.id);
          message.success('已删除');
          if (selectedId.value === record.id) {
            selectedId.value = null;
          }
          await loadList();
        } catch (e) {
          smartSentry.captureError(e);
        }
      },
    });
  }

  defineExpose({ reload: loadList });
</script>

<style lang="less" scoped>
  .section-title {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: 1rem;
    font-weight: 600;
    color: #0f172a;

    &::before {
      display: block;
      width: 4px;
      height: 16px;
      content: '';
      border-radius: 2px;
    }
  }
  .title-blue::before {
    background-color: #3b82f6;
  }
  .title-red::before {
    background-color: #ef4444;
  }

  :deep(.clickable-row) {
    cursor: pointer;

    &:hover > td {
      background-color: #f8fafc;
    }
  }

  .expand-enter-active,
  .expand-leave-active {
    overflow: hidden;
    transition: all 0.25s ease-in-out;
  }
  .expand-enter-from,
  .expand-leave-to {
    max-height: 0;
    opacity: 0;
    transform: translateY(-5px);
  }
  .expand-enter-to,
  .expand-leave-from {
    max-height: 250px;
    opacity: 1;
    transform: translateY(0);
  }
</style>
