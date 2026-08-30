<!--
  * 优惠配置分组 —— 列表
  *
  * 这一页最该回答的问题是「这个组配齐了没有、有没有类型被关掉」，
  * 所以列表上带的是「已配类型 / 启用中」这对聚合数字，而不是一堆配置字段。
  * 真正的编辑在工作台里（一页配完所有资产类型的风控）。
  *
  * @Author:    alaric
  * @Date:      2026-08-30
-->
<template>
  <a-card size="small" :bordered="false" :hoverable="true">
    <a-form class="smart-query-form">
      <a-row class="smart-query-form-row">
        <a-form-item label="分组名称" class="smart-query-form-item">
          <a-input style="width: 220px" v-model:value="queryForm.groupName" placeholder="分组名称，模糊匹配" />
        </a-form-item>
        <a-form-item label="状态" class="smart-query-form-item">
          <a-select style="width: 120px" v-model:value="queryForm.status" :options="GROUP_STATUS_OPTIONS" placeholder="全部" allowClear />
        </a-form-item>
        <a-form-item class="smart-query-form-item">
          <a-button type="primary" @click="onSearch">
            <template #icon><SearchOutlined /></template>
            查询
          </a-button>
          <a-button @click="onReset" class="smart-margin-left10">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </a-form-item>
      </a-row>
    </a-form>

    <a-card size="small" :bordered="false" :hoverable="true">
      <a-row class="smart-table-btn-block">
        <div class="smart-table-operate-block">
          <a-button type="primary" @click="goWorkbench()">
            <template #icon><PlusOutlined /></template>
            新建分组
          </a-button>
        </div>
      </a-row>

      <!--
        展开行直接摆出组内每种类型的配置和它自己的开关 —— 这样就不需要「详情」按钮了：
        运营真正想看的就是「这个组里配了哪几种、各自开着没有」，
        而那正好是一屏能放下的东西，多一次跳转纯属浪费。
        懒加载：展开哪一行才拉哪一行，列表页不为了一列摘要去 N+1。
      -->
      <a-table
        size="small"
        :dataSource="tableData"
        :columns="columns"
        rowKey="id"
        bordered
        :loading="tableLoading"
        :pagination="false"
        :expandedRowKeys="expandedKeys"
        @expand="onExpand"
      >
        <template #expandedRowRender="{ record }">
          <a-spin :spinning="expandLoadingId === record.id">
            <a-table
              size="small"
              :dataSource="itemsOf(record.id)"
              :columns="itemColumns"
              rowKey="id"
              :pagination="false"
              :locale="{ emptyText: '这个分组还没有配置任何资产类型' }"
            >
              <template #bodyCell="{ record: item, column }">
                <template v-if="column.dataIndex === 'prizeType'">
                  <a-tag>{{ prizeTypeDesc(item.prizeType) }}</a-tag>
                  <span class="text-xs text-slate-400">{{ item.prizeType }}</span>
                </template>

                <!-- 库存与预算是二选一的：券/实物看个数，积分/现金看金额，不适用的那一侧恒为 -1 -->
                <template v-else-if="column.dataIndex === 'quota'">
                  <span>{{ quotaText(item) }}</span>
                </template>

                <template v-else-if="column.dataIndex === 'reviewLevel'">
                  <span>{{ reviewLevelOf(item.reviewLevel).desc }}</span>
                </template>

                <template v-else-if="column.dataIndex === 'limitPeriod'">
                  <span>{{ limitPeriodOf(item.limitPeriod).desc }}</span>
                  <span v-if="item.limitStartTime" class="ml-1 text-xs text-slate-400">
                    {{ item.limitStartTime }} ~ {{ item.limitEndTime }}
                  </span>
                </template>

                <!--
                  单条配置的发放开关。组停用时锁死 —— 服务端也会拒绝，
                  但页面上还能拨动就是在骗人（拨完弹个错，比拨不动更烦）。
                -->
                <template v-else-if="column.dataIndex === 'itemStatus'">
                  <a-tooltip :title="record.status === GROUP_STATUS_ENUM.DISABLED.value ? '分组已停用，请先启用分组' : ''">
                    <a-switch
                      size="small"
                      :checked="item.status === GROUP_STATUS_ENUM.ENABLED.value"
                      :disabled="record.status === GROUP_STATUS_ENUM.DISABLED.value"
                      :loading="togglingItemId === item.id"
                      checked-children="发放中"
                      un-checked-children="已停用"
                      @change="(checked) => onToggleItem(record, item, checked)"
                    />
                  </a-tooltip>
                </template>
              </template>
            </a-table>
          </a-spin>
        </template>

        <template #bodyCell="{ text, record, column }">
          <!-- 配置完备度：两个数字不等就说明有类型被关掉了，这比单看一个总数有用 -->
          <template v-if="column.dataIndex === 'coverage'">
            <span v-if="!record.typeCount" class="text-orange-500">未配置任何类型</span>
            <template v-else>
              <a-tag :color="record.enabledTypeCount === record.typeCount ? 'green' : 'orange'">
                启用 {{ record.enabledTypeCount }} / 共 {{ record.typeCount }}
              </a-tag>
            </template>
          </template>

          <!--
            主开关。用 Switch 而不是「编辑里改状态」，是因为它的用途是出事时一键停掉整个活动的发放，
            那种时候没人会想去翻编辑抽屉。
            两个方向都要确认，但确认的内容完全不同 —— 见 onToggleStatus。
          -->
          <template v-else-if="column.dataIndex === 'status'">
            <a-switch
              :checked="record.status === GROUP_STATUS_ENUM.ENABLED.value"
              :loading="switchingId === record.id"
              checked-children="启用"
              un-checked-children="停用"
              @change="(checked) => onToggleStatus(record, checked)"
            />
          </template>

          <template v-else-if="column.dataIndex === 'action'">
            <div class="solvela-table-operate">
              <a-button @click="goWorkbench(record.id)" type="link">配置</a-button>
              <a-button @click="onCopy(record)" type="link">复制</a-button>
              <a-button @click="onDelete(record)" danger type="link">解散</a-button>
            </div>
          </template>
        </template>
      </a-table>

      <div class="solvela-query-table-page">
        <a-pagination
          showSizeChanger
          showQuickJumper
          show-less-items
          :pageSizeOptions="PAGE_SIZE_OPTIONS"
          :defaultPageSize="queryForm.pageSize"
          v-model:current="queryForm.pageNum"
          v-model:pageSize="queryForm.pageSize"
          :total="total"
          @change="queryData"
          @showSizeChange="queryData"
          :show-total="(t) => `共${t}条`"
        />
      </div>
    </a-card>

    <!--
      复制：新组名让人当场改一次。默认「原名 副本」，但一个活动的分组名
      （「2026中秋活动优惠配置」）几乎一定要改成新活动的名字，
      直接建出来再让人去工作台改，等于多绕一圈。
    -->
    <a-modal v-model:open="copyModalVisible" title="复制分组" :confirmLoading="copying" @ok="confirmCopy">
      <a-form :label-col="{ span: 5 }">
        <a-form-item label="源分组">
          <span class="text-slate-500">{{ copySource?.groupName }}</span>
        </a-form-item>
        <a-form-item label="新分组名称" required>
          <a-input v-model:value="copyName" placeholder="新分组名称" :maxlength="128" />
        </a-form-item>
      </a-form>
      <div class="text-xs text-slate-400">
        组内 {{ copySource?.typeCount || 0 }} 条配置会一起复制，已消耗的预算 / 库存归零。
      </div>
    </a-modal>

    <!--
      启用分组时的类型选择。
      为什么不能默认全开：关掉分组的那一刻，「原来哪几种类型是开的」这个信息就被覆盖掉了。
      猜一个默认的代价是把本来就该停发的类型重新放出去，而那是静默的资损方向。
    -->
    <a-modal v-model:open="enableModalVisible" title="启用分组" :confirmLoading="switchingId !== null" @ok="confirmEnable">
      <a-alert
        class="mb-4"
        type="info"
        show-icon
        message="勾选要启用的资产类型，没勾的会保持停用。"
        description="停用时组内配置被一起关掉了，系统不记得关之前哪些是开的 —— 这里需要你确认一次。"
      />
      <a-checkbox-group v-model:value="enableTypes" class="w-full">
        <div v-for="item in enableCandidates" :key="item.prizeType" class="mb-2">
          <a-checkbox :value="item.prizeType">{{ prizeTypeDesc(item.prizeType) }}（{{ item.prizeType }}）</a-checkbox>
        </div>
      </a-checkbox-group>
      <a-empty v-if="!enableCandidates.length" description="这个分组还没有配置任何资产类型" />
    </a-modal>
  </a-card>
