<!--
  * 期号配置 新建 / 编辑
  *
  * ⚠️ 这里只放<b>后端真的会接收</b>的字段。生成器原本还有
  * 租户id / 已售数量 / 开奖时间 / 开奖号码 四项，全部删掉了 ——
  * LotteryIssueAddForm 与 LotteryIssueUpdateForm 里根本没有这些字段，
  * 填了会被静默丢弃，却照样弹「操作成功」。表单能填的，必须是能存下的。
  *
  * 编辑态只放开时间：期号是 FPE 的 tweak（key = HMAC(secret, lotteryCode|issueNo)），
  * 改了等于换密钥，已发号码全部反解失效 —— 所以玩法与期号降为只读展示。
  *
  * @Author:    weolwo
  * @Date:      2026-05-09 16:54:51
  * @Copyright  weolwo
-->
<template>
  <a-modal :title="isEdit ? '编辑期号' : '新建期号'" :width="720" :open="visibleFlag" @cancel="onClose" :maskClosable="false" :destroyOnClose="true">
    <a-form ref="formRef" :model="form" :rules="rules" :label-col="{ span: 5 }">
      <!-- 彩票编码是 t_lottery_config 的数据而不是枚举，原先绑的 SmartEnumSelect 没给 enum-name，
           下拉永远是空的 —— 改成从彩票配置列表现拉 -->
      <a-form-item label="彩票玩法" name="lotteryCode">
        <a-select
          v-if="!isEdit"
          style="width: 100%"
          v-model:value="form.lotteryCode"
          :options="lotteryOptions"
          :loading="lotteryLoading"
          placeholder="请选择彩票"
          allowClear
          show-search
          option-filter-prop="label"
        />
        <span v-else class="readonly-field">
          {{ form.lotteryName || form.lotteryCode }}
          <a-tag color="default" class="ml-2">创建后不可修改</a-tag>
        </span>
      </a-form-item>

      <a-form-item label="期号" name="issueNo">
        <a-input v-if="!isEdit" style="width: 100%" v-model:value="form.issueNo" placeholder="如 2026_MID_01" />
        <span v-else class="readonly-field">
          <span class="font-mono font-bold">{{ form.issueNo }}</span>
          <a-tooltip title="期号是 FPE 算号的盐值，改了等于换密钥：已发出的号码全部反解失效、签名全废，新号码还可能与历史重复">
            <a-tag color="default" class="ml-2">创建后不可修改 <QuestionCircleOutlined /></a-tag>
          </a-tooltip>
        </span>
      </a-form-item>

      <a-form-item label="售卖开始时间" name="saleStartTime">
        <a-date-picker
          :show-time="DAY_START_SHOW_TIME"
          valueFormat="YYYY-MM-DD HH:mm:ss"
          v-model:value="form.saleStartTime"
          style="width: 100%"
          placeholder="售卖开始时间"
        />
      </a-form-item>
      <a-form-item label="售卖结束时间" name="saleEndTime">
        <a-date-picker :show-time="DAY_END_SHOW_TIME" valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.saleEndTime" style="width: 100%" placeholder="售卖结束时间" />
      </a-form-item>

      <!-- 计划开奖时间：对外承诺的开奖时刻，与开奖流程写入的 settleTime 是两回事。
           列表的「逾期未开奖」就是拿它和数据库当前时间比出来的，不填则永远不会告警 -->
      <a-form-item label="计划开奖时间" name="planDrawTime">
        <a-date-picker show-time valueFormat="YYYY-MM-DD HH:mm:ss" v-model:value="form.planDrawTime" style="width: 100%" placeholder="对外承诺的开奖时刻" />
        <div class="form-hint">对外承诺的开奖时刻。到点未开奖会在列表里标红告警；留空则不纳入逾期巡检。</div>
      </a-form-item>

      <a-alert v-if="isEdit" type="info" show-icon class="mt-1">
        <template #message><span class="text-xs">实际开奖时间与开奖号码由开奖流程写入，不在此编辑</span></template>
        <template #description>
          <span class="text-xs">开奖请到「彩票配置工作台」的开奖操作台执行。</span>
        </template>
      </a-alert>
    </a-form>

    <template #footer>
      <a-space>
        <a-button @click="onClose">取消</a-button>
        <a-button type="primary" @click="onSubmit">保存</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script setup>
  import { DAY_END_SHOW_TIME, DAY_START_SHOW_TIME } from '/@/constants/date-time-const';
  import { computed, reactive, ref, nextTick } from 'vue';
  import _ from 'lodash';
  import { message } from 'ant-design-vue';
  import { QuestionCircleOutlined } from '@ant-design/icons-vue';
  import { SmartLoading } from '/@/components/framework/smart-loading';
  import { lotteryIssueApi } from '/src/api/business/lottery/lottery-issue-api';
  import { smartSentry } from '/@/lib/smart-sentry';
  import { lotteryConfigApi } from '/src/api/business/lottery/lottery-config-api';

  // ------------------------ 事件 ------------------------

  const emits = defineEmits(['reloadList']);

  // ------------------------ 彩票下拉 ------------------------

  const lotteryOptions = ref([]);
  const lotteryLoading = ref(false);

  // 彩票配置没有专门的 optionList 接口，这里借分页接口一次性拉回 —— 配置表本身就是几十条量级。
  // 只拉一次，弹窗反复开关不重复请求。
  async function loadLotteryOptions() {
    if (lotteryOptions.value.length > 0) {
      return;
    }
    lotteryLoading.value = true;
    try {
      const res = await lotteryConfigApi.queryPage({ pageNum: 1, pageSize: 200 });
      lotteryOptions.value = (res.data?.list || []).map((item) => ({
        value: item.lotteryCode,
        // 编码同时显示：期号是挂在编码上的，纯中文名对不上
        label: `${item.lotteryName}（${item.lotteryCode}）`,
      }));
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      lotteryLoading.value = false;
    }
  }

  // ------------------------ 显示与隐藏 ------------------------
  // 是否显示
  const visibleFlag = ref(false);

  const isEdit = computed(() => !!form.id);

  function show(rowData) {
    Object.assign(form, formDefault);
    if (rowData && !_.isEmpty(rowData)) {
      // 列表行里带着 saleState / overdue 等派生字段和一堆表字段，
      // 只挑表单认识的几个进来 —— 整个 Object.assign 进去会把它们一起 POST 回服务端
      Object.assign(form, _.pick(rowData, Object.keys(formDefault)));
      // 只读展示用，不参与提交
      form.lotteryName = rowData.lotteryName;
      // 后端返回 ISO 形式（2026-07-28T18:20:13），picker 的 valueFormat 要空格分隔
      form.saleStartTime = normalize(rowData.saleStartTime);
      form.saleEndTime = normalize(rowData.saleEndTime);
      form.planDrawTime = normalize(rowData.planDrawTime);
    }
    // 编辑态玩法固定不可改，没必要再拉一次下拉
    if (!form.id) {
      loadLotteryOptions();
    }
    visibleFlag.value = true;
    nextTick(() => {
      formRef.value.clearValidate();
    });
  }

  function normalize(value) {
    return value ? String(value).replace('T', ' ').slice(0, 19) : undefined;
  }

  function onClose() {
    Object.assign(form, formDefault);
    visibleFlag.value = false;
  }

  // ------------------------ 表单 ------------------------

  // 组件ref
  const formRef = ref();

  /*
   * 字段与后端两个 Form 一一对应，多一个都不留：
   *   AddForm    = lotteryCode / issueNo / saleStartTime / saleEndTime / planDrawTime
   *   UpdateForm = id / saleStartTime / saleEndTime / planDrawTime
   * 原先这里还有 租户id / soldCount / settleTime / winningNumber 四项，
   * 后端一个都不收（见两个 Form 的类注释），填了纯属白填。
   * 其中 租户id 与 soldCount 当时还是必填 —— 强迫运营填两个会被丢弃的值。
   */
  const formDefault = {
    id: undefined, //id
    lotteryCode: undefined, //彩票编码
    issueNo: undefined, //期号
    saleStartTime: undefined, //售卖开始时间
    saleEndTime: undefined, //售卖结束时间
    planDrawTime: undefined, //计划开奖时间：对外承诺的开奖时刻
    lotteryName: undefined, //玩法名称，编辑态只读展示用，不提交
  };

  let form = reactive({ ...formDefault });

  const rules = {
    lotteryCode: [{ required: true, message: '请选择彩票玩法' }],
    issueNo: [{ required: true, message: '期号 必填' }],
    saleStartTime: [{ required: true, message: '售卖开始时间 必填' }],
    saleEndTime: [{ required: true, message: '售卖结束时间 必填' }],
  };

  // 点击确定，验证表单
  async function onSubmit() {
    try {
      await formRef.value.validateFields();
      save();
    } catch (err) {
      message.error('参数验证错误，请仔细填写表单数据!');
    }
  }

  // 新建、编辑API
  async function save() {
    SmartLoading.show();
    try {
      if (form.id) {
        // 只送 UpdateForm 认识的四个字段。lotteryCode / issueNo 连传都不传 ——
        // 后端本来就不接，传了只会让人误以为它们能改
        await lotteryIssueApi.update({
          id: form.id,
          saleStartTime: form.saleStartTime,
          saleEndTime: form.saleEndTime,
          planDrawTime: form.planDrawTime || null,
        });
      } else {
        await lotteryIssueApi.add({
          lotteryCode: form.lotteryCode,
          issueNo: form.issueNo,
          saleStartTime: form.saleStartTime,
          saleEndTime: form.saleEndTime,
          planDrawTime: form.planDrawTime || null,
        });
      }
      message.success('操作成功');
      emits('reloadList');
      onClose();
    } catch (err) {
      smartSentry.captureError(err);
    } finally {
      SmartLoading.hide();
    }
  }

  defineExpose({
    show,
  });
</script>

<style lang="less" scoped>
  .readonly-field {
    display: inline-flex;
    align-items: center;
    color: #0f172a;
  }

  .form-hint {
    margin-top: 4px;
    font-size: 12px;
    line-height: 1.5;
    color: #94a3b8;
  }
</style>
