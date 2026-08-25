<!--
  代码对比（CodeMirror 6 MergeView）

  只读的左右分栏代码对比，prop：original / modified / language / theme。
-->
<template>
  <div ref="containerRef" class="solvela-code-diff"></div>
</template>

<script setup>
  import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
  import { MergeView } from '@codemirror/merge';
  import { EditorState, EditorView, oneDark, baseExtensions, getLanguageExtension } from '/@/lib/codemirror';

  const props = defineProps({
    original: { type: String, default: '' },
    modified: { type: String, default: '' },
    language: { type: String, default: 'json' },
    theme: { type: String, default: 'vs-dark' },
  });

  const containerRef = ref(null);
  const viewRef = shallowRef(null);

  function extensions() {
    return [
      ...baseExtensions(),
      getLanguageExtension(props.language),
      props.theme === 'vs-dark' ? oneDark : [],
      EditorState.readOnly.of(true),
      EditorView.editable.of(false),
    ];
  }

  function mount() {
    viewRef.value?.destroy();
    viewRef.value = new MergeView({
      a: { doc: props.original ?? '', extensions: extensions() },
      b: { doc: props.modified ?? '', extensions: extensions() },
      parent: containerRef.value,
      // 并排视图
      orientation: 'a-b',
      highlightChanges: true,
      gutter: true,
    });
  }

  onMounted(mount);
  onBeforeUnmount(() => {
    viewRef.value?.destroy();
    viewRef.value = null;
  });

  // 弹窗每次打开传入的内容都可能变，直接重建（diff 视图无需保留光标/历史）
  watch(() => [props.original, props.modified, props.language, props.theme], mount);
</script>

<style scoped>
  .solvela-code-diff {
    height: 100%;
    overflow: auto;
  }

  .solvela-code-diff :deep(.cm-mergeView) {
    height: 100%;
  }

  .solvela-code-diff :deep(.cm-scroller) {
    font-family: 'JetBrains Mono', Consolas, Monaco, 'Courier New', monospace;
    font-size: 13px;
  }
</style>
