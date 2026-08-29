<!--
  * 商品编辑页（发布新商品 / 编辑商品，同一个页面）
  *
  * 骨架对齐原型 docs/mall.html：左侧锚点导航 + 主表单 + 吸底操作栏。
  *
  * 【为什么是整页而不是弹窗】
  * 商品是主子表：主表 + N 个 SKU + 轮播图 + 富文本详情 + 限兑规则。800px 的弹窗装不下。
  *
  * 【为什么有左侧锚点导航】
  * 表单四段、一屏放不下。运营最常问的是「还差哪块没填」——
  * 徽标把每段的待完善项数直接标出来，点一下滚过去，不用自己翻。
  *
  * 【为什么按钮在底部而不是顶部】
  * 填表是自上而下的，填完时视线在页面底部。按钮吸底的话「填完 → 提交」是连续动作；
  * 放顶部则每次提交都要先滚回去。吸底栏左侧同时是校验汇总，提交前能看到全局状态。
  *
  * 🔴 本页的路由菜单 cache_flag 必须为 0：keep-alive 的 include 用 menuId 当组件名，
  *    新建与编辑复用同一条路由，缓存会把上一个商品的数据串进来。
  *
  * @Copyright  weolwo
-->
<template>
  <a-spin :spinning="loading">
    <!---------- 顶栏 ----------->
    <div class="editor-topbar">
      <div class="flex items-center gap-3">
        <span class="back-link" @click="goBack">← 返回商品列表</span>
        <a-divider type="vertical" />
        <span class="editor-title">{{ isCreate ? '发布新商品' : '编辑商品' }}</span>
        <a-tag :color="statusMeta.color">{{ statusMeta.desc }}</a-tag>
      </div>
      <a-button size="small" @click="previewOpen = true">👁 C端预览</a-button>
    </div>

    <div class="editor-body">
      <!---------- 左侧锚点导航 ----------->
      <div class="anchor-nav">
        <a-card :bordered="false" class="shadow-sm" :body-style="{ padding: '8px' }">
          <div v-for="s in SECTIONS" :key="s.key" class="anchor-item" :class="{ active: activeSection === s.key }" @click="scrollTo(s.key)">
            <span>{{ s.label }}</span>
            <a-badge v-if="errorCountOf(s.key) > 0" :count="errorCountOf(s.key)" :number-style="{ backgroundColor: '#ef4444' }" />
            <span v-else class="anchor-ok">✓</span>
          </div>
        </a-card>
        <div class="anchor-foot">积分商城，不是电商。<br />没有购物车、没有满减、没有会员等级差异化定价。</div>
      </div>

      <!---------- 主表单 ----------->
      <div class="editor-main">
        <a-form :model="form" layout="vertical">
          <!-- ---------- 1. 基础信息 ---------- -->
          <a-card id="sec-base" :bordered="false" class="shadow-sm mb-5">
            <div class="section-title">1. 基础信息</div>
            <a-row :gutter="20">
              <a-col :span="12">
                <a-form-item label="商品名称" required :validate-status="form.commodityName ? '' : 'error'" :help="form.commodityName ? '' : '必填'">
                  <a-input v-model:value="form.commodityName" placeholder="最多 60 字" show-count :maxlength="60" />
                </a-form-item>
              </a-col>

              <a-col :span="12">
                <!--
                  铁律 8：10位大写字母+数字，全局唯一，创建后不可改。
                  运营既可以手输也可以点「生成」，两条路径服务端都会校验格式并判重 ——
                  唯一索引只是兜底，让它抛 SQL 异常的话，运营看到的是一串英文堆栈。
                -->
                <a-form-item
                  label="商品编码"
                  required
                  :validate-status="codeValid ? '' : 'error'"
                  :help="codeValid ? '10位大写字母+数字，全局唯一。创建后不可修改' : '格式必须是 10 位大写字母或数字'"
                >
                  <a-input-group compact>
                    <a-input
                      v-model:value="form.commodityCode"
                      :disabled="!isCreate"
                      style="width: calc(100% - 88px)"
                      placeholder="如 H88JHKJFNE"
                      @change="form.commodityCode = (form.commodityCode || '').toUpperCase()"
                    />
                    <a-button style="width: 88px" :disabled="!isCreate" :loading="codeLoading" @click="genCode">生成</a-button>
                  </a-input-group>
                </a-form-item>
              </a-col>

              <a-col :span="12">
                <a-form-item label="所属分类" required :validate-status="form.categoryId ? '' : 'error'" :help="form.categoryId ? '' : '必填'">
                  <!-- DDL 里业务上限死两级：不卡的话运营能建出五级菜单，C 端宫格导航根本渲染不了 -->
                  <a-cascader
                    v-model:value="categoryPath"
                    :options="categoryOptions"
                    :loading="categoryLoading"
                    placeholder="请选择分类（最多两级）"
                    change-on-select
                    @change="onCategoryChange"
                  />
                </a-form-item>
              </a-col>

              <a-col :span="12">
                <!-- 取值对齐 sa.enums.PrizeTypeEnum：类型一对齐，sa/ledger/handler 下现成的履约 handler 直接就能用 -->
                <a-form-item label="商品类型" required>
                  <a-radio-group v-model:value="form.commodityType" button-style="solid">
                    <a-radio-button value="PHYSICAL">📦 实物</a-radio-button>
                    <a-radio-button value="COUPON">🎟️ 优惠券</a-radio-button>
                    <a-radio-button value="BALANCE">💰 现金红包</a-radio-button>
                  </a-radio-group>
                  <div class="form-tip mt-1">{{ typeHint }}</div>
                </a-form-item>
              </a-col>

              <a-col v-if="isCoupon" :span="12">
                <!--
                  DDL 上一版写着「虚拟商品发卡密」却没有任何字段能存"发哪张券"，assetRef 就是补这个洞的。
                  不填的话兑换会在履约阶段失败 —— 而那时用户的积分已经扣掉了。
                -->
                <a-form-item
                  label="关联券模"
                  required
                  :validate-status="form.assetRef ? '' : 'error'"
                  :help="form.assetRef ? '兑换成功后按这个编码发券' : '优惠券商品必须指定发放哪张券'"
                >
                  <a-input v-model:value="form.assetRef" placeholder="券模编码，如 CPN10YUAN01" :maxlength="64" />
                </a-form-item>
              </a-col>

              <a-col :span="24">
                <a-form-item label="一句话卖点">
                  <a-input v-model:value="form.commodityIntro" placeholder="展示在商品名下方，如：限量 200 份，兑完即止" :maxlength="255" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-card>

          <!-- ---------- 2. 定价与规格 ---------- -->
          <a-card id="sec-sku" :bordered="false" class="shadow-sm mb-5">
            <div class="section-title">2. 定价与规格</div>

            <a-row :gutter="20">
              <a-col :span="8">
                <a-form-item label="支付方式" required>
                  <a-radio-group v-model:value="form.payType" @change="onPayTypeChange">
                    <a-radio :value="PAY_TYPE_ENUM.POINTS.value">纯积分</a-radio>
                    <a-radio :value="PAY_TYPE_ENUM.POINTS_CASH.value">积分 + 现金</a-radio>
                  </a-radio-group>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="基准兑换积分" required>
                  <a-input-number v-model:value="form.pointsPrice" :min="0" :precision="0" style="width: 100%" />
                  <div class="form-tip mt-1">SKU 留空时继承这个值</div>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="划线原价">
                  <a-input-number v-model:value="form.originalPrice" :min="0" :precision="2" style="width: 100%" addon-before="￥" />
                  <div class="form-tip mt-1">仅前端展示「价值￥199」，可留 0</div>
                </a-form-item>
              </a-col>
            </a-row>

            <a-alert
              v-if="form.payType === PAY_TYPE_ENUM.POINTS_CASH.value"
              type="warning"
              show-icon
              class="mb-4"
              message="积分+现金的订单要等支付回调，中间会悬挂锁定库存"
              description="下单后 30 分钟未支付将由定时任务自动取消并释放库存。纯积分订单是同步扣的，不存在这个悬挂期。"
            />

            <SkuEditor
              ref="skuEditorRef"
              v-model:value="form.skuList"
              :pay-type="form.payType"
              :points-price="form.pointsPrice"
              :cash-price="form.cashPrice"
            />
          </a-card>

          <!-- ---------- 3. 图文详情 ---------- -->
          <a-card id="sec-media" :bordered="false" class="shadow-sm mb-5">
            <div class="section-title">3. 图文详情</div>

            <a-alert
              type="info"
              show-icon
              class="mb-4"
              message="商品图直接在这里上传，不走素材库选择器"
              description="商品图又多又杂、复用率接近零，混进素材库会把运营的策展空间冲垮。所以上传后归到 MALL_COMMODITY 分类，素材库列表固定排除该分类——两边在运营视角下互不干扰。但底层仍是同一套文件模块：t_file_relation 照常登记，删除守卫、孤儿清理、存储介质切换全部照常生效。"
            />

            <a-form-item
              label="封面主图（建议 800×800）"
              required
              :validate-status="form.coverFileId ? '' : 'error'"
              :help="form.coverFileId ? '' : '必填'"
            >
              <ImageSlot v-model:fileId="form.coverFileId" placeholder="选择主图" />
            </a-form-item>

            <a-form-item :label="`轮播图册（最多 ${MAX_BANNER_COUNT} 张）`">
              <div class="flex flex-wrap gap-3">
                <div v-for="(fid, i) in form.bannerFileIds" :key="fid" class="banner-item">
                  <ImageSlot :file-id="fid" @update:fileId="(v) => onBannerChange(i, v)" />
                  <div class="banner-actions">
                    <a-button type="link" size="small" :disabled="i === 0" @click="moveBanner(i, -1)">←</a-button>
                    <span class="form-tip">sort {{ i }}</span>
                    <a-button type="link" size="small" :disabled="i === form.bannerFileIds.length - 1" @click="moveBanner(i, 1)">→</a-button>
                  </div>
                </div>
                <ImageSlot v-if="form.bannerFileIds.length < MAX_BANNER_COUNT" :file-id="null" placeholder="添加" @update:fileId="addBanner" />
              </div>
              <div class="form-tip mt-2">
                轮播图不单独建表，复用 <code>t_file_relation(biz_type='MALL_COMMODITY_BANNER', biz_id, sort)</code> —— 那张表的 sort
                列注释原文就是「附件顺序，轮播图必需」。
              </div>
              <div class="form-warn mt-1">
                ⚠ 保存时 confirm 的是<b>完整引用集合</b>：主图 + 轮播 + 每个 SKU 的图 + 富文本内嵌图。漏掉 SKU 图是最常见的一处（当前这单商品共
                {{ referencedFileIds.length }} 个 file_id，富文本内嵌图由后端再解析补入）。
              </div>
            </a-form-item>

            <a-form-item label="图文详情">
              <!-- 编辑器里插的图走 fileApi 上传，插入的是 /file/download/{id} 形态的 URL，
                   保存时后端能反查回 fileId 并登记引用。base64 前后端各拦一道 -->
              <Wangeditor v-model="form.detailContent" :height="360" />
            </a-form-item>

            <a-form-item label="兑换须知">
              <a-textarea
                v-model:value="form.exchangeNotice"
                :rows="3"
                :maxlength="1024"
                show-count
                placeholder="券的核销方式与有效期 / 实物的发货时效与售后口径。C端下单页固定展示"
              />
              <div class="form-tip mt-1">{{ noticeHint }}</div>
            </a-form-item>
          </a-card>

          <!-- ---------- 4. 兑换限制与上架 ---------- -->
          <a-card id="sec-limit" :bordered="false" class="shadow-sm mb-5">
            <div class="section-title">4. 兑换限制与上架</div>
            <a-row :gutter="20">
              <a-col :span="8">
                <!-- 取值对齐 t_promotion_config.limit_period 已有的字典，不新造一套 -->
                <a-form-item label="限兑周期">
                  <a-select v-model:value="form.limitPeriod" :options="LIMIT_PERIOD_OPTIONS" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="周期内限兑件数">
                  <a-input-number v-model:value="form.limitCount" :min="0" :precision="0" style="width: 100%" />
                  <div class="form-tip mt-1">0 = 不限制</div>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="首页推荐">
                  <a-switch :checked="form.isHome === 1" @change="(c) => (form.isHome = c ? 1 : 0)" />
                </a-form-item>
              </a-col>

              <a-col :span="16">
                <a-form-item label="兑换时间段">
                  <a-range-picker v-model:value="timeRange" show-time value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
                  <div class="form-tip mt-1">
                    留空 = 长期在线。落库时写哨兵值 1970-01-01 / 2099-12-31，不写 NULL —— 可空的话查询要写
                    <code>(start_time IS NULL OR ...)</code>，那个 OR 走不了索引。
                  </div>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="排序权重">
                  <a-input-number v-model:value="form.sort" :min="0" :precision="0" style="width: 100%" />
                  <div class="form-tip mt-1">从小到大</div>
                </a-form-item>
              </a-col>
            </a-row>

            <a-alert
              v-if="form.limitCount > 0"
              type="success"
              show-icon
              :message="`限兑规则：每个会员 ${periodLabel} 最多兑换 ${form.limitCount} 件`"
              description="并发正确性靠 t_mall_exchange_limit 的唯一索引 (member_id, commodity_id, period_key) + 条件 UPDATE 保证，不是 count 订单表——两个请求同时读到 count=0 会双双通过。"
            />
          </a-card>
        </a-form>
      </div>
    </div>

    <!---------- 吸底操作栏 ----------->
    <div class="sticky-footer">
      <div class="text-sm">
        <span v-if="errors.length === 0" class="footer-ok">✓ 校验通过，共 {{ form.skuList.length }} 个 SKU，总库存 {{ totalStock }}</span>
        <span v-else class="footer-error" @click="showErrors">⚠ {{ errors.length }} 项待完善，点击查看</span>
      </div>
      <div class="flex gap-3">
        <a-button size="large" style="width: 120px" :loading="saving" @click="submit(COMMODITY_STATUS_ENUM.DRAFT.value)">存为草稿</a-button>
        <a-button type="primary" size="large" style="width: 180px" :loading="saving" @click="submit(COMMODITY_STATUS_ENUM.ON.value)">
          {{ isOnline ? '保存并保持上架' : '提交并上架' }}
        </a-button>
      </div>
    </div>

    <CSidePreview v-model:open="previewOpen" :form="form" :sku-list="form.skuList" />
  </a-spin>
