/*
 *  ajax 请求
 *
 *  服务端契约（v3.72.0 起）：
 *    成功  2xx + 数据本身        —— 没有 { code, msg, data } 信封了
 *    无内容 204                  —— 删除、更新这类没有返回值的接口
 *    失败  4xx/5xx + { code, message, traceId }
 *
 *  于是本文件的职责收敛成三件：把 2xx 的 body 直接交给调用方、把非 2xx 归一成
 *  一个 ApiError 并弹提示、把下载这条二进制通道原样放行。
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-06 20:46:03
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */
import { message, Modal } from 'ant-design-vue';
import axios from 'axios';
import { localRead } from '/@/utils/local-util';
import { useUserStore } from '/@/store/modules/system/user';
import { decryptData, encryptData } from './encrypt';
import _ from 'lodash';
import LocalStorageKeyConst from '/@/constants/local-storage-key-const';

// token的消息头
const TOKEN_HEADER = 'Authorization';

/**
 * 响应体是密文的标记。
 *
 * 以前这件事写在响应信封的 dataType 字段里（10 = 加密）。信封是传输结构，
 * 里面不该有「这段内容要不要先解密」这种元信息 —— 那是 HTTP 头的活。
 */
const ENCRYPTED_HEADER = 'x-encrypted';

/**
 * 需要特殊处理的错误码。值是服务端 ErrorCode 的枚举常量名，不再是 30007 这类数字 ——
 * 告警和日志里看到 LOGIN_ACTIVE_TIMEOUT 不用查表就知道发生了什么。
 *
 * ⚠️ 这几个字符串是接口契约的一部分，改名要和后端一起改。
 */
const ERROR_CODE = {
  /** 未登录 / 令牌失效 / 令牌被吊销 */
  LOGIN_STATE_INVALID: 'LOGIN_STATE_INVALID',
  /** 账号被禁用或状态异常 */
  USER_STATUS_ERROR: 'USER_STATUS_ERROR',
  /** 等保：长时间未操作，需要重新登录 */
  LOGIN_ACTIVE_TIMEOUT: 'LOGIN_ACTIVE_TIMEOUT',
  /** 等保：连续登录失败已被锁定 */
  LOGIN_FAIL_LOCK: 'LOGIN_FAIL_LOCK',
  /** 等保：再错几次就要被锁了 */
  LOGIN_FAIL_WILL_LOCK: 'LOGIN_FAIL_WILL_LOCK',
};

// 创建axios对象
const solvelaAxios = axios.create({
  baseURL: import.meta.env.VITE_APP_API_URL,
});

// 退出系统
function logout() {
  useUserStore().logout();
  location.href = '/';
}

// ================================= 请求拦截器 =================================

solvelaAxios.interceptors.request.use(
  (config) => {
    // 在发送请求之前消息头加入token token
    const token = localRead(LocalStorageKeyConst.USER_TOKEN);
    if (token) {
      config.headers[TOKEN_HEADER] = 'Bearer ' + token;
    } else {
      delete config.headers[TOKEN_HEADER];
    }
    return config;
  },
  (error) => {
    // 对请求错误做些什么
    return Promise.reject(error);
  }
);

// ================================= 响应拦截器 =================================

solvelaAxios.interceptors.response.use(
  (response) => {
    // 下载走二进制通道：调用方要从响应头里取文件名，所以原样把整个 response 交出去
    if (response.config.responseType === 'blob') {
      return Promise.resolve(response);
    }

    // 加密接口：整个响应体就是那串密文
    if (response.headers[ENCRYPTED_HEADER] === '1') {
      const plain = decryptData(response.data);
      return Promise.resolve(plain ? JSON.parse(plain) : null);
    }

    // 2xx 一律是成功。204 时 axios 给的 data 是空串，调用方本来也不该读它
    return Promise.resolve(response.data);
  },
  async (error) => {
    const apiError = await normalizeError(error);
    notify(apiError);
    return Promise.reject(apiError);
  }
);

/**
 * 把 axios 的各种失败形态归一成一个对象：{ status, code, message, traceId }。
 *
 * 归一化本身就是价值：调用方 catch 到的东西以前有三种可能（axios 响应、Blob、Error），
 * 每个 catch 都得自己判一遍。现在只有一种。
 */
