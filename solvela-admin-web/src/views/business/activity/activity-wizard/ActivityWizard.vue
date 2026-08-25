<!--
  * 活动创建向导（外壳）
  *
  * 形态：活动配置页 →「创建活动向导」→ 先建活动 → 按类型挂载对应玩法的整套配置流程 → 完成。
  *
  * 三条设计约束，改动前务必先读：
  *
  * 1. 【第一步提交即落库，没有「最后统一提交」这个选项】
  *    玩法配置的三个保存接口都要求活动已存在（PrizePoolConfigService.workbenchSave 查不到活动
  *    直接返回「活动不存在」）。好消息是新建活动(status=0)能立刻配玩法 —— 那里的 status==1
  *    只用来决定是否启用结构锁，不是保存的前置条件。
  *
  * 2. 【外壳不持有「保存」按钮】
  *    TaskWizard 的提交是走完 5 步一次性提交（后端 wizardSubmit 是单个 @Transactional），
  *    它根本不存在可供外部调用的「中途 save」。所以保存权留在各玩法组件自己手里，
  *    外壳第二步只提供「稍后再配」，推进完成页由子组件的 emit('saved') 驱动。
  *
  * 3. 【外壳不加路由离开守卫】
  *    第一步提交即落库，此后外壳自身没有未保存状态；第二步的未保存改动由玩法组件自己的
  *    onBeforeRouteLeave 拦截（它注册在子组件里同样对本路由生效）。
  *    外壳再加一层的结果是内外各弹一次确认框。
  *
  * @Author:    alaric
  * @Date:      2026-07-29
