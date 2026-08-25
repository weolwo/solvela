/*
 * 意见反馈
 *
 * @Author:    1024创新实验室：开云
 * @Date:      2022-09-03 21:56:31
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
import { getRequest } from '/@/lib/axios';

export const scriptengineAPI = {
  // 脚本文档查询
  queryScriptDoc: () => {
    return getRequest('/script/engine/view');
  },
};
