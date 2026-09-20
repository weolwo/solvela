package solvela.member.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.enums.EnableStatusEnum;
import solvela.member.MemberGrade;
import solvela.member.grade.dao.MemberGradeDao;

import java.util.List;

/**
 * 「这么多成长值对应哪一级」—— 判级的唯一实现。
 *
 * <h3>🔴 它是纯函数，没有副作用</h3>
 * {@link #gradeOf} 只依赖入参和等级配置。这样它<b>好测</b>，而判级正是
 * 整套机制里唯一一处「算错了会直接影响用户看到什么」的地方。
 *
 * <p>别把「要不要升级」「要不要留痕」塞进来 —— 那是调用方的事。
 * 判级函数一旦有了副作用，就再也没法用一张表驱动测试了。
 *
 * <h3>纯映射：等级 = f(成长值)</h3>
 * 不做「只降一级」这类修饰。规则是纯的，客服解释起来只有一句话 ——
 * 不用讲「你本来该掉两级但我们只降一级」。
 *
 * <p>⚠️ <b>唯一的例外是保级缓冲期</b>：那时等级取 {@code protect_target}，
 * 不取 {@code f(periodValue)}。那个例外不在本类里 —— 本类只回答映射，
 * 缓冲期是调用方（{@code MemberGrowthService}）的判断。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberGradeResolver {

    private final MemberGradeDao memberGradeDao;

    /**
     * 启用中的等级，<b>按门槛升序</b>。
     *
     * <p>刻意不加缓存：等级配置是运营偶尔改一次的东西，而判级发生在成长值入账时
     * （本身就带着一次写库）。加缓存就要面对「运营改完多久生效」，
     * 那个问题今天没有收益。
     */
    public List<MemberGrade> enabledGrades() {
        return memberGradeDao.selectList(new LambdaQueryWrapper<MemberGrade>()
                .eq(MemberGrade::getStatus, EnableStatusEnum.ENABLED)
                .orderByAsc(MemberGrade::getThreshold));
    }

    /**
     * 成长值 → 等级。
     *
     * <p>取<b>门槛不超过成长值的最高那一档</b>。
     *
     * <h3>🔴 配置缺 grade=0 那一行时返回 0，并告警</h3>
     * 那一行是新会员的落点。它被删掉时，一个没攒够任何成长值的人会判不出等级 ——
     * 抛异常会让整条入账链路挂掉（而入账是对的，配置才是错的），
     * 所以这里兜底成 0 并留痕：让它变成一条能被发现的警告，而不是一次线上故障。
     */
    public int gradeOf(long periodValue, List<MemberGrade> grades) {
        if (grades == null || grades.isEmpty()) {
            log.warn("【会员等级】一条启用中的等级配置都没有，全部按 0 级处理");
            return 0;
        }
        int matched = 0;
        boolean hasZero = false;
        for (MemberGrade grade : grades) {
            if (grade.getThreshold() != null && grade.getThreshold() == 0L) {
                hasZero = true;
            }
            if (grade.getThreshold() != null && periodValue >= grade.getThreshold()) {
                // grades 已按门槛升序，所以后面的覆盖前面的，最终落在最高那一档
                matched = grade.getGradeCode() == null ? matched : grade.getGradeCode();
            }
        }
        if (!hasZero) {
            log.warn("【会员等级】等级配置里没有 threshold=0 的那一档 —— "
                    + "新会员会判不出等级。请补一行 grade=0 的配置");
        }
        return matched;
    }

    /** 便利重载：自己去查配置。循环里不要用它 —— 那是 N 次查询 */
    public int gradeOf(long periodValue) {
        return gradeOf(periodValue, enabledGrades());
    }
}
