<template>
  <div class="editor-container" :style="{ height: currentHeight }">
    <div class="ios-toolbar">
      <div class="toolbar-left">
        <button @click="saveToLocal" class="ios-btn ios-btn-secondary" :disabled="isReadOnly">
          <span class="icon">💾</span> {{ cacheKey ? '暂存本地' : '暂存(内存)' }}
        </button>
        <button @click="handlePreSubmit" class="ios-btn ios-btn-primary" :disabled="isReadOnly"><span class="icon">🚀</span> 提交变更</button>
        <div class="divider"></div>
        <button @click="formatCode" class="ios-btn ios-btn-icon" title="一键格式化">✨ 格式化</button>
      </div>

      <div class="toolbar-right">
        <select v-model="currentTheme" class="ios-select" title="切换主题">
          <option value="vs-dark">🌙 Dark</option>
          <option value="vs">☀️ Light</option>
        </select>
        <select v-model="currentHeight" class="ios-select" title="窗口大小">
          <option value="30vh">🪟 小 屏)</option>
          <option value="50vh">💻 中 屏)</option>
          <option value="80vh">🖥️ 大 屏)</option>
          <option value="90vh">🚀 全屏模式</option>
        </select>
        <button @click="toggleReadOnly" :class="['ios-btn', isReadOnly ? 'ios-btn-danger' : 'ios-btn-warning']">
          {{ isReadOnly ? '🔒 只读' : '✏️ 编辑' }}
        </button>
      </div>
    </div>

    <SmartCodeEditor
      v-model:value="innerCode"
      :theme="currentTheme"
      :language="currentLanguage"
      :read-only="isReadOnly"
      :dictionary="dictionary"
      height="100%"
      class="code-box"
    />

    <div v-if="showDiffModal" class="ios-modal-overlay">
      <div class="ios-modal-content">
        <div class="modal-header">
          <h3>🧐 提交变更确认 (Diff)</h3>
          <p class="subtitle">左侧：线上原数据 &nbsp;|&nbsp; 右侧：本次修改的数据</p>
        </div>
        <div class="diff-editor-wrapper">
          <SmartCodeDiff
            :original="originalValue"
            :modified="innerCode"
            :language="currentLanguage"
            :theme="currentTheme"
            class="code-box"
          />
        </div>
        <div class="modal-footer">
          <button @click="showDiffModal = false" class="ios-btn ios-btn-secondary">返回修改</button>
          <button @click="confirmSubmit" class="ios-btn ios-btn-primary">✅ 确认无误，执行提交！</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
  import { ref, watch, onMounted } from 'vue';
  import { message } from 'ant-design-vue';
  import SmartCodeEditor from '/@/components/business/code-editor/SmartCodeEditor.vue';
  import SmartCodeDiff from '/@/components/business/code-editor/SmartCodeDiff.vue';

  // ==========================================
  // 🌟 1. 定义对外暴露的 API (Props & Emits)
  // ==========================================
  const props = defineProps({
    // v-model 绑定的代码内容
    modelValue: { type: String, default: '' },
    // 线上原始代码 (用于 Diff 对比)
    originalValue: { type: String, default: '' },
    // 本地缓存的唯一 Key (如果不传，则不开启浏览器缓存防丢机制)
    cacheKey: { type: String, default: '' },
    // 业务字典树 (给智能提示用的)
    dictionary: {
      type: Object,
      default: () => ({ objects: {}, functions: [], keywords: [] }),
    },
    // 编辑器语言（固定，无切换入口）
    language: { type: String, default: 'QL' },
    // 默认高度
    defaultHeight: { type: String, default: '30vh' },
  });

  const emit = defineEmits(['update:modelValue', 'submit', 'save']);

  // ==========================================
  // 🌟 2. 内部状态映射
  // ==========================================
  const innerCode = ref('');
  // 语言由调用方通过 language prop 固定，编辑器不提供切换入口
  // ⚠️ 取值必须与 /@/lib/codemirror.js 的 LANGUAGE_LOADERS key 完全一致，**大小写敏感**：
  //    'QL' | 'java' | 'json' | 'javascript'。传错不报错，只是静默没有高亮。
  const currentLanguage = ref(props.language);
  const currentTheme = ref('vs-dark');
  const isReadOnly = ref(false);
  const currentHeight = ref(props.defaultHeight);
  const showDiffModal = ref(false);


  // 同步外部传进来的 v-model
  watch(
    () => props.modelValue,
    (newVal) => {
      if (newVal !== innerCode.value) innerCode.value = newVal;
    },
    { immediate: true }
  );

  // 内部代码改变时，触发外部 v-model 更新，并静默缓存
  watch(innerCode, (newVal) => {
    emit('update:modelValue', newVal);
    if (!isReadOnly.value && props.cacheKey) {
      localStorage.setItem(props.cacheKey, newVal);
    }
  });

  // ==========================================
  // 🚀 4. 交互动作逻辑
  // ==========================================
  onMounted(() => {
    // 如果开启了缓存机制，优先从缓存恢复
    if (props.cacheKey) {
      const cachedData = localStorage.getItem(props.cacheKey);
      if (cachedData) innerCode.value = cachedData;
    }
  });

  const saveToLocal = () => {
    if (props.cacheKey) {
      localStorage.setItem(props.cacheKey, innerCode.value);
      emit('save', innerCode.value);
      alert('💾 草稿已安全保存在浏览器本地！');
    } else {
      emit('save', innerCode.value);
      alert('⚠️ 未配置 cacheKey，当前仅触发 Save 事件。');
    }
  };

  const handlePreSubmit = () => {
    if (innerCode.value === props.originalValue) {
      alert('🤨 代码毫无改动，无需提交！');
      return;
    }
    showDiffModal.value = true;
  };

  const confirmSubmit = () => {
    // 🌟 将提交动作抛给父组件处理，组件本身不发 Ajax！
    emit('submit', innerCode.value);
    showDiffModal.value = false;

    // 如果父组件处理成功，按理说应当清除本地缓存，但这步最好由父组件在 onSuccess 里调，
    // 这里为了体验闭环，先自动清除：
    if (props.cacheKey) localStorage.removeItem(props.cacheKey);
  };

  // CodeMirror 没有通用 formatter。
  // JSON 用原生 JSON.stringify 重排；java / QL 没有格式化器，明确告知而不是静默无反应。
  const formatCode = () => {
    if (currentLanguage.value !== 'json') {
      message.info(`当前语言（${currentLanguage.value}）暂不支持一键格式化`);
      return;
    }
    try {
      innerCode.value = JSON.stringify(JSON.parse(innerCode.value), null, 2);
    } catch (e) {
      message.error('JSON 格式有误，无法格式化：' + e.message);
    }
  };

  const toggleReadOnly = () => (isReadOnly.value = !isReadOnly.value);

