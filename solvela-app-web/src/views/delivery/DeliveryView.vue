<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { fetchAddresses, type Address } from '@/api/address'
import { fillDeliveryAddress, fetchDeliveries, type DeliveryItem } from '@/api/delivery'
import { ApiError } from '@/api/errors'
import { useAsync } from '@/composables/useAsync'

/**
 * 我的实物奖品。
 *
 * <h3>🔴 这一页存在的理由</h3>
 * 后端 `PhysicalAssetHandler` 的注释把实物履约写成三段，第 ② 段是
 * 「用户在 C 端补填收货信息」—— 而那个入口从来没有存在过。
 * 中了实物奖之后用户侧是断的：看不到、也填不了地址，只能等客服找上门。
 *
 * <h3>挑地址用内嵌的 Sheet，不跳去地址簿</h3>
 * 地址簿已经有一个挑选模式（`?pick=1`），但它<b>写死了要回兑换页</b>
 * （`onPick` 里拿 `commodity` 拼 redeem 路由）。为了这一页去把它改成通用的
 * 「回哪儿由 query 说了算」，会动到一条已经跑通很久的流程 ——
 * 而这里要的只是「选一个」，Sheet 里列出来点一下就够了，还少一次页面跳转。
 *
 * <p>真到第三个地方要挑地址时再抽通用的，那时才值得动它。
 *
 * <h3>物流只展示，不查</h3>
 * 全仓没有接任何快递查询接口。这里老老实实显示「公司 + 单号」让用户自己去查，
 * <b>不画一个查不出东西的「查看物流」按钮</b> —— 那和这个项目里
 * 「画一个点了什么都不会发生的按钮比不画更糟」是同一条。
 */

const deliveries = useAsync(fetchDeliveries)

/* ---- 挑地址 ---- */

const pickerOpen = ref(false)
/** 正在给哪一单填。null = 没在填 */
const filling = ref<DeliveryItem | null>(null)
const submitting = ref(false)
/** 结果提示。成功失败都用它 —— 文案一律由服务端给 */
const hint = ref('')

/**
 * 地址簿。**懒加载**：进这一页的人大多只是来看物流的，
 * 没人点「填写收货信息」就不该白拉一次地址列表。
 */
const addresses = ref<Address[] | null>(null)
const addressesLoading = ref(false)
const addressesError = ref('')

async function openPicker(item: DeliveryItem): Promise<void> {
  filling.value = item
  hint.value = ''
  pickerOpen.value = true
  if (addresses.value !== null || addressesLoading.value) {
    return
  }
  addressesLoading.value = true
  addressesError.value = ''
  try {
    addresses.value = await fetchAddresses()
  } catch (error) {
    addressesError.value = error instanceof ApiError ? error.message : '地址簿加载失败'
  } finally {
    addressesLoading.value = false
  }
}

/**
 * 选中一个地址并提交。
 *
 * <p>🔴 成功之后**重拉整页**，不本地把这一单改成「待发货」：
 * 这一单可能刚好被运营发出去了、也可能被取消了，本地猜一个状态
 * 只会让用户看到一个和服务端不一致的页面，而他会按那个去做下一步。
 * 与兑换记录页付完款的处理同一条。
 */
