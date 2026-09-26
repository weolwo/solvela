package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.DeviceTrust;
import solvela.member.api.MemberStepUpApi;
import solvela.member.api.StepUpCmd;
import solvela.member.api.StepUpResult;
import solvela.member.stepup.MemberStepUpService;

/**
 * {@link MemberStepUpApi} 的 HTTP 薄壳。做法同 {@link DeviceInternalController}：
 * {@code implements} 契约接口，路径与方法只在契约里定义一次。
 *
 * <p>⚠️ 本进程里有两个 {@link MemberStepUpApi} 类型的 bean（本类与 {@link MemberStepUpService}），
 * 进程内要注入就注入实现类。
 *
 * @Date 2026-09-26
 */
@RestController
@RequiredArgsConstructor
public class MemberStepUpInternalController implements MemberStepUpApi {

    private final MemberStepUpService stepUpService;

    @Override
    public DeviceTrust check(StepUpCmd cmd) {
        return stepUpService.check(cmd);
    }

    @Override
    public StepUpResult sendCode(StepUpCmd cmd) {
        return stepUpService.sendCode(cmd);
    }

    @Override
    public StepUpResult verify(StepUpCmd cmd) {
        return stepUpService.verify(cmd);
    }

    @Override
    public boolean revokeOthers(StepUpCmd cmd) {
        return stepUpService.revokeOthers(cmd);
    }
}
