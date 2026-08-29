package solvela.admin.enums;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import solvela.admin.constant.UserTypeEnum;
import solvela.admin.module.system.job.api.domain.SolvelaJobLogQueryForm;
import solvela.admin.module.system.job.api.domain.SolvelaJobLogVO;
import solvela.admin.module.system.job.constant.SolvelaJobExecuteStatusEnum;
import solvela.admin.module.system.job.repository.SolvelaJobLogDao;
import solvela.admin.module.system.job.repository.domain.SolvelaJobLogEntity;
import solvela.admin.module.system.loginlog.LoginLogDao;
import solvela.admin.module.system.loginlog.domain.LoginLogQueryForm;
import solvela.admin.module.system.loginlog.domain.LoginLogVO;
import solvela.enums.LoginLogResultEnum;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 桶各列改成枚举之后的真实验收（连数据库，只读）。
 *
 * <p>与 {@link DataTracerEnumMappingTest} 同样的三条路径，但覆盖数据量更大的几张表：
 * {@code t_solvela_job_log.status} 有 563 行、跨 6 个取值，
 * {@code t_login_log.user_type} 有 196 行 —— 空表证明不了任何事。
 *
 * <p>额外钉住 {@code @EnumSerialize}：job log 的 VO 上挂着它，
 * 输出必须仍然是 {@code {"status":2,"statusDesc":"成功"}}，
 * 而不是字段类型一换 desc 就悄悄变 null。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class BucketAEnumMappingTest {

    @Autowired
    private SolvelaJobLogDao jobLogDao;

    @Autowired
    private LoginLogDao loginLogDao;

    private final JsonMapper mapper = JsonMapper.builder().build();

    // ------------------------------------------------------------ t_solvela_job_log.status

    @Test
    @DisplayName("job log：实体的 status 能从 int 列装配成枚举")
    void jobLog实体装配() {
        List<SolvelaJobLogEntity> list = jobLogDao.selectList(null);
        assertFalse(list.isEmpty(), "t_solvela_job_log 没有数据，这条用例失去意义");
        for (SolvelaJobLogEntity e : list) {
            assertNotNull(e.getStatus(), "status 装配成了 null");
        }
    }

    @Test
    @DisplayName("job log：XML 的 resultType + 按枚举过滤，两条路一起验")
    void jobLog按状态查询() {
        List<SolvelaJobLogVO> all = jobLogDao.query(new Page<>(1, 1000), new SolvelaJobLogQueryForm());
        assertFalse(all.isEmpty(), "查不到数据，这条用例失去意义");
        for (SolvelaJobLogVO vo : all) {
            assertNotNull(vo.getStatus(), "VO.status 是 null —— resultType 没走到枚举 TypeHandler");
        }

        int sum = 0;
        for (SolvelaJobExecuteStatusEnum status : SolvelaJobExecuteStatusEnum.values()) {
            SolvelaJobLogQueryForm form = new SolvelaJobLogQueryForm();
            form.setStatus(status);
            List<SolvelaJobLogVO> hit = jobLogDao.query(new Page<>(1, 1000), form);
            for (SolvelaJobLogVO vo : hit) {
                assertTrue(status == vo.getStatus(),
                        "按 " + status + " 查询却查出了 " + vo.getStatus() + "，条件没有正确下推");
            }
            sum += hit.size();
        }
        assertEquals(all.size(), sum,
                "按各状态分别查询的总数与不带条件的总数对不上，说明有行的 status 落在枚举之外");
    }

    @Test
    @DisplayName("job log：@EnumSerialize 仍然输出 statusDesc，字段换成枚举也不能变")
    void jobLogVO序列化带desc() {
        List<SolvelaJobLogVO> list = jobLogDao.query(new Page<>(1, 1), new SolvelaJobLogQueryForm());
        assertFalse(list.isEmpty());

        SolvelaJobLogVO vo = list.get(0);
        String json = mapper.writeValueAsString(vo);

        assertTrue(json.contains("\"status\":" + vo.getStatus().getValue()),
                "status 应该序列化成数字，实际：" + json);
        assertTrue(json.contains("\"statusDesc\":\"" + vo.getStatus().getDesc() + "\""),
                "statusDesc 丢了或不对，实际：" + json);
    }

    // ------------------------------------------------------------ t_login_log.user_type

    @Test
    @DisplayName("login log：XML 里 #{query.userType} 要按 value 下推")
    void loginLog按用户类型查询() {
        List<LoginLogVO> all = loginLogDao.queryByPage(new Page<>(1, 500), new LoginLogQueryForm());
        assertFalse(all.isEmpty(), "t_login_log 没有数据，这条用例失去意义");
        for (LoginLogVO vo : all) {
            assertNotNull(vo.getUserType(), "VO.userType 是 null");
        }

        int sum = 0;
        for (UserTypeEnum userType : UserTypeEnum.values()) {
            LoginLogQueryForm form = new LoginLogQueryForm();
            form.setUserType(userType);
            List<LoginLogVO> hit = loginLogDao.queryByPage(new Page<>(1, 500), form);
            for (LoginLogVO vo : hit) {
                assertTrue(userType == vo.getUserType(),
                        "按 " + userType + " 查询却查出了 " + vo.getUserType());
            }
            sum += hit.size();
        }
        assertEquals(all.size(), sum, "分类型查询的总数与总量对不上");
    }

    @Test
    @DisplayName("login log：login_result 统一口径后仍然 0-成功 1-失败 2-退出")
    void loginResult口径() {
        List<LoginLogVO> all = loginLogDao.queryByPage(new Page<>(1, 500), new LoginLogQueryForm());
        assertFalse(all.isEmpty(), "t_login_log 没有数据，这条用例失去意义");

        for (LoginLogVO vo : all) {
            assertNotNull(vo.getLoginResult(), "loginResult 装配成了 null");
        }

        // 库里 0×170 / 1×15 / 2×11：成功一定是最多的那个。
        // 这条断言的意义在于：如果哪天有人把 0/1 调回去，成功与失败的比例会立刻反转过来。
        long success = all.stream().filter(v -> v.getLoginResult() == LoginLogResultEnum.LOGIN_SUCCESS).count();
        long fail = all.stream().filter(v -> v.getLoginResult() == LoginLogResultEnum.LOGIN_FAIL).count();
        assertTrue(success > fail,
                "登录成功(" + success + ") 居然不比失败(" + fail + ") 多 —— 0/1 口径多半被改反了");
    }
}
