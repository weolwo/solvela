package sa.admin.module.system.support;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import sa.admin.module.system.support.SupportBaseController;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartRequestUtil;
import sa.base.constant.SwaggerTagConst;
import sa.base.module.support.file.domain.form.FileMetaUpdateForm;
import sa.base.module.support.file.domain.form.FileQueryForm;
import sa.base.module.support.file.domain.vo.FileDetailVO;
import sa.base.module.support.file.domain.vo.FileVO;
import sa.base.module.support.file.service.FileAssetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件服务
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2019年10月11日 15:34:47
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@RestController
@Tag(name = SwaggerTagConst.Support.FILE)
public class AdminFileController extends SupportBaseController {

    @Resource
    private FileAssetService fileAssetService;

    @Operation(summary = "分页查询 @author 1024创新实验室-主任-卓大")
    @PostMapping("/file/queryPage")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<PageResult<FileVO>> queryPage(@RequestBody @Valid FileQueryForm queryForm) {
        return ResponseDTO.ok(fileAssetService.queryPage(queryForm));
    }

    /**
     * 文件详情。<b>带引用列表</b> —— 运营删图前看一眼「这张图正在 3 个活动里用」，
     * 比删完了才发现活动页变叉强得多。
     */
    @Operation(summary = "文件详情（含引用） @author 1024")
    @GetMapping("/file/detail/{fileId}")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<FileDetailVO> detail(@PathVariable Long fileId) {
        return ResponseDTO.ok(fileAssetService.detail(fileId));
    }

    /**
     * 改名 / 打标签。<b>只动展示层信息，storageKey 一个字符都不碰</b> ——
     * key 不可变是 CDN 能设 immutable、以及「换图不用刷缓存」的前提。
     */
    @Operation(summary = "修改文件名称与标签 @author 1024")
    @PostMapping("/file/updateMeta")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<String> updateMeta(@RequestBody @Valid FileMetaUpdateForm form) {
        fileAssetService.updateMeta(form.getFileId(), form.getOriginalName(), form.getTags(),
                SmartRequestUtil.getRequestUser());
        return ResponseDTO.ok();
    }

    /**
     * 删除文件。有任何业务在引用就拒绝。
     *
     * <p><b>删除不可恢复</b>：行标记为已删除的同时对象从存储里真删掉。
     * 之所以不只做软删 —— 孤儿清理任务还没落地，只软删的话存储只增不减，
     * 也没有任何机制会回来收它。
     */
    @Operation(summary = "删除文件（有引用则拒绝） @author 1024")
    @GetMapping("/file/delete/{fileId}")
    @SaCheckPermission("support:file:query")
    public ResponseDTO<String> delete(@PathVariable Long fileId) {
        fileAssetService.delete(fileId, SmartRequestUtil.getRequestUser());
        return ResponseDTO.ok();
    }

}
