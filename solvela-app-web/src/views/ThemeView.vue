<script setup lang="ts">
/**
 * 主题。
 *
 * <h3>两个维度分开摆，不合成一个列表</h3>
 * 外观（深浅）和皮肤（平台观感）是两件事：前者用户随时会切，后者选一次就不动了。
 * 合成「浅色 / 深色 / iOS 浅色 / iOS 深色」四项的话，加第三套皮肤就变成六项，
 * 而用户想做的其实只是「把它调暗一点」。
 *
 * <h3>🔴 选中态靠对勾，不只靠颜色</h3>
 * 只用主色描边区分的话，色觉障碍用户看不出选了哪个 ——
 * 与 Checkbox 那边「既有底色也有文字」是同一条。
 *
 * <h3>预览块是真的在预览</h3>
 * 皮肤那两项右边各有一小块样例，用的是<b>那套皮肤自己的圆角</b>。
 * 光写「小圆角、更紧的留白」，用户读完还是不知道会变成什么样。
 */
import { APPEARANCES, SKINS, useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
</script>

<template>
  <div class="page">
    <NavBar title="主题" />

    <div class="page__body">
      <section class="group">
        <h2 class="group__title">外观</h2>
        <div class="card">
          <button
            v-for="item in APPEARANCES"
            :key="item.id"
            class="row"
            type="button"
            :aria-pressed="theme.appearance === item.id"
            @click="theme.selectAppearance(item.id)"
          >
            <span class="row__label">{{ item.label }}</span>
            <Icon v-if="theme.appearance === item.id" name="check" :size="20" class="row__check" />
          </button>
        </div>
        <p class="group__note">«跟随系统» 会随手机的深色模式自动切换，切换时无需重开应用。</p>
      </section>

      <section class="group">
        <h2 class="group__title">皮肤</h2>
        <div class="card">
          <button
            v-for="item in SKINS"
            :key="item.id"
            class="row"
            type="button"
            :aria-pressed="theme.skin === item.id"
            @click="theme.selectSkin(item.id)"
          >
            <span class="row__main">
              <span class="row__label">{{ item.label }}</span>
              <span class="row__hint">{{ item.hint }}</span>
            </span>
            <!-- 预览块用那套皮肤自己的圆角：文字描述再准也不如看一眼 -->
            <span class="preview" :class="`preview--${item.id}`" aria-hidden="true">
              <span class="preview__bar" />
              <span class="preview__btn" />
            </span>
            <Icon v-if="theme.skin === item.id" name="check" :size="20" class="row__check" />
          </button>
        </div>
        <p class="group__note">
          皮肤只改圆角和留白这类「观感」，不改颜色 —— 深浅由上面那一组决定。
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.page__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-lg);
  padding: var(--sv-space-md) var(--sv-space-page) calc(var(--sv-safe-bottom) + var(--sv-space-lg));
}

.group__title {
  margin: 0 0 var(--sv-space-sm);
  padding-left: var(--sv-space-xs);
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  font-weight: 500;
}

.group__note {
  margin: var(--sv-space-sm) 0 0;
  padding: 0 var(--sv-space-xs);
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
  line-height: 1.5;
}

.card {
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
  overflow: hidden;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sv-space-md);
  width: 100%;
  min-height: var(--sv-row-height);
  padding: var(--sv-space-sm) var(--sv-space-md);
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.row + .row {
  box-shadow: inset 0 0.5px 0 var(--sv-border-color);
}

.row:active {
  background: var(--sv-bg-pressed);
}

.row:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: -2px;
}

.row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.row__label {
  flex: 1;
  font-size: var(--sv-font-body);
}

.row__hint {
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}

.row__check {
  flex: none;
  color: var(--sv-color-primary);
}

/* ---- 皮肤预览 ---- */

.preview {
  flex: none;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 52px;
  height: 40px;
  padding: 6px;
  background: var(--sv-bg-fill);
}

.preview__bar {
  height: 6px;
  background: var(--sv-text-placeholder);
}

.preview__btn {
  height: 10px;
  background: var(--sv-color-primary);
}

/*
 * 🔴 预览块写死自己那套圆角，【不用】var(--sv-radius-*)。
 * 用变量的话两个预览会长得一模一样 —— 因为当前生效的皮肤只有一套值，
 * 而这里正要展示的是「另一套长什么样」。
 */
.preview--default {
  border-radius: 10px;
}

.preview--default .preview__bar,
.preview--default .preview__btn {
  border-radius: 999px;
}

.preview--ios {
  border-radius: 6px;
}

.preview--ios .preview__bar,
.preview--ios .preview__btn {
  border-radius: 3px;
}
</style>