async function normalizeError(error) {
  // 压根没连上：断网、超时、跨域被拦
  if (!error.response) {
    let text = '网络发生错误';
    if (error.message && error.message.indexOf('timeout') !== -1) {
      text = '网络超时';
    } else if (error.message === 'Network Error') {
      text = '网络连接错误';
    }
    return { status: 0, code: 'NETWORK_ERROR', message: text, traceId: null };
  }

  const { status, data, config, headers } = error.response;

  // 下载接口失败时，错误体也是以 Blob 形态到达的（responseType 是请求时定的，
  // 服务端返 JSON 也改不了它）。不读出来的话，用户只会看到「网络发生错误」，
  // 而真正的原因就在这段 Blob 里
  let body = data;
  if (config && config.responseType === 'blob' && data instanceof Blob) {
    try {
      body = JSON.parse(await data.text());
    } catch (e) {
      body = null;
    }
  }

  return {
    status,
    code: body && body.code ? body.code : 'UNKNOWN',
    message: body && body.message ? body.message : '服务开小差了，请稍后再试',
    // 用户截图报障时，凭它一次定位到服务端日志
    traceId: (body && body.traceId) || (headers && headers['traceid']) || null,
  };
}

/**
 * 统一弹提示。
 *
 * 放在拦截器里而不是每个调用点，是因为「失败要告诉用户」这件事没有例外，
 * 而散在两百多个 catch 里一定会漏 —— 漏掉的表现是「点了没反应」。
 */
function notify(apiError) {
  const { code, message: text } = apiError;

  if (code === ERROR_CODE.LOGIN_STATE_INVALID || code === ERROR_CODE.USER_STATUS_ERROR) {
    message.destroy();
    message.error('您没有登录，请重新登录');
    setTimeout(logout, 300);
    return;
  }

  // 等保安全的登录提醒：这两句话里写着「还剩几次」「锁到什么时候」，
  // 必须用 Modal 强制看到，toast 一晃而过等于没说
  if (code === ERROR_CODE.LOGIN_FAIL_LOCK || code === ERROR_CODE.LOGIN_FAIL_WILL_LOCK) {
    Modal.error({ title: '重要提醒', content: text });
    return;
  }

  if (code === ERROR_CODE.LOGIN_ACTIVE_TIMEOUT) {
    Modal.error({ title: '重要提醒', content: text, onOk: logout });
    setTimeout(logout, 3000);
    return;
  }

  message.destroy();
  message.error(text);
}

// ================================= 对外提供请求方法：通用请求，get， post, 下载download等 =================================

/**
 * get请求
 */
export const getRequest = (url, params) => {
  return request({ url, method: 'get', params });
};

/**
 * 通用请求封装
 * @param config
 */
export const request = (config) => {
  return solvelaAxios.request(config);
};

/**
 * post请求
 */
export const postRequest = (url, data) => {
  return request({
    data,
    url,
    method: 'post',
  });
};

// ================================= 加密 =================================

/**
 * 加密请求参数的post请求
 */
export const postEncryptRequest = (url, data) => {
  return request({
    data: { encryptData: encryptData(data) },
    url,
    method: 'post',
  });
};

// ================================= 下载 =================================

export const postDownload = function (url, data) {
  request({
    method: 'post',
    url,
    data,
    responseType: 'blob',
  })
    .then((data) => {
      handleDownloadData(data);
    })
    .catch(() => {
      // 失败的提示已经由响应拦截器统一弹过了，这里不要再弹一次
    });
};

/**
 * 文件下载
 */
export const getDownload = function (url, params) {
  request({
    method: 'get',
    url,
    params,
    responseType: 'blob',
  })
    .then((data) => {
      handleDownloadData(data);
    })
    .catch(() => {
      // 同上
    });
};

/**
 * 取文件字节，交给调用方自己处理（图片预览转 object URL 等）。
 *
 * <p>为什么图片不能直接 `<img :src="fileUrl">`：下载接口要鉴权，而 `<img>` 发不出
 * Authorization 头（token 存在 localStorage 里，不是 cookie），浏览器拿回来的是一段
 * JSON 错误体，表现为一张打不开的图且控制台不报错。开发环境还多一层 ——
 * 前端 8081、后端 1024，相对路径会打到 dev server 上。
 */
export const getBlob = function (url, params) {
  return request({ method: 'get', url, params, responseType: 'blob' }).then((res) => res.data);
};

function handleDownloadData(response) {
  if (!response) {
    return;
  }

  // 获取返回类型
  let contentType = _.isUndefined(response.headers['content-type']) ? response.headers['Content-Type'] : response.headers['content-type'];

  // 构建下载数据
  let url = window.URL.createObjectURL(new Blob([response.data], { type: contentType }));
  let link = document.createElement('a');
  link.style.display = 'none';
  link.href = url;

  // 从消息头获取文件名
  let str = _.isUndefined(response.headers['content-disposition'])
    ? response.headers['Content-Disposition'].split(';')[1]
    : response.headers['content-disposition'].split(';')[1];

  let filename = _.isUndefined(str.split('fileName=')[1]) ? str.split('filename=')[1] : str.split('fileName=')[1];
  link.setAttribute('download', decodeURIComponent(filename));

  // 触发点击下载
  document.body.appendChild(link);
  link.click();

  // 下载完释放
  document.body.removeChild(link); // 下载完成移除元素
  window.URL.revokeObjectURL(url); // 释放掉blob对象
}
