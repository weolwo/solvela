package solvela.app.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 盯住一条【没有任何症状】的安全退化：某个取 IP 的请求头，nginx 忘了处理。
 *
 * <h3>为什么这件事会悄悄发生</h3>
 * {@link ClientIp} 取 IP 的办法是<b>按顺序找第一个有值的头</b>。
 * 而这些头全部是客户端可以随手伪造的 —— 唯一让它们可信的，是入口的 nginx
 * 把每一个都【覆盖或删掉】（见 {@code deploy/nginx/snippets/proxy-upstream.conf}）。
 *
 * <p>于是这里有一条跨越两种语言、两个仓库目录的约束：
 * <b>Java 侧清单里的每一个头，nginx 侧都必须处理到</b>。
 * 往 Java 清单里加一个头而忘了改 nginx，后果是那个头又可以伪造了 ——
 * 而表现是<b>什么都没有</b>：请求成功，日志里是一个长得很正常的 IP，
 * 风控、归属地、登录记录全部被污染，没有任何报错指向这里。
 *
 * <p>这正是「改一处忘另一处」里最坏的一种：另一处不在同一个文件、
 * 不在同一个模块、甚至不是同一门语言。所以用一条测试把它钉住。
 *
 * <h3>为什么读文件而不是复述一份清单</h3>
 * 在测试里再写一遍期望的头名，那份清单同样会漂 —— 只是把问题挪了个地方。
 * 读真文件才能保证「测试绿」等于「线上那份配置是对的」。
 */
@DisplayName("取 IP 的请求头：nginx 与 Java 两份清单必须对齐")
class ClientIpHeaderCoverageTest {

    /**
     * 相对路径的基准是模块目录（surefire 的工作目录就是它），确定的。
     * 文件挪了位置这条测试会立刻报出来，而不是悄悄退化成永远通过。
     */
    private static final Path NGINX_SNIPPET =
            Path.of("../../deploy/nginx/snippets/proxy-upstream.conf");

    @Test
    @DisplayName("🔴 每一个会被读取的头，nginx 都得覆盖或删掉")
    void everyTrustedHeaderIsNeutralisedByNginx() throws IOException {
        assertThat(NGINX_SNIPPET)
                .as("找不到 nginx 片段（%s）—— 部署配置挪过位置的话这条测试要跟着改，"
                        + "否则它会从「验真配置」退化成「永远通过」",
                        NGINX_SNIPPET.toAbsolutePath().normalize())
                .exists();
        String nginx = Files.readString(NGINX_SNIPPET, StandardCharsets.UTF_8);

        for (String header : ClientIp.HEADERS) {
            assertThat(nginx)
                    .as("nginx 没有处理 %s。这个头是客户端可以随便写的，"
                            + "而 ClientIp 会读它 —— 现在任何访客都能把自己的 IP "
                            + "伪造成任意值，且不留痕迹。"
                            + "去 deploy/nginx/snippets/proxy-upstream.conf 里补一行："
                            + "覆盖成 $http_cf_connecting_ip，或者置成空串删掉它。", header)
                    .contains("proxy_set_header " + header + " ");
        }
    }

    @Test
    @DisplayName("XFF 与 X-Real-IP 必须被【覆盖】成 CF-Connecting-IP，而不是置空")
    void forwardedHeadersCarryTheCloudflareValue() throws IOException {
        String nginx = Files.readString(NGINX_SNIPPET, StandardCharsets.UTF_8);

        /*
         * 这两个头不能只是删掉：删了之后后端就只剩 remoteAddr，
         * 那是 cloudflared 容器的地址 —— 所有人的 IP 会变成同一个，
         * 登录日志和归属地直接失去意义。必须把真实值【填进去】。
         *
         * CF-Connecting-IP 是唯一可信的来源：它由 Cloudflare 边缘强制覆写，
         * 客户端发什么都会被丢掉。而 X-Forwarded-For 是 Cloudflare【追加】的，
         * 客户端自己写的那一段会排在前面 —— 恰好就是代码要取的那一段。
         */
        // 把连续空白压成一个空格再比，免得这条测试被 nginx 配置里的对齐空格绑死
        String flat = nginx.replaceAll("[ \t]+", " ");

        assertThat(flat)
                .as("X-Forwarded-For 必须被【填成】 CF-Connecting-IP，不能只是删掉")
                .contains("proxy_set_header X-Forwarded-For $http_cf_connecting_ip;");
        assertThat(flat)
                .as("X-Real-IP 必须被【填成】 CF-Connecting-IP，不能只是删掉")
                .contains("proxy_set_header X-Real-IP $http_cf_connecting_ip;");
    }
}
