<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  type Address,
  deleteAddress,
  fetchAddresses,
  formatAddressLine,
  setDefaultAddress,
} from '@/api/address'
import { ApiError } from '@/api/errors'
import { useAsync } from '@/composables/useAsync'
import type { Id } from '@/types/contract'

/**
 * 地址簿。
 *
 * <h3>两种进法，行为不一样</h3>
 * 从「我的」进来是<b>管理</b>：点一条是编辑。
 * 从兑换页进来（`?pick=1`）是<b>挑一个</b>：点一条就选中并返回，编辑挪到右侧的按钮上。
 * 用 query 而不是两个路由：同一个列表、同一份数据，差别只在点击的落点。
 *
 * <h3>删除要二次确认，但不用弹窗</h3>
 * 项目原则是「要碰 teleport / 滚动锁定 / 焦点陷阱 的别自己写」，而一个确认框
 * 正好三样都沾。所以确认做成<b>行内的两段式</b>：点「删除」变成「确定删除？」，
 * 再点一次才真删，点别处取消。没有焦点陷阱要处理，也照样拦住了误触。
 */

const route = useRoute()
const router = useRouter()

const addresses = useAsync(fetchAddresses)

/** 从兑换页进来的挑选模式 */
const picking = computed(() => route.query.pick === '1')

const hintText = ref('')
/** 正在等确认删除的那条 */
const confirmingDelete = ref<Id | null>(null)
/** 有请求在飞时禁掉所有操作，避免连点把顺序点乱 */
const busy = ref(false)

function goEdit(addressId: Id): void {
  void router.push({ name: 'address-edit', params: { id: addressId } })
}

function goCreate(): void {
  // 新增完要回到这里，并且保留 pick 模式 —— 从兑换页过来新增的人，建完就是要选它
  void router.push({ name: 'address-new', query: route.query })
}

/** 从哪个商品的兑换页过来的。挑选模式下必有，否则回不去 */
const pickedFromCommodity = computed<string | null>(() => {
  const raw = route.query.commodity
  const value = Array.isArray(raw) ? raw[0] : raw
  return typeof value === 'string' && value !== '' ? value : null
})

/** 挑选模式下点一条 = 选它并回兑换页 */
function onPick(address: Address): void {
  const commodityId = pickedFromCommodity.value
  if (!picking.value || commodityId === null) {
    goEdit(address.id)
    return
  }
  // 用 replace：用户按返回该回到兑换页，而不是又落回地址簿
  void router.replace({
    name: 'redeem',
    params: { id: commodityId },
    query: { ...routeQueryWithoutPick(), address: address.id },
  })
}

/** 回兑换页时要把挑选相关的 query 摘掉，只留规格选择那些 */
function routeQueryWithoutPick(): Record<string, unknown> {
  const { pick, commodity, ...rest } = route.query
  void pick
  void commodity
  return rest
}

async function onSetDefault(address: Address): Promise<void> {
  if (busy.value || address.isDefault) {
    return
  }
  busy.value = true
  hintText.value = ''
  try {
    await setDefaultAddress(address.id)
    // 重新拉而不是本地改：默认地址是「一个账号最多一个」的约束，
    // 由服务端保证。本地自己翻两条记录只是猜它做了什么
    await addresses.reload()
  } catch (error) {
    hintText.value = error instanceof ApiError ? error.message : '设置失败，请稍后再试'
  } finally {
    busy.value = false
  }
}

async function onDelete(address: Address): Promise<void> {
  if (busy.value) {
    return
  }
  if (confirmingDelete.value !== address.id) {
    confirmingDelete.value = address.id
    return
  }
  busy.value = true
  hintText.value = ''
  try {
    await deleteAddress(address.id)
    await addresses.reload()
  } catch (error) {
    hintText.value = error instanceof ApiError ? error.message : '删除失败，请稍后再试'
  } finally {
    busy.value = false
    confirmingDelete.value = null
  }
}
</script>

