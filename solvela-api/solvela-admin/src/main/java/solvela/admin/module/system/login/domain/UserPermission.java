package solvela.admin.module.system.login.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 员工的权限快照：sa-token 鉴权时问的就是这两份清单。
 *
 * <p>住在 admin 而不是 base：C 端有自己的一套鉴权（{@code AuthorizationInterceptor}），
 * 从不经过 sa-token 的 {@code StpInterface}，这份结构只有管理端会产生、也只有管理端会消费。
 *
 * <p>是 record 而不是可变 bean：它是<b>缓存里的一张快照</b>。可变的快照意味着
 * 任何拿到它的调用方都能就地改掉别人缓存里的那份对象（同一个 JVM 内 Caffeine/本地缓存尤其如此），
 * 而这种越权修改是查不出来的。两个字段都用不可变列表包一层，从源头堵死。
 *
 * @param permissionList 接口权限点
 * @param roleList       角色编码
 */
public record UserPermission(List<String> permissionList, List<String> roleList) implements Serializable {

    public UserPermission {
        permissionList = permissionList == null ? List.of() : List.copyOf(permissionList);
        roleList = roleList == null ? List.of() : List.copyOf(roleList);
    }
}