</script>

<style scoped>
  /* 此处完全保留你上一版绝美的 iOS 毛玻璃及组件样式代码，不省略任何一行！ */
  .editor-container {
    display: flex;
    flex-direction: column;
    border-radius: 12px;
    overflow: hidden;
    transition: height 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
    border: 1px solid rgba(255, 255, 255, 0.1);
    background: #1e1e1e;
    position: relative;
  }
  .ios-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 16px;
    background: rgba(45, 45, 45, 0.75);
    backdrop-filter: saturate(180%) blur(20px);
    -webkit-backdrop-filter: saturate(180%) blur(20px);
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    z-index: 10;
  }
  .toolbar-left,
  .toolbar-right {
    display: flex;
    gap: 10px;
    align-items: center;
  }
  .divider {
    width: 1px;
    height: 20px;
    background-color: rgba(255, 255, 255, 0.2);
    margin: 0 4px;
  }
  .ios-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', sans-serif;
    font-size: 13px;
    font-weight: 500;
    padding: 6px 14px;
    border-radius: 8px;
    border: none;
    cursor: pointer;
    transition: all 0.2s ease;
    outline: none;
  }
  .ios-btn:active {
    transform: scale(0.96);
  }
  .ios-btn:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
  .ios-btn-primary {
    background-color: #007aff;
    color: #ffffff;
  }
  .ios-btn-secondary {
    background-color: rgba(255, 255, 255, 0.15);
    color: #e5e5e5;
  }
  .ios-btn-danger {
    background-color: rgba(255, 59, 48, 0.15);
    color: #ff453a;
  }
  .ios-btn-warning {
    background-color: rgba(255, 149, 0, 0.15);
    color: #ff9f0a;
  }
  .ios-btn-icon {
    background-color: transparent;
    color: #e5e5e5;
    padding: 6px 10px;
  }
  .ios-select {
    appearance: none;
    background-color: rgba(255, 255, 255, 0.1);
    color: #e5e5e5;
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 8px;
    padding: 6px 24px 6px 12px;
    font-size: 13px;
    outline: none;
    cursor: pointer;
    background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23e5e5e5' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
    background-repeat: no-repeat;
    background-position: right 8px center;
    background-size: 12px;
  }
  .ios-select option {
    background-color: #2d2d2d;
    color: #e5e5e5;
  }
  .code-box {
    flex: 1;
    width: 100%;
  }

  /* Diff 弹窗样式 */
  .ios-modal-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.6);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    z-index: 100;
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 20px;
  }
  .ios-modal-content {
    width: 95%;
    height: 90%;
    background: #1e1e1e;
    border-radius: 16px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    animation: modalPop 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  }
  @keyframes modalPop {
    0% {
      transform: scale(0.95);
      opacity: 0;
    }
    100% {
      transform: scale(1);
      opacity: 1;
    }
  }
  .modal-header {
    padding: 16px 20px;
    background: rgba(255, 255, 255, 0.05);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }
  .modal-header h3 {
    margin: 0;
    color: #fff;
    font-size: 16px;
    font-weight: 600;
  }
  .subtitle {
    margin: 4px 0 0 0;
    color: #aaa;
    font-size: 12px;
  }
  .diff-editor-wrapper {
    flex: 1;
    width: 100%;
    position: relative;
  }
  .modal-footer {
    padding: 16px 20px;
    background: rgba(255, 255, 255, 0.05);
    border-top: 1px solid rgba(255, 255, 255, 0.1);
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
</style>
