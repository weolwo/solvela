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
import { javascript } from '@codemirror/lang-javascript';

/**
 * QLExpress 语言
 * 原规则（保持一致，改这里要同步 docs 里的脚本文档）：
 *   关键字  如果|则|否则|返回|大于|等于|并且|或者
 *   类型    用户|订单
 *   字符串  "..."
 *   数字    [0-9]+
 */
const QL_KEYWORDS = /^(如果|则|否则|返回|大于|等于|并且|或者)/;
const QL_TYPES = /^(用户|订单)/;

const qlLanguage = StreamLanguage.define({
  name: 'QL',
  token(stream) {
    if (stream.eatSpace()) return null;
    const rest = stream.string.slice(stream.pos);
    let m = rest.match(QL_KEYWORDS);
    if (m) {
      stream.pos += m[0].length;
      return 'keyword';
    }
    m = rest.match(QL_TYPES);
    if (m) {
      stream.pos += m[0].length;
      return 'typeName';
    }
    if (stream.peek() === '"') {
      stream.next();
      while (!stream.eol() && stream.next() !== '"');
      return 'string';
    }
    if (/[0-9]/.test(stream.peek())) {
      stream.eatWhile(/[0-9]/);
      return 'number';
    }
    stream.next();
    return null;
  },
});

const LANGUAGE_LOADERS = {
  QL: () => qlLanguage,
  java: () => java(),
  json: () => json(),
  javascript: () => javascript(),
};

export function getLanguageExtension(languageId) {
  const loader = LANGUAGE_LOADERS[languageId];
  return loader ? loader() : [];
}

export const SUPPORTED_LANGUAGES = Object.keys(LANGUAGE_LOADERS);

/**
 * QL 智能提示
 * - 输入 `对象名.` 时提示该对象的属性
 * - 否则提示：顶级对象 / 内置函数 / 关键字
 * @param {Function} getDictionary 返回当前字典（用函数而非快照，保证切换模板后取到最新的）
 */
export function createQlCompletionSource(getDictionary) {
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
