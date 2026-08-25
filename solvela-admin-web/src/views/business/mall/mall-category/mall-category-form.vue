<!--
  * 商城-商品分类 表单：新建（批量）与编辑（单条）共用同一张表
  *
  * 【为什么合并】
  * 早先拆成两个组件：新建是 900px 的表格式录入，编辑是 560px 的竖式表单 ——
  * 同一件事两套界面，运营点「编辑」会跳出一个和刚才完全不一样的东西。
  * 现在编辑态就是「只有一行、且那一行锁定行数」的同一张表：
  * 列的位置、图标控件、开关样式全都一致，只是少了「+ 添加一行 / + 子分类」。
  *
  * 【顶部那个「上级分类」在两种模式下含义是统一的】
  * 新建：本批都挂在它下面；编辑：这一条挂在它下面。
  * 正因为含义能统一，两种模式才共用得了一张表 —— 否则批量表就得给每行配一个上级下拉，
  * 而那正是「同批新建的父分类还没有 id，子分类挂不上去」的死结所在。
  *
  * 【同时录上下级：矛盾怎么解的】
  * 不给每行配上级，改成本批统一一个上级 + 行内可展开一层子行。
  * 提交时后端在同一个事务里先建父、拿到自增 id 再建子 —— 前端全程不需要知道那个 id。
  * 选了某个一级分类当上级时，「+ 子分类」消失：那已经是第二级，再挂就是三级。
  *
  * @Author:    weolwo
  * @Date:      2026-08-22 19:28:16
  * @Copyright  weolwo
-->
<template>
  <a-modal
    :title="isEdit ? '编辑分类' : '新建分类'"
    :width="900"
    :open="visibleFlag"
    :mask-closable="false"
    :destroy-on-close="true"
    @cancel="onClose"
  >
    <a-form layout="vertical">
      <a-form-item label="上级分类">
        <a-select v-model:value="parentId" :options="parentOptions" :disabled="parentDisabled" style="width: 260px" @change="onParentChange" />
        <div class="form-tip">{{ parentTip }}</div>
      </a-form-item>
    </a-form>

    <div class="batch-table">
      <div class="batch-head">
        <span class="col-name">分类名称</span>
        <span class="col-icon">图标</span>
        <span class="col-sort">排序</span>
        <span class="col-status">状态</span>
        <span v-if="!isEdit" class="col-action">操作</span>
      </div>

      <template v-for="(row, index) in rows" :key="row.rowKey">
        <div class="batch-row">
          <div class="col-name">
            <a-input v-model:value="row.categoryName" placeholder="如 数码3C" :maxlength="50" :status="rowError(row, rows) ? 'error' : ''" />
          </div>
          <div class="col-icon"><ImageSlot v-model:fileId="row.iconFileId" size="small" /></div>
          <div class="col-sort"><a-input-number v-model:value="row.sort" :min="0" :precision="0" style="width: 100%" /></div>
          <div class="col-status">
            <a-switch
              :checked="row.status === CATEGORY_STATUS_ENUM.ENABLED.value"
              size="small"
              @change="(c) => (row.status = c ? CATEGORY_STATUS_ENUM.ENABLED.value : CATEGORY_STATUS_ENUM.DISABLED.value)"
            />
          </div>
          <!-- 编辑态整列不渲染而不是留个空格子：空表头 + 空单元格看起来像是漏了字段。
               编辑态也没有「删除」—— 那是列表页的动作，且带删除守卫（有子分类/有商品不许删） -->
          <div v-if="!isEdit" class="col-action">
            <a-button v-if="allowChildren" type="link" size="small" :disabled="reachedLimit" @click="addChild(index)">+ 子分类</a-button>
            <a-button type="link" size="small" danger :disabled="rows.length === 1" @click="removeRow(index)">删除</a-button>
          </div>
        </div>
        <div v-if="rowError(row, rows)" class="row-error">{{ rowError(row, rows) }}</div>

        <div v-for="(child, childIndex) in row.children" :key="child.rowKey" class="batch-row is-child">
          <div class="col-name">
            <span class="child-mark">└</span>
            <a-input
              v-model:value="child.categoryName"
              placeholder="如 手机配件"
              :maxlength="50"
              :status="rowError(child, row.children) ? 'error' : ''"
            />
          </div>
          <div class="col-icon"><ImageSlot v-model:fileId="child.iconFileId" size="small" /></div>
          <div class="col-sort"><a-input-number v-model:value="child.sort" :min="0" :precision="0" style="width: 100%" /></div>
          <div class="col-status">
            <a-switch
              :checked="child.status === CATEGORY_STATUS_ENUM.ENABLED.value"
              size="small"
              @change="(c) => (child.status = c ? CATEGORY_STATUS_ENUM.ENABLED.value : CATEGORY_STATUS_ENUM.DISABLED.value)"
            />
          </div>
          <div class="col-action">
            <a-button type="link" size="small" danger @click="removeChild(index, childIndex)">删除</a-button>
          </div>
        </div>
        <div v-if="rowChildError(row)" class="row-error is-child">{{ rowChildError(row) }}</div>
      </template>
    </div>

    <div class="batch-foot">
      <template v-if="!isEdit">
        <a-button type="dashed" size="small" :disabled="reachedLimit" @click="addRow">+ 添加一行</a-button>
        <span class="form-tip">
          共 {{ totalCount }} 个分类，单次上限 {{ MAX_CATEGORY_BATCH }} 个{{ reachedLimit ? '（已达上限，请分批提交）' : '' }}
        </span>
      </template>
      <span v-else-if="editChildCount > 0" class="form-tip"> 它下面有 {{ editChildCount }} 个子分类；停用后这些子分类在 C 端也会一并不可见 </span>
    </div>

    <template #footer>
      <a-space>
        <a-button @click="onClose">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="!!blockingError" @click="onSubmit">
          保存{{ !isEdit && totalCount > 1 ? ` ${totalCount} 个` : '' }}
        </a-button>
      </a-space>
    </template>
  </a-modal>
