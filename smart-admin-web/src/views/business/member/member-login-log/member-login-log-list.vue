<!--
  * 会员登录日志（append-only，按月分区）
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 20:58:39
  * @Copyright  weolwo
-->
<template>
    <!---------- 查询表单form begin ----------->
    <a-form class="smart-query-form">
        <a-row class="smart-query-form-row">
            <a-form-item label="会员号" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.memberId" placeholder="会员号" />
            </a-form-item>
            <a-form-item label="客户端IP" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.clientIp" placeholder="客户端IP（兼容IPv6，39位足够）" />
            </a-form-item>
            <a-form-item label="即登录时间" class="smart-query-form-item">
                <a-range-picker v-model:value="queryForm.createTime" :presets="defaultTimeRanges" style="width: 200px" @change="onChangeCreateTime" />
            </a-form-item>
            <a-form-item label="状态" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.status" placeholder="状态：0-失败, 1-成功, 2-登出。⚠️与tLoginLog.loginResult取值相反" />
            </a-form-item>
            <a-form-item label="设备端" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.deviceType" placeholder="设备端：APP/H5/WECHAT/PC" />
            </a-form-item>
            <a-form-item label="追踪ID" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.traceId" placeholder="全链路追踪ID，对应 LogTraceFilter 的 MDC traceId" />
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
            rowKey="id"
            bordered
            :loading="tableLoading"
            :pagination="false"
        >
            <template #bodyCell="{ text, record, column }">


                <template v-if="column.dataIndex === 'action'">
                    <div class="smart-table-operate">
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
    import { memberLoginLogApi } from '/@/api/business/member/member-login-log-api';
    import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
    import { smartSentry } from '/@/lib/smart-sentry';
    import TableOperator from '/@/components/support/table-operator/index.vue';
    import { defaultTimeRanges } from '/@/lib/default-time-ranges';

    // ---------------------------- 表格列 ----------------------------

    const columns = ref([
        {
            title: 'id',
            dataIndex: 'id',
            ellipsis: true,
        },
        {
            title: '会员号',
            dataIndex: 'memberId',
            ellipsis: true,
        },
        {
            title: '客户端IP',
            dataIndex: 'clientIp',
            ellipsis: true,
        },
        {
            title: 'IP归属地',
            dataIndex: 'ipRegion',
            ellipsis: true,
        },
        {
            title: '设备端',
            dataIndex: 'deviceType',
            ellipsis: true,
        },
        {
            title: '操作系统',
            dataIndex: 'osName',
            ellipsis: true,
        },
        {
            title: '浏览器',
            dataIndex: 'browserName',
            ellipsis: true,
        },
        {
            title: '状态',
            dataIndex: 'status',
            ellipsis: true,
        },
        {
            title: '备注',
            dataIndex: 'remark',
            ellipsis: true,
        },
        {
            title: 'traceId',
            dataIndex: 'traceId',
            ellipsis: true,
        },
        {
            title: '登录时间',
            dataIndex: 'createTime',
            ellipsis: true,
        },
    ]);

    // ---------------------------- 查询数据表单和方法 ----------------------------

    const queryFormState = {
        memberId: undefined, //会员号
        clientIp: undefined, //客户端IP（兼容IPv6，39位足够）
        createTime: [], //发生时间（即登录时间）
        createTimeBegin: undefined, //发生时间（即登录时间） 开始
        createTimeEnd: undefined, //发生时间（即登录时间） 结束
        status: undefined, //状态：0-失败, 1-成功, 2-登出。⚠️与tLoginLog.loginResult取值相反
        deviceType: undefined, //设备端：APP/H5/WECHAT/PC
        traceId: undefined, //全链路追踪ID，对应 LogTraceFilter 的 MDC traceId
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
            let queryResult = await memberLoginLogApi.queryPage(queryForm);
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