</template>

<script setup>
  import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
  import { mallCommodityApi } from '/@/api/business/mall/mall-commodity-api';
  import { mallCategoryApi } from '/@/api/business/mall/mall-category-api';
  import {
    COMMODITY_STATUS_ENUM,
    COMMODITY_TYPE_ENUM,
    LIMIT_PERIOD_ENUM,
    LIMIT_PERIOD_OPTIONS,
    PAY_TYPE_ENUM,
    SHELF_END_SENTINEL,
    SHELF_START_SENTINEL,
  } from '/@/constants/business/mall/mall-commodity-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import Wangeditor from '/@/components/framework/wangeditor/index.vue';
  import ImageSlot from '../components/image-slot.vue';
  import SkuEditor from './components/sku-editor.vue';
  import CSidePreview from './components/c-side-preview.vue';

  const COMMODITY_LIST_PATH = '/mall/mall-commodity/list';

  /** 铁律 8 的唯一真源在后端 SolvelaCodeUtil，这里是同源常量 */
  const BIZ_CODE_REGEX = /^[A-Z0-9]{10}$/;

  const MAX_BANNER_COUNT = 8;

  const SECTIONS = [
    { key: 'sec-base', label: '基础信息' },
    { key: 'sec-sku', label: '定价与规格' },
    { key: 'sec-media', label: '图文详情' },
    { key: 'sec-limit', label: '兑换限制' },
  ];

  const route = useRoute();
  const router = useRouter();

  const loading = ref(false);
  const saving = ref(false);
  const codeLoading = ref(false);
  const previewOpen = ref(false);
  const skuEditorRef = ref();

  /**
   * 编辑中的商品 id。新建保存成功后要就地改写它（连同地址栏），
   * 否则运营再点一次保存就会建出第二个商品。
   */
  const commodityId = ref(route.query.id ? Number(route.query.id) : null);
  const isCreate = computed(() => !commodityId.value);

  function buildEmptyForm() {
    return {
      id: null,
      commodityCode: '',
      categoryId: null,
      commodityType: COMMODITY_TYPE_ENUM.PHYSICAL.value,
      assetRef: '',
      commodityName: '',
      commodityIntro: '',
      coverFileId: null,
      bannerFileIds: [],
      detailContent: '',
      exchangeNotice: '',
      payType: PAY_TYPE_ENUM.POINTS.value,
      originalPrice: 0,
      pointsPrice: 0,
      cashPrice: 0,
      limitPeriod: LIMIT_PERIOD_ENUM.LIFETIME.value,
      limitCount: 0,
      startTime: null,
      endTime: null,
      status: COMMODITY_STATUS_ENUM.DRAFT.value,
      isHome: 0,
      sort: 0,
      skuList: [],
    };
  }

  const form = reactive(buildEmptyForm());
  const timeRange = ref([]);
  const categoryPath = ref([]);

  const isCoupon = computed(() => form.commodityType === COMMODITY_TYPE_ENUM.COUPON.value);
  const isOnline = computed(() => form.status === COMMODITY_STATUS_ENUM.ON.value);

  const statusMeta = computed(() => Object.values(COMMODITY_STATUS_ENUM).find((item) => item.value === form.status) || COMMODITY_STATUS_ENUM.DRAFT);

  const typeHint = computed(
    () =>
      ({
        PHYSICAL: '走 t_physical_delivery 发货，需要收货地址',
        COUPON: '走 t_member_coupon 发券，需指定券模',
        BALANCE: '走 t_member_wallet 直接入账',
      })[form.commodityType] || ''
  );

  const periodLabel = computed(() => {
    const meta = Object.values(LIMIT_PERIOD_ENUM).find((p) => p.value === form.limitPeriod);
    return meta ? meta.desc : '';
  });

  const noticeHint = computed(() => (isCoupon.value ? '券类商品务必写清核销方式和有效期——这是客诉最集中的地方' : '实物建议写明发货时效与售后口径'));

  const totalStock = computed(() => form.skuList.reduce((sum, sku) => sum + (sku.totalStock || 0), 0));

  /**
   * 本次保存引用到的全部 fileId。后端 confirm 要的就是这个完整集合（不是增量），
   * 这里只是把数量显示给运营看 —— 真正的收集在服务端，还会补上富文本内嵌图。
   * SKU 图是最容易漏的一处：它藏在 skuList 里。
   */
  const referencedFileIds = computed(() => {
    const ids = new Set();
    if (form.coverFileId) {
      ids.add(form.coverFileId);
    }
    form.bannerFileIds.forEach((id) => ids.add(id));
    form.skuList.forEach((sku) => {
      if (sku.skuCoverFileId) {
        ids.add(sku.skuCoverFileId);
      }
    });
    return [...ids];
  });

  // ---------------------------- 校验汇总 ----------------------------
  //
  // 前端校验只是防呆，后端必须重算一遍（防绕过页面直接 POST）。
  // 这里不用 a-form 的 rules：rules 只能逐字段弹提示，而运营要的是「还差哪几块」的全局视图。

  const codeValid = computed(() => BIZ_CODE_REGEX.test(form.commodityCode || ''));

  /**
   * SKU 那段的校验住在子组件里（编码格式/重复只有它知道）。
   * 用一个 ref 中转而不是在 computed 里直接调子组件的方法：computed 求值时子组件
   * 可能还没挂载，而且那样也追踪不到子组件内部的响应式变化。
   */
  const skuErrors = ref([]);
  let skuErrorTimer = null;

  function refreshSkuErrors() {
    clearTimeout(skuErrorTimer);
    skuErrorTimer = setTimeout(() => {
      skuErrors.value = skuEditorRef.value ? skuEditorRef.value.validate() : [];
    }, 200);
  }

  const errors = computed(() => {
    const list = [];
    if (!form.commodityName) {
      list.push({ sec: 'sec-base', msg: '商品名称未填' });
    }
    if (!codeValid.value) {
      list.push({ sec: 'sec-base', msg: '商品编码格式不对（10位大写字母+数字）' });
    }
    if (!form.categoryId) {
      list.push({ sec: 'sec-base', msg: '未选择分类' });
    }
    if (isCoupon.value && !form.assetRef) {
      list.push({ sec: 'sec-base', msg: '优惠券商品未指定券模' });
    }
    if (form.pointsPrice === null || form.pointsPrice === undefined) {
      list.push({ sec: 'sec-sku', msg: '未填基准兑换积分' });
    }
    skuErrors.value.forEach((msg) => list.push({ sec: 'sec-sku', msg }));
    if (!form.coverFileId) {
      list.push({ sec: 'sec-media', msg: '未设置封面主图' });
    }
    if (isCoupon.value && !form.exchangeNotice) {
      list.push({ sec: 'sec-media', msg: '券类商品建议填写核销说明' });
    }
    if (timeRange.value && timeRange.value.length === 2 && timeRange.value[0] >= timeRange.value[1]) {
      list.push({ sec: 'sec-limit', msg: '兑换结束时间必须晚于开始时间' });
    }
    return list;
  });

  const errorCountOf = (sec) => errors.value.filter((e) => e.sec === sec).length;

  function showErrors() {
    Modal.warning({
      title: `还有 ${errors.value.length} 项待完善`,
      content: errors.value.map((e) => `· ${e.msg}`).join('\n'),
      okText: '去修改',
      onOk: () => scrollTo(errors.value[0].sec),
    });
  }

  // ---------------------------- 锚点导航 ----------------------------

  const activeSection = ref(SECTIONS[0].key);

  function scrollTo(key) {
    const el = document.getElementById(key);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  /**
   * 滚动高亮。从后往前找第一个「顶已经越过 140px」的段 —— 正着找的话，
   * 页面滚到底部时前面几段都满足条件，高亮会停在第一个而不是当前这个。
   */
  function onScroll() {
    for (let i = SECTIONS.length - 1; i >= 0; i--) {
      const el = document.getElementById(SECTIONS[i].key);
      if (el && el.getBoundingClientRect().top <= 140) {
        activeSection.value = SECTIONS[i].key;
        return;
      }
    }
    activeSection.value = SECTIONS[0].key;
  }

  // ---------------------------- 未保存拦截 ----------------------------

  const baseSnapshot = ref('');
  const currentSnapshot = computed(() => snapshotOf({ form, timeRange: timeRange.value, categoryPath: categoryPath.value }));
  const isDirty = computed(() => baseSnapshot.value !== '' && currentSnapshot.value !== baseSnapshot.value);

  /**
   * rowKey 是 SKU 编辑器发给 Vue 的行标识，不是业务数据。
   * 不排除它的话：子组件回填 rowKey 会让表单在「刚打开还没动过」时就被判成有改动，
   * 于是每次点返回都弹一次「有未保存的改动」。
   */
  function snapshotOf(value) {
    return JSON.stringify(value, (key, val) => (key === 'rowKey' ? undefined : val));
  }

  /** 子组件的回填走的是 watcher（pre-flush），所以基线要等它落定之后再取 */
  async function resetSnapshot() {
    await nextTick();
    baseSnapshot.value = currentSnapshot.value;
  }

  onBeforeRouteLeave(() => {
    if (!isDirty.value) {
      return true;
    }
    return new Promise((resolve) => {
      Modal.confirm({
        title: '有未保存的改动',
        content: '离开本页后已填写的内容将全部丢失。',
        okText: '仍然离开',
        okType: 'danger',
        cancelText: '留在本页',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  });

  // ---------------------------- 加载 ----------------------------

  const categoryFlatList = ref([]);
  const categoryOptions = ref([]);
  const categoryLoading = ref(false);

  /**
   * 扁平分类列表组成两级级联。DDL 业务上限死两级（parent 的 parent 必须为 0），
   * 所以这里只认一层 children，不做递归 —— 递归反而会把「运营手滑建出的三级」渲染出来，
   * 让一个本该被拦下的错误看起来是正常的。
   */
  function buildCascader(list) {
    const roots = list.filter((c) => !c.parentId);
    return roots.map((root) => {
      const children = list.filter((c) => c.parentId === root.id).map((c) => ({ value: c.id, label: c.categoryName }));
      return children.length ? { value: root.id, label: root.categoryName, children } : { value: root.id, label: root.categoryName };
    });
  }

  /** 回显：把 categoryId 还原成级联控件要的路径 */
  function resolveCategoryPath(list, categoryId) {
    const target = list.find((c) => c.id === categoryId);
    if (!target) {
      return [];
    }
    return target.parentId ? [target.parentId, target.id] : [target.id];
  }

  async function loadCategory() {
    categoryLoading.value = true;
    try {
      const res = await mallCategoryApi.enabledList();
      categoryFlatList.value = res || [];
      categoryOptions.value = buildCascader(categoryFlatList.value);
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      categoryLoading.value = false;
    }
  }

  function onCategoryChange(path) {
    form.categoryId = path && path.length ? path[path.length - 1] : null;
  }

  async function genCode() {
    codeLoading.value = true;
    try {
      const res = await mallCommodityApi.generateCode();
      form.commodityCode = res;
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      codeLoading.value = false;
    }
  }

  async function loadDetail() {
    if (isCreate.value) {
      await genCode();
      // 新建商品先摆出那一行单规格 SKU。放在这里而不是子组件的 watch 里：
      // 那个 watch 跑在 setup 同步阶段，碰不到声明在它下面的 computed
      await skuEditorRef.value?.seedIfEmpty();
      await resetSnapshot();
      return;
    }
    loading.value = true;
    try {
      const res = await mallCommodityApi.detail(commodityId.value);
      const detail = res || {};
      Object.assign(form, {
        ...buildEmptyForm(),
        ...detail,
        bannerFileIds: detail.bannerFileIds || [],
        skuList: detail.skuList || [],
      });
      // 哨兵值 = 不限，渲染成空的时间选择器
      const start = detail.startTime === SHELF_START_SENTINEL ? null : detail.startTime;
      const end = detail.endTime === SHELF_END_SENTINEL ? null : detail.endTime;
      timeRange.value = start || end ? [start, end] : [];
      categoryPath.value = resolveCategoryPath(categoryFlatList.value, detail.categoryId);
      await resetSnapshot();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      loading.value = false;
    }
  }

  onMounted(async () => {
    // 先分类后详情：详情回显要用分类列表把 categoryId 还原成级联路径
    await loadCategory();
    await loadDetail();
    refreshSkuErrors();
    window.addEventListener('scroll', onScroll, { passive: true });
  });

  onUnmounted(() => {
    window.removeEventListener('scroll', onScroll);
    clearTimeout(skuErrorTimer);
  });

  // ---------------------------- 轮播图 ----------------------------

  function addBanner(fileId) {
    if (fileId) {
      form.bannerFileIds.push(fileId);
    }
  }

  function onBannerChange(index, fileId) {
    if (fileId) {
      form.bannerFileIds[index] = fileId;
    } else {
      form.bannerFileIds.splice(index, 1);
    }
  }

  function moveBanner(index, offset) {
    const target = index + offset;
    const list = form.bannerFileIds;
    [list[index], list[target]] = [list[target], list[index]];
  }

  // ---------------------------- 保存 ----------------------------

  function onPayTypeChange() {
    if (form.payType === PAY_TYPE_ENUM.POINTS.value) {
      // 只 disabled 不清零的话，脏值照样进 payload。服务端也会强制归零，两边一致才不会让人困惑
      form.cashPrice = 0;
      form.skuList.forEach((sku) => (sku.skuCashPrice = null));
    }
  }

  /**
   * 存草稿不校验，提交上架才校验。
   * 运营常常要先存一半（等设计出图、等券模编码），草稿卡校验的话这个动作就没意义了。
   */
  async function submit(targetStatus) {
    await skuEditorRef.value.ensureNotEmpty();
    await nextTick();
    // 校验是防抖的，这里等它落定再判，否则刚改完就点提交会用到上一轮的结果
    refreshSkuErrors();
    await new Promise((resolve) => setTimeout(resolve, 250));

    if (targetStatus === COMMODITY_STATUS_ENUM.ON.value && errors.value.length) {
      showErrors();
      return;
    }

    saving.value = true;
    try {
      const param = {
        ...form,
        id: commodityId.value,
        status: targetStatus,
        assetRef: isCoupon.value ? form.assetRef : null,
        // 留空 = 长期在线，交给服务端填哨兵值
        startTime: timeRange.value && timeRange.value[0] ? timeRange.value[0] : null,
        endTime: timeRange.value && timeRange.value[1] ? timeRange.value[1] : null,
        // 剔除三类不属于请求契约的字段：
        //   rowKey                              前端的行标识
        //   lockedStock / soldCount / available 运行态数据，只由下单和履约链路维护
        // 后端的 SkuForm 本来就没有这几个字段（Jackson 配了忽略未知属性，发过去也只是被丢掉），
        // 但发一份「看起来能改已售数量」的报文本身就是误导
        skuList: form.skuList.map(({ rowKey, lockedStock, soldCount, availableStock, ...sku }) => sku),
      };
      const res = await mallCommodityApi.save(param);

      const created = isCreate.value;
      commodityId.value = res;

      if (created) {
        // 地址栏换成编辑态：不换的话刷新会回到「新建」，而运营再点一次保存会建出第二个商品
        router.replace({ path: route.path, query: { ...route.query, id: res } });
      }
      // 重新拉一次拿服务端的权威值（编码、SKU 编码、虚拟列库存都是它算的）
      await loadDetail();
      refreshSkuErrors();

      if (targetStatus === COMMODITY_STATUS_ENUM.DRAFT.value) {
        // 草稿的语义就是「还没填完，待会儿接着填」，留在本页是对的，不打断
        message.success('草稿已保存');
        return;
      }
      showSuccessExits();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  /**
   * 上架成功后的三个出口。
   *
   * <p>不替运营选：发布完一个商品，下一步要么再发一个（批量上新是常态），要么回列表核对，
   * 也可能想留在本页再改点什么。<b>但停在原地什么都不提示是最差的</b> ——
   * 页面看起来和点之前一模一样，运营不确定到底存没存进去。
   *
   * <p>形态照搬任务向导的成功页三出口（TaskWizard），那套运营已经用熟了。
   */
  function showSuccessExits() {
    Modal.success({
      title: '已提交并上架',
      content: `商品「${form.commodityName}」已上架，编码 ${form.commodityCode}。`,
      okText: '返回商品列表',
      closable: true,
      maskClosable: true,
      onOk: () => goList(form.commodityName),
      // 「继续发布」放次要按钮位：多数情况下运营发完就走，批量上新才连着发
      cancelText: '继续发布下一个',
      okCancel: true,
      onCancel: continueCreate,
    });
  }

  /**
   * 同一批商品往往只有名称、价格、图片不一样 —— 分类、类型、支付方式、限兑规则、
   * 上架档期通常整批一致。全部重置的话，运营发第二个商品要把这些再点一遍。
   *
   * <p>刻意<b>不</b>保留的：名称、卖点、图片、图文详情、券模编码、规格与库存 ——
   * 这些每个商品都不一样，留着只会让运营在一份别人的数据上改，改漏一处就是一个错商品。
   */
  async function continueCreate() {
    const keep = {
      categoryId: form.categoryId,
      commodityType: form.commodityType,
      payType: form.payType,
      limitPeriod: form.limitPeriod,
      limitCount: form.limitCount,
      isHome: form.isHome,
      sort: form.sort,
      pointsPrice: form.pointsPrice,
      cashPrice: form.cashPrice,
      originalPrice: form.originalPrice,
      exchangeNotice: form.exchangeNotice,
    };
    const keepCategoryPath = [...categoryPath.value];
    const keepTimeRange = [...(timeRange.value || [])];

    Object.assign(form, buildEmptyForm(), keep);
    categoryPath.value = keepCategoryPath;
    timeRange.value = keepTimeRange;
    commodityId.value = null;
    // 地址栏也要回到新建态，否则刷新一下又变成编辑刚才那个商品
    await router.replace({ path: route.path, query: {} });

    await genCode();
    await skuEditorRef.value.seedIfEmpty();
    await resetSnapshot();
    refreshSkuErrors();
    window.scrollTo({ top: 0, behavior: 'smooth' });
    message.success('已保留分类与定价规则，接着填下一个商品');
  }

  /**
   * 回列表。带上商品名让列表页回填搜索条件 —— 列表按 sort 排，刚发布的那个
   * 不一定在第一页，不定位的话运营还得自己搜一遍才能确认。
   */
  function goList(commodityName) {
    router.push(commodityName ? { path: COMMODITY_LIST_PATH, query: { commodityName } } : COMMODITY_LIST_PATH);
  }

  function goBack() {
    goList();
  }
</script>

<style scoped lang="less">
  .editor-topbar {
    position: sticky;
    top: 0;
    z-index: 50;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 24px;
    margin: -12px -12px 0;
    background: #fff;
    box-shadow: 0 1px 4px rgb(15 23 42 / 8%);
  }

  .back-link {
    font-size: 14px;
    color: #94a3b8;
    cursor: pointer;
  }

  .back-link:hover {
    color: #3b82f6;
  }

  .editor-title {
    font-size: 18px;
    font-weight: 700;
    color: #1e293b;
  }

  .editor-body {
    display: flex;
    gap: 20px;
    align-items: flex-start;
    max-width: 1360px;
    /* 吸底栏 88px，不留出来的话最后一段会被它盖住 */
    padding: 20px 0 88px;
    margin: 0 auto;
  }

  .anchor-nav {
    position: sticky;
    top: 88px;
    flex-shrink: 0;
    width: 176px;
  }

  .anchor-item {
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    font-size: 13px;
    cursor: pointer;
    border-radius: 6px;
    transition: all 0.18s;
  }

  .anchor-item:hover {
    background: #f1f5f9;
  }

  .anchor-item.active {
    font-weight: 600;
    color: #1d4ed8;
    background: #eff6ff;
  }

  .anchor-ok {
    font-size: 12px;
    color: #10b981;
  }

  .anchor-foot {
    padding: 0 8px;
    margin-top: 12px;
    font-size: 12px;
    line-height: 1.6;
    color: #94a3b8;
  }

  .editor-main {
    flex: 1;
    min-width: 0;
  }

  .section-title {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 18px;
    font-size: 15px;
    font-weight: 600;
    color: #0f172a;
  }

  .section-title::before {
    display: block;
    width: 4px;
    height: 16px;
    content: '';
    background-color: #3b82f6;
    border-radius: 2px;
  }

  .sticky-footer {
    position: fixed;
    right: 0;
    bottom: 0;
    left: 0;
    z-index: 99;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 24px;
    background: #fff;
    box-shadow: 0 -2px 12px rgb(15 23 42 / 8%);
  }

  .footer-ok {
    color: #059669;
  }

  .footer-error {
    color: #ef4444;
    cursor: pointer;
  }

  .banner-item {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .banner-actions {
    display: flex;
    gap: 2px;
    align-items: center;
  }

  .form-tip {
    font-size: 12px;
    color: #94a3b8;
  }

  .form-warn {
    font-size: 12px;
    color: #d97706;
  }
</style>
