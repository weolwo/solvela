<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { type AddressInput, createAddress, fetchAddress, updateAddress } from '@/api/address'
import { ApiError } from '@/api/errors'
import { useAsync } from '@/composables/useAsync'
import { toId } from '@/types/contract'

/**
 * 新增 / 编辑收货地址。同一个组件两种用法，靠有没有 `params.id` 区分。
 *
 * <h3>省市区暂时是三个普通输入框</h3>
 * 正经做法是三级联动选择器，而那是<b>不该自己写</b>的那一类
 *（滚动惯性、大数据量的虚拟列表、级联加载）。而且它要一份行政区划数据 ——
 * 后端还没有这个接口，硬编码一份进前端包体是几百 KB 且会过期。
 * 所以先用三个输入框把链路跑通，等区划接口到位再换成选择器，
 * <b>那时 {@link AddressInput} 的形状不用变</b>（本来就是省/市/区分开存的）。
 *
 * <h3>手机号提交明文，列表回来是脱敏值</h3>
 * 这两个不是同一个东西，见 {@link import('@/api/address')} 关于 PII 的那段。
 * 所以编辑时手机号那一栏<b>是空的</b>，不是把 138****8000 填回去让用户改 ——
 * 那样一提交就把星号存进库了。空着就是「不改手机号」。
 */

const route = useRoute()
const router = useRouter()

const rawId = route.params.id
const addressId = Array.isArray(rawId) ? rawId[0] : rawId
const editing = computed(() => addressId !== undefined && addressId !== '')

const existing = useAsync(async () =>
  editing.value && addressId !== undefined ? await fetchAddress(toId(addressId)) : null,
)

const receiverName = ref('')
const phone = ref('')
const province = ref('')
const city = ref('')
const district = ref('')
const detailAddress = ref('')

// 编辑：数据回来了再回填。手机号刻意不回填，见上面 PII 那段
watch(existing.data, (value) => {
  if (value === null) {
    return
  }
  receiverName.value = value.receiverName
  province.value = value.province
  city.value = value.city
  district.value = value.district
  detailAddress.value = value.detailAddress
})

/* ---- 校验 ---- */
const submitted = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

/** 中国大陆手机号。宽松到只卡「1 开头的 11 位数字」—— 号段会变，卡太死会误伤 */
const PHONE_PATTERN = /^1\d{10}$/

const receiverError = computed(() =>
  submitted.value && receiverName.value.trim() === '' ? '请填写收件人' : undefined,
)
const phoneError = computed(() => {
  if (!submitted.value) {
    return undefined
  }
  const value = phone.value.trim()
  // 编辑时留空 = 不改手机号，是合法的
  if (value === '' && editing.value) {
    return undefined
  }
  if (value === '') {
    return '请填写手机号'
  }
  return PHONE_PATTERN.test(value) ? undefined : '手机号格式不对'
})
const regionError = computed(() =>
  submitted.value &&
  (province.value.trim() === '' || city.value.trim() === '' || district.value.trim() === '')
    ? '省、市、区都要填'
    : undefined,
)
const detailError = computed(() =>
  submitted.value && detailAddress.value.trim() === '' ? '请填写详细地址' : undefined,
)

const invalid = computed(
  () =>
    receiverError.value !== undefined ||
    phoneError.value !== undefined ||
    regionError.value !== undefined ||
    detailError.value !== undefined,
)

async function onSubmit(): Promise<void> {
  submitted.value = true
  if (invalid.value || submitting.value) {
    return
  }
  submitting.value = true
  errorMessage.value = ''
  const input: AddressInput = {
    receiverName: receiverName.value.trim(),
    receiverPhone: phone.value.trim(),
    province: province.value.trim(),
    city: city.value.trim(),
    district: district.value.trim(),
    detailAddress: detailAddress.value.trim(),
  }
  try {
    if (editing.value && addressId !== undefined) {
      await updateAddress(toId(addressId), input)
    } else {
      await createAddress(input)
    }
    // 回地址簿。用 back() 而不是 push：新增/编辑是从列表进来的，
    // push 会在历史里留下一个表单页，用户再按返回又回到刚填完的表单
    router.back()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '保存失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <NavBar :title="editing ? '编辑收货地址' : '新增收货地址'" />

    <div v-if="existing.loading.value" class="loading" aria-hidden="true">
      <span class="loading__dot" />
    </div>

    <form v-else class="form" novalidate @submit.prevent="onSubmit">
      <p v-if="errorMessage !== ''" class="form__error" role="alert">{{ errorMessage }}</p>

      <Field v-model="receiverName" placeholder="收件人姓名" icon="user" :error="receiverError" />
      <Field
        v-model="phone"
        placeholder="手机号"
        icon="phone"
        type="tel"
        autocomplete="tel"
        :maxlength="11"
        :hint="editing ? '留空表示不修改手机号' : undefined"
        :error="phoneError"
      />

      <div class="region">
        <Field v-model="province" placeholder="省" :error="regionError" />
        <Field v-model="city" placeholder="市" />
        <Field v-model="district" placeholder="区/县" />
      </div>

      <Field v-model="detailAddress" placeholder="街道、门牌号" :error="detailError" />

      <Button type="submit" :loading="submitting">保存</Button>
    </form>
  </div>
</template>

<style scoped>
.page {
  padding-bottom: var(--sv-space-xl);
}

.loading {
  display: flex;
  justify-content: center;
  padding-top: 30vh;
}

.loading__dot {
  width: 28px;
  height: 28px;
  border: 3px solid var(--sv-bg-fill);
  border-top-color: var(--sv-color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .loading__dot {
    animation-duration: 2.4s;
  }
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--sv-space-md);
  padding: var(--sv-space-md) var(--sv-space-page) 0;
}

.form__error {
  margin: 0;
  color: var(--sv-color-danger);
  font-size: var(--sv-font-caption);
}

/*
 * 省市区一行三格。错误提示挂在「省」那一格上，一句话说完三格的问题 ——
 * 三格各挂一句「必填」会让这一行突然变高两倍。
 */
.region {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sv-space-sm);
  align-items: start;
}
</style>
