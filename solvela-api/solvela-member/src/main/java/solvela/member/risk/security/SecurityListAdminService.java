package solvela.member.risk.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.SecurityListDimensionEnum;
import solvela.enums.SecurityListStatusEnum;
import solvela.enums.SecurityListTypeEnum;
import solvela.member.SecurityList;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.dao.SecurityListDao;
import solvela.member.risk.domain.SecurityListAddCmd;
import solvela.member.risk.domain.query.SecurityListQuery;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.security.IpKeys;
import solvela.security.IpRange;
import solvela.security.SecurityKeys;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 后台「安全名单」页：人工加、撤、查 {@code t_security_list}。自动条目走 {@link SecurityListWriter}。
 *
 * <h3>撤销不删行</h3>
 * 「谁加的、谁解的、为什么」都要查得到。删行的话，下一个人看到的是
 * 「这个 IP 从来没被封过」—— 而他正要判断「上次是不是误封」。
 *
 * <h3>改完立即生效</h3>
 * 登录风控读进程内的名单缓存（{@link RiskListCache}，当场重载）；网关读规则快照（当场重发）。
 * 撤销 IP 黑名单时一并删掉网关的处罚键（网关方案 §6.4：后台解封 = 撤销条目 + 删除处罚键）。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityListAdminService {

    private final SecurityListDao listDao;

    private final RiskListCache listCache;

    private final GatewaySnapshotPublisher snapshotPublisher;

    private final StringRedisTemplate redis;

    /** 新增时默认的有效期。IP 会被回收重新分配 —— 永久黑名单迟早误伤一个无辜的新住户 */
    private static final Duration DEFAULT_LIST_TTL = Duration.ofDays(30);

    /** 身份摘要是 {@code PiiHasher} 的输出：64 位 hex */
    private static final int IDENTITY_HASH_HEX_LENGTH = 64;

    /**
     * 名单分页。不传状态时<b>只看有效的</b> —— 客服日常要的是「现在谁被封着」，
     * 而历史（含已撤销）是另一个问题，得显式问。
     */
    public PageResult<SecurityList> queryListPage(SecurityListQuery query) {
        Page<SecurityList> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SecurityList> wrapper = new LambdaQueryWrapper<SecurityList>()
                .eq(query.getListType() != null, SecurityList::getListType, query.getListType())
                .eq(query.getDimension() != null, SecurityList::getDimension, query.getDimension())
                .eq(SolvelaStringUtil.isNotBlank(query.getListValue()),
                        SecurityList::getListValue, query.getListValue())
                .eq(SecurityList::getStatus,
                        query.getStatus() == null ? SecurityListStatusEnum.VALID : query.getStatus())
                .orderByDesc(SecurityList::getId);
        // 单表查询走 BaseMapper.selectPage，所以这里自己造 Page（SolvelaPageUtil 那个返回 Page<?>，
        // 泛型对不上 selectPage 的签名），再用同一个工具把结果裹成 PageResult
        listDao.selectPage(page, wrapper);
        return SolvelaPageUtil.convert2PageResult(page, page.getRecords());
    }

    /**
     * 新增一条。
     *
     * <h3>🔴 校验在这里，不在表单上</h3>
     * 「灰名单必须有分、黑白名单必须没分」「会员白名单必须填到期」这类规则是
     * <b>名单自己的语义</b>，不是某个页面的输入约束。放在表单注解上的话，
     * 下一个调用方（客服工具、脚本）绕过去就能写出一条读出来会炸的名单。
     *
     * <h3>🔴 值要当场解析一遍</h3>
     * IP / CIDR 写错的表现是「加了黑名单但永远不命中」—— 而那在页面上看不出来，
     * 列表里那一行好端端地躺着。所以这里拿缓存用的同一个解析器
     * （{@link IpRange}）先试一次，解不出来直接拒绝。
     *
     * @return 新增后的那一行
     */
    public SecurityList addList(SecurityListAddCmd cmd) {
        if (cmd.getListType() == null || cmd.getDimension() == null) {
            throw new IllegalArgumentException("名单类型与维度必填");
        }
        if (SolvelaStringUtil.isBlank(cmd.getReason())) {
            throw new IllegalArgumentException("必须写明为什么进名单 —— 这句话是给下一个看到它的人的");
        }
        String value = normalizeListValue(cmd.getDimension(), cmd.getListValue());
        validateScore(cmd.getListType(), cmd.getScore());
        LocalDateTime expireTime = resolveExpireTime(cmd);

        if (cmd.getDimension() == SecurityListDimensionEnum.MEMBER
                && cmd.getListType() == SecurityListTypeEnum.BLACK) {
            /*
             * 🔴 会员没有黑名单。那一份由 t_member.status（冻结）管 ——
             * 名单里再放一份就有了两个答案，而「这个人到底能不能用」只该有一个。
             *
             * 灰名单 2026-10-17 放开（网关方案 §5.5）：可信设备按新设备算、资产出口每次都要二次验证、
             * 网关账号维度阈值减半。它的分不参与打分（登录风控只按 IP 灰名单加分），但照样必填 ——
             * 名单缓存按「有分」认灰名单，与 IP 那一份同一个判据。
             */
            throw new IllegalArgumentException("会员维度没有黑名单：封号请用会员状态（冻结）");
        }

        SecurityList row = toRow(cmd, value, expireTime);
        listDao.insert(row);
        listCache.reload();
        snapshotPublisher.publishQuietly("新增名单");
        log.info("【安全名单】新增, 类型: {}, 维度: {}, 值: {}, 到期: {}, 操作人: {}",
                cmd.getListType(), cmd.getDimension(), value, expireTime, cmd.getOperator());
        return row;
    }

    /**
     * 撤销。<b>不删行</b>，记下撤销人、原因、时间。
     *
     * <p>🔴 撤销的是 IP 黑名单时，同一个操作里把网关的 IP 处罚键也删掉：自动黑名单背后都有一个 24 小时的处罚键，
     * 只撤条目的话，后台显示「已解封」，人还要被挡到处罚到期 —— 「后台说的和实际不一样」。
     * 网关各实例的进程内缓存最多再挡几秒。
     *
     * @return false 表示这一行不存在或本来就已撤销（幂等，连点两次不会覆盖第一次记的人）
     */
    public boolean revokeList(Long id, String operator, String reason) {
        if (id == null || SolvelaStringUtil.isBlank(reason)) {
            throw new IllegalArgumentException("撤销必须写原因 —— 「上次为什么解的」和「为什么加的」一样重要");
        }
        SecurityList row = listDao.selectById(id);
        if (row == null || row.getStatus() == SecurityListStatusEnum.REVOKED) {
            return false;
        }
        SecurityList update = new SecurityList();
        update.setId(id);
        update.setStatus(SecurityListStatusEnum.REVOKED);
        update.setRevokeOperator(operator);
        update.setRevokeReason(reason);
        update.setRevokeTime(LocalDateTime.now());
        listDao.updateById(update);
        releaseIpPenalty(row);
        listCache.reload();
        snapshotPublisher.publishQuietly("撤销名单");
        log.info("【安全名单】撤销, id: {}, 操作人: {}, 原因: {}", id, operator, reason);
        return true;
    }

    /**
     * 撤销的是 IP 黑名单：一并删掉网关的 IP 处罚键。自动拉黑与那次处罚是同一件事的两面，
     * 只撤名单的话，人在后台点了解封，对方还要被挡到处罚自然到期。
     */
    private void releaseIpPenalty(SecurityList row) {
        boolean ipBlack = row.getListType() == SecurityListTypeEnum.BLACK
                && (row.getDimension() == SecurityListDimensionEnum.IP
                || row.getDimension() == SecurityListDimensionEnum.IP_RANGE);
        String ipKey = ipBlack ? IpKeys.ipOfListValue(row.getListValue()) : null;
        if (ipKey != null) {
            redis.delete(SecurityKeys.securityPenaltyIp(ipKey));
        }
    }

    private static SecurityList toRow(SecurityListAddCmd cmd, String value, LocalDateTime expireTime) {
        SecurityList row = new SecurityList();
        row.setListType(cmd.getListType());
        row.setDimension(cmd.getDimension());
        row.setListValue(value);
        row.setScore(cmd.getScore());
        row.setReason(cmd.getReason());
        row.setSource(SecurityListWriter.SOURCE_MANUAL);
        row.setStatus(SecurityListStatusEnum.VALID);
        row.setExpireTime(expireTime);
        row.setOperator(cmd.getOperator());
        return row;
    }

    /** 各维度的值形状。解不出来当场拒绝，而不是留一行「永远不命中」的名单 */
    private static String normalizeListValue(SecurityListDimensionEnum dimension, String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("名单值必填");
        }
        switch (dimension) {
            case IP, IP_RANGE -> {
                // 用缓存命中时的同一个解析器：这里解得出来，那里就一定匹配得上
                if (IpRange.parse(value) == null) {
                    throw new IllegalArgumentException("IP 或网段格式不对: " + value);
                }
            }
            case IDENTITY -> {
                if (value.length() != IDENTITY_HASH_HEX_LENGTH
                        || !value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                    throw new IllegalArgumentException("身份摘要必须是 64 位十六进制（由接入层对明文算出）");
                }
                // 库里统一存大写，免得同一个摘要因为大小写进了两行
                return value.toUpperCase(Locale.ROOT);
            }
            case MEMBER -> {
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("会员维度的值必须是会员号: " + value);
                }
            }
        }
        return value;
    }

    private static void validateScore(SecurityListTypeEnum listType, Integer score) {
        if (listType == SecurityListTypeEnum.GREY) {
            if (score == null || score <= 0) {
                throw new IllegalArgumentException("灰名单必须给分 —— 不给分的灰名单命中了也什么都不会发生");
            }
            return;
        }
        if (score != null) {
            throw new IllegalArgumentException("只有灰名单有分：黑名单直接拒绝，白名单直接放宽，分数在这两档上没有意义");
        }
    }

    /**
     * 到期时间。
     *
     * <h3>🔴 会员名单（白、灰）必须有到期</h3>
     * 白名单的典型用途是应用市场审核账号、压测账号 —— 都是「这一阵子」的事。
     * 长期白名单会在没人记得的时候继续生效，而白名单上的账号恰恰是最值得猜的。
     * 灰名单同理：一个人不该因为某个月的一次异常，永远比别人多验几道码。
     */
    private static LocalDateTime resolveExpireTime(SecurityListAddCmd cmd) {
        if (cmd.getExpireTime() != null) {
            if (cmd.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("到期时间不能是过去 —— 那条名单加完立刻就失效了");
            }
            return cmd.getExpireTime();
        }
        if (cmd.getDimension() == SecurityListDimensionEnum.MEMBER) {
            throw new IllegalArgumentException("会员名单必须填到期时间，不允许长期");
        }
        return LocalDateTime.now().plus(DEFAULT_LIST_TTL);
    }
}
