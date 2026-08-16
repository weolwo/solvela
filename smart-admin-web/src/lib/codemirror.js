/*
 * CodeMirror 6 装配层
 *
 * 本项目只需要「浏览器里看代码 + 偶尔改一改」，CodeMirror 6 按需装语言约 250KB 即可满足。
 * 只读回显的场景不要用这里，用 highlight.js（见 code-generator-preview-modal.vue 的用法），更轻。
 *
 * ⚠️ 新增语言时在 LANGUAGE_LOADERS 里补一条。key **大小写敏感**，
 *    历史上写成大写 'JSON' 导致高亮静默失效（不报错），改动务必与调用方对齐。
 */
import { EditorState, Compartment } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import {
  syntaxHighlighting,
  defaultHighlightStyle,
  indentOnInput,
  bracketMatching,
  StreamLanguage,
  foldGutter,
  foldKeymap,
} from '@codemirror/language';
import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from '@codemirror/autocomplete';
import { searchKeymap, highlightSelectionMatches } from '@codemirror/search';
import { oneDark } from '@codemirror/theme-one-dark';
import { java } from '@codemirror/lang-java';
import { json } from '@codemirror/lang-json';

/*
 * 只支持 java 与 json 两种。
 *
 * QLExpress 脚本用 java 高亮：QLExpress 4 的语法已是 Java 风格（含原生 JSON 字面量），
 * 比自己写的关键字 tokenizer 准得多。
 * ⚠️ 编辑器只做**着色**，不做语法检查。QL 的语法校验要靠后端的 check(script) AST 预校验接口，
 *    前端拿到错误位置再划红线 —— 那是另一件事，不要指望编辑器本身。
 */
const LANGUAGE_LOADERS = {
  java: () => java(),
  json: () => json(),
};

export function getLanguageExtension(languageId) {
  const loader = LANGUAGE_LOADERS[languageId];
  return loader ? loader() : [];
}

/** 语言选择列表（工具栏下拉用） */
export const LANGUAGE_OPTIONS = [
  { value: 'java', label: 'Java / QLExpress' },
  { value: 'json', label: 'JSON' },
];

/**
 * 字典驱动的智能提示（QLExpress 规则脚本用）
 * - 输入 `对象名.` 时提示该对象的属性
 * - 否则提示：顶级对象 / 内置函数 / 关键字
 * @param {Function} getDictionary 返回当前字典（用函数而非快照，保证切换模板后取到最新的）
 */
export function createDictCompletionSource(getDictionary) {
  return (context) => {
    const dict = getDictionary() || {};
    const before = context.state.doc.sliceString(context.state.doc.lineAt(context.pos).from, context.pos);

    // `对象名.` → 属性提示
    const dotMatch = before.match(/([a-zA-Z一-龥]+)\.$/);
    if (dotMatch) {
      const target = dict.objects?.[dotMatch[1]];
      if (!target?.properties) return null;
      return {
        from: context.pos,
        options: Object.entries(target.properties).map(([name, cfg]) => ({
          label: name,
          type: 'property',
          detail: `[属性] ${cfg.type}`,
          info: cfg.desc,
        })),
      };
    }

    const word = context.matchBefore(/[\w一-龥]*/);
    if (!word || (word.from === word.to && !context.explicit)) return null;

    const options = [];
    for (const [objName, objConfig] of Object.entries(dict.objects || {})) {
      options.push({ label: objName, type: 'class', detail: '[对象]', info: objConfig.desc });
    }
    (dict.functions || []).forEach((fn) => {
      options.push({
        label: fn.label,
        type: 'function',
        detail: '[内置函数]',
        info: fn.desc,
        // 字典里可能带 ${1:xx} 形式的占位符，CodeMirror 不认，插入前去掉
        apply: String(fn.insertText || fn.label).replace(/\$\{\d+:?([^}]*)\}/g, '$1'),
      });
    });
    (dict.keywords || []).forEach((kw) => options.push({ label: kw, type: 'keyword' }));

    return options.length ? { from: word.from, options } : null;
  };
}

/** 供外部按需切换的 compartment，避免整块重建编辑器 */
export function createCompartments() {
  return { language: new Compartment(), theme: new Compartment(), readOnly: new Compartment() };
}

/** 基础扩展集：行号、折叠、历史、括号匹配、搜索、快捷键 */
export function baseExtensions() {
  return [
    lineNumbers(),
    highlightActiveLineGutter(),
    highlightActiveLine(),
    foldGutter(),
    history(),
    indentOnInput(),
    bracketMatching(),
    closeBrackets(),
    highlightSelectionMatches(),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    EditorView.lineWrapping,
    keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...historyKeymap, ...foldKeymap, ...completionKeymap, ...searchKeymap, indentWithTab]),
  ];
}

export { EditorState, EditorView, oneDark, autocompletion };
