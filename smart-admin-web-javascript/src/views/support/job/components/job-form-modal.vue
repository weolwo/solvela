<!--
  * JOB 表单
  * @Author:    huke
  * @Date:      2024/06/29
-->
<template>
  <div>
    <!-- 编辑 -->
    <a-modal :open="updateModalShow" :width="650" :title="isAdd ? '添加':'编辑'" ok-text="确认" cancel-text="取消" @cancel="closeUpdateModal" @ok="confirmUpdateJob">
      <a-form ref="updateFormRef" :model="updateForm" :rules="updateRules" :label-col="{ span: 4 }">
        <a-form-item label="任务名称" name="jobName">
          <a-input placeholder="请输入任务名称" v-model:value="updateForm.jobName" :maxlength="100" :showCount="true" />
        </a-form-item>
        <a-form-item label="任务描述" name="remark">
          <a-textarea
              :auto-size="{ minRows: 2, maxRows: 4 }"
              v-model:value="updateForm.remark"
              placeholder="(可选)请输入任务备注描述"
              :maxlength="250"
              :showCount="true"
          />
        </a-form-item>
        <a-form-item label="排序" name="sort">
          <a-input-number
              v-model:value="updateForm.sort"
              :min="-99999999"
              :max="99999999"
              :precision="0"
              style="width: 100%"
              placeholder="值越小越靠前"
          >
          </a-input-number>
        </a-form-item>
        <!--
          🔴 从「手打全限定类名」改成「下拉选择已注册的执行器」。
          手打是「配了却永远不跑」的温床：旧实现用类名匹配，执行类一旦被 CGLIB 代理
          就永远对不上，而保存时的校验用的是原类名，必然通过 —— 保存成功、任务不跑、零报错。
          现在候选项直接来自后端注册表，与运行期匹配用的是同一份真源，选不出不存在的执行器。
        -->
        <a-form-item label="执行器" name="handlerName">
          <a-select
              v-model:value="updateForm.handlerName"
              placeholder="请选择执行器"
              :loading="handlerLoading"
              show-search
              option-filter-prop="label"
              :options="handlerOptions"
          />
          <div v-if="selectedHandlerDesc" style="color: #999; font-size: 12px; margin-top: 4px">
            {{ selectedHandlerDesc }}
          </div>
        </a-form-item>
        <a-form-item label="任务参数" name="param">
          <a-textarea
              :auto-size="{ minRows: 3, maxRows: 6 }"
              v-model:value="updateForm.param"
              placeholder="(可选)请输入任务执行参数"
              :maxlength="1000"
              :showCount="true"
          />
        </a-form-item>
        <!--
          🔴 「固定间隔」已下线：它与抢占式调度概念上不相容 ——
          「上次跑完再等 N 秒」要求知道任务何时结束，而抢占发生在任务开始之前。
          短周期改用 cron 的秒字段；「不许与上一次重叠」由阻塞策略负责，不该由触发类型表达。
        -->
        <a-form-item label="触发类型" name="triggerType">
          <a-radio-group v-model:value="updateForm.triggerType">
            <a-radio-button :value="TRIGGER_TYPE_ENUM.CRON.value">CRON表达式</a-radio-button>
            <a-radio-button :value="TRIGGER_TYPE_ENUM.ONE_TIME.value">一次性</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="触发时间" name="triggerTime">
          <a-input
              v-if="updateForm.triggerType === TRIGGER_TYPE_ENUM.CRON.value"
              placeholder="六段式：秒 分 时 日 月 周。示例：0 15 2 * * *（每天 2:15）"
              v-model:value="updateForm.cron"
              :maxlength="100"
              :showCount="true"
          />
          <a-date-picker
              v-else
              v-model:value="updateForm.oneTime"
              show-time
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择触发时刻"
              style="width: 100%"
          />
          <div v-if="updateForm.triggerType === TRIGGER_TYPE_ENUM.ONE_TIME.value" style="color: #999; font-size: 12px">
            一次性任务执行后自动终结，且不参与打散（业务时间点必须准时）
          </div>
        </a-form-item>
        <a-form-item label="运行档位" name="presetCode">
          <a-radio-group v-model:value="updateForm.presetCode">
            <a-radio-button v-for="item in presetOptions" :key="item.value" :value="item.value">
              {{ item.desc }}
            </a-radio-button>
          </a-radio-group>
          <div style="color: #999; font-size: 12px; margin-top: 4px">{{ selectedPresetHint }}</div>
        </a-form-item>
        <a-form-item label="是否开启" name="enabledFlag">
          <a-switch v-model:checked="updateForm.enabledFlag" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 立即执行 -->
    <a-modal
        :open="executeModalShow"
        :width="650"
        title="执行任务"
        ok-text="执行"
        cancel-text="取消"
        @cancel="closeExecuteModal"
        @ok="confirmExecuteJob"
    >
      <br />
      <a-alert type="info" show-icon style="margin-left: 25px">
        <template #message> 点击【执行】后会按照【任务参数】，无论任务是否开启，都会立即执行。 </template>
      </a-alert>
      <br />
      <a-form :label-col="{ span: 4 }">
        <a-form-item label="任务名称" name="jobName">
          <a-input v-model:value="executeForm.jobName" :disabled="true" />
        </a-form-item>
        <a-form-item label="执行器" name="handlerName">
          <a-input v-model:value="executeForm.handlerName" :disabled="true" />
        </a-form-item>
        <a-form-item label="任务参数" name="param">
          <a-textarea
              :auto-size="{ minRows: 3, maxRows: 6 }"
              v-model:value="executeForm.param"
              placeholder="(可选)请输入任务执行参数"
              :maxlength="1000"
              :showCount="true"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script setup>
