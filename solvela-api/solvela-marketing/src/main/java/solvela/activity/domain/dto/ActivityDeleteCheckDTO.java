package solvela.activity.domain.dto;

import java.util.List;

/**
 * 删除前检查结果。
 * <p>
 * 单独出一个检查接口而不是只在 delete 里拦：运营点删除时 Popconfirm 里就该显示
 * 「该活动下有 3 个奖池」，而不是点完确认才被拒。
 * 但 delete() 里同样会再拦一次 —— 前端校验只是防呆（铁律 2）。
 *
 * @param deletable 是否允许删除
 * @param reason    不可删时的人话说明，可直接展示；可删时为 null
 * @param refs      下游引用明细，供前端展开
 * @Author weolwo
 * @Date 2026-07-29
 */
public record ActivityDeleteCheckDTO(

        /** 是否允许删除 */
        boolean deletable,

        /** 不可删时的人话说明，可直接展示；可删时为 null */
        String reason,

        /** 下游引用明细，供前端展开 */
        List<ActivityRefItem> refs) {

    public static ActivityDeleteCheckDTO ok() {
        return new ActivityDeleteCheckDTO(true, null, List.of());
    }

    public static ActivityDeleteCheckDTO reject(String reason, List<ActivityRefItem> refs) {
        return new ActivityDeleteCheckDTO(false, reason, refs);
    }
}
