<!--
  * 商城-商品主表
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:29:59
  * @Copyright  weolwo
-->
<template>
    <!---------- 查询表单form begin ----------->
    <a-form class="smart-query-form">
        <a-row class="smart-query-form-row">
            <a-form-item label="商品编码" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.commodityCode" placeholder="商品编码：10位大写字母+数字，全局唯一，创建后不可改" />
            </a-form-item>
            <a-form-item label="商品类型" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.commodityType" placeholder="商品类型：PHYSICAL-实物(走tPhysicalDelivery), COUPON-优惠券(走tMemberCoupon), BALANCE-现金/红包(走钱包入账)" />
            </a-form-item>
            <a-form-item label="商品名称" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.commodityName" placeholder="商品名称" />
            </a-form-item>
            <a-form-item label="支付方式" class="smart-query-form-item">
                <a-input style="width: 200px" v-model:value="queryForm.payType" placeholder="支付方式：1-纯积分, 2-积分+现金" />
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
                <a-button @click="confirmBatchDelete" type="primary" danger size="small" :disabled="selectedRowKeyList.length == 0">
                    <template #icon>
                        <DeleteOutlined />
                    </template>
                    批量删除
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
            :row-selection="{ selectedRowKeys: selectedRowKeyList, onChange: onSelectChange }"
        >
            <template #bodyCell="{ text, record, column }">


                <template v-if="column.dataIndex === 'action'">
                    <div class="smart-table-operate">
                        <a-button @click="showForm(record)" type="link">编辑</a-button>
                        <a-button @click="onDelete(record)" danger type="link">删除</a-button>
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

        <MallCommodityForm  ref="formRef" @reloadList="queryData"/>

    </a-card>
</template>
<script setup>
    import { reactive, ref, onMounted } from 'vue';
    import { message, Modal } from 'ant-design-vue';
    import { SmartLoading } from '/@/components/framework/smart-loading';
    import { mallCommodityApi } from '/@/api/business/mall/mall-commodity-api';
    import { PAGE_SIZE_OPTIONS } from '/@/constants/common-const';
    import { smartSentry } from '/@/lib/smart-sentry';
    import TableOperator from '/@/components/support/table-operator/index.vue';
    import MallCommodityForm from './mall-commodity-form.vue';

    // ---------------------------- 表格列 ----------------------------

    const columns = ref([
        {
            title: 'id',
            dataIndex: 'id',
            ellipsis: true,
        },
        {
            title: '商品编码',
            dataIndex: 'commodityCode',
            ellipsis: true,
        },
        {
            title: '分类id',
            dataIndex: 'categoryId',
            ellipsis: true,
        },
        {
            title: '商品类型',
            dataIndex: 'commodityType',
            ellipsis: true,
        },
        {
            title: '资产引用',
            dataIndex: 'assetRef',
            ellipsis: true,
        },
        {
            title: '商品名称',
            dataIndex: 'commodityName',
            ellipsis: true,
        },
        {
            title: '副标题',
            dataIndex: 'commodityIntro',
            ellipsis: true,
        },
        {
            title: '封面主图 ',
            dataIndex: 'coverFileId',
            ellipsis: true,
        },
        {
            title: '图文详情',
            dataIndex: 'detailContent',
            ellipsis: true,
        },
        {
            title: '兑换须知',
            dataIndex: 'exchangeNotice',
            ellipsis: true,
        },
        {
            title: '支付方式',
            dataIndex: 'payType',
            ellipsis: true,
        },
        {
            title: '划线原价',
            dataIndex: 'originalPrice',
            ellipsis: true,
        },
        {
            title: '基准兑换积分',
            dataIndex: 'pointsPrice',
            ellipsis: true,
        },
        {
            title: '基准兑换现金',
            dataIndex: 'cashPrice',
            ellipsis: true,
        },
        {
            title: '限兑周期',
            dataIndex: 'limitPeriod',
            ellipsis: true,
        },
        {
            title: '周期内单会员限兑件数',
            dataIndex: 'limitCount',
            ellipsis: true,
        },
        {
            title: '上架开始时间',
            dataIndex: 'startTime',
            ellipsis: true,
        },
        {
            title: '上架结束时间',
            dataIndex: 'endTime',
            ellipsis: true,
        },
        {
            title: '状态',
            dataIndex: 'status',
            ellipsis: true,
        },
        {
            title: '是否首页推荐',
            dataIndex: 'isHome',
            ellipsis: true,
        },
        {
            title: '排序权重',
            dataIndex: 'sort',
            ellipsis: true,
        },
        {
            title: '累计兑件数',
            dataIndex: 'soldCount',
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
        commodityCode: undefined, //商品编码：10位大写字母+数字，全局唯一，创建后不可改
        commodityType: undefined, //商品类型：PHYSICAL-实物(走tPhysicalDelivery), COUPON-优惠券(走tMemberCoupon), BALANCE-现金/红包(走钱包入账)
        commodityName: undefined, //商品名称
        payType: undefined, //支付方式：1-纯积分, 2-积分+现金
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
            let queryResult = await mallCommodityApi.queryPage(queryForm);
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

    // ---------------------------- 单个删除 ----------------------------
    //确认删除
    function onDelete(data){
        Modal.confirm({
            title: '提示',
            content: '确定要删除选吗?',
            okText: '删除',
            okType: 'danger',
            onOk() {
                requestDelete(data);
            },
            cancelText: '取消',
            onCancel() {},
        });
    }

    //请求删除
    async function requestDelete(data){
        SmartLoading.show();
        try {
            let deleteForm = {
                goodsIdList: selectedRowKeyList.value,
            };
            await mallCommodityApi.delete(data.id);
            message.success('删除成功');
            queryData();
        } catch (e) {
            smartSentry.captureError(e);
        } finally {
            SmartLoading.hide();
        }
    }

    // ---------------------------- 批量删除 ----------------------------

    // 选择表格行
    const selectedRowKeyList = ref([]);

    function onSelectChange(selectedRowKeys) {
        selectedRowKeyList.value = selectedRowKeys;
    }

    // 批量删除
    function confirmBatchDelete() {
        Modal.confirm({
            title: '提示',
            content: '确定要批量删除这些数据吗?',
            okText: '删除',
            okType: 'danger',
            onOk() {
                requestBatchDelete();
            },
            cancelText: '取消',
            onCancel() {},
        });
    }

    //请求批量删除
    async function requestBatchDelete() {
        try {
            SmartLoading.show();
            await mallCommodityApi.batchDelete(selectedRowKeyList.value);
            message.success('删除成功');
            queryData();
        } catch (e) {
            smartSentry.captureError(e);
        } finally {
            SmartLoading.hide();
        }
    }
</script>
