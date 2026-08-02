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
        <select v-model="currentLanguage" class="ios-select" title="切换语言">
          <option v-for="lang in languageList" :key="lang.value" :value="lang.value">
            {{ lang.label }}
          </option>
        </select>
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

    <vue-monaco-editor
      v-model:value="innerCode"
      :theme="currentTheme"
      :language="currentLanguage"
      :options="dynamicOptions"
      @mount="handleMount"
      class="monaco-box"
    />

    <div v-if="showDiffModal" class="ios-modal-overlay">
      <div class="ios-modal-content">
        <div class="modal-header">
          <h3>🧐 提交变更确认 (Diff)</h3>
          <p class="subtitle">左侧：线上原数据 &nbsp;|&nbsp; 右侧：本次修改的数据</p>
        </div>
        <div class="diff-editor-wrapper">
          <vue-monaco-diff-editor
            :original="originalValue"
            :modified="innerCode"
            :language="currentLanguage"
            :theme="currentTheme"
            :options="diffOptions"
            class="monaco-box"
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

<script>
  // 🌟 架构师神技：将注册状态提到模块级作用域！
  // 这样即使页面上有 10 个编辑器组件，Monaco 的语言注册也只会执行一次！
  let isProviderRegistered = false;
  let globalActiveDict = { objects: {}, functions: [], keywords: [] };
</script>

<script setup>
  import { ref, shallowRef, computed, watch, onMounted } from 'vue';
  import { VueMonacoEditor, VueMonacoDiffEditor } from '@guolao/vue-monaco-editor';

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
    // 默认语言
    defaultLanguage: { type: String, default: 'QL' },
    // 默认高度
    defaultHeight: { type: String, default: '30vh' },
  });

  const emit = defineEmits(['update:modelValue', 'submit', 'save']);

  // ==========================================
  // 🌟 2. 内部状态映射
  // ==========================================
  const innerCode = ref('');
  const currentLanguage = ref(props.defaultLanguage);
  const currentTheme = ref('vs-dark');
  const isReadOnly = ref(false);
  const currentHeight = ref(props.defaultHeight);
  const showDiffModal = ref(false);

  // ⚠️ value 必须是 monaco 注册的语言 id，且**大小写敏感**。
  //    原来这里写的是 ['QL', 'JSON', 'Javascript']，monaco 注册的却是小写的 json / javascript，
  //    于是选中这两项时 model 语言会静默回落成 plaintext —— 表现是「没有语法高亮」，不报任何错。
  //    QL 是本文件在下面用 monaco.languages.register({ id: 'QL' }) 注册的，大小写就是 QL。
  const languageList = [
    { label: 'QL', value: 'QL' },
    { label: 'JSON', value: 'json' },
    { label: 'Javascript', value: 'javascript' },
  ];

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
  // 🧠 3. Monaco 底层挂载与语言注册
  // ==========================================
  const editorRef = shallowRef();

  const handleMount = (editor, monaco) => {
    editorRef.value = editor;
    // 更新全局字典引用为当前组件的字典
    globalActiveDict = props.dictionary;

    if (!isProviderRegistered) {
      monaco.languages.register({ id: 'QL' });

      // 词法高亮
      monaco.languages.setMonarchTokensProvider('QL', {
        tokenizer: {
          root: [
            [/如果|则|否则|返回|大于|等于|并且|或者/, 'keyword'],
            [/用户|订单/, 'type'], // 这里最好是动态生成，但为性能考虑可写死常见大类
            [/"[^"]*"/, 'string'],
            [/[0-9]+/, 'number'],
          ],
        },
      });

      // 智能提示 (每次触发都会读取最新的 globalActiveDict)
      monaco.languages.registerCompletionItemProvider('QL', {
        triggerCharacters: ['.'],
        provideCompletionItems: function (model, position) {
          const textUntilPosition = model.getValueInRange({
            startLineNumber: position.lineNumber,
            startColumn: 1,
            endLineNumber: position.lineNumber,
            endColumn: position.column,
          });
          const match = textUntilPosition.match(/([a-zA-Z\u4e00-\u9fa5]+)\.$/);
          const suggestions = [];

          if (match) {
            const objectName = match[1];
            const targetObj = globalActiveDict.objects?.[objectName];
            if (targetObj && targetObj.properties) {
              for (const [propName, propConfig] of Object.entries(targetObj.properties)) {
                suggestions.push({
                  label: propName,
                  kind: monaco.languages.CompletionItemKind.Field,
                  insertText: propName,
                  detail: `[属性] ${propConfig.type}`,
                  documentation: propConfig.desc,
                });
              }
            }
            return { suggestions };
          }

          // 顶级对象提示
          if (globalActiveDict.objects) {
            for (const [objName, objConfig] of Object.entries(globalActiveDict.objects)) {
              suggestions.push({
                label: objName,
                kind: monaco.languages.CompletionItemKind.Class,
                insertText: objName,
                detail: `[对象]`,
                documentation: objConfig.desc,
              });
            }
          }

          // 顶级函数提示
          if (globalActiveDict.functions) {
            globalActiveDict.functions.forEach((fn) => {
              suggestions.push({
                label: fn.label,
                kind: monaco.languages.CompletionItemKind.Function,
                insertText: fn.insertText,
                insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                detail: `[内置函数]`,
                documentation: fn.desc,
              });
            });
          }

          // 关键字提示
          if (globalActiveDict.keywords) {
            globalActiveDict.keywords.forEach((kw) => {
              suggestions.push({
                label: kw,
                kind: monaco.languages.CompletionItemKind.Keyword,
                insertText: kw,
              });
            });
          }

          return { suggestions };
        },
      });

      isProviderRegistered = true;
      console.log('✅ SmartCodeEditor: 通用 QLExpress 语言包挂载成功！');
    }
  };

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

  const formatCode = () => {
    if (editorRef.value) editorRef.value.getAction('editor.action.formatDocument').run();
  };

  const toggleReadOnly = () => (isReadOnly.value = !isReadOnly.value);

  // 配置项计算
  const dynamicOptions = computed(() => ({
    automaticLayout: true,
    wordWrap: 'on',
    scrollBeyondLastLine: false,
    minimap: { enabled: false },
    formatOnPaste: true,
    formatOnType: true,
    autoIndent: 'full',
    fontSize: 14,
    fontFamily: "Consolas, 'Courier New', monospace",
    readOnly: isReadOnly.value,
  }));
  const diffOptions = { readOnly: true, automaticLayout: true, renderSideBySide: true };
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
  .monaco-box {
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
