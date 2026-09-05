import type { Id } from '@/types/contract'

import { request } from './http'

/**
 * 收货地址簿。字段名照 `t_mall_address`，2026-09-05 已接通真实接口。
 *
 * <h3>地址在商城域，不在会员域</h3>
 * 表是 `t_mall_address` —— 它只服务于实物兑换的收货。
 *
 * <h3>🔴 三列密文、省市区明文，这是刻意的</h3>
 * 收件人姓名、手机号、详细门牌落库加密（与 `t_physical_delivery` <b>同一套
 * PiiTypeHandler、同一把密钥</b>）；省市区不加密 —— 识别不到具体自然人，
 * 但后台发货分布与物流成本统计要用，一起加密的话那些统计就全废了。
 *
 * <p>脱敏是<b>展示层</b>的事：列表接口解密后在应用层截成 `138****8000` 再下发，
 * <b>不存第二份明文脱敏值</b>。所以下面 {@link Address.receiverPhone} 是脱敏值，
 * 而 {@link AddressInput.receiverPhone} 是明文 —— 两者不是同一个东西。
 */

export interface Address {
  id: Id
  /** 收件人姓名。落库密文，下发时后台已解密 */
  receiverName: string
  /** 收件人手机号，**列表接口下发脱敏值**，如 138****8000 */
  receiverPhone: string
  /** 省 / 市 / 区。明文，可统计，前端不做字符串拆分 */
  province: string
  city: string
  district: string
  /** 详细门牌地址。落库密文 */
  detailAddress: string
  /** 是不是默认地址。一个会员<b>最多一条</b>，由服务端保证 */
  isDefault: boolean
}

/** 新建 / 编辑时提交的字段。id 与 isDefault 不在里面 —— 默认地址走单独接口 */
export interface AddressInput {
  receiverName: string
  /** 提交的是**明文**手机号。列表回来的是脱敏值，两者不是同一个东西 */
  receiverPhone: string
  province: string
  city: string
  district: string
  detailAddress: string
}

/** 拼成一行展示用。省市区之间不加空格是国内地址的习惯写法 */
export function formatAddressLine(address: Address): string {
  return `${address.province}${address.city}${address.district} ${address.detailAddress}`
}

/**
 * 我的地址，<b>默认那条排最前</b>（兑换页取第 0 条作预选，这个顺序就是「默认」的含义）。
 * 手机号是脱敏值。
 */
export function fetchAddresses(): Promise<Address[]> {
  return request<Address[]>({ url: '/address' })
}

/** 取一条。不存在或不是自己的，后端一律回 404（不给探测者区分信号） */
export function fetchAddress(addressId: Id): Promise<Address> {
  return request<Address>({ url: `/address/${addressId}` })
}

/** 新增。<b>第一条地址自动成为默认</b>，不用再点一次「设为默认」 */
export function createAddress(input: AddressInput): Promise<Address> {
  return request<Address>({ url: '/address', method: 'POST', data: input })
}

/**
 * 编辑。
 *
 * <p>⚠️ `receiverPhone` 留空表示<b>不修改手机号</b> —— 列表下发的是脱敏值，
 * 不该把 `138****8000` 回填给用户改，一提交就把星号存进库了。
 */
export function updateAddress(addressId: Id, input: AddressInput): Promise<Address> {
  return request<Address>({ url: `/address/${addressId}`, method: 'PUT', data: input })
}

/**
 * 删除。
 *
 * <p>删掉的如果是默认地址，<b>服务端会自动把剩下的第一条置默认</b> ——
 * 不会让账号进入「一条地址都不默认」的状态。
 */
export function deleteAddress(addressId: Id): Promise<void> {
  return request<void>({ url: `/address/${addressId}`, method: 'DELETE' })
}

/**
 * 设为默认。
 *
 * <p>服务端<b>一个事务做完两件事</b>（旧的取消、新的置上）——
 * 前端调两次的话，中间断网会留下两个默认地址，或者一个都没有。
 */
export function setDefaultAddress(addressId: Id): Promise<void> {
  return request<void>({ url: `/address/${addressId}/default`, method: 'PUT' })
}
