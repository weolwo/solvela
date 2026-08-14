<!--
  * 阶梯奖励搭建器（t_task_prize_mapping 子表）
  * 维护阶梯数组的增删改，stageLevel 由行序自动生成，至少保留 1 级
  *
  * @Author:    alaric
  * @Date:      2026-07-19
-->
<template>
  <a-space direction="vertical" class="w-full" :size="12">
    <a-table
      :data-source="modelValue"
      :columns="LADDER_COLUMNS"
      :pagination="false"
      :row-key="(record, index) => index"
      size="small"
      bordered
    >
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.dataIndex === LADDER_FIELD.STAGE_LEVEL">
          <a-tag color="blue">第 {{ index + 1 }} 级</a-tag>
        </template>

        <template v-else-if="column.dataIndex === LADDER_FIELD.STAGE_CONDITION">
          <a-input-number
            :value="record.stageCondition"
            :min="1"
            :addon-after="conditionUnit"
            class="w-full"
            @update:value="(v) => updateRow(index, LADDER_FIELD.STAGE_CONDITION, v)"
          />
        </template>

        <template v-else-if="column.dataIndex === LADDER_FIELD.PRIZE_MODE">
          <a-select
            :value="record.prizeMode"
            :options="PRIZE_MODE_OPTIONS"
            class="w-full"
            @update:value="(v) => updateRow(index, LADDER_FIELD.PRIZE_MODE, v)"
          />
        </template>

        <!--
          只能从当前活动下已配置的奖品里选。此前是自由文本框，编码拼错或跨活动填写时，
          任务能存下来但发奖必然查不到奖品配置，而那次失败是静默的。
        -->
        <template v-else-if="column.dataIndex === LADDER_FIELD.PRIZE_CODE">
          <a-select
            :value="record.prizeCode"
            :options="prizeOptions"
            :loading="prizeLoading"
            :disabled="prizeOptions.length === 0"
            :placeholder="prizeOptions.length ? '请选择奖品' : '当前活动下暂无奖品'"
            class="w-full"
            show-search
            option-filter-prop="label"
            @update:value="(v) => updateRow(index, LADDER_FIELD.PRIZE_CODE, v)"
          />
        </template>

        <template v-else-if="column.dataIndex === LADDER_FIELD.PRIZE_VALUE">
          <a-input-number
            :value="record.prizeValue"
            :min="0"
            class="w-full"
            @update:value="(v) => updateRow(index, LADDER_FIELD.PRIZE_VALUE, v)"
          />
        </template>

        <template v-else-if="column.dataIndex === LADDER_FIELD.ACTION">
          <a-button
            type="link"
            size="small"
            danger
            :disabled="modelValue.length <= MIN_PRIZE_LADDER_COUNT"
            @click="removeRow(index)"
          >
            删除
          </a-button>
        </template>
      </template>
    </a-table>

    <a-button type="dashed" block @click="addRow">
      <template #icon><PlusOutlined /></template>
      添加阶梯
    </a-button>
  </a-space>
</template>

<script setup>
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { DEFAULT_PRIZE_LADDER, MIN_PRIZE_LADDER_COUNT, PRIZE_MODE_OPTIONS } from './task-wizard-const';

  const props = defineProps({
    // 阶梯数组（t_task_prize_mapping 行集合）
    modelValue: {
      type: Array,
      default: () => [],
    },
    // 达标条件单位，随任务类型变化（次 / 元）
    conditionUnit: {
      type: String,
      default: '次',
    },
    // 当前活动下已配置的奖品，来自 t_prize_config（由向导按 activityCode 拉取）
    prizeOptions: {
      type: Array,
      default: () => [],
    },
    prizeLoading: {
      type: Boolean,
      default: false,
    },
  });

  const emit = defineEmits(['update:modelValue']);

  const LADDER_FIELD = {
    STAGE_LEVEL: 'stageLevel',
    STAGE_CONDITION: 'stageCondition',
    PRIZE_MODE: 'prizeMode',
    PRIZE_CODE: 'prizeCode',
    PRIZE_VALUE: 'prizeValue',
    ACTION: 'action',
  };

  const LADDER_COLUMNS = [
    { title: '阶梯层级', dataIndex: LADDER_FIELD.STAGE_LEVEL, width: 100 },
    { title: '达标条件 stage_condition', dataIndex: LADDER_FIELD.STAGE_CONDITION, width: 180 },
    { title: '发奖模式 prize_mode', dataIndex: LADDER_FIELD.PRIZE_MODE, width: 160 },
    { title: '奖励编码 prize_code', dataIndex: LADDER_FIELD.PRIZE_CODE },
    { title: '奖励额度 prize_value', dataIndex: LADDER_FIELD.PRIZE_VALUE, width: 160 },
    { title: '操作', dataIndex: LADDER_FIELD.ACTION, width: 80 },
  ];

  function updateRow(index, field, value) {
    const next = props.modelValue.map((row, i) => (i === index ? { ...row, [field]: value } : row));
    emit('update:modelValue', next);
  }

  function addRow() {
    emit('update:modelValue', [...props.modelValue, { ...DEFAULT_PRIZE_LADDER }]);
  }

  function removeRow(index) {
    emit('update:modelValue', props.modelValue.filter((_, i) => i !== index));
  }
</script>