</template>
<script setup>
  import { onMounted, reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { message, Modal } from 'ant-design-vue';
  import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue';
  import { SolvelaLoading } from '/@/components/framework/solvela-loading';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import { promotionGroupApi } from '/src/api/business/risk/promotion-group-api';
  import { GROUP_STATUS_ENUM, GROUP_STATUS_OPTIONS } from '/src/constants/business/risk/promotion-group-const';
  import { prizeTypeOf } from '/src/constants/business/prize/prize-config-const';
  import { limitPeriodOf, QUOTA_BASED_PRIZE_TYPES, reviewLevelOf } from '/src/constants/business/risk/promotion-config-const';

  const router = useRouter();

  const columns = [
    { title: '分组名称', dataIndex: 'groupName', ellipsis: true },
    { title: '分组编码', dataIndex: 'groupCode', width: 130 },
    { title: '配置完备度', dataIndex: 'coverage', width: 170 },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 110 },
    { title: '更新时间', dataIndex: 'updateTime', width: 170 },
    { title: '操作', dataIndex: 'action', width: 140 },
  ];

  const prizeTypeDesc = (type) => prizeTypeOf(type);

  const itemColumns = [
    { title: '资产类型', dataIndex: 'prizeType', width: 150 },
    { title: '预算 / 库存', dataIndex: 'quota', width: 180 },
    { title: '审核层级', dataIndex: 'reviewLevel', width: 110 },
    { title: '限制周期', dataIndex: 'limitPeriod' },
    { title: '发放开关', dataIndex: 'itemStatus', width: 130 },
  ];

  const UNLIMITED = -1;

  /**
   * 库存与预算二选一：券/实物看个数，积分/现金看金额，不适用的那一侧恒为 -1。
   * 展开行里只有一列位置，所以这里直接判出该显示哪一侧 ——
   * 把两个都摆出来，其中一个永远是「-1」，看的人只会疑惑。
   */
  function quotaText(item) {
    const quotaBased = QUOTA_BASED_PRIZE_TYPES.includes(item.prizeType);
    if (quotaBased) {
      return Number(item.totalQuota) === UNLIMITED ? '库存不限' : `库存 ${item.usedQuota ?? 0} / ${item.totalQuota}`;
    }
    return Number(item.totalAmount) === UNLIMITED ? '预算不限' : `预算 ${Number(item.usedAmount ?? 0)} / ${Number(item.totalAmount)}`;
  }

  // ---------------------------- 展开行 ----------------------------

  const expandedKeys = ref([]);
  const expandLoadingId = ref(null);
  const togglingItemId = ref(null);
  // groupId -> 组内配置列表。懒加载：展开哪一行才拉哪一行，
  // 列表页不为了一列摘要去 N+1（完备度那两个数字是 SQL 聚合出来的，不用这份数据）
  const itemCache = ref({});

  const itemsOf = (groupId) => itemCache.value[groupId] || [];

  async function onExpand(expanded, record) {
    if (!expanded) {
      expandedKeys.value = expandedKeys.value.filter((k) => k !== record.id);
      return;
    }
    expandedKeys.value = [...expandedKeys.value, record.id];
    // 已经拉过就不再打接口；开关拨动后会就地更新这份缓存
    if (itemCache.value[record.id]) {
      return;
    }
    await loadItems(record.id);
  }

  async function loadItems(groupId) {
    expandLoadingId.value = groupId;
    try {
      const detail = await promotionGroupApi.workbenchDetail(groupId);
      itemCache.value = { ...itemCache.value, [groupId]: detail?.itemList || [] };
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      expandLoadingId.value = null;
    }
  }

  /**
   * 组内单条配置的发放开关。
   *
   * 拨完要同时刷两处：展开行里那一条的状态，以及外层那一行的「配置完备度」——
   * 只刷一处的话，关掉一个类型后完备度还写着「启用 3 / 共 3」，
   * 而那一列正是运营用来判断「这个组现在到底在发什么」的。
   */
  async function onToggleItem(group, item, checked) {
    togglingItemId.value = item.id;
    try {
      await promotionGroupApi.updateItemStatus({
        id: item.id,
        status: checked ? GROUP_STATUS_ENUM.ENABLED.value : GROUP_STATUS_ENUM.DISABLED.value,
      });
      message.success('操作成功');
      await Promise.all([loadItems(group.id), queryData()]);
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      togglingItemId.value = null;
    }
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormDefault = { groupName: undefined, status: undefined, pageNum: 1, pageSize: 10 };
  const queryForm = reactive({ ...queryFormDefault });
  const tableData = ref([]);
  const total = ref(0);
  const tableLoading = ref(false);

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await promotionGroupApi.queryPage(queryForm);
      tableData.value = res.list || [];
      total.value = res.total || 0;
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      tableLoading.value = false;
    }
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  function onReset() {
    Object.assign(queryForm, queryFormDefault);
    queryData();
  }

  onMounted(queryData);

  // ---------------------------- 工作台 ----------------------------

  // 不传 id 即新建：工作台自己会向服务端要一个带默认值的空壳
  function goWorkbench(id) {
    router.push({ path: '/risk/promotion-group/workbench', query: id ? { id } : {} });
  }

  // ---------------------------- 主开关 ----------------------------

  const switchingId = ref(null);
  const enableModalVisible = ref(false);
  const enableCandidates = ref([]);
  const enableTypes = ref([]);
  let enablingRecord = null;

  /**
   * 两个方向的交互刻意不对称。
   *
   * 关：是熔断动作，出事时要能一次停掉，所以只确认「你知道这会连带停掉 N 条配置」。
   * 开：必须先把组内有哪些类型拉回来让人勾选 —— 系统不记得关之前哪些是开的。
   */
  async function onToggleStatus(record, checked) {
    if (!checked) {
      Modal.confirm({
        title: '停用分组',
        content: `会同时停用组内 ${record.typeCount || 0} 条优惠配置，这些类型将立即停止发放。确定吗？`,
        okText: '停用',
        okType: 'danger',
        onOk: () => submitStatus(record.id, GROUP_STATUS_ENUM.DISABLED.value, []),
      });
      return;
    }
    // 拉回组内已有的类型供勾选
    SolvelaLoading.show();
    try {
      const detail = await promotionGroupApi.workbenchDetail(record.id);
      enableCandidates.value = detail?.itemList || [];
      // 默认勾上库里已经是启用状态的那些；组是关的时候通常一个都没有，
      // 那就让人从零勾 —— 这正是这个弹窗存在的意义
      enableTypes.value = enableCandidates.value
        .filter((i) => i.status === GROUP_STATUS_ENUM.ENABLED.value)
        .map((i) => i.prizeType);
      enablingRecord = record;
      enableModalVisible.value = true;
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      SolvelaLoading.hide();
    }
  }

  async function confirmEnable() {
    if (enableCandidates.value.length && !enableTypes.value.length) {
      message.warning('至少选择一种要启用的资产类型');
      return;
    }
    await submitStatus(enablingRecord.id, GROUP_STATUS_ENUM.ENABLED.value, enableTypes.value);
    enableModalVisible.value = false;
  }

  async function submitStatus(id, status, enablePrizeTypes) {
    switchingId.value = id;
    try {
      await promotionGroupApi.updateStatus({ id, status, enablePrizeTypes });
      message.success('操作成功');
      // 主开关会连带改组内每一条配置，展开行的缓存必须作废重拉，
      // 否则展开还显示着「发放中」，而库里已经全停了
      delete itemCache.value[id];
      itemCache.value = { ...itemCache.value };
      if (expandedKeys.value.includes(id)) {
        await loadItems(id);
      }
      await queryData();
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      switchingId.value = null;
    }
  }

  // ---------------------------- 复制 ----------------------------

  const copyModalVisible = ref(false);
  const copying = ref(false);
  const copyName = ref('');
  const copySource = ref(null);

  function onCopy(record) {
    copySource.value = record;
    copyName.value = `${record.groupName || ''} 副本`;
    copyModalVisible.value = true;
  }

  async function confirmCopy() {
    if (!copyName.value?.trim()) {
      message.warning('请填写新分组名称');
      return;
    }
    copying.value = true;
    try {
      const newId = await promotionGroupApi.copy({ id: copySource.value.id, groupName: copyName.value.trim() });
      message.success('已复制');
      copyModalVisible.value = false;
      await queryData();
      // 复制出来的组多半还要改预算 —— 那是唯一不该照抄的业务字段，
      // 直接带到工作台，省掉「回列表再找一遍新建的那一行」
      if (newId) {
        goWorkbench(newId);
      }
    } catch (err) {
      solvelaSentry.captureError(err);
    } finally {
      copying.value = false;
    }
  }

  // ---------------------------- 解散 ----------------------------

  function onDelete(record) {
    Modal.confirm({
      title: '解散分组',
      content: '组内的优惠配置会保留下来（变回未分组的独立配置），只是不再归属这个分组。确定吗？',
      okText: '解散',
      okType: 'danger',
      async onOk() {
        SolvelaLoading.show();
        try {
          await promotionGroupApi.delete(record.id);
          message.success('已解散');
          await queryData();
        } catch (err) {
          solvelaSentry.captureError(err);
        } finally {
          SolvelaLoading.hide();
        }
      },
    });
  }
</script>
