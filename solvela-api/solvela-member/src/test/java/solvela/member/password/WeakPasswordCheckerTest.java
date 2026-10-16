package solvela.member.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.member.api.MemberPasswordPolicy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已泄露密码表（P3 ③）。
 *
 * <h3>🔴 这个类最重要的一条用例是最后那条</h3>
 * 「表里的每一条都要能过强度校验」—— 一条过不了强度校验的记录是<b>死条目</b>：
 * 它永远轮不到本类来挡（{@code MemberPasswordPolicy} 先把它拒了），
 * 于是这份表看起来很长，实际有效的只有一部分。
 * 而「看起来做了」正是这类功能最常见的失败方式。
 *
 * @Date 2026-10-16
 */
@DisplayName("已泄露密码表")
class WeakPasswordCheckerTest {

    private final WeakPasswordChecker checker = new WeakPasswordChecker();

    @Test
    @DisplayName("🔴 强度够、但人人都在用的那些要挡住")
    void 命中() {
        // 这几个都满足 8 位 + 字母 + 数字，所以强度校验一个都拦不住
        for (String password : new String[]{"Password1", "password123", "qwerty123", "Aa123456",
                "woaini1314", "admin@123", "abcd1234", "iloveyou1"}) {
            assertTrue(MemberPasswordPolicy.isValid(password), password + " 该是能过强度校验的");
            assertTrue(checker.leaked(password), password + " 该被已泄露表挡住");
        }
    }

    @Test
    @DisplayName("大小写不敏感 —— 漏掉 PASSWORD1 的泄露密码表是摆设")
    void 大小写() {
        assertTrue(checker.leaked("PASSWORD1"));
        assertTrue(checker.leaked("PaSsWoRd1"));
        assertTrue(checker.leaked("Passw0rd!"));
    }

    @Test
    @DisplayName("正常密码不该被挡 —— 这一侧错了，用户连注册都完不成")
    void 不命中() {
        for (String password : new String[]{"SvRegister2026", "Qing7Shan2Yue", "mu4Dan1Hua9Kai",
                "Tr0ubled0rse42", "x7Kp2mQw9Lz"}) {
            assertFalse(checker.leaked(password), password + " 不该被挡");
        }
    }

    @Test
    @DisplayName("空与 null 不由本类说话（邮箱注册允许不设密码）")
    void 空值() {
        assertFalse(checker.leaked(null));
        assertFalse(checker.leaked(""));
    }

    @Test
    @DisplayName("🔴 表里不该有【过不了强度校验】的条目 —— 那种条目永远轮不到本类来挡")
    void 没有死条目() throws Exception {
        java.util.List<String> dead = new java.util.ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                new org.springframework.core.io.ClassPathResource("member/weak-passwords.txt").getInputStream(),
                java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                if (!MemberPasswordPolicy.isValid(value)) {
                    dead.add(value);
                }
            }
        }
        assertTrue(dead.isEmpty(),
                "这些条目过不了强度校验，所以永远轮不到已泄露表来挡 —— 删掉它们，"
                        + "否则这份表看起来很长、实际有效的只有一部分: " + dead);
    }
}
