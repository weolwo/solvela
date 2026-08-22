<!--
  * 会员主表
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
                <a-input style="width: 200px" v-model:value="queryForm.memberName" placeholder="账号" />
            </a-form-item>
            <a-form-item label="状态" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.status" placeholder="状态：1-正常, 2-冻结(风控/违规), 3-已注销" />
            </a-form-item>
            <a-form-item label="来源渠道" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.registerSource" placeholder="注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT..." />
            </a-form-item>
            <a-form-item label="邀请人" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.inviteId" placeholder="邀请人memberId：没有邀请体系时恒为空，留着比事后加表便宜" />
            </a-form-item>
            <a-form-item label="注册时间" class="smart-query-form-item">
                <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
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
        <!---------- 表格操作行 begin ----------->
        <a-row class="smart-table-btn-block">
            <div class="smart-table-operate-block">
                <a-button @click="showForm" type="primary" size="small">
                    <template #icon>
                        <PlusOutlined />
                    </template>
                    新建
                </a-button>
            </div>
            <div class="smart-table-setting-block">
                <TableOperator v-model="columns" :tableId="null" :refresh="queryData" />
            </div>
        </a-row>
        <!---------- 表格操作行 end ----------->

        <!---------- 表格 begin ----------->
        <a-table
            size="small"
            :scroll="{ y: 800 }"
            :dataSource="tableData"
            :columns="columns"
            rowKey="memberId"
            bordered
            :loading="tableLoading"
            :pagination="false"
        >
            <template #bodyCell="{ text, record, column }">


                <template v-if="column.dataIndex === 'action'">
                    <div class="smart-table-operate">
                        <a-button @click="showForm(record)" type="link">编辑</a-button>
                    </div>
                </template>
            </template>
        </a-table>
        <!---------- 表格 end ----------->

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
    import { reactive, ref, onMounted } from 'vue';
    import { message, Modal } from 'ant-design-vue';
    import { SmartLoading } from '/@/components/framework/smart-loading';
    import { memberApi } from '/@/api/business/member/member-api';
    import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
    import { smartSentry } from '/@/lib/smart-sentry';
    import TableOperator from '/@/components/support/table-operator/index.vue';
    import { defaultTimeRanges } from '/@/lib/default-time-ranges';

    // ---------------------------- 表格列 ----------------------------

    const columns = ref([
        {
            title: '会员号',
            dataIndex: 'memberId',
            ellipsis: true,
        },
        {
            title: '账号',
            dataIndex: 'memberName',
            ellipsis: true,
        },
        {
            title: '上次修改账号的时间',
            dataIndex: 'nameUpdateTime',
            ellipsis: true,
        },
        {
            title: '昵称',
            dataIndex: 'nickname',
            ellipsis: true,
        },
        {
            title: '头像',
            dataIndex: 'avatarFileId',
            ellipsis: true,
        },
        {
            title: '性别',
            dataIndex: 'gender',
            ellipsis: true,
        },
        {
            title: '生日',
            dataIndex: 'birthday',
            ellipsis: true,
        },
        {
            title: '状态：1-正常, 2-冻结(风控/违规), 3-已注销',
            dataIndex: 'status',
            ellipsis: true,
        },
        {
            title: '注册来源渠道',
            dataIndex: 'registerSource',
            ellipsis: true,
        },
        {
            title: '注册IP：批量注册的识别依据',
            dataIndex: 'registerIp',
            ellipsis: true,
        },
        {
            title: '邀请人',
            dataIndex: 'inviteId',
            ellipsis: true,
        },
        {
            title: '运营备注',
            dataIndex: 'remark',
            ellipsis: true,
        },
        {
            title: '创建人',
            dataIndex: 'createBy',
            ellipsis: true,
        },
        {
            title: '注册时间',
            dataIndex: 'createTime',
            ellipsis: true,
        },
        {
            title: '更新人',
            dataIndex: 'updateBy',
            ellipsis: true,
        },
        {
            title: '更新时间',
            dataIndex: 'updateTime',
            ellipsis: true,
        },
        {
            title: '操作',
            dataIndex: 'action',
            fixed: 'right',
            width: 90,
        },
    ]);

    // ---------------------------- 查询数据表单和方法 ----------------------------

    const queryFormState = {
        memberName: undefined, //账号：微信号风格，字母开头6~20位[A-Za-z][A-Za-z0-9_-]。全局唯一(大小写不敏感)，用户可改
        status: undefined, //状态：1-正常, 2-冻结(风控/违规), 3-已注销
        registerSource: undefined, //注册来源渠道：H5/APP/WECHAT/INVITE/IMPORT...
        inviteId: undefined, //邀请人memberId：没有邀请体系时恒为空，留着比事后加表便宜
        createTime: [], //注册时间
        createTimeBegin: undefined, //注册时间 开始
        createTimeEnd: undefined, //注册时间 结束
        pageNum: 1,
        pageSize: 10,
    };
    // 查询表单form
    const queryForm = reactive({ ...queryFormState });
    // 表格加载loading
    const tableLoading = ref(false);
    // 表格数据
    const tableData = ref([]);
    // 总数
    const total = ref(0);

    // 重置查询条件
    function resetQuery() {
        let pageSize = queryForm.pageSize;
        Object.assign(queryForm, queryFormState);
        queryForm.pageSize = pageSize;
        queryData();
    }

    // 搜索
    function onSearch(){
      queryForm.pageNum = 1;
      queryData();
    }

    // 查询数据
    async function queryData() {
        tableLoading.value = true;
        try {
            let queryResult = await memberApi.queryPage(queryForm);
            tableData.value = queryResult.data.list;
            total.value = queryResult.data.total;
        } catch (e) {
            smartSentry.captureError(e);
        } finally {
            tableLoading.value = false;
        }
    }

    function onChangeCreateTime(dates, dateStrings){
        queryForm.createTimeBegin = dateStrings[0];
        queryForm.createTimeEnd = dateStrings[1];
    }


    onMounted(queryData);

</script>
