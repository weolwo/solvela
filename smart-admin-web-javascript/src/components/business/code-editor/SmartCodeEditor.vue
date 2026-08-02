<!--
  代码编辑器（CodeMirror 6）

  替代原来的 vue-monaco-editor。**prop 形态刻意与它保持一致**
  （v-model:value / language / theme / height），调用方基本不用改。

  ⚠️ language 取值大小写敏感：'QL' | 'java' | 'json' | 'javascript'，
     传错不会报错，只是没有高亮 —— monaco 时期就因为写成 'JSON' 静默失效过。

  只读回显请优先用 highlight.js（见 code-generator-preview-modal.vue），比本组件更轻。
-->
<template>
  <div ref="containerRef" class="smart-code-editor" :style="{ height: normalizedHeight }"></div>
</template>

<script setup>
  import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
  import {
    EditorState,
    EditorView,
    oneDark,
    autocompletion,
    baseExtensions,
    getLanguageExtension,
    createCompartments,
    createQlCompletionSource,
  } from '/@/lib/codemirror';

  const props = defineProps({
    value: { type: String, default: '' },
    language: { type: String, default: 'json' },
    // 沿用 monaco 的主题名，'vs-dark' 为暗色，其余为亮色
    theme: { type: String, default: 'vs-dark' },
    readOnly: { type: Boolean, default: false },
    height: { type: [String, Number], default: '300px' },
    // QL 智能提示用的字典
    dictionary: { type: Object, default: () => ({}) },
  });
  const emit = defineEmits(['update:value', 'ready']);

  const containerRef = ref(null);
  const viewRef = shallowRef(null);
  const compartments = createCompartments();

  // monaco 时期 height 传纯数字会导致容器塌陷，这里统一补 px
  const normalizedHeight = ref('');
  const normalizeHeight = (h) => (typeof h === 'number' || /^\d+$/.test(String(h)) ? `${h}px` : String(h));
  normalizedHeight.value = normalizeHeight(props.height);

  const themeExtension = (t) => (t === 'vs-dark' ? oneDark : []);

  function buildState(doc) {
    return EditorState.create({
      doc,
      extensions: [
        ...baseExtensions(),
        autocompletion({ override: props.language === 'QL' ? [createQlCompletionSource(() => props.dictionary)] : undefined }),
        compartments.language.of(getLanguageExtension(props.language)),
        compartments.theme.of(themeExtension(props.theme)),
        // readOnly 拦截修改；editable 同时去掉 contenteditable 与光标，
        // 否则只读时仍然能落光标、看起来像可以编辑（与原 monaco 只读观感不一致）
        compartments.readOnly.of([EditorState.readOnly.of(props.readOnly), EditorView.editable.of(!props.readOnly)]),
        EditorView.updateListener.of((update) => {
          if (!update.docChanged) return;
          const next = update.state.doc.toString();
          if (next !== props.value) emit('update:value', next);
        }),
      ],
    });
  }

  onMounted(() => {
    viewRef.value = new EditorView({ state: buildState(props.value ?? ''), parent: containerRef.value });
    emit('ready', viewRef.value);
  });

  onBeforeUnmount(() => {
    viewRef.value?.destroy();
    viewRef.value = null;
  });

  // 外部改值时同步进编辑器（跳过由本组件自身输入触发的回环）
  watch(
    () => props.value,
    (val) => {
      const view = viewRef.value;
      if (!view) return;
      const current = view.state.doc.toString();
      if (val === current) return;
      view.dispatch({ changes: { from: 0, to: current.length, insert: val ?? '' } });
    }
  );

  // 语言 / 主题 / 只读走 compartment 热切换，不重建编辑器（否则光标与撤销历史会丢）
  watch(
    () => props.language,
    (lang) => viewRef.value?.dispatch({ effects: compartments.language.reconfigure(getLanguageExtension(lang)) })
  );
  watch(
    () => props.theme,
    (t) => viewRef.value?.dispatch({ effects: compartments.theme.reconfigure(themeExtension(t)) })
  );
  watch(
    () => props.readOnly,
    (ro) => viewRef.value?.dispatch({ effects: compartments.readOnly.reconfigure([EditorState.readOnly.of(ro), EditorView.editable.of(!ro)]) })
  );
  watch(
    () => props.height,
    (h) => (normalizedHeight.value = normalizeHeight(h))
  );

  defineExpose({
    getView: () => viewRef.value,
    getValue: () => viewRef.value?.state.doc.toString() ?? '',
  });
</script>

<style scoped>
  .smart-code-editor {
    overflow: hidden;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
  }

  .smart-code-editor :deep(.cm-editor) {
    height: 100%;
  }

  .smart-code-editor :deep(.cm-scroller) {
    font-family: 'JetBrains Mono', Consolas, Monaco, 'Courier New', monospace;
    font-size: 13px;
  }
</style>
