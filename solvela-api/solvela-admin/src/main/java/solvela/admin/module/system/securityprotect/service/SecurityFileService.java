package solvela.admin.module.system.securityprotect.service;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import solvela.base.domain.ResponseDTO;
import solvela.base.module.file.FileMimeTypeUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 三级等保 文件 相关
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2024/08/22 19:25:59
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>，Since 2012
 */

@Service
@Slf4j
public class SecurityFileService {

    @Resource
    private Level3ProtectConfigService level3ProtectConfigService;

    // 定义白名单MIME类型
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList("application/json", "application/zip", "application/x-7z-compressed", "application/pdf", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-works", "text/csv", "audio/*", "video/*",
            // 图片类型 svg有安全隐患，所以不使用"image/*"
            "image/jpeg", "image/png", "image/gif", "image/bmp");

    /**
     * 检测文件安全类型
     */
    public ResponseDTO<String> checkFile(MultipartFile file) {

        // 检验文件大小
        if (level3ProtectConfigService.getMaxUploadFileSizeMb() > 0) {
            long maxSize = level3ProtectConfigService.getMaxUploadFileSizeMb() * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return ResponseDTO.userErrorParam("上传文件最大为:" + level3ProtectConfigService.getMaxUploadFileSizeMb() + " mb");
            }
        }

        // 文件类型安全检测
        if (level3ProtectConfigService.isFileDetectFlag()) {
            String fileType = FileMimeTypeUtil.detect(file);
            if (ALLOWED_MIME_TYPES.stream().noneMatch(allowedType -> FileMimeTypeUtil.matches(fileType, allowedType))) {
                return ResponseDTO.userErrorParam("禁止上传此文件类型");
            }
        }

        return ResponseDTO.ok();
    }

}
