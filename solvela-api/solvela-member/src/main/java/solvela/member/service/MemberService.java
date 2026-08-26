package solvela.member.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.domain.PageResult;
import solvela.base.domain.RequestUser;
import solvela.base.domain.ResponseDTO;
import solvela.exception.BusinessException;
import solvela.base.dao.SolvelaPageUtil;
import solvela.member.constant.MemberConst;
import solvela.member.dao.MemberDao;
import solvela.member.Member;
import solvela.member.manager.MemberManager;
import solvela.member.domain.query.MemberQuery;
import solvela.member.domain.dto.MemberDTO;

import java.util.*;

/**
 * 会员标识解析 —— 关联键（{@code member_id}）与账号（{@code member_name}）之间的唯一翻译入口。
 *
 * <p><b>为什么需要它</b>：v3.71.0 之后，十张业务表的关联键是 {@code member_id}，
 * 而人（运营、客服、Excel 导入）说得出口的只有账号。两者的换算必须只有一个实现 ——
 * 散在各处的话，「查不到会员时怎么办」这件事就会出现好几种答案
 * （有的抛异常、有的落 null、有的塞个空串），而落 null 那种会在
 * {@code NOT NULL} 的快照列上表现为一句和会员毫无关系的报错。
 *
 * <p><b>两组方法的区别是「查不到时怎么办」，不是「查什么」</b>：
 * <ul>
 *   <li>{@code getXxx} 查不到返回 null —— 用于展示、用于「有就补上」的场景；</li>
 *   <li>{@code requireXxx} 查不到直接抛 {@link BusinessException} —— 用于写入路径。
 *       🔴 写入路径必须用 require：{@code member_id} 是 NOT NULL 的关联键，
 *       放一个库里根本不存在的会员号进去，等于凭空造出一个查不到主体的账/单，
 *       而且<b>当场不报错</b>，要等到有人去 join 会员表才发现，那时已经无从追溯是谁写的。</li>
 * </ul>
 *
 * <p><b>刻意不加缓存</b>。账号是可改的（DDL 注释：限频但允许改），缓存一上就要面对
 * 「改名后多久生效」这个问题；而当前调用点全部在<b>请求边界</b>上（每次抽奖/领号/上报事件
 * 各一次主键点查），本身就不是热点。真需要时再加，并且要连改名入口一起设计失效，
 * 不能只在这里挂个 {@code @Cacheable} 了事。
 *
 * @Date 2026-08-22
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberDao memberDao;
    private final MemberManager memberManager;


    /**
     * 分页查询
     */
    public PageResult<MemberDTO> queryPage(MemberQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<MemberDTO> list = memberDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }

    /**
     * 冻结 / 解冻。
     *
     * <p><b>只在「正常」与「冻结」之间切</b>：已注销是终态，注销会把 phone_hash 置 NULL
     * 以释放号码，那个动作不可逆 —— 从注销改回正常只会得到一个登录不了、也没法找回的账号。
     * 前端把已注销那行的开关禁掉了，这里是真正的约束。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> updateStatus(Long memberId, Integer status, RequestUser user) {
        if (status == null
                || (status != MemberConst.STATUS_NORMAL && status != MemberConst.STATUS_FROZEN)) {
            return ResponseDTO.userErrorParam("只能在「正常」与「冻结」之间切换");
        }
        Member member = memberManager.getById(memberId);
        if (member == null) {
            return ResponseDTO.userErrorParam("会员不存在");
        }
        if (MemberConst.STATUS_CANCELLED == nullToZero(member.getStatus())) {
            return ResponseDTO.userErrorParam("该会员已注销，注销是终态，不能改回其它状态");
        }
        Member update = new Member();
        update.setMemberId(memberId);
        update.setStatus(status);
        update.setUpdateBy(user == null ? null : user.getUserName());
        memberDao.updateById(update);
        return ResponseDTO.ok();
    }

    /**
     * 保存运营备注（列表里点一下就能改的那个）。
     *
     * <p>单独一个接口而不是复用一个「更新会员」的大接口：备注是运营唯一有权改的字段，
     * 开一个能改整行的口子，等于把昵称、状态、注册来源也一并暴露给了这个入口。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> updateRemark(Long memberId, String remark, RequestUser user) {
        Member member = memberManager.getById(memberId);
        if (member == null) {
            return ResponseDTO.userErrorParam("会员不存在");
        }
        String trimmed = StringUtils.trimToNull(remark);
        if (trimmed != null && trimmed.length() > MemberConst.MAX_REMARK_LENGTH) {
            return ResponseDTO.userErrorParam("备注最长 " + MemberConst.MAX_REMARK_LENGTH + " 字");
        }
        Member update = new Member();
        update.setMemberId(memberId);
        // 清空备注要真的写回 null，所以这一列在实体上挂了 updateStrategy = ALWAYS
        update.setRemark(trimmed);
        update.setUpdateBy(user == null ? null : user.getUserName());
        memberDao.updateById(update);
        return ResponseDTO.ok();
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 会员号 → 账号。查不到返回 null。
     */
    public String getMemberName(Long memberId) {
        return memberId == null ? null : memberDao.selectMemberNameById(memberId);
    }

    /**
     * 账号 → 会员号。查不到返回 null。
     */
    public Long getMemberId(String memberName) {
        return memberName == null || memberName.isBlank() ? null : memberDao.selectMemberIdByName(memberName);
    }

    /**
     * 会员号 → 账号，查不到直接拒绝。
     *
     * <p>写入路径取展示快照用这个：单据类的 {@code member_name} 目前仍是 NOT NULL，
     * 拿 null 去写会撞 MySQL 严格模式，报的是一句看不出根因的
     * {@code Field 'member_name' doesn't have a default value}。
     */
    public String requireMemberName(Long memberId) {
        if (memberId == null) {
            throw new BusinessException("会员号不能为空");
        }
        String memberName = memberDao.selectMemberNameById(memberId);
        if (memberName == null) {
            throw new BusinessException("会员不存在：" + memberId);
        }
        return memberName;
    }

    /**
     * 账号 → 会员号，查不到直接拒绝。
     */
    public Long requireMemberId(String memberName) {
        Long memberId = getMemberId(memberName);
        if (memberId == null) {
            throw new BusinessException("会员不存在：" + memberName);
        }
        return memberId;
    }

    /**
     * 校验会员号真实存在。
     *
     * <p>给「只需要关联键、不需要账号」的写入路径用（状态类两张表）——
     * 少查一列不是重点，重点是<b>不要因为不需要名字就跳过存在性校验</b>：
     * 关联键指向一个不存在的会员，是这次换键要消灭的那类静默错误本身。
     */
    public void requireExists(Long memberId) {
        requireMemberName(memberId);
    }

    /**
     * 批量：账号 → 会员号。查不到的账号<b>不出现在结果里</b>，由调用方决定怎么报错
     * （Excel 导入要报「第几行的哪个账号不存在」，逐个抛异常给不出行号）。
     */
    public Map<String, Long> mapMemberIdByNames(Collection<String> memberNames) {
        Map<String, Long> result = new LinkedHashMap<>();
        Set<String> distinct = distinctNonBlank(memberNames);
        if (distinct.isEmpty()) {
            return result;
        }
        memberDao.selectIdMapByNames(distinct).forEach((name, row) ->
                result.put(name, ((Number) row.get("memberId")).longValue()));
        return result;
    }

    private Set<String> distinctNonBlank(Collection<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        if (values != null) {
            values.stream().filter(v -> v != null && !v.isBlank()).forEach(distinct::add);
        }
        return distinct;
    }
}
