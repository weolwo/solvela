/*
 * 错误上报sentry
 *
 * @Author:    1024创新实验室-主任：卓大
 * @Date:      2022-09-06 20:49:28
 * @Wechat:    zhuda1024
 * @Email:     lab1024@163.com
 * @Copyright  1024创新实验室 （ https://1024lab.net ），Since 2012
 */

export const solvelaSentry = {
    /**
     * sentry 主动上报
     */
    captureError: (error) => {
        // 接口失败已经由 axios 拦截器归一成 { status, code, message, traceId } 并弹过提示了，
        // 不必再往控制台刷一遍红 —— 「参数填错了」不是需要有人排查的异常。
        //
        // 上一版的判据是「有 config/headers/request/status 就当作 axios 错误跳过」，
        // 那是在认 axios 的原始响应对象。归一之后那些字段没了，
        // 于是每一次普通的业务校验失败都会变成一条 console.error。
        if (error && typeof error === 'object' && error.code && 'status' in error) {
            return;
        }
        // Sentry.captureException(error);
        console.error(error);
    },
};
