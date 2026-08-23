<!--
  * 会员列表
  *
  * 【后台能改的只有两样】状态（冻结/解冻，风控动作）和运营备注。
  * 没有「新建会员」：会员是 C 端自己注册出来的，后台凭空造一个会绕过注册链路的
  * 手机号校验、发号器、钱包初始化 —— 造出来的是一个数据不全、登录不了的壳。
  * 生成器留的那个「新建 / 编辑」按钮没有对应的后端接口，点了只会报错，已移除。
  *
  * 【手机号 / 邮箱不在列表里】它们在库里是密文（AES/SM4），列表页解密展示等于
  * 把全量手机号铺在一屏上，截个图就是一次泄露。要看具体某个人的联系方式，
  * 应该走单独的、有审计的详情入口。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:39:08
  * @Copyright  weolwo
-->
<template>
  <!---------- 查询表单form begin ----------->
  <a-form class="smart-query-form">
    <a-row class="smart-query-form-row">
      <a-form-item label="账号" class="smart-query-form-item">
        <a-input style="width: 160px" v-model:value="queryForm.memberName" placeholder="支持模糊搜索" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="昵称" class="smart-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.nickname" placeholder="支持模糊搜索" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="会员号" class="smart-query-form-item">
        <a-input style="width: 140px" v-model:value="queryForm.memberId" placeholder="10 位数字" allow-clear @press-enter="onSearch" />
      </a-form-item>
      <a-form-item label="性别" class="smart-query-form-item">
        <a-select style="width: 100px" v-model:value="queryForm.gender" :options="GENDER_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="状态" class="smart-query-form-item">
        <a-select style="width: 110px" v-model:value="queryForm.status" :options="MEMBER_STATUS_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="来源渠道" class="smart-query-form-item">
        <a-select style="width: 130px" v-model:value="queryForm.registerSource" :options="REGISTER_SOURCE_OPTIONS" placeholder="全部" allow-clear />
      </a-form-item>
      <a-form-item label="注册时间" class="smart-query-form-item">
        <a-range-picker v-model:value="createTime" :presets="defaultTimeRanges" style="width: 230px" @change="onChangeCreateTime" />
      </a-form-item>
      <a-form-item class="smart-query-form-item">
        <a-button type="primary" @click="onSearch">
          <template #icon>
            <SearchOutlined />
          </template>
          查询
        </a-button>
        <a-button @click="resetQuery" class="smart-margin-left10">
          <template #icon>
            <ReloadOutlined />
          </template>
          重置
        </a-button>
      </a-form-item>
    </a-row>
  </a-form>
  <!---------- 查询表单form end ----------->

  <a-card size="small" :bordered="false" :hoverable="true">
    <a-row class="smart-table-btn-block">
      <div class="smart-table-operate-block"></div>
      <div class="smart-table-setting-block">
        <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
      </div>
    </a-row>

    <a-table
      size="small"
      :scroll="{ x: 1400 }"
      :dataSource="tableData"
      :columns="columns"
      rowKey="memberId"
      bordered
      :loading="tableLoading"
      :pagination="false"
    >
      <template #bodyCell="{ text, record, column }">
        <template v-if="column.dataIndex === 'memberName'">
          <div class="member-cell">
            <FileThumb v-if="record.avatarFileId" :file-id="record.avatarFileId" :height="32" />
            <div class="member-cell-text">
              <div>{{ record.memberName }}</div>
              <div class="cell-sub">{{ record.nickname }}</div>
            </div>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'gender'">
          {{ descOf(GENDER_ENUM, text) }}
        </template>

        <template v-else-if="column.dataIndex === 'status'">
          <!--
            三值状态用一个开关表达不了，所以已注销单独渲染成标签：
            注销会把 phone_hash 置 NULL 以释放号码，那个动作不可逆 ——
            给它一个能拨回去的开关，等于提供了一个只会产出坏账号的操作。
          -->
          <a-tag v-if="text === MEMBER_STATUS_ENUM.CANCELLED.value" :color="MEMBER_STATUS_ENUM.CANCELLED.color">
            {{ MEMBER_STATUS_ENUM.CANCELLED.desc }}
          </a-tag>
          <a-switch
            v-else
            :checked="text === MEMBER_STATUS_ENUM.NORMAL.value"
            :loading="statusLoadingId === record.memberId"
            checked-children="正常"
            un-checked-children="冻结"
            @change="(checked) => onToggleStatus(record, checked)"
          />
        </template>

        <template v-else-if="column.dataIndex === 'remark'">
          <!-- 点一下就地编辑：备注是运营高频动作，为它开一个弹窗太重 -->
          <div v-if="editingRemarkId !== record.memberId" class="remark-cell" @click="startEditRemark(record)">
            <span v-if="record.remark" class="remark-text">{{ record.remark }}</span>
            <span v-else class="remark-empty">点击添加备注</span>
            <EditOutlined class="remark-edit-icon" />
          </div>
          <div v-else class="remark-editing">
            <a-input
              ref="remarkInputRef"
              v-model:value="remarkDraft"
              size="small"
              :maxlength="255"
              placeholder="留空即清除备注"
              @press-enter="saveRemark(record)"
              @keyup.esc="cancelEditRemark"
            />
            <a-button type="link" size="small" :loading="remarkSaving" @click="saveRemark(record)">保存</a-button>
            <a-button type="link" size="small" @click="cancelEditRemark">取消</a-button>
          </div>
        </template>

        <template v-else-if="column.dataIndex === 'registerSource'">
          {{ sourceLabel(text) }}
        </template>
      </template>
    </a-table>

    <div class="smart-query-table-page">
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
        :show-total="(total) => `共${total}条`"
      />
    </div>
  </a-card>