import { message } from 'ant-design-vue';
import { computed, reactive, ref } from 'vue';
import { jobApi } from '/@/api/support/job-api';
import { smartSentry } from '/@/lib/smart-sentry';
import { SmartLoading } from '/@/components/framework/smart-loading/index';
import { TRIGGER_TYPE_ENUM, PRESET_ENUM } from '/@/constants/support/job-const';

// emit
const emit = defineEmits(['reloadList']);

// ------------------------------------ 执行器下拉 -------------------------------------

const handlerList = ref([]);
const handlerLoading = ref(false);

const handlerOptions = computed(() =>
  handlerList.value.map((e) => ({
    value: e.handlerName,
    label: `${e.title}（${e.handlerName}）`,
  }))
);

// 选中项的补充说明：把幂等与超时摆出来，省得运营去翻代码
const selectedHandlerDesc = computed(() => {
  const hit = handlerList.value.find((e) => e.handlerName === updateForm.handlerName);
  if (!hit) {
    return '';
  }
  const timeout = hit.defaultTimeoutSeconds > 0 ? `${hit.defaultTimeoutSeconds} 秒` : '不限';
  return `分组：${hit.groupName} ｜ 超时：${timeout} ｜ ${hit.idempotent ? '幂等，可安全重跑' : '非幂等，重跑需谨慎'}`;
});

// 档位候选：受执行器声明的车道约束 ——
// FAST 执行器只能选轻量档，其余档位的超时都突破了快车道 30 秒的硬上限。
// 这个约束服务端也会再校验一次（前端 UI 校验只是防呆，铁律 2）
const presetOptions = computed(() => {
  const hit = handlerList.value.find((e) => e.handlerName === updateForm.handlerName);
  const lane = hit?.lane;
  return Object.values(PRESET_ENUM).filter((p) => !lane || !p.lane || p.lane === lane);
});

const selectedPresetHint = computed(() => {
  const hit = Object.values(PRESET_ENUM).find((e) => e.value === updateForm.presetCode);
  return hit ? hit.hint : '';
});

async function loadHandlerList() {
  try {
    handlerLoading.value = true;
    const res = await jobApi.queryHandlerList();
    handlerList.value = res.data || [];
  } catch (e) {
    smartSentry.captureError(e);
  } finally {
    handlerLoading.value = false;
  }
}

