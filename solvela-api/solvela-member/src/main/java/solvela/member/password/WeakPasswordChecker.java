package solvela.member.password;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 已泄露 / 极常见密码的本地表（方案 §9 P3 ③）。
 *
 * <h3>它和强度校验是两件事</h3>
 * {@code MemberPasswordPolicy} 回答「这个密码够不够复杂」，本类回答
 * 「这个密码是不是<b>人人都在用</b>」。两者不能互相替代：{@code Password1} 完全满足
 * 8 位 + 字母 + 数字，而它在每一份泄露库的前几十名里 —— 撞库脚本的第一发就是它。
 *
 * <h3>🔴 为什么<b>不</b>做成风控规则</h3>
 * 做成规则就意味着规则页上有一个能把它关掉的下拉框。而这件事没有「误伤」可言：
 * 被拒的用户只需要换一个密码，代价是十秒钟；而放过去的那个账号，
 * 从创建的那一刻起就已经在别人的字典里了。
 * 判据与「新绑联系方式冷静期」同一个（§12.6）：<b>不该有开关的东西就不要给它开关。</b>
 *
 * <h3>为什么是本地表，不是外部接口</h3>
 * 外部 k-匿名接口（只发哈希前 5 位）覆盖面大得多，但它把<b>注册与改密</b>这两条路
 * 挂在了一个第三方的可用性上 —— 而它挂了的时候，要么放行（等于没做），
 * 要么拒绝（等于注册不可用）。这一份表的覆盖面有限，但它的失败模式只有一种：
 * 打包时漏了文件，而那在启动时就炸（见下）。
 *
 * <h3>🔴 文件读不出来 = 启动失败</h3>
 * 与规则表的双向校验同一个取向：一个<b>静默空掉</b>的黑名单比没有这个功能更糟 ——
 * 页面上、代码里、文档里都写着「我们挡了泄露密码」，而实际一个都没挡。
 * 它是打进 jar 的资源，读不出来只可能是构建坏了，越早炸越好。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Component
public class WeakPasswordChecker {

    private static final String RESOURCE = "member/weak-passwords.txt";

    /**
     * 全部小写。<b>比对也转小写</b> —— 一个漏掉 {@code PASSWORD1} 的泄露密码表是摆设，
     * 而攻击者的字典本来就会把大小写变体都试一遍。
     */
    private final Set<String> leaked;

    public WeakPasswordChecker() {
        this.leaked = load();
        log.info("【密码】已泄露密码表加载完成，{} 条", leaked.size());
    }

    /**
     * 这个密码在表里吗。
     *
     * <p>O(1) 的集合查找，所以表长到几万行也不影响注册接口的耗时 ——
     * 这正是选本地表而不是外部接口的另一个理由。
     */
    public boolean leaked(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            // 空密码由强度校验负责说话（邮箱注册允许不设密码，那条路压根不该走到这里）
            return false;
        }
        return leaked.contains(rawPassword.toLowerCase(Locale.ROOT));
    }

    private static Set<String> load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        Set<String> values = new HashSet<>(512);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim().toLowerCase(Locale.ROOT);
                // # 开头是注释：这份表要能被人读、被人维护，所以它带着说明
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                values.add(value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("【密码】已泄露密码表读不出来，拒绝启动: " + RESOURCE
                    + " —— 一个静默空掉的黑名单比没有这个功能更糟", e);
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("【密码】已泄露密码表是空的，拒绝启动: " + RESOURCE);
        }
        return Set.copyOf(values);
    }
}