</template>

<script setup>
  import { nextTick, onMounted, reactive, ref } from 'vue';
  import { message, Modal } from 'ant-design-vue';
  import { EditOutlined } from '@ant-design/icons-vue';
  import { memberApi } from '/@/api/business/member/member-api';
  import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
  import { defaultTimeRanges } from '/@/lib/default-time-ranges';
  import {
    descOf,
    GENDER_ENUM,
    GENDER_OPTIONS,
    MEMBER_STATUS_ENUM,
    MEMBER_STATUS_OPTIONS,
    REGISTER_SOURCE_OPTIONS,
  } from '/@/constants/business/member/member-const';
  import { smartSentry } from '/@/lib/smart-sentry';
  import TableOperator from '/@/components/support/table-operator/index.vue';
  import FileThumb from '/@/components/support/file-thumb/index.vue';

  // ---------------------------- 表格列 ----------------------------

  const columns = ref([
    { title: '账号 / 昵称', dataIndex: 'memberName', width: 200 },
    { title: '会员号', dataIndex: 'memberId', width: 130 },
    { title: '性别', dataIndex: 'gender', width: 70 },
    { title: '生日', dataIndex: 'birthday', width: 110 },
    { title: '状态', dataIndex: 'status', width: 100 },
    { title: '来源渠道', dataIndex: 'registerSource', width: 100 },
    { title: '注册IP', dataIndex: 'registerIp', width: 130, ellipsis: true },
    { title: '邀请人', dataIndex: 'inviteId', width: 130 },
    { title: '运营备注', dataIndex: 'remark', width: 260 },
    { title: '注册时间', dataIndex: 'createTime', width: 170, ellipsis: true },
    { title: '更新人', dataIndex: 'updateBy', width: 110, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, ellipsis: true },
  ]);

  function sourceLabel(value) {
    const meta = REGISTER_SOURCE_OPTIONS.find((item) => item.value === value);
    // 字典是开放的（DDL 注释末尾有省略号），取不到就把原值显示出来，别渲染成空白
    return meta ? meta.label : value;
  }

  // ---------------------------- 查询 ----------------------------

  const queryFormState = {
    memberId: undefined,
    memberName: undefined,
    nickname: undefined,
    gender: undefined,
    status: undefined,
    registerSource: undefined,
    createTimeBegin: undefined,
    createTimeEnd: undefined,
    pageNum: 1,
    pageSize: 10,
  };
  const queryForm = reactive({ ...queryFormState });
  const createTime = ref([]);
  const tableLoading = ref(false);
  const tableData = ref([]);
  const total = ref(0);

  function onChangeCreateTime(dates, dateStrings) {
    queryForm.createTimeBegin = dateStrings[0] || undefined;
    queryForm.createTimeEnd = dateStrings[1] || undefined;
  }

  function resetQuery() {
    const pageSize = queryForm.pageSize;
    Object.assign(queryForm, queryFormState);
    queryForm.pageSize = pageSize;
    createTime.value = [];
    queryData();
  }

  function onSearch() {
    queryForm.pageNum = 1;
    queryData();
  }

  async function queryData() {
    tableLoading.value = true;
    try {
      const res = await memberApi.queryPage(queryForm);
      tableData.value = res.data.list;
      total.value = res.data.total;
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      tableLoading.value = false;
      // 换页/重查后仍停在编辑态的话，那一行已经不是刚才那个会员了
      cancelEditRemark();
    }
  }

  onMounted(queryData);

  // ---------------------------- 冻结 / 解冻 ----------------------------

  const statusLoadingId = ref(null);

  /**
   * 冻结要二次确认，解冻不用：冻结会让会员当场登录不了，是有对外影响的动作；
   * 解冻只是把这个影响撤销，误点的代价小得多。
   */
  function onToggleStatus(record, checked) {
    const status = checked ? MEMBER_STATUS_ENUM.NORMAL.value : MEMBER_STATUS_ENUM.FROZEN.value;
    if (!checked) {
      Modal.confirm({
        title: '确认冻结？',
        content: `冻结后会员「${record.memberName}」将无法登录，正在进行的兑换与提现也会被拦下。`,
        okText: '冻结',
        okType: 'danger',
        cancelText: '取消',
        onOk: () => requestStatus(record, status),
      });
      return;
    }
    requestStatus(record, status);
  }

  async function requestStatus(record, status) {
    statusLoadingId.value = record.memberId;
    try {
      await memberApi.updateStatus(record.memberId, status);
      message.success(status === MEMBER_STATUS_ENUM.NORMAL.value ? '已解冻' : '已冻结');
      queryData();
    } catch (e) {
      smartSentry.captureError(e);
      // 服务端拒绝时把开关拨回去 —— 重查即可
      queryData();
    } finally {
      statusLoadingId.value = null;
    }
  }

  // ---------------------------- 运营备注：行内编辑 ----------------------------

  const editingRemarkId = ref(null);
  const remarkDraft = ref('');
  const remarkSaving = ref(false);
  const remarkInputRef = ref();

  function startEditRemark(record) {
    editingRemarkId.value = record.memberId;
    remarkDraft.value = record.remark || '';
    // 进编辑态就把光标放进去，省一次点击
    nextTick(() => remarkInputRef.value && remarkInputRef.value.focus());
  }

  function cancelEditRemark() {
    editingRemarkId.value = null;
    remarkDraft.value = '';
  }

  async function saveRemark(record) {
    const next = (remarkDraft.value || '').trim();
    if (next === (record.remark || '')) {
      // 没改就别发请求：备注是高频点击，空提交会把 update_time 和更新人白白刷一遍
      cancelEditRemark();
      return;
    }
    remarkSaving.value = true;
    try {
      await memberApi.updateRemark({ memberId: record.memberId, remark: next });
      // 就地更新，不整表重查：重查会把滚动位置和展开状态一起重置掉
      record.remark = next;
      message.success(next ? '备注已保存' : '备注已清除');
      cancelEditRemark();
    } catch (e) {
      smartSentry.captureError(e);
    } finally {
      remarkSaving.value = false;
    }
  }
</script>

<style scoped lang="less">
  .member-cell {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .member-cell-text {
    min-width: 0;
  }

  .cell-sub {
    font-size: 12px;
    color: #bfbfbf;
  }

  .remark-cell {
    display: flex;
    gap: 6px;
    align-items: center;
    min-height: 24px;
    padding: 2px 4px;
    cursor: pointer;
    border-radius: 4px;
  }

  .remark-cell:hover {
    background: #f5f5f5;
  }

  .remark-cell:hover .remark-edit-icon {
    opacity: 1;
  }

  .remark-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .remark-empty {
    color: #cbd5e1;
  }

  .remark-edit-icon {
    flex-shrink: 0;
    color: #bfbfbf;
    opacity: 0;
    transition: opacity 0.15s;
  }

  .remark-editing {
    display: flex;
    gap: 2px;
    align-items: center;
  }
</style>