</template>

<script setup>
  import { computed, ref } from 'vue';
  import { message } from 'ant-design-vue';
  import { mallCategoryApi } from '/@/api/business/mall/mall-category-api';
  import { CATEGORY_STATUS_ENUM, MAX_CATEGORY_BATCH, ROOT_PARENT_ID } from '/@/constants/business/mall/mall-category-const';
  import { solvelaSentry } from '/@/lib/solvela-sentry';
  import ImageSlot from '../components/image-slot.vue';

  const emits = defineEmits(['reloadList']);

  const visibleFlag = ref(false);
  const saving = ref(false);
  const parentId = ref(ROOT_PARENT_ID);
  const rows = ref([]);
  /** 编辑中的分类 id；为空即新建（批量）态 */
  const editingId = ref(null);
  /** 全量分类，由列表页传进来 —— 上级下拉和重名判断都从它算 */
  const allCategoryList = ref([]);

  const isEdit = computed(() => !!editingId.value);

  /** rowKey 只用于 v-for，不提交。用 index 当 key 的话删中间一行会让输入框内容串位 */
  let rowSeed = 0;

  function blankRow() {
    return {
      rowKey: `cat-${rowSeed++}`,
      categoryName: '',
      iconFileId: null,
      sort: 0,
      status: CATEGORY_STATUS_ENUM.ENABLED.value,
      children: [],
    };
  }

  // ---------------------------- 上级 ----------------------------

  /** 编辑中的分类有几个子分类。决定「能不能改成二级」和停用时的波及提示 */
  const editChildCount = computed(() => (editingId.value ? allCategoryList.value.filter((c) => c.parentId === editingId.value).length : 0));

  /** 挂在顶级下、且是新建态，才允许再录子分类 */
  const allowChildren = computed(() => !isEdit.value && !parentId.value);

  const parentOptions = computed(() => {
    const roots = allCategoryList.value.filter((c) => !c.parentId && c.id !== editingId.value).map((c) => ({ value: c.id, label: c.categoryName }));
    return [{ value: ROOT_PARENT_ID, label: '顶级分类' }, ...roots];
  });

  /**
   * 已经有子分类的分类不能改成二级：那样它的子分类就成了三级。
   * 服务端也会拒绝，这里把下拉直接禁掉，省得改完才被打回来。
   */
  const parentDisabled = computed(() => isEdit.value && editChildCount.value > 0);

  const parentTip = computed(() => {
    if (parentDisabled.value) {
      return `该分类下有 ${editChildCount.value} 个子分类，不能改成二级分类`;
    }
    if (isEdit.value) {
      return '分类最多两级：二级分类下面不能再挂分类';
    }
    return allowChildren.value
      ? '本批都建为一级分类，可以用「+ 子分类」在某一行下面直接录二级'
      : '本批都建为该分类的子级。分类最多两级，所以这里不能再录子分类';
  });

  // ---------------------------- 校验 ----------------------------

  const totalCount = computed(() => rows.value.reduce((sum, r) => sum + 1 + r.children.length, 0));
  const reachedLimit = computed(() => !isEdit.value && totalCount.value >= MAX_CATEGORY_BATCH);

  /** 同一个上级下已有的分类名。编辑态要排除自己，否则「不改名字只改排序」会被自己挡住 */
  const existingSiblingNames = computed(() => {
    const pid = parentId.value || ROOT_PARENT_ID;
    return allCategoryList.value.filter((c) => (c.parentId || ROOT_PARENT_ID) === pid && c.id !== editingId.value).map((c) => c.categoryName);
  });

  /**
   * 单行的错误。同级重名分两种来源：与库里已有的撞、与本次提交的其它行撞 ——
   * 提示要分开说，不然运营看着一个空白的表单被告知「已存在」会以为是系统抽风。
   */
  function rowError(row, siblings) {
    const name = (row.categoryName || '').trim();
    if (!name) {
      return '';
    }
    // 子行的兄弟是同一个父下的子行，它们的父是本次新建的，库里不可能有同名兄弟
    const isTopLevel = siblings === rows.value;
    if (isTopLevel && existingSiblingNames.value.includes(name)) {
      return `「${name}」在该上级下已经存在`;
    }
    const sameNameCount = siblings.filter((r) => (r.categoryName || '').trim() === name).length;
    if (sameNameCount > 1) {
      return `本次提交里有 ${sameNameCount} 个「${name}」`;
    }
    return '';
  }

  function rowChildError(row) {
    for (const child of row.children) {
      const err = rowError(child, row.children);
      if (err) {
        return err;
      }
    }
    return '';
  }

  const blockingError = computed(() => {
    const named = rows.value.filter((r) => (r.categoryName || '').trim());
    if (!named.length) {
      return '至少要填一个分类名称';
    }
    for (const row of rows.value) {
      if (rowError(row, rows.value) || rowChildError(row)) {
        return '有重名的分类';
      }
      // 父没填名字却挂了子分类 —— 后端建不出这棵树
      if (!(row.categoryName || '').trim() && row.children.length) {
        return '有子分类的行必须填上级名称';
      }
    }
    return '';
  });

  // ---------------------------- 行操作 ----------------------------

  function addRow() {
    if (reachedLimit.value) {
      return;
    }
    rows.value.push(blankRow());
  }

  function removeRow(index) {
    rows.value.splice(index, 1);
  }

  function addChild(index) {
    if (reachedLimit.value) {
      return;
    }
    rows.value[index].children.push(blankRow());
  }

  function removeChild(index, childIndex) {
    rows.value[index].children.splice(childIndex, 1);
  }

  /** 切上级时把已录的子分类收掉：挂到一级分类下之后它们就成了三级 */
  function onParentChange() {
    if (isEdit.value) {
      return;
    }
    if (!allowChildren.value) {
      const dropped = rows.value.reduce((sum, r) => sum + r.children.length, 0);
      if (dropped > 0) {
        rows.value.forEach((r) => (r.children = []));
        message.warning(`已移除 ${dropped} 个子分类：挂到一级分类下面之后，它们就成了三级`);
      }
    }
  }

  // ---------------------------- 打开 / 关闭 ----------------------------

  /**
   * 新建（批量）。只填一行时它就等价于单条新建，不多花任何操作。
   */
  function show(categoryList, presetParentId) {
    allCategoryList.value = categoryList || [];
    editingId.value = null;
    parentId.value = presetParentId || ROOT_PARENT_ID;
    rows.value = [blankRow()];
    visibleFlag.value = true;
  }

  /**
   * 编辑（单条）。同一张表，只是锁死一行、隐藏批量相关的按钮 ——
   * 运营看到的列位置、控件、开关和新建时完全一致。
   */
  function showEdit(record, categoryList) {
    allCategoryList.value = categoryList || [];
    editingId.value = record.id;
    parentId.value = record.parentId || ROOT_PARENT_ID;
    rows.value = [
      {
        rowKey: `cat-${rowSeed++}`,
        categoryName: record.categoryName,
        iconFileId: record.iconFileId ?? null,
        sort: record.sort ?? 0,
        status: record.status ?? CATEGORY_STATUS_ENUM.ENABLED.value,
        children: [],
      },
    ];
    visibleFlag.value = true;
  }

  function onClose() {
    rows.value = [];
    editingId.value = null;
    visibleFlag.value = false;
  }

  // ---------------------------- 提交 ----------------------------

  async function onSubmit() {
    if (blockingError.value) {
      message.warning(blockingError.value);
      return;
    }
    saving.value = true;
    try {
      if (isEdit.value) {
        const row = rows.value[0];
        await mallCategoryApi.save({
          id: editingId.value,
          parentId: parentId.value,
          categoryName: row.categoryName.trim(),
          iconFileId: row.iconFileId,
          sort: row.sort,
          status: row.status,
        });
        message.success('操作成功');
      } else {
        // 名字空着的行直接丢掉 —— 运营多点了一次「添加一行」不该变成一次报错
        const categoryList = rows.value
          .filter((r) => (r.categoryName || '').trim())
          .map((r) => ({
            categoryName: r.categoryName.trim(),
            iconFileId: r.iconFileId,
            sort: r.sort,
            status: r.status,
            children: r.children
              .filter((c) => (c.categoryName || '').trim())
              .map((c) => ({
                categoryName: c.categoryName.trim(),
                iconFileId: c.iconFileId,
                sort: c.sort,
                status: c.status,
              })),
          }));
        const res = await mallCategoryApi.batchSave({ parentId: parentId.value, categoryList });
        message.success(`已新建 ${res.data} 个分类`);
      }
      emits('reloadList');
      onClose();
    } catch (e) {
      solvelaSentry.captureError(e);
    } finally {
      saving.value = false;
    }
  }

  defineExpose({ show, showEdit });