const isAdd = ref(false);
const updateModalShow = ref(false);
const updateFormRef = ref();

const updateFormDefault = {
  jobId: null,
  jobName: '',
  handlerName: undefined,
  triggerType: TRIGGER_TYPE_ENUM.CRON.value,
  triggerValue: null,
  cron: '',
  oneTime: null,
  presetCode: PRESET_ENUM.NORMAL.value,
  param: '',
  enabledFlag: false,
  remark: '',
  sort: null,
};
let updateForm = reactive({ ...updateFormDefault });
const updateRules = {
  jobName: [{ required: true, message: '请输入任务名称' }],
  handlerName: [{ required: true, message: '请选择执行器' }],
  triggerType: [{ required: true, message: '请选择触发类型' }],
  presetCode: [{ required: true, message: '请选择运行档位' }],
  sort: [{ required: true, message: '请输入排序' }],
};

// 打开编辑弹框
function openUpdateModal(record) {
  isAdd.value = null == record;
  // 每次打开都重新拉一次：执行器列表来自后端注册表，发版后会变
  loadHandlerList();
  // 更新
  if(!isAdd.value){
    Object.assign(updateForm, record);
    if (TRIGGER_TYPE_ENUM.CRON.value === record.triggerType) {
      updateForm.cron = record.triggerValue;
    }
    if (TRIGGER_TYPE_ENUM.ONE_TIME.value === record.triggerType) {
      updateForm.oneTime = record.triggerValue;
    }
  }
  updateModalShow.value = true;
}

// 关闭编辑弹框
function closeUpdateModal() {
  Object.assign(updateForm, updateFormDefault);
  updateModalShow.value = false;
}

// 确认更新
async function confirmUpdateJob() {
  updateFormRef.value
      .validate()
      .then(async () => {

        if (TRIGGER_TYPE_ENUM.CRON.value === updateForm.triggerType) {
          updateForm.triggerValue = updateForm.cron;
        }
        if (TRIGGER_TYPE_ENUM.ONE_TIME.value === updateForm.triggerType) {
          updateForm.triggerValue = updateForm.oneTime;
        }

        if(!updateForm.triggerValue){
          message.error('请填写 触发时间');
          return;
        }

        try {
          SmartLoading.show();
          if(isAdd.value){
            await jobApi.addJob(updateForm)
            message.success('添加成功');
          }else {
            await jobApi.updateJob(updateForm);
            message.success('更新成功');
          }
          closeUpdateModal();
          emit('reloadList');
        } catch (error) {
          smartSentry.captureError(error);
        } finally {
          SmartLoading.hide();
        }
      })
      .catch((error) => {
        console.log('error', error);
        message.error('参数验证错误，请检查表单数据');
      });
}

// ------------------------------------ 执行任务 -------------------------------------
const executeModalShow = ref(false);
const executeFormDefault = {
  jobId: null,
  jobName: '',
  handlerName: '',
  param: null,
};
let executeForm = reactive({ ...executeFormDefault });

// 打开执行弹框
function openExecuteModal(record) {
  Object.assign(executeForm, record);
  executeModalShow.value = true;
}

// 关闭执行弹框
function closeExecuteModal() {
  Object.assign(executeForm, executeFormDefault);
  executeModalShow.value = false;
}

// 确认执行
async function confirmExecuteJob() {
  try {
    let executeParam = {
      jobId: executeForm.jobId,
      param: executeForm.param,
    };
    await jobApi.executeJob(executeParam);
    // loading 延迟后再提示刷新
    SmartLoading.show();
    await new Promise((resolve) => setTimeout(resolve, 2000));
    message.success('执行成功');
    closeExecuteModal();
    emit('reloadList');
  } catch (e) {
    smartSentry.captureError(e);
  } finally {
    SmartLoading.hide();
  }
}

defineExpose({
  openUpdateModal,
  openExecuteModal,
});
</script>
