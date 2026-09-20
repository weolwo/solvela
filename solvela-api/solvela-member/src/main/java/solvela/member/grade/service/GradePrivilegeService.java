package solvela.member.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.EnableStatusEnum;
import solvela.exception.BusinessException;
import solvela.member.GradePrivilege;
import solvela.member.MemberGrade;
import solvela.member.grade.dao.GradePrivilegeDao;
import solvela.member.grade.dao.MemberGradeDao;

import java.util.List;

/**
 * 等级权益的管理端维护。
 *
 * <h3>为什么它需要一个后台入口</h3>
 * 这张表原先是拿 SQL 灌进去的，运营想改一句权益文案得找人改数据库。
 * 而权益文案恰恰是<b>最常改的那一类内容</b> —— 它是用户「为什么要保级」的
 * 唯一答案，措辞本身就是运营动作。
 *
 * <h3>🔴 它仍然只是展示，加了后台也不会变</h3>
 * 真正的权益靠三样东西实现：{@code t_task_config.target_audience = GRADE_GTE_N}、
 * 脚本里的 {@code member_gradeAtLeast(n)}、商城价格模型。
 * 在这里新增一条「专享折扣」<b>不会</b>产生任何折扣 ——
 * 它只是让用户在等级页看见这四个字。
 *
 * <p>⚠️ 所以这个后台的危险不在于删错数据，而在于让人<b>误以为配了就生效了</b>。
 * 页面上必须把这句话说出来，见 member-grade-privilege-list.vue 顶部的说明。
 *
 * @author alaric
 * @date 2026-09-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradePrivilegeService {

    private final GradePrivilegeDao gradePrivilegeDao;
    private final MemberGradeDao memberGradeDao;

    /**
     * 全部权益（含停用），按等级升序、同级按 sort 倒序。
     *
     * <p>不分页：这张表的量级是「每档几条」，全量拿回去按等级分组展示，
     * 比翻页更接近运营看它的方式 —— 他要比较的是「白金比金卡多了什么」。
     */
    public List<GradePrivilege> listAll() {
        return gradePrivilegeDao.selectList(new LambdaQueryWrapper<GradePrivilege>()
                .orderByAsc(GradePrivilege::getGradeCode)
                .orderByDesc(GradePrivilege::getSort));
    }

    /**
     * 新增或修改一条。
     *
     * @param operator 操作人，落审计列
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(GradePrivilege form, String operator) {
        checkBasics(form);
        checkGradeExists(form.getGradeCode());
        checkCodeNotDuplicated(form);

        if (form.getId() == null) {
            form.setCreateBy(operator);
            gradePrivilegeDao.insert(form);
        } else {
            if (gradePrivilegeDao.selectById(form.getId()) == null) {
                throw new BusinessException("权益不存在，可能已被删除");
            }
            form.setUpdateBy(operator);
            gradePrivilegeDao.updateById(form);
        }
        log.info("【等级权益】已保存：grade={}, code={}, name={}, 操作人={}",
                form.getGradeCode(), form.getPrivilegeCode(), form.getPrivilegeName(), operator);
    }

    /** 启用 / 停用。停用后 C 端等级页立刻不再展示这一条 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, EnableStatusEnum status, String operator) {
        GradePrivilege existing = gradePrivilegeDao.selectById(id);
        if (existing == null) {
            throw new BusinessException("权益不存在");
        }
        GradePrivilege update = new GradePrivilege();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateBy(operator);
        gradePrivilegeDao.updateById(update);
        log.info("【等级权益】{}（等级 {}）改为 {}，操作人={}",
                existing.getPrivilegeName(), existing.getGradeCode(), status.getDesc(), operator);
    }

    /**
     * 物理删除。
     *
     * <h3>🔴 为什么这里必须有「删」，光有停用不够</h3>
     * 唯一键是 {@code (grade_code, privilege_code)}，而<b>停用的行照样占着这个键</b>。
     * 只给停用的话，运营停掉白金的 {@code BIRTHDAY_GIFT} 之后想重新加一条同编码的，
     * 会撞上唯一键 —— 而他在页面上看到的是一条「已停用」的行，
     * 根本不会想到那就是挡住他的东西。
     *
     * <p>删得起的原因是这张表<b>纯展示、无引用</b>：没有任何流水、订单、
     * 发放记录指向 privilege_id。真有引用的那天，这个方法要换成软删。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, String operator) {
        GradePrivilege existing = gradePrivilegeDao.selectById(id);
        if (existing == null) {
            // 幂等：重复点删除不该报错吓人一跳
            return;
        }
        gradePrivilegeDao.deleteById(id);
        log.info("【等级权益】已删除：grade={}, code={}, name={}, 操作人={}",
                existing.getGradeCode(), existing.getPrivilegeCode(),
                existing.getPrivilegeName(), operator);
    }

    private void checkBasics(GradePrivilege form) {
        if (form.getGradeCode() == null || form.getGradeCode() < 0) {
            throw new BusinessException("等级不能为空，且不能小于 0");
        }
        if (form.getSort() == null) {
            form.setSort(0);
        }
        if (form.getStatus() == null) {
            form.setStatus(EnableStatusEnum.ENABLED);
        }
    }

    /**
     * 🔴 等级必须是配置里真实存在的一档。
     *
     * <p>C 端等级页是拿 {@code enabledGrades()} 去 map 里取权益的
     * （{@code MemberGradeQueryService.privilegesByGrade}）。挂在一个不存在的
     * grade_code 上的权益<b>永远不会被任何人看到</b> —— 不报错、不告警，
     * 运营只会觉得「我明明配了」。这种错在页面上完全看不出来，只能在这里拦。
     */
    private void checkGradeExists(Integer gradeCode) {
        long exists = memberGradeDao.selectCount(new LambdaQueryWrapper<MemberGrade>()
                .eq(MemberGrade::getGradeCode, gradeCode));
        if (exists == 0) {
            throw new BusinessException("等级 " + gradeCode + " 不存在，请先在等级配置里加这一档");
        }
    }

    /**
     * 同一档里权益编码不能重复。
     *
     * <p>⚠️ 这里主动查一次而不是靠唯一键兜底：撞键抛出来的是
     * {@code DuplicateKeyException}，页面上显示的是一串 SQL 约束名，
     * 运营看不懂也不知道该改哪个字段。
     *
     * <p>并发下依然可能同时插入 —— 那时唯一键会拦住，这是对的：
     * 这里的查询负责<b>把话说清楚</b>，唯一键负责<b>保证正确</b>，两者不互相替代。
     */
    private void checkCodeNotDuplicated(GradePrivilege form) {
        GradePrivilege same = gradePrivilegeDao.selectOne(new LambdaQueryWrapper<GradePrivilege>()
                .eq(GradePrivilege::getGradeCode, form.getGradeCode())
                .eq(GradePrivilege::getPrivilegeCode, form.getPrivilegeCode())
                .last("LIMIT 1"));
        if (same != null && !same.getId().equals(form.getId())) {
            throw new BusinessException(String.format(
                    "等级 %d 下已经有编码为 %s 的权益（%s），同一档里编码不能重复",
                    form.getGradeCode(), form.getPrivilegeCode(), same.getPrivilegeName()));
        }
    }
}