-->
<template>
  <div>
    <!-- 面包屑 -->
    <div class="mb-4 flex items-center gap-2">
      <a-button type="link" class="p-0 text-slate-500" @click="backToList">← 返回活动配置</a-button>
      <span class="text-slate-300">/</span>
      <span class="text-sm font-bold">创建活动向导</span>
    </div>

    <a-card :bordered="false" class="shadow-sm">
      <!-- 步骤条按类型伸缩：BASIC 两步、玩法类三步 -->
      <div class="px-6 pt-2 pb-6">
        <a-steps :current="stepIndex" size="small" :items="stepItems" />
      </div>

      <a-divider class="my-0" />

      <!-- ==================== 第一步：创建活动 ====================
           左右布局：左侧活动本体，右侧就地建奖品。
           右侧存在的理由：新建活动的资产大库必然为空，而抽奖/彩票第二步都要从资产大库引入奖项 ——
           不在这里做掉，运营进到第二步只能跳出向导去「奖品配置」建完再回来。 -->
      <div v-if="step === STEP.BASE" class="p-6">
        <!-- 左右平分：奖品那一侧要放列表、行内表单和滚动区，给窄了操作很挤 -->
        <a-row :gutter="24">
          <a-col :xs="24" :lg="12">
            <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
              <a-form-item label="活动名称" name="activityName">
                <a-input v-model:value="form.activityName" size="large" placeholder="例如：618 年中狂欢大促" :maxlength="64" />
              </a-form-item>

              <a-form-item label="活动起止时间" name="timeRange">
                <a-range-picker
                  v-model:value="form.timeRange"
                  :show-time="RANGE_SHOW_TIME"
                  :placeholder="['开始时间', '结束时间']"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  class="w-full"
                  size="large"
                />
              </a-form-item>

              <a-form-item label="活动编码" name="activityCode">
                <div class="flex gap-2">
                  <a-input v-model:value="form.activityCode" size="large" placeholder="10 位大写字母 + 数字" class="font-mono" />
                  <a-button size="large" :loading="codeLoading" @click="genCode">重新生成</a-button>
                </div>
                <div class="text-xs text-slate-400 mt-1">进入向导即预填一个可用编码，可手工改写；创建后不可修改。</div>
              </a-form-item>

              <a-form-item label="玩法类型" name="activityType">
                <div class="grid grid-cols-4 gap-3">
                  <div
                    v-for="t in typeList"
                    :key="t.value"
                    class="type-card p-4 flex flex-col items-center text-center"
                    :class="{ active: form.activityType === t.value, basic: !t.component }"
                    @click="form.activityType = t.value"
                  >
                    <span class="text-3xl mb-2">{{ t.icon }}</span>
                    <span class="font-bold text-sm">{{ t.desc }}</span>
                    <span class="text-[10px] text-slate-400 mt-1 leading-tight">{{ t.hint }}</span>
                  </div>
                </div>
                <!-- 把选错的代价和退路写在选择的当下，而不是等出事了再解释 -->
                <div class="text-xs mt-2" :class="isBasic ? 'text-green-600' : 'text-slate-400'">
                  <template v-if="isBasic">
                    基础活动只有活动外壳，可挂奖品、作为预算与统计的归集维度。创建后<b>可随时升级</b>为任一玩法类型。
                  </template>
                  <template v-else> 创建后类型即冻结（已配的玩法配置会因改类型变成孤儿数据）。不确定就先建<b>基础活动</b>，之后再升级。 </template>
                </div>
              </a-form-item>
            </a-form>
          </a-col>

          <a-col :xs="24" :lg="12">
            <div class="bg-slate-50 rounded-lg border border-slate-200 p-4 h-full">
              <PrizeQuickPanel ref="prizePanelRef" :activity-code="''" @change="onPrizeChange" />
            </div>
          </a-col>
        </a-row>

        <div class="flex justify-end gap-2 mt-6">
          <a-button size="large" @click="backToList">取消</a-button>
          <a-button type="primary" size="large" :loading="submitting" @click="submitBase">
            {{ submitLabel }}
          </a-button>
        </div>
      </div>

      <!-- ==================== 第二步：配置玩法（BASIC 不经过） ==================== -->
      <div v-else-if="step === STEP.GAMEPLAY" class="p-6">
        <!-- 活动摘要条：第一步已落库，此处只读回显 -->
        <div class="flex items-center gap-4 mb-4 px-4 py-3 bg-slate-50 rounded-lg border border-slate-200 flex-wrap">
          <a-tag :color="typeMeta.color" class="m-0">{{ typeMeta.icon }} {{ typeMeta.desc }}</a-tag>
          <span class="font-bold text-sm">{{ created.activityName }}</span>
          <span class="font-mono text-xs text-slate-500">{{ created.activityCode }}</span>
          <a-tag color="default" class="m-0 ml-auto">未开始 · 结构锁未启用</a-tag>
        </div>

        <!-- 配到一半发现少个奖品时，就地补一个，不用跳出向导 -->
        <a-collapse v-model:activeKey="prizePanelKey" class="mb-4" ghost>
          <a-collapse-panel key="prize" header="🎁 活动奖品（配置玩法时需要从这里引入）">
            <PrizeQuickPanel :activity-code="created.activityCode" />
          </a-collapse-panel>
        </a-collapse>

        <!-- 动态挂载对应玩法组件；活动已锁定，保存按钮在组件自己手里 -->
        <component :is="gameplayComponent" v-if="gameplayComponent" :activity-code="created.activityCode" :embedded="true" @saved="onGameplaySaved" />

        <div class="flex justify-start mt-6">
          <a-button size="large" @click="onConfigLater">稍后再配</a-button>
          <span class="text-xs text-slate-400 self-center ml-3">
            活动已创建，配置完成后会自动进入下一步；也可以稍后从活动列表点「继续配置」回来。
          </span>
        </div>
      </div>

      <!-- ==================== 展示配置（可跳过） ==================== -->
      <div v-else-if="step === STEP.DISPLAY" class="p-6">
        <div class="flex items-center gap-4 mb-4 px-4 py-3 bg-slate-50 rounded-lg border border-slate-200 flex-wrap">
          <a-tag :color="typeMeta.color" class="m-0">{{ typeMeta.icon }} {{ typeMeta.desc }}</a-tag>
          <span class="font-bold text-sm">{{ created.activityName }}</span>
          <span class="font-mono text-xs text-slate-500">{{ created.activityCode }}</span>
          <span class="text-xs text-slate-400 ml-auto">有些活动不需要 C 端展示，这一步可以跳过</span>
        </div>

        <ActivityDisplayForm :activity-code="created.activityCode" :skippable="true" @saved="step = STEP.DONE" @skip="step = STEP.DONE" />
      </div>

      <!-- ==================== 完成页 ==================== -->
      <div v-else class="p-6">
        <a-result
          status="success"
          :title="isBasic ? '基础活动创建成功' : '活动与玩法配置已完成'"
          :sub-title="`${created.activityName}（${created.activityCode}）`"
        >
          <template #extra>
            <a-space>
              <a-button type="primary" size="large" @click="backToList">回活动列表</a-button>
              <a-button size="large" @click="startAnother">再建一个</a-button>
              <a-button v-if="!isBasic" size="large" @click="goWorkbench">去玩法工作台</a-button>
            </a-space>
          </template>
          <div v-if="isBasic" class="text-center text-xs text-slate-500">
            基础活动尚未挂载任何玩法。之后想加抽奖/任务/彩票，可在活动列表点「升级玩法」—— 它没有玩法下游，升级不会产生孤儿数据。
          </div>
        </a-result>
      </div>
    </a-card>
  </div>
