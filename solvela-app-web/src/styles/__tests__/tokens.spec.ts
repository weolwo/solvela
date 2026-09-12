import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * 每一个用到的 CSS 变量都必须真的定义过。
 *
 * <h3>🔴 这条对应一批已经上线的视觉 bug</h3>
 * 2026-09-10 全仓有 11 处在用两个<b>根本不存在</b>的变量
 *（`--sv-text-tertiary`、`--sv-border-subtle`，主题里叫
 * `--sv-text-placeholder` 和 `--sv-border-color`）。分布在 5 个页面里，
 * 其中包括优惠记录和兑换记录。
 *
 * <h3>它的失败方式是「安静地变丑」</h3>
 * CSS 变量取不到值时不会报错、不会警告，只会：
 * <ul>
 *   <li>`color: var(--不存在)` → 该声明作废，文字<b>继承</b>父级颜色 ——
 *       本该是浅灰的辅助信息，渲染成和正文一样的深色，整页看起来又平又重；</li>
 *   <li>`border-top: 1px solid var(--不存在)` → <b>整条声明</b>非法，
 *       分隔线<b>完全不出现</b>。记录列表因此糊成一片。</li>
 * </ul>
 *
 * <p>控制台干净、测试全绿、构建成功 —— 只有人眼看得出来，
 * 而写代码的人往往不会逐页去看。所以用一条测试盯着。
 *
 * <h3>为什么是扫文件，不是渲染后取计算值</h3>
 * 渲染只能覆盖到那次渲染真的走到的分支（`:active`、暗色、某个 v-if 里的样式
 * 都测不到）。而这个性质是<b>纯静态</b>的：源码里写了什么变量、主题里定义了什么，
 * 两个集合的差就是答案。
 */

/*
 * vitest 在 jsdom 环境下 import.meta.url 不是 file: 协议，fileURLToPath 会抛。
 * 直接从进程工作目录拼 —— vitest 的 cwd 就是项目根。
 */
const SRC = resolve(process.cwd(), 'src')

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) {
      walk(path, out)
    } else if (/\.(vue|css)$/.test(name)) {
      out.push(path)
    }
  }
  return out
}

const files = walk(SRC)

/** 主题里定义了哪些：`--sv-xxx:` 出现在声明位置 */
function definedTokens(): Set<string> {
  const defined = new Set<string>()
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    for (const m of text.matchAll(/(--sv-[a-z0-9-]+)\s*:/g)) {
      defined.add(m[1]!)
    }
  }
  return defined
}

/**
 * 用到了哪些：`var(--sv-xxx)`，连同它的 fallback 一起看。
 *
 * 🔴 必须匹配到收尾的 `)` 或 `,`，不能只匹配名字。
 * 只匹配名字的话，注释里写的 `var(--sv-radius-*)` 这种<b>通配符说明</b>
 * 会被当成一个叫 `--sv-radius-` 的变量报出来 —— 2026-09-12 就误报过一次。
 */
function usedTokens(): Map<string, string[]> {
  const used = new Map<string, string[]>()
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    for (const m of text.matchAll(/var\(\s*(--sv-[a-z0-9-]+)\s*([,)])/g)) {
      // 带 fallback 的（var(--x, red)）不算问题：取不到也有兜底
      if (m[2] === ',') {
        continue
      }
      const name = m[1]!
      const where = used.get(name) ?? []
      where.push(file.slice(SRC.length).replace(/\\/g, '/'))
      used.set(name, where)
    }
  }
  return used
}

describe('CSS 变量', () => {
  it('🔴 用到的每一个都要定义过 —— 没定义不会报错，只会安静地变丑', () => {
    const defined = definedTokens()
    const missing = [...usedTokens().entries()]
      .filter(([name]) => !defined.has(name))
      .map(([name, where]) => `${name}  ←  ${[...new Set(where)].join(', ')}`)

    expect(
      missing,
      '这些变量没有定义。color 会退化成继承父级（辅助文字变成正文色），\n' +
        'border 那一整条声明会作废（分隔线直接不出现）。\n' +
        '主题里的正确名字见 src/styles/theme.css：\n',
    ).toEqual([])
  })

  it('主题确实定义了那几个最常被写错的名字', () => {
    const defined = definedTokens()
    // 这四个是上面那批 bug 里被写错的目标名，钉住它们免得有人反过来改主题
    for (const token of [
      '--sv-text-placeholder',
      '--sv-text-secondary',
      '--sv-border-color',
      '--sv-radius-lg',
    ]) {
      expect(defined, `${token} 不见了`).toContain(token)
    }
  })
})