async function submit(address: Address): Promise<void> {
  const target = filling.value
  if (target === null || submitting.value) {
    return
  }
  submitting.value = true
  hint.value = ''
  try {
    const result = await fillDeliveryAddress(target.deliveryId, address.addressId)
    // 文案一律用服务端给的：成功时是「收货信息已保存，我们会尽快发货」，
    // 失败时是「已经在发货流程里了」—— 同一件事不该有两种说法
    hint.value = result.message
    if (result.accepted) {
      pickerOpen.value = false
      await deliveries.reload()
    }
  } catch (error) {
    hint.value = error instanceof ApiError ? error.message : '保存失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

/** 一条地址拼成一行给用户认 */
function addressLine(a: Address): string {
  return `${a.province}${a.city}${a.district}${a.detailAddress}`
}

/** 来源标签。用户不关心「提案」这种运营词，只说是抽的还是兑的 */
function sourceLabel(item: DeliveryItem): string {
  return item.sourceType === 'MALL' ? '商城兑换' : '活动奖品'
}

/** 有没有待办 —— 有的话在页头提一句，别让用户自己一条条找 */
const pendingCount = computed(
  () => (deliveries.data.value ?? []).filter((d) => d.needAddress).length,
)
</script>

<template>
  <div class="page">
    <NavBar title="我的实物奖品" />

    <div class="page__body">
      <!--
        有待填的就顶上提一句。中了奖却不知道要填地址，是这一页要解决的核心问题 ——
        进来就得看见，而不是让用户自己在列表里找哪条有按钮。
      -->
      <p v-if="pendingCount > 0" class="notice">
        有 <b>{{ pendingCount }}</b> 件奖品还没填收货信息，填完我们才能发货
      </p>

      <Section
        title="全部"
        :loading="deliveries.loading.value"
        :error="deliveries.error.value"
        :empty="(deliveries.data.value ?? []).length === 0"
        empty-text="还没有实物奖品，去活动中心试试手气"
        @retry="deliveries.reload"
      >
        <Card>
          <div v-for="item in deliveries.data.value ?? []" :key="item.deliveryId" class="item">
            <div class="item__head">
              <p class="item__name">{{ item.prizeName }}</p>
              <span class="item__status" :class="{ 'item__status--todo': item.needAddress }">
                {{ item.statusText }}
              </span>
            </div>

            <p class="item__meta">{{ sourceLabel(item) }} · {{ item.createTime }}</p>

            <!-- 填过了才显示收件信息。电话是脱敏值，后端截好的 -->
            <p v-if="item.receiverAddress !== null" class="item__addr">
              {{ item.receiverName }} {{ item.receiverPhone }}<br />
              {{ item.receiverAddress }}
            </p>

            <!--
              物流只展示不查：全仓没接任何快递接口。
              画一个「查看物流」按钮点了却什么都查不到，比不画更糟。
            -->
            <p v-if="item.logisticsNo !== null" class="item__logi">
              {{ item.logisticsCompany }} · 单号 {{ item.logisticsNo }}
            </p>

            <!-- needAddress 是服务端给的，前端不按 status 自己推 -->
            <div v-if="item.needAddress" class="item__op">
              <Button size="small" :block="false" @click="openPicker(item)">填写收货信息</Button>
            </div>
          </div>
        </Card>
      </Section>
    </div>

    <Sheet v-model="pickerOpen" title="选择收货地址">
      <p v-if="hint !== ''" class="hint" role="alert">{{ hint }}</p>

      <p v-if="addressesLoading" class="tip">加载中…</p>
      <p v-else-if="addressesError !== ''" class="tip">{{ addressesError }}</p>

      <!--
        一条地址都没有时，给的是「去添加」而不是一句「暂无地址」——
        这一页的用户手里正拿着一件发不出去的奖品，死路的提示帮不了他。
      -->
      <div v-else-if="(addresses ?? []).length === 0" class="empty">
        <p class="tip">还没有收货地址</p>
        <RouterLink class="empty__link" :to="{ name: 'address-new' }">去添加一个</RouterLink>
      </div>

      <ul v-else class="picks">
        <li v-for="a in addresses ?? []" :key="a.addressId">
          <button class="pick" type="button" :disabled="submitting" @click="submit(a)">
            <span class="pick__who">{{ a.receiverName }} {{ a.receiverPhone }}</span>
            <span class="pick__addr">{{ addressLine(a) }}</span>
          </button>
        </li>
      </ul>
    </Sheet>
  </div>
</template>

<style scoped>
.page__body {
  padding: var(--sv-space-md) var(--sv-space-page);
}

.notice {
  margin: 0 0 var(--sv-space-md);
  padding: 10px 12px;
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
  color: var(--sv-color-primary);
  background: var(--sv-color-primary-soft);
  border-radius: var(--sv-radius-sm);
}

.item {
  padding: var(--sv-space-md);
}

.item + .item {
  border-top: 1px solid var(--sv-border-color);
}

.item__head {
  display: flex;
  align-items: baseline;
  gap: var(--sv-space-sm);
}

.item__name {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: var(--sv-font-caption);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__status {
  flex: none;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}

/* 要用户动手的那种状态得看得出来，否则他不会知道自己还欠一步 */
.item__status--todo {
  color: var(--sv-color-primary);
  font-weight: 600;
}

.item__meta,
.item__addr,
.item__logi {
  margin: var(--sv-space-xs) 0 0;
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  line-height: 1.6;
}

.item__logi {
  font-variant-numeric: tabular-nums;
}

.item__op {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sv-space-sm);
}

.hint {
  margin: 0 0 var(--sv-space-sm);
  color: var(--sv-color-primary);
  font-size: var(--sv-font-footnote);
}

.tip {
  margin: var(--sv-space-sm) 0;
  color: var(--sv-text-placeholder);
  font-size: var(--sv-font-footnote);
}

.empty {
  padding: var(--sv-space-md) 0;
  text-align: center;
}

.empty__link {
  color: var(--sv-color-primary);
  font-size: var(--sv-font-caption);
  font-weight: 600;
}

.picks {
  margin: 0;
  padding: 0;
  list-style: none;
}

.pick {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 12px 4px;
  font: inherit;
  color: inherit;
  text-align: left;
  background: none;
  border: none;
  border-bottom: 1px solid var(--sv-border-color);
  cursor: pointer;
}

.pick:disabled {
  opacity: 0.5;
  cursor: default;
}

.pick__who {
  font-size: var(--sv-font-caption);
  font-weight: 600;
}

.pick__addr {
  color: var(--sv-text-secondary);
  font-size: var(--sv-font-footnote);
  line-height: 1.5;
}
</style>
