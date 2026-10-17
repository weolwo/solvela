import Antd from 'ant-design-vue';
import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import SecurityIncidentList from '../security-incident-list.vue';

/**
 * 安全事件页（网关方案 §6.4、任务 T13 的验收）。
 *
 * <h3>🔴 命中片段与 UA 是攻击者可控的内容</h3>
 * 关键字拦截会把「命中的那一段」落库，网关会把 UA 落库 —— 两样都是请求方自己填的。
 * 这个页面要是用 v-html 渲染它们，一次探测请求（UA 里放一个 {@code <img src=x onerror=...>}）
 * 就能在运营打开页面的那一刻执行脚本：拿走后台会话、替运营点「解除处罚」。
 *
 * <p>所以这里断言的是<b>结构</b>：那两个格子里不能长出任何元素，原文要原样作为文字出现。
 * 用「有没有 img 元素」而不是「html 里有没有 onerror 字样」—— 被正确转义的文本里照样有那几个字母。
 */

const EVIL_UA = '<img src=x onerror="window.__pwned = true">Mozilla/5.0';
const EVIL_SAMPLE = '<script>window.__pwned = true</script>';

vi.mock('/@/api/business/security/security-api', () => ({
  securityApi: {
    queryIncidentPage: () =>
      Promise.resolve({
        list: [
          {
            id: 1,
            incidentType: 3,
            ruleCode: 'XSS_SCRIPT',
            action: 2,
            suppressReason: 0,
            penaltyLevel: null,
            clientIp: '198.51.100.7',
            deviceId: null,
            memberId: null,
            httpMethod: 'POST',
            requestPath: '/member/update',
            matchedField: 4,
            matchedParam: 'nickname',
            matchedSample: EVIL_SAMPLE,
            userAgent: EVIL_UA,
            penaltyUntil: '2026-10-18 12:00:00',
            traceId: 't-1',
            createTime: '2026-10-17 12:00:00',
          },
        ],
        total: 1,
      }),
  },
}));

vi.mock('/@/lib/solvela-sentry', () => ({ solvelaSentry: { captureError: vi.fn() } }));

const global = {
  plugins: [Antd],
  directives: { privilege: {} },
};

async function mountPage() {
  const w = mount(SecurityIncidentList, { global, attachTo: document.body });
  await new Promise((resolve) => setTimeout(resolve, 0));
  await w.vm.$nextTick();
  return w;
}

describe('安全事件页', () => {
  it('🔴 UA 与命中片段里的标签不会变成元素，也不会执行', async () => {
    window.__pwned = false;
    const w = await mountPage();

    const ua = w.find('[data-untrusted="ua"]');
    const sample = w.find('[data-untrusted="sample"]');
    expect(ua.exists()).toBe(true);
    expect(sample.exists()).toBe(true);
    expect(ua.findAll('*')).toHaveLength(0);
    expect(sample.findAll('*')).toHaveLength(0);
    expect(w.findAll('img')).toHaveLength(0);
    expect(w.findAll('td script')).toHaveLength(0);
    expect(window.__pwned).toBe(false);
    w.unmount();
  });

  it('原文按文字原样显示 —— 运营要看得出对方塞了什么', async () => {
    const w = await mountPage();

    expect(w.find('[data-untrusted="ua"]').text()).toBe(EVIL_UA);
    expect(w.find('[data-untrusted="sample"]').text()).toBe(EVIL_SAMPLE);
    w.unmount();
  });
});
