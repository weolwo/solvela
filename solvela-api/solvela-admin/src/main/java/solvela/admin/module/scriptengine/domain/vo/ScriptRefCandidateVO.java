package solvela.admin.module.scriptengine.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 挂载时可选的业务对象。
 *
 * <p>各挂载点的对象类型不同（活动 / 抽奖配置 / 任务模板 / 奖池），但挂载表只认一个字符串编码，
 * 所以对外统一成 {@code code + name + remark} 这一种形状 —— 前端不需要为每种类型写一份渲染。
 */
@Data
@AllArgsConstructor
public class ScriptRefCandidateVO {

    @Schema(description = "业务对象编码，就是写进 t_script_ref.ref_id 的值")
    private String code;

    @Schema(description = "名称，给人认的")
    private String name;

    @Schema(description = "补充信息，如所属活动、当前状态；可能为空")
    private String remark;
}
