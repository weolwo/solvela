package sa.admin.module.system.support;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import sa.base.common.controller.SupportBaseController;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.constant.SwaggerTagConst;
import sa.base.module.support.heartbeat.HeartBeatService;
import sa.base.module.support.heartbeat.domain.HeartBeatRecordQueryForm;
import sa.base.module.support.heartbeat.domain.HeartBeatRecordVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 心跳记录
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2022-01-09 20:57:24
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Tag(name = SwaggerTagConst.Support.HEART_BEAT)
@RestController
public class AdminHeartBeatController extends SupportBaseController {

    @Resource
    private HeartBeatService heartBeatService;

    @PostMapping("/heartBeat/query")
    @Operation(summary = "查询心跳记录 @author 卓大")
    public ResponseDTO<PageResult<HeartBeatRecordVO>> query(@RequestBody @Valid HeartBeatRecordQueryForm pageParam) {
        return heartBeatService.pageQuery(pageParam);
    }

}