</script>

<style scoped lang="less">
  .batch-table {
    border: 1px solid #f0f0f0;
    border-radius: 6px;
  }

  .batch-head,
  .batch-row {
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 8px 12px;
  }

  .batch-head {
    font-size: 13px;
    font-weight: 500;
    color: #64748b;
    background: #fafafa;
    border-bottom: 1px solid #f0f0f0;
  }

  .batch-row {
    border-top: 1px solid #f5f5f5;
  }

  .batch-row.is-child {
    background: #fcfdff;
  }

  .col-name {
    display: flex;
    flex: 1;
    gap: 6px;
    align-items: center;
    min-width: 0;
  }

  .child-mark {
    color: #cbd5e1;
  }

  .col-icon {
    width: 56px;
  }

  .col-sort {
    width: 90px;
  }

  .col-status {
    width: 56px;
  }

  .col-action {
    width: 150px;
    text-align: right;
  }

  .row-error {
    padding: 0 12px 6px;
    font-size: 12px;
    color: #ef4444;
  }

  .row-error.is-child {
    padding-left: 30px;
  }

  .batch-foot {
    display: flex;
    gap: 12px;
    align-items: center;
    min-height: 24px;
    margin-top: 12px;
  }

  .form-tip {
    font-size: 12px;
    color: #94a3b8;
  }
</style>
