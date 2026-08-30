package solvela.app.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动控制类
 */
@Tag(name = "活动控制类")
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {
}
