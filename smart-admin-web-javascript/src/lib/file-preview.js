/*
 * 文件预览地址
 *
 * 下载接口是要登录态的，而 `<img src>` 发不出 Authorization 头 —— 所以预览图不能直接用
 * 后端下发的 fileUrl，必须自己带着 token 取回字节再转成 object URL。
 *
 * 按 fileId 缓存：一个素材网格里同一张图会被列表、抽屉、选图器反复渲染，
 * 不缓存就是每次挂载都重新下载一遍整张图。缓存的是 Promise 而不是结果，
 * 否则同一张图并发挂载三次就会发出三个请求。
 *
 * @Copyright  1024创新实验室 （ https://1024lab.net ）
 */
import { getBlob } from '/@/lib/axios';

const previewCache = new Map();

/**
 * 取某个文件的可直接塞进 `<img src>` 的地址。
 *
 * @param fileId 文件ID
 * @returns Promise<string> object URL；取不到时抛出，由调用方降级成占位图
 */
export function getFilePreviewUrl(fileId) {
  if (!fileId) {
    return Promise.reject(new Error('fileId 不能为空'));
  }
  if (previewCache.has(fileId)) {
    return previewCache.get(fileId);
  }
  const task = getBlob(`/support/file/download/${fileId}`, { inline: true })
    .then((blob) => URL.createObjectURL(blob))
    .catch((e) => {
      // 失败的不要留在缓存里，否则一次网络抖动会让这张图在整个会话里都显示不出来
      previewCache.delete(fileId);
      throw e;
    });
  previewCache.set(fileId, task);
  return task;
}

/**
 * 丢弃某个文件的预览缓存。删除/换图后调用，避免继续拿旧字节。
 */
export function clearFilePreviewUrl(fileId) {
  const task = previewCache.get(fileId);
  if (!task) {
    return;
  }
  previewCache.delete(fileId);
  task.then((url) => URL.revokeObjectURL(url)).catch(() => {});
}
