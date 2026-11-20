import { describe, expect, it } from 'vitest';
import { decryptData, encryptData } from '../encrypt';

/**
 * 登录密码走的就是这条 SM4：前端加密，后端 ApiEncryptServiceSmImpl 解密。
 *
 * 🔴 GOLDEN_123456 抄自后端 ApiEncryptServiceTest —— 那是「前端实测能登录成功的那一串」。
 * 不能按当前实现重新生成一遍，那样就只是在自证：sm-crypto 升级后要是输出变了，
 * 前后端各自的测试都还绿着，而所有人都登录不了。
 * 两边的常量必须是同一个值。
 */
const GOLDEN_123456 = 'NzFlNWI2MjE5ODUyODAxOWM0OTI3ZDhkM2ZkNWY1ODY=';

describe('SM4 登录加密与后端对齐', () => {
  it('明文 123456 的密文与后端黄金向量一致', () => {
    expect(encryptData('123456')).toBe(GOLDEN_123456);
  });

  it('能解开后端黄金向量', () => {
    expect(decryptData(GOLDEN_123456)).toBe('123456');
  });

  it('各类明文往返一致', () => {
    for (const plain of ['a', 'P@ssw0rd!', '中文密码测试', '0123456789abcdefg']) {
      expect(decryptData(encryptData(plain))).toBe(plain);
    }
  });
});
