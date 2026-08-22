<!--
  * 奖品配置表
  *
  * @Author:    weolwo
  * @Date:      2026-04-18 20:20:44
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="form.id ? '编辑' : '添加'" :width="800" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <!-- 活动编码是随机码，让人手打不现实，改为从活动列表选 -->
      <a-form-item label="归属活动" name="activityCode">
        <a-select
          style="width: 100%"
          v-model:value="form.activityCode"
          :options="activityOptions"
          :loading="activityLoading"
          :disabled="!!form.id"
          show-search
          option-filter-prop="label"
          placeholder="请选择归属活动"
        />
      </a-form-item>
      <!-- 资产类型决定下面能选哪些优惠配置，必须放在优惠配置之前选 -->
      <a-form-item label="资产类型" name="prizeType">
        <a-select
          style="width: 100%"
          v-model:value="form.prizeType"
          :options="PRIZE_TYPE_OPTIONS"
          placeholder="请选择资产类型"
          @change="onPrizeTypeChange"
        />
      </a-form-item>
      <!-- 优惠配置：预算与风控的载体。全量拉回后按 prizeType 本地过滤，避免切一次类型打一次接口 -->
      <a-form-item label="优惠配置" name="promotionConfigId">
        <a-select
          style="width: 100%"
          v-model:value="form.promotionConfigId"
          :options="promotionOptions"
          :loading="promotionLoading"
          :disabled="!form.prizeType"
          show-search
          option-filter-prop="label"
          :placeholder="form.prizeType ? '请选择优惠配置' : '请先选择资产类型'"
        />
        <div v-if="form.prizeType && promotionOptions.length === 0 && !promotionLoading" class="mt-1 text-xs text-orange-500">
          该资产类型下还没有启用中的优惠配置，请先到「优惠配置」页创建，否则发奖时会被判「资产配置异常」
        </div>
      </a-form-item>
      <a-form-item label="奖品名称" name="prizeName">
        <a-input style="width: 100%" v-model:value="form.prizeName" placeholder="奖品名称" />
      </a-form-item>
      <!-- 奖品编码：10位大写字母+数字，可手输也可一键生成；创建后被奖池物资/流水引用，编辑时锁死 -->
      <a-form-item label="奖品编码" name="prizeCode">
        <a-input-group compact>
          <a-input
            style="width: calc(100% - 96px)"
            v-model:value="form.prizeCode"
            :disabled="!!form.id"
            :maxlength="10"
            placeholder="如 H88JHKJFNE，或点右侧生成"
            @change="onCodeInput"
          />
          <a-tooltip :title="form.id ? '奖品编码创建后不可修改' : '随机生成一个未被占用的编码'">
            <!-- ghost + 紫调：跟页脚那颗蓝色「保存」拉开区分，一眼看出这是个辅助动作而不是提交 -->
            <!--
              宽度必须写死 96px 跟左边输入框的 calc(100% - 96px) 对上。
              这里不能用 w-24：本项目根字号是 14px，rem 型工具类全部被缩放成 0.875 倍，
              w-24 实际只有 84px，按钮右边会空出 12px 对不齐
            -->
            <a-button
              class="w-[96px]! font-medium"
              :class="form.id ? '' : 'border-violet-300! bg-violet-50! text-violet-600! hover:border-violet-400! hover:text-violet-700!'"
              :disabled="!!form.id"
              :loading="codeGenerating"
              @click="generateCode"
            >
              <template #icon><ThunderboltOutlined /></template>
              生成
            </a-button>
          </a-tooltip>
        </a-input-group>
      </a-form-item>
      <a-form-item label="奖品级别" name="prizeLevel">
        <a-input-number style="width: 100%" v-model:value="form.prizeLevel" placeholder="奖品级别" />
      </a-form-item>
      <a-form-item label="奖励价值" name="prizeValue">
        <a-input-number style="width: 100%" v-model:value="form.prizeValue" placeholder="奖励价值" />
      </a-form-item>
      <!-- ==================== 高级配置：写进 ext（json） ==================== -->
      <a-form-item label="高级配置">
        <div class="flex items-center gap-2">
          <a-switch v-model:checked="advancedOn" checked-children="开" un-checked-children="关" />
          <PictureOutlined class="text-violet-500" />
          <span class="text-xs text-slate-400">奖品图片等扩展信息，存入 ext</span>
          <a-tag v-if="extImages.length" color="purple" class="m-0!">{{ extImages.length }} 张图</a-tag>
        </div>
      </a-form-item>

      <a-form-item v-if="advancedOn" :wrapper-col="{ offset: 5, span: 19 }">
        <div class="rounded-lg border border-solid border-violet-100 bg-violet-50/30 p-3">
          <a-alert type="info" show-icon class="mb-3 rounded-lg!">
            <template #message>
              <span class="text-xs leading-relaxed text-slate-600">
                图片存进奖品的 <b class="text-slate-800">ext</b> 字段（JSON），形如
                <code class="rounded bg-slate-100 px-1 py-0.5 text-[11px] text-violet-700">{{ EXT_SHAPE_HINT }}</code>，
                字段名由你自己起、C 端按这个名字取图；最多 {{ MAX_EXT_IMAGES }} 张。ext 里的其它内容（如跳转链接）不会被覆盖。
              </span>
            </template>
          </a-alert>

          <div
            v-for="(item, index) in extImages"
            :key="item._uid"
            class="mb-2 flex items-center gap-3 rounded-lg border border-solid border-slate-200 bg-slate-50/70 px-3 py-2.5 transition-colors hover:border-violet-300 hover:bg-violet-50/40"
          >
            <span
              class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-violet-500 text-xs font-semibold text-white"
            >
              {{ index + 1 }}
            </span>
            <a-input
              v-model:value="item.name"
              class="w-44! shrink-0"
              :maxlength="32"
              placeholder="字段名，如 mainImage"
              @change="onExtNameInput(item)"
            />
            <ImageField v-model:fileId="item.fileId" class="min-w-0 flex-1" />
            <a-button danger type="text" size="small" class="shrink-0" @click="removeExtImage(index)">
              <template #icon><DeleteOutlined /></template>
            </a-button>
          </div>

          <div v-if="extImageError" class="mb-2 text-xs text-red-500">{{ extImageError }}</div>

          <!--
            满 3 张时直接不渲染按钮，换一行说明文字。
            比渲染一颗禁用按钮更干净：不用去和 antd 注入的 disabled 配色抢优先级
            （实测无论工具类还是 scoped 规则都压不住它），也少一个点不动的控件
          -->
          <a-button v-if="canAddExtImage" type="dashed" block class="ext-add-btn h-9 font-medium" @click="addExtImage">
            <template #icon><PlusOutlined /></template>
            添加图片（{{ extImages.length }}/{{ MAX_EXT_IMAGES }}）
          </a-button>
          <div
            v-else
            class="flex h-9 items-center justify-center rounded-lg border border-dashed border-slate-200 bg-slate-50 text-xs text-slate-400"
          >
            已添加 {{ MAX_EXT_IMAGES }} 张，达到上限（移除一张后可继续添加）
          </div>
        </div>
      </a-form-item>
      <!-- 开关与列表页的两个 a-switch 同一套语义：开=人工审批（更保守的一侧放在「开」） -->
      <a-form-item label="审批模式" name="approveMode">
        <a-switch
          v-model:checked="form.approveMode"
          :checkedValue="APPROVE_MODE_MANUAL"
          :unCheckedValue="APPROVE_MODE_AUTO"
          checked-children="人工审批"
          un-checked-children="自动免审"
        />
      </a-form-item>
      <a-form-item label="排序权重" name="sortWeight">
        <a-input-number style="width: 100%" v-model:value="form.sortWeight" placeholder="排序权重" />
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-switch
          v-model:checked="form.status"
          :checkedValue="PRIZE_STATUS_ENUM.ENABLED.value"
          :unCheckedValue="PRIZE_STATUS_ENUM.DISABLED.value"
          checked-children="启用"
          un-checked-children="停用"
        />
      </a-form-item>

    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="onClose">取消</a-button>
        <!--
          奖品通常是一个活动下连着配好几个，保存完就关弹窗意味着「再点新建、再选一遍活动/类型/优惠配置」。
          「保存并继续」保留这三项公共前缀，只清掉奖品自身的字段。编辑态没有这个语义，故只在新建时出现。
        -->
        <a-button v-if="!form.id" :loading="saving" @click="onSubmit(true)">保存并继续</a-button>
        <a-button type="primary" :loading="saving" @click="onSubmit(false)">保存</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script setup>
  import { computed, reactive, ref, nextTick } from 'vue';
  import _ from 'lodash';
  import { message } from 'ant-design-vue';
  import { DeleteOutlined, PictureOutlined, PlusOutlined, ThunderboltOutlined } from '@ant-design/icons-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { prizeConfigApi } from '/src/api/business/prize/prize-config-api';
  import { activityConfigApi } from '/src/api/business/activity/activity-config-api';
  import { promotionConfigApi } from '/src/api/business/risk/promotion-config-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { regular } from '/@/constants/regular-const';
  import ImageField from '/@/components/support/image-field/index.vue';
  import { PRIZE_STATUS_ENUM, PRIZE_TYPE_OPTIONS } from '/src/constants/business/prize/prize-config-const';

  // 审批模式：对齐 t_prize_config.approve_mode，与列表页的开关同一套取值
  const APPROVE_MODE_AUTO = 0;
  const APPROVE_MODE_MANUAL = 1;

  // ------------------------ 事件 ------------------------

  const emits = defineEmits(['reloadList']);

  // ------------------------ 显示与隐藏 ------------------------
  // 是否显示
  const visibleFlag = ref(false);

  function show(rowData) {
    Object.assign(form, formDefault);
    if (rowData && !_.isEmpty(rowData)) {
      Object.assign(form, rowData);
    }
    // 新建时给两个开关一个明确初始值，否则 a-switch 拿到 undefined 会显示成「关」，
    // 但提交上去是 undefined —— 界面和实际存的值对不上
    if (!form.id) {
      form.approveMode = form.approveMode ?? APPROVE_MODE_AUTO;
      form.status = form.status ?? PRIZE_STATUS_ENUM.ENABLED.value;
    }
    parseExt(form.ext);
    loadActivityOptions();
    loadPromotionOptions();
    visibleFlag.value = true;
    nextTick(() => {
      formRef.value.clearValidate();
    });
  }

  function onClose() {
    Object.assign(form, formDefault);
    visibleFlag.value = false;
  }

  // ------------------------ 表单 ------------------------

  // 组件ref
  const formRef = ref();

  const formDefault = {
    // 🔴 id 必须在这里列出来。formDefault 少一个 key，Object.assign(form, formDefault) 就清不掉它 ——
    // 于是「编辑一条 → 关闭 → 点新建」会拿到一个 form.id still set 的表单：
    // 标题显示「编辑」、归属活动与奖品编码被 :disabled="!!form.id" 锁死填不了，
    // 而 save() 判 form.id 走的是 update —— 填完保存会把刚才编辑的那条记录覆盖成这次的内容。
    id: undefined, //配置ID
    activityCode: undefined, //活动编码
    promotionConfigId: undefined, //优惠配置ID
    prizeType: undefined, //资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM
    prizeName: undefined, //奖品名称
    prizeCode: undefined, //奖品编码
    prizeLevel: undefined, //奖品级别
    prizeValue: undefined, //奖励价值
    approveMode: undefined, //审批模式：0-自动免审, 1-人工审批
    sortWeight: undefined, //排序权重
    ext: undefined, //扩展信息：如奖品图片URL、跳转链接等
    status: undefined, //状态：0-停用, 1-启用
  };

  let form = reactive({ ...formDefault });

  const rules = {
    activityCode: [{ required: true, message: '归属活动 必选' }],
    prizeType: [{ required: true, message: '资产类型 必选' }],
    // 不选优惠配置的话，发奖时 addProposal 第一步就会判「资产配置异常」，必须卡死
    promotionConfigId: [{ required: true, message: '优惠配置 必选' }],
    prizeCode: [
      { required: true, message: '奖品编码 必填' },
      { pattern: regular.bizCode, message: regular.bizCodeDesc },
    ],
    approveMode: [{ required: true, message: '审批模式 必填' }],
  };

  // ------------------------ 优惠配置级联下拉 ------------------------

  // 全量优惠配置（启用中），只拉一次，按 prizeType 本地过滤
  const promotionAllList = ref([]);
  const promotionLoading = ref(false);

  const promotionOptions = computed(() => {
    if (!form.prizeType) {
      return [];
    }
    return promotionAllList.value
      .filter((item) => item.prizeType === form.prizeType)
      .map((item) => ({
        value: item.id,
        // 标题里直接带预算水位与审批档位，运营选的时候就能看出这个池子还发不发得出来
        label: `${item.promoName}｜预算 ${budgetText(item)}｜数量 ${quotaText(item)}｜${reviewText(item.reviewLevel)}`,
      }));
  });

  const UNLIMITED = -1;
  const budgetText = (item) =>
    Number(item.totalAmount) === UNLIMITED ? '不限' : `${Number(item.usedAmount)}/${Number(item.totalAmount)}`;
  const quotaText = (item) => (item.totalQuota === UNLIMITED ? '不限' : `${item.usedQuota}/${item.totalQuota}`);
  const reviewText = (level) => (level === 0 ? '免审' : level === 1 ? '单层审批' : '双层审批');

  async function loadPromotionOptions() {
    promotionLoading.value = true;
    try {
      const res = await promotionConfigApi.optionList();
      promotionAllList.value = res.data || [];
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      promotionLoading.value = false;
    }
  }

  // 换了资产类型，原来选中的优惠配置多半已经不属于新类型了，直接清掉避免带着脏值提交
  function onPrizeTypeChange() {
    const stillValid = promotionAllList.value.some(
      (item) => item.id === form.promotionConfigId && item.prizeType === form.prizeType
    );
    if (!stillValid) {
      form.promotionConfigId = undefined;
    }
  }

  // ------------------------ 高级配置：ext(json) 里的图片 ------------------------

  const MAX_EXT_IMAGES = 3;
  const EXT_IMAGES_KEY = 'images';
  // 放在 JS 里而不是直接写进模板：这串里有 {{ }}，写在模板上会被 Vue 当插值解析
  const EXT_SHAPE_HINT = '{"images": {"mainImage": 12}}';

  const advancedOn = ref(false);
  const extImages = ref([]);
  const extImageError = ref('');
  // ext 里除 images 之外的内容（DDL 注释提到还会放跳转链接等），原样留着不动
  let extRest = {};
  let extUidSeq = 0;

  function newExtImage(name = '', fileId = null) {
    return { _uid: `ext_${++extUidSeq}`, name, fileId };
  }

  /**
   * 把 ext 字符串拆成「图片列表 + 其它内容」。
   * ext 是运营/其它系统都可能写的自由 JSON，解析失败时不能把人家的内容吃掉 ——
   * 解析不了就整段原样留在 extRest 之外（保持原字符串），只是这次不提供图片编辑。
   */
  function parseExt(raw) {
    extImages.value = [];
    extRest = {};
    extImageError.value = '';
    if (!raw) {
      return;
    }
    let parsed;
    try {
      parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch (e) {
      // 不是合法 JSON：不动它，保存时原样写回，免得把手工填的内容洗掉
      extRest = null;
      return;
    }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      extRest = null;
      return;
    }
    const { [EXT_IMAGES_KEY]: images, ...rest } = parsed;
    extRest = rest;
    if (images && typeof images === 'object' && !Array.isArray(images)) {
      extImages.value = Object.entries(images)
        .slice(0, MAX_EXT_IMAGES)
        .map(([name, fileId]) => newExtImage(name, Number(fileId) || null));
    }
    // 有图就默认把高级配置展开，否则运营看不出这条奖品其实配过图
    advancedOn.value = extImages.value.length > 0;
  }

  const canAddExtImage = computed(() => extImages.value.length < MAX_EXT_IMAGES);

  function addExtImage() {
    if (!canAddExtImage.value) {
      return;
    }
    extImages.value.push(newExtImage());
  }

  function removeExtImage(index) {
    extImages.value.splice(index, 1);
    extImageError.value = '';
  }

  // 字段名要当 JSON 的 key 用，留白和奇怪符号会让 C 端取值很难受
  function onExtNameInput(item) {
    if (item.name) {
      item.name = item.name.trim().replace(/\s+/g, '');
    }
    extImageError.value = '';
  }

  /**
   * 校验并组装 ext 字符串。返回 { ok, ext }。
   * 只填了名字没传图、或只传了图没起名的行，都算没配完 —— 直接放行会写出半截 JSON。
   */
  function buildExt() {
    // 原始 ext 不是合法 JSON 对象时不接管，保存时原样送回
    if (extRest === null) {
      return { ok: true, ext: form.ext };
    }

    const rows = extImages.value.filter((i) => i.name || i.fileId);
    const incomplete = rows.some((i) => !i.name || !i.fileId);
    if (incomplete) {
      return { ok: false, message: '高级配置里有未配完的图片：字段名和图片都要填' };
    }
    const names = rows.map((i) => i.name);
    if (new Set(names).size !== names.length) {
      return { ok: false, message: '高级配置里的字段名不能重复' };
    }

    const next = { ...extRest };
    if (rows.length) {
      next[EXT_IMAGES_KEY] = Object.fromEntries(rows.map((i) => [i.name, i.fileId]));
    } else {
      delete next[EXT_IMAGES_KEY];
    }
    // 整个 ext 空了就存 null，别往库里塞一个 "{}"
    return { ok: true, ext: Object.keys(next).length ? JSON.stringify(next) : null };
  }

  // ------------------------ 归属活动下拉 ------------------------

  const activityOptions = ref([]);
  const activityLoading = ref(false);

  async function loadActivityOptions() {
    activityLoading.value = true;
    try {
      const res = await activityConfigApi.optionList();
      activityOptions.value = (res.data || []).map((item) => ({
        value: item.activityCode,
        label: `${item.activityName}（${item.activityCode}）`,
      }));
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      activityLoading.value = false;
    }
  }

  // ------------------------ 奖品编码 ------------------------

  const codeGenerating = ref(false);

  // 手输时统一转大写，省得运营因为小写被规则拦下来
  function onCodeInput() {
    if (form.prizeCode) {
      form.prizeCode = form.prizeCode.toUpperCase();
    }
  }

  async function generateCode() {
    codeGenerating.value = true;
    try {
      const res = await prizeConfigApi.generateCode();
      form.prizeCode = res.data;
      formRef.value.clearValidate('prizeCode');
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      codeGenerating.value = false;
    }
  }

  const saving = ref(false);

  // 点击确定，验证表单。continueAdding=true 时保存后不关弹窗，接着录下一个奖品
  async function onSubmit(continueAdding = false) {
    try {
      await formRef.value.validateFields();
    } catch (err) {
      message.error('参数验证错误，请仔细填写表单数据!');
      return;
    }
    // ext 不是 a-form 管辖的字段，单独校验；出错时打开高级配置开关，否则提示指向的是被收起来的内容
    const built = buildExt();
    if (!built.ok) {
      extImageError.value = built.message;
      advancedOn.value = true;
      message.error(built.message);
      return;
    }
    save(built.ext, continueAdding);
  }

  /**
   * 连续录入时保留的「公共前缀」：同一个活动下配多个奖品，这几项几乎不会变，
   * 每次都重选一遍纯属重复劳动。奖品自身的字段（名称/编码/级别/价值/图片）一律清空。
   */
  function resetForNextPrize() {
    const keep = {
      activityCode: form.activityCode,
      prizeType: form.prizeType,
      promotionConfigId: form.promotionConfigId,
      approveMode: form.approveMode,
      sortWeight: form.sortWeight,
      status: form.status,
    };
    Object.assign(form, formDefault, keep);
    extImages.value = [];
    extRest = {};
    extImageError.value = '';
    advancedOn.value = false;
    nextTick(() => formRef.value?.clearValidate());
  }

  // 新建、编辑API
  async function save(ext, continueAdding) {
    saving.value = true;
    SmartLoading.show();
    try {
      const params = { ...form, ext };
      if (form.id) {
        await prizeConfigApi.update(params);
      } else {
        await prizeConfigApi.add(params);
      }
      // 连续录入时把刚存的奖品名带进提示 —— 弹窗不关，否则运营分不清「到底存进去没有」
      message.success(continueAdding ? `已保存「${params.prizeName}」，可继续录入下一个` : '操作成功');
      emits('reloadList');
      if (continueAdding) {
        resetForNextPrize();
      } else {
        onClose();
      }
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      saving.value = false;
      SmartLoading.hide();
    }
  }

  defineExpose({
    show,
  });
</script>

<style scoped lang="less">
  /*
   * 行内布局与配色全部走 Tailwind，这里只收 ghost 折叠面板自带的左右内边距 ——
   * 那部分是 antd 的内部结构（:deep 才够得着），用工具类表达不了
   */
  .ext-add-btn {
    color: #7c3aed;
    border-color: #ddd6fe;

    &:hover {
      color: #6d28d9;
      border-color: #a78bfa;
    }
  }

</style>
