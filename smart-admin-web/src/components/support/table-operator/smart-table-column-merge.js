/*
 *  表格列设置
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-08-26 23:45:51
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */

import _ from 'lodash';
import { DEFAULT_HIDDEN_COLUMNS } from '/@/constants/support/table-id-const';

/**
 * 判断某一列在「用户从未设置过」时是否应该显示。
 *
 * 优先级：页面显式声明 > 全局默认隐藏名单 > 显示。
 *
 * 为什么需要这个：代码生成器产出的列表页会把表里每个字段都列出来，
 * 包含租户id、创建人、更新人、更新时间这类审计字段 ——
 * 十几列铺开，运营真正要看的业务字段反而被挤到屏幕外。
 */
function resolveDefaultShowFlag(column) {
  // 页面显式写了 showFlag: false，以页面为准（它最清楚自己哪列是噪音）
  if (column.showFlag === false) {
    return false;
  }
  // 全局审计噪音列：默认不显示，但仍在「列设置」里可勾选回来
  if (DEFAULT_HIDDEN_COLUMNS.includes(column.dataIndex)) {
    return false;
  }
  return true;
}

/**
 * 将原视表格列和用户表格列进行合并、排序
 * @param {*} originalTableColumnArray
 * @param {*} userTableColumnArray
 */
export function mergeColumn(originalTableColumnArray, userTableColumnArray) {
  let saveFlag = false;
  if (!userTableColumnArray) {
    return originalTableColumnArray;
  }

  //第一步：将用户的列数据转为Map，以后备使用
  let userTableColumnMap = new Map();
  for (const item of userTableColumnArray) {
    userTableColumnMap.set(item.columnKey, item);
  }

  //第二步：以前端的table columns列为基础，将用户后端的数据填充到前端表格列里
  let fontColumnSort = 1;
  let newColumns = [];
  for (const fontColumn of originalTableColumnArray) {
    // 原始表格列的默认显示状态：页面声明 > 全局默认隐藏名单 > 显示
    // 注意这只是「用户没设置过」时的默认值，下面用户的设置会覆盖它
    fontColumn.columnKey = fontColumn.dataIndex;
    fontColumn.showFlag = resolveDefaultShowFlag(fontColumn);
    fontColumn.sort = fontColumnSort;

    // 如果用户存在此列，则覆盖 sort和width、showFlag字段
    let userColumn = userTableColumnMap.get(fontColumn.columnKey);
    if (userColumn) {
      fontColumn.sort = userColumn.sort;
      fontColumn.showFlag = userColumn.showFlag;
      if (fontColumn.dragAndDropFlag) {
        saveFlag = true;
        delete fontColumn.dragAndDropFlag;
      } else {
        if (userColumn.width) {
          fontColumn.width = userColumn.width;
        }
      }
    }
    newColumns.push(fontColumn);
    fontColumnSort++;
  }

  //第三步：前端列进行排序
  newColumns = _.sortBy(newColumns, (e) => e.sort);
  return {
    newColumns,
    saveFlag,
  };
}
