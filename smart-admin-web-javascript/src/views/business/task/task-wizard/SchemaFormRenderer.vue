<!--
  * ui_schema 动态表单渲染器
  * 由模板 uiSchema.params 驱动控件分发与 visibleWhen 级联显隐，新增模板零前端改动
  * 需渲染在外层 <a-form> 内部：a-form-item 通过 namePrefix + key 挂到外层表单的校验树上
  *
  * @Author:    alaric
  * @Date:      2026-07-19
-->
<template>
  <a-form-item
    v-for="param in visibleParams"
    :key="param.key"
    :name="namePath(param)"
    :rules="paramRules(param)"
    :extra="param.help"
  >
    <template #label>
      <a-space :size="4">
        <span>{{ param.label }}</span>
        <a-typography-text type="secondary" class="text-xs">{{ param.key }}</a-typography-text>
      </a-space>
    </template>

    <!-- number -->
    <a-input-number
      v-if="param.widget === WIDGET_TYPE.NUMBER"
      :value="modelValue[param.key]"
      :min="param.min"
      :max="param.max"
      :addon-after="param.unit"
      class="w-full"
      @update:value="(v) => updateValue(param.key, v)"
    />

    <!-- text -->
    <a-input
      v-else-if="param.widget === WIDGET_TYPE.TEXT"
      :value="modelValue[param.key]"
      @update:value="(v) => updateValue(param.key, v)"
    />

    <!-- select -->
    <a-select
      v-else-if="param.widget === WIDGET_TYPE.SELECT"
      :value="modelValue[param.key]"
      :options="param.options"
      @update:value="(v) => updateValue(param.key, v)"
    />

    <!-- radio -->
    <a-radio-group
      v-else-if="param.widget === WIDGET_TYPE.RADIO"
      :value="modelValue[param.key]"
      :options="param.options"
      option-type="button"
      @update:value="(v) => updateValue(param.key, v)"
    />

    <!-- switch -->
    <a-switch
      v-else-if="param.widget === WIDGET_TYPE.SWITCH"
      :checked="modelValue[param.key]"
      @update:checked="(v) => updateValue(param.key, v)"
    />

    <!-- slider -->
    <a-row v-else-if="param.widget === WIDGET_TYPE.SLIDER" :gutter="12" align="middle">
      <a-col flex="auto">
        <a-slider
          :value="modelValue[param.key]"
          :min="param.min"
          :max="param.max"
          @update:value="(v) => updateValue(param.key, v)"
        />
      </a-col>
      <a-col>
        <a-typography-text type="secondary">{{ modelValue[param.key] }} {{ param.unit }}</a-typography-text>
      </a-col>
    </a-row>

    <!-- image_upload：占位交互，仅写入 mock URL，不发起真实上传请求 -->
    <a-space v-else-if="param.widget === WIDGET_TYPE.IMAGE_UPLOAD" direction="vertical" class="w-full">
      <a-upload
        list-type="picture-card"
        :show-upload-list="false"
        :before-upload="(file) => onMockUpload(param, file)"
      >
        <a-space direction="vertical" :size="4">
          <PlusOutlined />
          <a-typography-text type="secondary">点击上传</a-typography-text>
        </a-space>
      </a-upload>
      <a-space v-if="modelValue[param.key]">
        <a-tag color="green">已上传</a-tag>
        <a-typography-text type="secondary" class="text-xs">{{ modelValue[param.key] }}</a-typography-text>
        <a-button type="link" size="small" danger @click="updateValue(param.key, undefined)">删除</a-button>
      </a-space>
    </a-space>

    <!-- 未知控件降级提示，不阻塞其他字段 -->
    <a-alert v-else type="warning" show-icon :message="`未知控件类型 widget: ${param.widget}`" />
  </a-form-item>

  <a-empty
    v-if="!visibleParams.length"
    :image="Empty.PRESENTED_IMAGE_SIMPLE"
    description="该模板无需配置参数（完成即达标），判定逻辑由模板内置 rule_script 决定"
  />
</template>

<script setup>
  import { computed } from 'vue';
  import { Empty } from 'ant-design-vue';
  import { PlusOutlined } from '@ant-design/icons-vue';
  import { WIDGET_TYPE, isSchemaParamVisible } from './task-wizard-const';

  const props = defineProps({
    // ui_schema 的 params 数组
    schemaParams: {
      type: Array,
      default: () => [],
    },
    // 参数值对象（key -> value）
    modelValue: {
      type: Object,
      default: () => ({}),
    },
    // 外层 a-form 校验树上的路径前缀，如 ['ruleParams']；为空时以 key 直连
    namePrefix: {
      type: Array,
      default: () => [],
    },
  });

  const emit = defineEmits(['update:modelValue']);

  // 模拟上传写入的 URL 前缀（真实上传接入后整体替换）
  const MOCK_UPLOAD_URL_PREFIX = 'https://cdn.example.com/task/';

  const visibleParams = computed(() => props.schemaParams.filter((p) => isSchemaParamVisible(p, props.modelValue)));

  function namePath(param) {
    return props.namePrefix.length ? [...props.namePrefix, param.key] : param.key;
  }

  function paramRules(param) {
    return param.required ? [{ required: true, message: `请配置${param.label}` }] : [];
  }

  function updateValue(key, value) {
    emit('update:modelValue', { ...props.modelValue, [key]: value });
  }

  function onMockUpload(param, file) {
    updateValue(param.key, `${MOCK_UPLOAD_URL_PREFIX}${param.key}_${file.name}`);
    // 返回 false 阻止 a-upload 发起真实请求
    return false;
  }
</script>
