<!--
  * 商城-兑换订单
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:35:46
  * @Copyright  weolwo
-->
<template>
    <!---------- 查询表单form begin ----------->
    <a-form class="smart-query-form">
        <a-row class="smart-query-form-row">
            <a-form-item label="订单号" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.orderNo" placeholder="订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键" />
            </a-form-item>
            <a-form-item label="会员账号" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.memberName" placeholder="下单时的会员账号【展示快照，非关联键，不要用于查询】" />
            </a-form-item>
            <a-form-item label="商品编码" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.commodityCode" placeholder="商品编码（跨环境稳定的那个）" />
            </a-form-item>
            <a-form-item label="商品类型快照" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.commodityType" placeholder="商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它" />
            </a-form-item>
            <a-form-item label="状态" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.status" placeholder="状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败" />
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
            rowKey="id"
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

        <MallOrderForm  ref="formRef" @reloadList="queryData"/>

    </a-card>
</template>
<script setup>
    import { reactive, ref, onMounted } from 'vue';
    import { message, Modal } from 'ant-design-vue';
    import { SmartLoading } from '/@/components/framework/smart-loading';
    import { mallOrderApi } from '/@/api/business/mall/mall-order-api';
    import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
    import { smartSentry } from '/@/lib/smart-sentry';
    import TableOperator from '/@/components/support/table-operator/index.vue';
    import MallOrderForm from './mall-order-form.vue';

    // ---------------------------- 表格列 ----------------------------

    const columns = ref([
        {
            title: 'id',
            dataIndex: 'id',
            ellipsis: true,
        },
        {
            title: '订单号',
            dataIndex: 'orderNo',
            ellipsis: true,
        },
        {
            title: '会员号：关联键',
            dataIndex: 'memberId',
            ellipsis: true,
        },
        {
            title: '会员账号',
            dataIndex: 'memberName',
            ellipsis: true,
        },
        {
            title: '商品id',
            dataIndex: 'commodityId',
            ellipsis: true,
        },
        {
            title: '商品编码',
            dataIndex: 'commodityCode',
            ellipsis: true,
        },
        {
            title: 'SKUid',
            dataIndex: 'skuId',
            ellipsis: true,
        },
        {
            title: 'SKU编码',
            dataIndex: 'skuCode',
            ellipsis: true,
        },
        {
            title: '商品类型快照',
            dataIndex: 'commodityType',
            ellipsis: true,
        },
        {
            title: '资产引用快照',
            dataIndex: 'assetRef',
            ellipsis: true,
        },
        {
            title: '商品名称快照',
            dataIndex: 'commodityName',
            ellipsis: true,
        },
        {
            title: '封面图',
            dataIndex: 'coverFileId',
            ellipsis: true,
        },
        {
            title: '规格快照',
            dataIndex: 'skuAttrs',
            ellipsis: true,
        },
        {
            title: '兑换件数',
            dataIndex: 'quantity',
            ellipsis: true,
        },
        {
            title: '单件积分单价快照',
            dataIndex: 'pointsPrice',
            ellipsis: true,
        },
        {
            title: '单件现金单价快照',
            dataIndex: 'cashPrice',
            ellipsis: true,
        },
        {
            title: '实付积分合计',
            dataIndex: 'payPoints',
            ellipsis: true,
        },
        {
            title: '实付现金合计',
            dataIndex: 'payCash',
            ellipsis: true,
        },
        {
            title: '收货地址',
            dataIndex: 'addressId',
            ellipsis: true,
        },
        {
            title: '状态',
            dataIndex: 'status',
            ellipsis: true,
        },
        {
            title: '待支付超时时间',
            dataIndex: 'expireTime',
            ellipsis: true,
        },
        {
            title: '支付/扣分完成时间',
            dataIndex: 'payTime',
            ellipsis: true,
        },
        {
            title: '履约完成时间',
            dataIndex: 'finishTime',
            ellipsis: true,
        },
        {
            title: '取消时间',
            dataIndex: 'cancelTime',
            ellipsis: true,
        },
        {
            title: '订单来源',
            dataIndex: 'sourceType',
            ellipsis: true,
        },
        {
            title: '来源单号',
            dataIndex: 'sourceBizId',
            ellipsis: true,
        },
        {
            title: '履约单引用',
            dataIndex: 'fulfillRefId',
            ellipsis: true,
        },
        {
            title: '履约失败原因',
            dataIndex: 'failReason',
            ellipsis: true,
        },
        {
            title: '备注',
            dataIndex: 'remark',
            ellipsis: true,
        },
        {
            title: '创建人',
            dataIndex: 'createBy',
            ellipsis: true,
        },
        {
            title: '创建时间',
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
        orderNo: undefined, //订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键
        memberName: undefined, //下单时的会员账号【展示快照，非关联键，不要用于查询】
        commodityCode: undefined, //商品编码（跨环境稳定的那个）
        commodityType: undefined, //商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它
        status: undefined, //状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败
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
            let queryResult = await mallOrderApi.queryPage(queryForm);
            tableData.value = queryResult.data.list;
            total.value = queryResult.data.total;
        } catch (e) {
            smartSentry.captureError(e);
        } finally {
            tableLoading.value = false;
        }
    }


    onMounted(queryData);

    // ---------------------------- 添加/修改 ----------------------------
    const formRef = ref();

    function showForm(data) {
        formRef.value.show(data);
    }

</script>
