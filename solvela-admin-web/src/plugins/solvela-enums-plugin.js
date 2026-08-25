/*
 * 枚举插件
 * 此插件为 1024创新实验室 自创的插件
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-06 20:51:03
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
import _ from 'lodash';
import { FLAG_NUMBER_ENUM } from '/@/constants/common-const';

export default {
  install: (app, solvelaEnumWrapper) => {
    const solvelaEnumPlugin = {};
    /**
     * 根据枚举值获取描述
     * @param {*} constantName 枚举名
     * @param {*} value          枚举值
     * @returns
     */
    solvelaEnumPlugin.getDescByValue = function (constantName, value) {
      if (!solvelaEnumWrapper || !Object.prototype.hasOwnProperty.call(solvelaEnumWrapper, constantName)) {
        console.error('无法找到变量名称：' + constantName + '，请检查 /constants/index.js 文件中是否引入此变量！');
        return '';
      }
      // boolean类型需要做特殊处理
      if (constantName === 'FLAG_NUMBER_ENUM' && !_.isUndefined(value) && typeof value === 'boolean') {
        value = value ? FLAG_NUMBER_ENUM.TRUE.value : FLAG_NUMBER_ENUM.FALSE.value;
      }

      let solvelaEnum = solvelaEnumWrapper[constantName];
      for (let item in solvelaEnum) {
        if (solvelaEnum[item].value === value) {
          return solvelaEnum[item].desc;
        }
      }
      return '';
    };
    /**
     * 根据枚举名获取对应的描述键值对[{value:desc}]
     * @param {*} constantName 枚举名
     * @returns
     */
    solvelaEnumPlugin.getValueDescList = function (constantName) {
      if (!Object.prototype.hasOwnProperty.call(solvelaEnumWrapper, constantName)) {
        console.error('无法找到变量名称：' + constantName + '，请检查 /constants/index.js 文件中是否引入此变量！');
        return [];
      }
      const result = [];
      let targetSolvelaEnum = solvelaEnumWrapper[constantName];
      for (let item in targetSolvelaEnum) {
        result.push(targetSolvelaEnum[item]);
      }
      return result;
    };

    /**
     * 根据枚举名获取对应的value描述键值对{value:desc}
     * @param {*} constantName 枚举名
     * @returns
     */
    solvelaEnumPlugin.getValueDesc = function (constantName) {
      if (!Object.prototype.hasOwnProperty.call(solvelaEnumWrapper, constantName)) {
        console.error('无法找到变量名称：' + constantName + '，请检查 /constants/index.js 文件中是否引入此变量！');
        return {};
      }
      let solvelaEnum = solvelaEnumWrapper[constantName];
      let result = {};
      for (let item in solvelaEnum) {
        let key = solvelaEnum[item].value + '';
        result[key] = solvelaEnum[item].desc;
      }
      return result;
    };

    app.config.globalProperties.$solvelaEnumPlugin = solvelaEnumPlugin;
    app.provide('solvelaEnumPlugin', solvelaEnumPlugin);
  },
};