<template>
  <div class="page">
    <NavBar :title="picking ? '选择收货地址' : '地址簿'" />

    <p v-if="hintText !== ''" class="page__hint" role="alert">{{ hintText }}</p>

    <Section
      title="我的收货地址"
      :loading="addresses.loading.value"
      :error="addresses.error.value"
      :empty="(addresses.data.value ?? []).length === 0"
      empty-text="还没有收货地址，新增一个吧"
      @retry="addresses.reload"
    >
      <div class="list">
        <div v-for="item in addresses.data.value ?? []" :key="item.id" class="row">
          <button class="row__main" type="button" @click="onPick(item)">
            <span class="row__head">
              <span class="row__receiver">{{ item.receiverName }}</span>
              <span class="row__phone">{{ item.receiverPhone }}</span>
              <span v-if="item.isDefault" class="row__default">默认</span>
            </span>
            <span class="row__line">{{ formatAddressLine(item) }}</span>
          </button>

          <div class="row__ops">
            <button
              v-if="!item.isDefault"
              class="op"
              type="button"
              :disabled="busy"
              @click="onSetDefault(item)"
            >
              设为默认
            </button>
            <button v-if="picking" class="op" type="button" @click="goEdit(item.id)">编辑</button>
            <!--
              两段式删除：第一次点变成「确定删除？」，再点一次才真删。
              比弹窗轻，也不用处理焦点陷阱。
            -->
            <button
              class="op op--danger"
              type="button"
              :disabled="busy"
              @click="onDelete(item)"
              @blur="confirmingDelete = null"
            >
              {{ confirmingDelete === item.id ? '确定删除？' : '删除' }}
            </button>
          </div>
        </div>
      </div>
    </Section>

    <div class="page__foot">
      <Button @click="goCreate">新增收货地址</Button>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding-bottom: calc(96px + var(--sv-safe-bottom));
}

.page__hint {
  margin: 0 var(--sv-space-page);
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-sm);
}

.row {
  border-radius: var(--sv-radius-lg);
  background: var(--sv-bg-surface);
}

.row__main {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-xs);
  width: 100%;
  border: 0;
  padding: var(--sv-space-md) var(--sv-space-md) var(--sv-space-sm);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.row__main:active {
  background: var(--sv-bg-pressed);
  border-radius: var(--sv-radius-lg) var(--sv-radius-lg) 0 0;
}

.row__main:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: -2px;
  border-radius: var(--sv-radius-lg);
}

.row__head {
  display: flex;
  align-items: center;
  gap: var(--sv-space-sm);
}

.row__receiver {
  font-size: var(--sv-font-body);
  font-weight: 600;
}

.row__phone {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  font-variant-numeric: tabular-nums;
}

/* 默认标既有底色也有文字：只靠颜色区分对色觉障碍用户等于没区分 */
.row__default {
  padding: 1px var(--sv-space-sm);
  border-radius: var(--sv-radius-pill);
  background: var(--sv-color-primary-soft);
  color: var(--sv-color-primary);
  font-size: var(--sv-font-footnote);
}

.row__line {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-caption);
  line-height: 1.5;
}

.row__ops {
  display: flex;
  justify-content: flex-end;
  gap: var(--sv-space-md);
  padding: 0 var(--sv-space-md) var(--sv-space-md);
}

.op {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--sv-text-secondary);
  font: inherit;
  font-size: var(--sv-font-footnote);
  cursor: pointer;
}

.op--danger {
  color: var(--sv-color-danger);
}

.op:disabled {
  cursor: default;
  opacity: 0.5;
}

.op:focus-visible {
  outline: 2px solid var(--sv-color-primary);
  outline-offset: 2px;
  border-radius: var(--sv-radius-sm);
}

.page__foot {
  position: fixed;
  inset: auto 0 0;
  padding: var(--sv-space-md) var(--sv-space-page) calc(var(--sv-space-md) + var(--sv-safe-bottom));
  background: var(--sv-bg-surface);
  box-shadow: 0 -0.5px 0 var(--sv-border-color);
}
</style>