</template>

<script setup>
  import { RANGE_SHOW_TIME } from '/@/constants/date-time-const';
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { message } from 'ant-design-vue';
  import { useRoute, useRouter } from 'vue-router';
  import { activityConfigApi } from '/src/api/business/activity/activity-config-api';
  import { ACTIVITY_TYPE_LIST, activityTypeMeta, hasGameplay } from '/src/constants/business/activity/activity-config-const';
  import { regular } from '/@/constants/regular-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import DrawWorkbench from '/@/views/business/draw/draw-workbench/DrawWorkbench.vue';
  import TaskWizard from '/@/views/business/task/task-wizard/TaskWizard.vue';
  import LotteryWorkbench from '/@/views/business/lottery/lottery-workbench/LotteryWorkbench.vue';
  import PrizeQuickPanel from './components/PrizeQuickPanel.vue';
  import ActivityDisplayForm from '../components/activity-display-form.vue';

  // ⚠️ 路由 path 以 t_menu 实际记录为准，不要照搬组件目录：
  // 活动配置列表的菜单 path 是 /activity/activity-config/list（component 才是 /business/... 开头的目录）
  const ACTIVITY_LIST_PATH = '/activity/activity-config/list';

  /**
   * 步骤用语义名而不是数字下标。
   *
   * 原先是 { BASE:0, GAMEPLAY:1, DONE:2 } 的数字常量，加了「展示配置」之后 BASIC 类型
   * 会跳过 GAMEPLAY、后面的下标整体前移一位 —— 数字常量就得按类型算两套，
   * 而「当前在第几步」和「步骤条渲染几项」一旦对不齐，高亮就串位，且构建期完全发现不了。
   *
   * 改成语义名之后，下标只在渲染步骤条时算一次，业务代码里不再出现魔数。
   */
  const STEP = { BASE: 'BASE', GAMEPLAY: 'GAMEPLAY', DISPLAY: 'DISPLAY', DONE: 'DONE' };

  // 组件名 → 组件实现的唯一映射。常量文件只存名字（它不该 import 组件），在这里落地
  const GAMEPLAY_COMPONENTS = { DrawWorkbench, TaskWizard, LotteryWorkbench };

  const router = useRouter();
  const route = useRoute();

  const typeList = ACTIVITY_TYPE_LIST;
  const step = ref(STEP.BASE);
  const formRef = ref();
  const submitting = ref(false);
  const codeLoading = ref(false);

  // 单一状态源：第一步的全部输入（铁律 4）
  const form = reactive({
    activityName: '',
    timeRange: [],
    activityCode: '',
    activityType: 'BASIC',
  });

  // 已落库的活动信息，第二步与完成页都读它（而不是继续读 form，避免「再建一个」时被清空）
  const created = reactive({ activityCode: '', activityName: '', activityType: '' });

  // 第一步右侧攒下的奖品（草稿态），随活动一起提交
  const prizePanelRef = ref();
  const draftPrizeList = ref([]);
  // 第二步的奖品面板默认收起：那一步的主角是玩法配置，奖品只是「发现少了再补」
  const prizePanelKey = ref([]);

  function onPrizeChange(list) {
    draftPrizeList.value = list;
  }

  const submitLabel = computed(() => {
    const n = draftPrizeList.value.length;
    if (isBasic.value) {
      return n ? `创建活动（含 ${n} 个奖品）` : '创建活动';
    }
    return n ? `创建并配置玩法（含 ${n} 个奖品）` : '创建并配置玩法';
  });

  const isBasic = computed(() => !hasGameplay(created.activityType || form.activityType));
  const typeMeta = computed(() => activityTypeMeta(created.activityType || form.activityType));
  const gameplayComponent = computed(() => GAMEPLAY_COMPONENTS[typeMeta.value.component] || null);

  const stepItems = computed(() =>
    isBasic.value
      ? [{ title: '创建活动' }, { title: '展示配置' }, { title: '完成' }]
      : [{ title: '创建活动' }, { title: '配置玩法' }, { title: '展示配置' }, { title: '完成' }]
  );

  // 语义名 → 步骤条下标。BASIC 少一档，后面整体前移一位；这是唯一出现数字的地方
  const stepIndex = computed(() => {
    const order = isBasic.value ? [STEP.BASE, STEP.DISPLAY, STEP.DONE] : [STEP.BASE, STEP.GAMEPLAY, STEP.DISPLAY, STEP.DONE];
    const idx = order.indexOf(step.value);
    return idx < 0 ? 0 : idx;
  });

  const rules = {
    activityName: [{ required: true, message: '请输入活动名称' }],
    timeRange: [{ required: true, message: '请选择起止时间', type: 'array' }],
    activityCode: [
      { required: true, message: '请输入或生成活动编码' },
      { pattern: regular.bizCode, message: regular.bizCodeDesc },
    ],
    activityType: [{ required: true, message: '请选择玩法类型' }],
  };

  async function genCode() {
    codeLoading.value = true;
    try {
      const res = await activityConfigApi.generateCode();
      form.activityCode = res.data;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      codeLoading.value = false;
    }
  }

  async function submitBase() {
    try {
      await formRef.value.validate();
    } catch (e) {
      return;
    }
    submitting.value = true;
    try {
      /*
       * 走聚合接口而不是先 add 活动再循环 add 奖品：
       * 奖品的 activityCode 必填，活动必须先存在；若前端串行发起，中途任一奖品失败就会留下
       * 「活动建好了、奖品只建了一半」的残局，而运营看到的只是一个失败提示。
       * 聚合到一个事务后，界面上的成败与库里的状态永远一致。
       */
      await activityConfigApi.wizardCreate({
        activityCode: form.activityCode,
        activityName: form.activityName,
        activityType: form.activityType,
        startTime: form.timeRange[0],
        endTime: form.timeRange[1],
        prizeList: draftPrizeList.value,
      });
      message.success(draftPrizeList.value.length ? `活动已创建，同时建了 ${draftPrizeList.value.length} 个奖品` : '活动已创建');
      // 落库成功后把活动信息定格下来，后续步骤只读它
      created.activityCode = form.activityCode;
      created.activityName = form.activityName;
      created.activityType = form.activityType;
      // BASIC 没有玩法配置那一步，直接进展示配置；展示配置本身可跳过
      step.value = hasGameplay(form.activityType) ? STEP.GAMEPLAY : STEP.DISPLAY;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      submitting.value = false;
    }
  }

  // 玩法组件保存成功后由它主动通知，外壳据此推进 —— 外壳自己不判断「配完了没有」
  function onGameplaySaved() {
    step.value = STEP.DISPLAY;
  }

  function onConfigLater() {
    message.info('活动已创建，可稍后从活动列表点「继续配置」回来');
    backToList();
  }

  function backToList() {
    router.push(ACTIVITY_LIST_PATH);
  }

  function goWorkbench() {
    const target = typeMeta.value.route;
    if (target) {
      router.push(target);
    }
  }

  // 「再建一个」与「新建入口」是同一件事，复用同一段重置逻辑，避免两处各自遗漏某个字段
  function startAnother() {
    initFromQuery();
  }

  /**
   * 按当前 query 初始化向导。两种入口：
   * ① 新建：预填一个编码，从第一步开始。
   * ② 继续配置（?activityCode=&activityType=&activityName=&step=2）：
   *    活动已存在，直接进第二步。这是「空壳活动」的恢复路径 ——
   *    第一步落库后运营中途关掉浏览器，活动已在库里，不该让他重建一个。
   * ③ 补配展示（同上，step=display）：
   *    展示配置在向导里是可跳过的一步，跳过之后必须还有路回来 ——
   *    否则运营等设计出图后想补主视觉，只能重建一个活动。
   */
  function initFromQuery() {
    const { activityCode, activityType, activityName, step: stepParam } = route.query;
    if (activityCode && stepParam === 'display') {
      created.activityCode = activityCode;
      created.activityName = activityName || '';
      created.activityType = activityType || '';
      form.activityType = created.activityType;
      step.value = STEP.DISPLAY;
      return;
    }
    if (activityCode && stepParam === '2') {
      created.activityCode = activityCode;
      created.activityType = activityType || '';
      created.activityName = activityName || activityCode;
      form.activityType = created.activityType;
      step.value = STEP.GAMEPLAY;
      return;
    }
    // 新建：把上一次的残留状态清干净再开始。
    // 草稿奖品也必须清 —— 否则「再建一个」会把上个活动的奖品原样带进新活动，
    // 而奖品编码全局唯一，第二次提交必然撞码，报错信息还会指向一个运营没印象的编码。
    Object.assign(form, { activityName: '', timeRange: [], activityCode: '', activityType: 'BASIC' });
    Object.assign(created, { activityCode: '', activityName: '', activityType: '' });
    draftPrizeList.value = [];
    step.value = STEP.BASE;
    genCode();
  }

  /*
   * ⚠️ 必须 watch route.query，不能只写 onMounted。
   *
   * 从「继续配置」的 URL（带 query）导航回菜单的「活动创建向导」（无 query）时，
   * 路由 path 没变，Vue 不会重新挂载本组件 —— onMounted 不会再跑，
   * 结果是点开菜单看到的是上一个活动停在第二步的配置页，而不是空白的新建表单。
   *
   * 这与三个玩法组件里 watch(props.activityCode) 防的是同一类问题
   * （彩票 P4 验收记过：导航到同一 hash 路由不会触发组件重新挂载）。
   * 当时只想到了子组件，外壳自己反而漏了，实测才暴露出来。
   */
  watch(() => route.query, initFromQuery);

  onMounted(initFromQuery);
</script>

<style scoped>
  .type-card {
    border: 2px solid #e4e7eb;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s;
    position: relative;
    overflow: hidden;
    background: #fff;
  }
  .type-card:hover {
    border-color: #91caff;
    box-shadow: 0 4px 12px rgba(22, 119, 255, 0.1);
  }
  .type-card.active {
    border-color: #1677ff;
    background: #e6f2ff;
  }
  .type-card.active::after {
    content: '✓';
    position: absolute;
    top: -1px;
    right: -1px;
    background: #1677ff;
    color: #fff;
    font-size: 12px;
    font-weight: bold;
    padding: 2px 8px;
    border-bottom-left-radius: 8px;
  }
  .type-card.basic.active {
    border-color: #52c41a;
    background: #f6ffed;
  }
  .type-card.basic.active::after {
    background: #52c41a;
  }
</style>
