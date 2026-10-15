package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.MemberSecurityApi;
import solvela.member.api.SecurityPromptAnswerCmd;
import solvela.member.api.SecurityPromptView;
import solvela.member.api.StepUpCmd;
import solvela.member.risk.SecurityPromptService;

import java.util.List;

/**
 * {@link MemberSecurityApi} 的 HTTP 薄壳。做法同 {@link MemberStepUpInternalController}：
 * {@code implements} 契约接口，路径与方法只在契约里定义一次。
 *
 * <p>⚠️ 本进程里有两个 {@link MemberSecurityApi} 类型的 bean（本类与 {@link SecurityPromptService}），
 * 进程内要注入就注入实现类。
 *
 * @Date 2026-10-16
 */
@RestController
@RequiredArgsConstructor
public class MemberSecurityInternalController implements MemberSecurityApi {

    private final SecurityPromptService securityPromptService;

    @Override
    public List<SecurityPromptView> list(StepUpCmd cmd) {
        return securityPromptService.list(cmd);
    }

    @Override
    public boolean answer(SecurityPromptAnswerCmd cmd) {
        return securityPromptService.answer(cmd);
    }
}
