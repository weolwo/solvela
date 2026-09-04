package solvela.ledger.logistic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
import solvela.base.stat.Checkup;
import solvela.base.stat.Rate;
import solvela.base.stat.StatRow;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaCollectionUtil;
import solvela.base.sonicexcel.SolvelaExcelUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.base.sonicexcel.error.SonicReadResult;
import solvela.enums.DeliveryStatusEnum;
import solvela.ledger.logistic.dao.PhysicalDeliveryDao;
import solvela.ledger.PhysicalDelivery;
import solvela.ledger.logistic.domain.command.PhysicalDeliveryAddCommand;
import solvela.ledger.logistic.domain.excel.PhysicalDeliveryImportRow;
import solvela.ledger.logistic.domain.dto.PhysicalDeliveryDTO;
import solvela.ledger.logistic.domain.query.PhysicalDeliveryQuery;
import solvela.ledger.logistic.domain.excel.PhysicalDeliveryShipImportRow;
import solvela.ledger.logistic.domain.command.PhysicalDeliveryUpdateCommand;
import solvela.ledger.logistic.domain.dto.PhysicalDeliveryStatDTO;
import solvela.ledger.stat.domain.query.LedgerStatQuery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;

import solvela.member.service.MemberService;
import solvela.exception.BusinessException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发货物流表 Service
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class PhysicalDeliveryService {

    private final PhysicalDeliveryDao physicalDeliveryDao;

    /**
     * 会员号 -> 账号：履约单要落展示快照，导入还要按账号反查会员号
     */
    private final MemberService memberService;

    /**
     * 分页查询
     */
    public PageResult<PhysicalDeliveryDTO> queryPage(PhysicalDeliveryQuery queryForm) {
        Page<?> page = SolvelaPageUtil.convert2PageQuery(queryForm);
        List<PhysicalDeliveryDTO> list = physicalDeliveryDao.queryPage(page, queryForm);
        return SolvelaPageUtil.convert2PageResult(page, list);
    }


    private static final long MINUTES_PER_HOUR = 60L;

    /**
     * 积压超过一天才提示：仓库本来就不是分钟级发货，门槛太低会天天报警，报警就没人看了
     */
    private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    /**
     * 发货统计：今天新增了多少履约单，以及手上到底积压着多少单没发出去。
     *
     * <p><b>积压那一组数字刻意不跟时间范围走</b>：它是存量 ——
     * 限制在今天，压了三天的那些单子会正好从页面上消失，
     * 而那恰恰是这个页面唯一需要有人动手的东西。
     */
    public PhysicalDeliveryStatDTO stat(LedgerStatQuery form) {
        StatRow newRow = StatRow.of(physicalDeliveryDao.selectNewStat(form));
        StatRow row = StatRow.of(physicalDeliveryDao.selectStatusStat());

        PhysicalDeliveryStatDTO vo = new PhysicalDeliveryStatDTO();
        vo.setNewCount(newRow.count("newCount"));
        vo.setNewMemberCount(newRow.count("newMemberCount"));
        fillStatusStock(row, vo);
        vo.setSourceList(sourceStats());
        vo.setIssueList(checkup(row));
        return vo;
    }

    /**
     * 履约状态存量：全量，不受时间范围影响。
     *
     * <p>待发货拆成两类：收件信息没补全的（想发也发不了，要催用户）和地址齐了等发货的
     * （运营今天真正能干的活）。实物是三段式履约，status=0 里天然混着这两种，
     * 不拆开的话「待发货 80 单」会让仓库以为有 80 单可发。
     */
    private void fillStatusStock(StatRow row, PhysicalDeliveryStatDTO vo) {
        long total = row.count("totalCount");
        long pending = row.count("pendingCount");
        long pendingNoAddress = row.count("pendingNoAddressCount");
        long cancelled = row.count("cancelledCount");

        vo.setTotalCount(total);
        vo.setPendingCount(pending);
        vo.setPendingNoAddressCount(pendingNoAddress);
        // 地址齐了、就等发货的那一部分：这才是运营今天能干的活
        vo.setPendingReadyCount(pending - pendingNoAddress);
        // 没有积压时 SQL 里的 MIN(...) 是 NULL，被 COALESCE 兜成 0；不要解读成「等了 0 分钟」
        vo.setPendingOldestMinutes(pending == 0 ? 0L : row.count("pendingOldestMinutes"));
        vo.setDeliveredCount(row.count("deliveredCount"));
        vo.setSignedCount(row.count("signedCount"));
        vo.setReturnedCount(row.count("returnedCount"));
        vo.setCancelledCount(cancelled);
        /*
         * 发货率的分母剔掉已作废：那些单子是被主动撤回的（页面「删除」= UPDATE status=-1），
         * 算成「没发出去」会平白拉低发货率，而运营根本没法把它们发出去。
         */
        vo.setValidCount(total - cancelled);
        vo.setDeliveredRate(Rate.share(row.count("deliveredCount") + row.count("signedCount"), total - cancelled));
    }

    /**
     * 来源分布：哪条业务线的实物奖压得最多。同样是全量存量。
     */
    private List<PhysicalDeliveryStatDTO.SourceStatDTO> sourceStats() {
        List<PhysicalDeliveryStatDTO.SourceStatDTO> sourceList = new ArrayList<>();
        for (StatRow stat : StatRow.of(physicalDeliveryDao.selectSourceStat())) {
            long count = stat.count("deliveryCount");
            long cancelled = stat.count("cancelledCount");

            PhysicalDeliveryStatDTO.SourceStatDTO item = new PhysicalDeliveryStatDTO.SourceStatDTO();
            item.setSourceType(stat.text("sourceType"));
            item.setDeliveryCount(count);
            item.setPendingCount(stat.count("pendingCount"));
            item.setCancelledCount(cancelled);
            // 分母同样剔掉已作废，与上面那个总发货率是同一个口径
            item.setDeliveredRate(Rate.share(stat.count("shippedCount"), count - cancelled));
            sourceList.add(item);
        }
        return sourceList;
    }

    /**
     * 履约体检：全是「单子卡住了但没人会发现」的情形。
     */
    private List<String> checkup(StatRow row) {
        long pending = row.count("pendingCount");
        long pendingNoAddress = row.count("pendingNoAddressCount");
        long oldestMinutes = row.count("pendingOldestMinutes");
        return new Checkup()
                .countIf(pendingNoAddress,
                        "有 {} 单待发货是因为「收件信息还没补全」：实物是三段式履约，"
                                + "中奖时用户还没填地址，履约单先落、收件信息后补 —— 这批单子想发也发不了，"
                                + "要去催用户填地址，不是仓库的活。剩下 {} 单是地址齐了等发货的",
                        pending - pendingNoAddress)
                .when(pending > 0 && oldestMinutes >= MINUTES_PER_DAY,
                        "最久的一单待发货已经压了 {} 小时："
                                + "履约单不会自己往下走，没人发就一直是待发货，用户那边看到的就是「奖到手了但东西没来」",
                        oldestMinutes / MINUTES_PER_HOUR)
                .countIf(row.count("shippedNoLogisticsNo"),
                        "有 {} 单状态是已发货/已签收却没有物流单号："
                                + "用户查不到件，客服也查不到，出了纠纷拿不出发货凭证")
                .countIf(row.count("returnedNoLogisticsNo"),
                        "有 {} 单异常退回却没有物流单号：退回这件事本身无从追溯，"
                                + "东西到底回没回来只能靠人问")
                .countIf(row.count("returnedCount"),
                        "有 {} 单异常退回：这是终态，不会有任何流程再推进它们，"
                                + "东西既没到用户手上也没重新发出，需要人工决定是补发还是作废")
                .issues();
    }

    /**
     * 添加
     */
    public void add(PhysicalDeliveryAddCommand addForm) {
        String tooLong = checkPii(addForm.getReceiverName(), addForm.getReceiverPhone(), addForm.getReceiverAddress());
        if (tooLong != null) {
            throw new BusinessException(tooLong);
        }
        PhysicalDelivery physicalDelivery = SolvelaBeanUtil.copy(addForm, PhysicalDelivery.class);
        // 表单只收会员号，账号快照由服务端补 —— 顺带校验会员真实存在。
        // ⚠️ 这一句不能省：member_name 仍是 NOT NULL 且无默认值，
        //    漏了会以「Field 'member_name' doesn't have a default value」整条被拒。
        physicalDelivery.setMemberName(memberService.requireMemberName(addForm.getMemberId()));
        physicalDeliveryDao.insert(physicalDelivery);
    }

    /**
     * 更新
     *
     */
    public void update(PhysicalDeliveryUpdateCommand updateForm) {
        String tooLong = checkPii(updateForm.getReceiverName(), updateForm.getReceiverPhone(), updateForm.getReceiverAddress());
        if (tooLong != null) {
            throw new BusinessException(tooLong);
        }
        PhysicalDelivery physicalDelivery = SolvelaBeanUtil.copy(updateForm, PhysicalDelivery.class);
        physicalDeliveryDao.updateById(physicalDelivery);
    }

    /**
     * 批量删除
     */
    public void batchDiscard(List<Long> idList) {
        if (SolvelaCollectionUtil.isEmpty(idList)) {
            return;
        }

        physicalDeliveryDao.discardBatchIds(idList);
    }

    /**
     * 单个删除
     */
    public void discard(Long id) {
        if (null == id) {
            return;
        }

        physicalDeliveryDao.discardById(id);
    }

    // ------------------------------------------------------------------ Excel 导入

    /**
     * 导入 —— 新增模式：整行新建履约单，等价于批量点「新建」。
     *
     * <p>三段校验全过才落库（{@code @Transactional} + 先攒后插）：导入是一次操作，
     * 「成功 470 条失败 30 条」这种半截状态没法让人判断该补哪些行，只能整批退回重来。
     * 三段分别管<b>行内</b>（必填、超长）、<b>行间</b>（文件自查重、账号能不能换成会员号）、
     * <b>与库内</b>（这单是不是已经存在）—— 一次把三类问题全报出来，运营改一遍就能过。
     */
    @Transactional(rollbackFor = Exception.class)
    public String importAdd(InputStream file) {
        List<PhysicalDeliveryImportRow> dataList = readRows(file, PhysicalDeliveryImportRow.class);
        RowErrors errors = new RowErrors();

        checkAddRows(dataList, errors);
        // 账号 -> 会员号：Excel 里运营填的是账号（没人记得住 10 位会员号），
        // 而落库的关联键是 member_id，必须在这里换一次。
        Map<String, Long> memberIdMap = resolveMemberIds(dataList, errors);
        checkNotExisting(dataList, errors);
        errors.throwIfAny();

        dataList.forEach(row -> physicalDeliveryDao.insert(toEntity(row, memberIdMap.get(row.memberName()))));
        return "成功导入 " + dataList.size() + " 条";
    }

    /**
     * 新增模式的行内校验 + 文件内自查重。
     */
    private void checkAddRows(List<PhysicalDeliveryImportRow> dataList, RowErrors errors) {
        Set<String> fileKeySet = new HashSet<>();
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryImportRow row = dataList.get(i);

            if (SolvelaStringUtil.isBlank(row.memberName())) {
                errors.add(i, "「会员账号」不能为空");
            }
            if (SolvelaStringUtil.isBlank(row.sourceBizId())) {
                errors.add(i, "「来源单号」不能为空");
            }
            if (SolvelaStringUtil.isBlank(row.sourceType())) {
                errors.add(i, "「来源类型」不能为空");
            }
            // 🔴 收件三项要落进密文列，上限由密文列宽反推（算式见 PiiCipher.cipherTextLength）。
            //    这里逐行拦，是因为再往下就是 MySQL 非严格模式的静默截断 ——
            //    表现是「导入成功了，但那几行读出来解密失败」，而且那几行救不回来。
            checkPiiLength(errors, i, "收件人姓名", row.receiverName(), RECEIVER_NAME_MAX);
            checkPiiLength(errors, i, "收件人电话", row.receiverPhone(), RECEIVER_PHONE_MAX);
            checkPiiLength(errors, i, "收件详细地址", row.receiverAddress(), RECEIVER_ADDRESS_MAX);
            // 文件内自查重：同一批里出现两行同键，逐行插入时才炸就说不清是哪两行冲突了
            if (hasKey(row.sourceBizId(), row.sourceType())
                    && !fileKeySet.add(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errors.add(i, "与文件中其它行的「来源单号 + 来源类型」重复");
            }
        }
    }

    /**
     * 账号 -> 会员号。
     *
     * <p>🔴 一次批量查完，不逐行点查：一次导入几百行就是几百次往返。
     *
     * <p>🔴 查不到的账号必须逐行报错退回，不能跳过、更不能置空 ——
     * 换键之前一个拼错的账号会静默生成一张永远查不到主人的履约单。
     */
    private Map<String, Long> resolveMemberIds(List<PhysicalDeliveryImportRow> dataList, RowErrors errors) {
        Map<String, Long> memberIdMap = memberService.mapMemberIdByNames(
                dataList.stream().map(PhysicalDeliveryImportRow::memberName).toList());
        for (int i = 0; i < dataList.size(); i++) {
            String memberName = dataList.get(i).memberName();
            if (SolvelaStringUtil.isNotBlank(memberName) && !memberIdMap.containsKey(memberName)) {
                errors.add(i, "「会员账号」" + memberName + " 不存在");
            }
        }
        return memberIdMap;
    }

    /**
     * 与库内已有履约单查重：撞唯一键 uk_t_biz_phy_dlv_src 的行，
     * 说明这单已经存在，该走「回填模式」而不是新增。
     */
    private void checkNotExisting(List<PhysicalDeliveryImportRow> dataList, RowErrors errors) {
        Map<String, PhysicalDelivery> existMap = loadExisting(dataList.stream()
                .map(r -> new String[]{r.sourceBizId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryImportRow row = dataList.get(i);
            if (hasKey(row.sourceBizId(), row.sourceType())
                    && existMap.containsKey(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errors.add(i, "对应的履约单已存在，请改用「回填物流」模式");
            }
        }
    }

    /**
     * 逐个 set 而不是 {@code SolvelaBeanUtil.copy}：导入行模型是 record，访问器叫
     * {@code memberName()} 不叫 {@code getMemberName()}，Spring BeanUtils 认 JavaBean getter，
     * 拷过去会是一个空对象 —— 而且不报错。
     */
    private PhysicalDelivery toEntity(PhysicalDeliveryImportRow row, Long memberId) {
        PhysicalDelivery entity = new PhysicalDelivery();
        entity.setMemberId(memberId);
        // 账号同时作为展示快照落库（履约单是单据，记的是「导入当时那个账号」）
        entity.setMemberName(row.memberName());
        entity.setSourceBizId(row.sourceBizId());
        entity.setSourceType(row.sourceType());
        entity.setReceiverName(row.receiverName());
        entity.setReceiverPhone(row.receiverPhone());
        entity.setReceiverAddress(row.receiverAddress());
        entity.setLogisticsCompany(row.logisticsCompany());
        entity.setLogisticsNo(row.logisticsNo());
        entity.setStatus(DeliveryStatusEnum.PENDING);
        return entity;
    }

    /**
     * 导入 —— 回填模式：按 (来源单号, 来源类型) 匹配已有履约单，回填物流信息与状态。
     *
     * <p>匹配不到的行报错退回，<b>不新建</b>：履约单是提案发奖时生成的，
     * 凭一张 Excel 凭空造单等于绕过提案与预算，是资损口子。
     */
    @Transactional(rollbackFor = Exception.class)
    public String importShip(InputStream file) {
        List<PhysicalDeliveryShipImportRow> dataList = readRows(file, PhysicalDeliveryShipImportRow.class);
        RowErrors errors = new RowErrors();

        checkShipRows(dataList, errors);
        Map<String, PhysicalDelivery> existMap = loadExisting(dataList.stream()
                .map(r -> new String[]{r.sourceBizId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryShipImportRow row = dataList.get(i);
            if (hasKey(row.sourceBizId(), row.sourceType())
                    && !existMap.containsKey(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errors.add(i, "找不到对应的履约单（来源单号 + 来源类型）");
            }
        }
        errors.throwIfAny();

        for (PhysicalDeliveryShipImportRow row : dataList) {
            PhysicalDelivery exist = existMap.get(uniqueKey(row.sourceBizId(), row.sourceType()));
            physicalDeliveryDao.updateById(toShipUpdate(row, exist.getId()));
        }
        return "成功回填 " + dataList.size() + " 条";
    }

    /**
     * 回填模式的行内校验 + 文件内自查重。
     */
    private void checkShipRows(List<PhysicalDeliveryShipImportRow> dataList, RowErrors errors) {
        Set<String> fileKeySet = new HashSet<>();
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryShipImportRow row = dataList.get(i);

            if (SolvelaStringUtil.isBlank(row.sourceBizId())) {
                errors.add(i, "「来源单号」不能为空");
            }
            if (SolvelaStringUtil.isBlank(row.sourceType())) {
                errors.add(i, "「来源类型」不能为空");
            }
            if (hasKey(row.sourceBizId(), row.sourceType())
                    && !fileKeySet.add(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errors.add(i, "与文件中其它行的「来源单号 + 来源类型」重复");
            }
            // 状态列为空表示「这次不改状态」，只回填单号；填了就必须是合法值（模板里是下拉，正常填不错）
            if (row.status() != null && DeliveryStatusEnum.resolve(row.status()) == null) {
                errors.add(i, "「状态」取值非法");
            }
            if (SolvelaStringUtil.isBlank(row.logisticsCompany()) && SolvelaStringUtil.isBlank(row.logisticsNo())
                    && row.status() == null) {
                errors.add(i, "物流公司、物流单号、状态至少要填一项");
            }
        }
    }

    /**
     * 回填只带上这次真要改的列：<b>空值一律不覆盖</b>。
     * 回填表通常只填变动的那几列，null 在这里是「不改」而不是「清空」。
     */
    private PhysicalDelivery toShipUpdate(PhysicalDeliveryShipImportRow row, Long id) {
        PhysicalDelivery update = new PhysicalDelivery();
        update.setId(id);
        if (SolvelaStringUtil.isNotBlank(row.logisticsCompany())) {
            update.setLogisticsCompany(row.logisticsCompany());
        }
        if (SolvelaStringUtil.isNotBlank(row.logisticsNo())) {
            update.setLogisticsNo(row.logisticsNo());
        }
        // Excel 行模型刻意保持 Integer（那是文件格式的边界，单元格里是人手填的文本），
        // 到这里做一次显式转换。合法性已经在上面的校验里保证过，null 表示不改状态。
        update.setStatus(DeliveryStatusEnum.resolve(row.status()));
        return update;
    }

    /**
     * 读文件并挡掉「压根没法开始校验」的两种情况：解析就失败了、以及一行数据都没有。
     *
     * <p>这一步的错误与后面的行校验不是一类：解析失败说明文件格式对不上（多半是拿旧模板填的），
     * 报出来的是「第几行第几列读不出来」，跟业务规则无关。
     */
    private <T> List<T> readRows(InputStream file, Class<T> rowType) {
        SonicReadResult<T> result = SolvelaExcelUtil.importExcel(file, rowType);
        if (result.hasError()) {
            throw new BusinessException("有 " + result.errors().size() + " 行数据有误：" + result.describeErrors(5));
        }
        if (result.isEmpty()) {
            throw new BusinessException("数据为空");
        }
        return result.data();
    }

    /** 复合键两列都填了才谈得上查重 —— 缺列的行上面已经单独报过错了 */
    private boolean hasKey(String sourceBizId, String sourceType) {
        return SolvelaStringUtil.isNotBlank(sourceBizId) && SolvelaStringUtil.isNotBlank(sourceType);
    }

    /**
     * 导入过程中攒下来的行级错误。
     *
     * <p>攒而不是遇到第一个就抛，是导入这件事的核心体验：一次把问题全报出来，
     * 运营改一遍就能过；抛第一个的话，五处错就要来回五次。
     *
     * <p>行号在这里统一翻译：{@code data()} 的下标 0 对应 Excel 里肉眼可见的第 2 行
     * （第 1 行是表头）。各处自己拼 {@code "第 " + (i + 2) + " 行"} 的话，
     * 早晚有一处忘了 +2，而那种错报出来比不报还误导人。
     */
    private static final class RowErrors {

        /** 一次最多展示几条。全量堆到弹窗里没人读得完，也塞不下 */
        private static final int DISPLAY_LIMIT = 5;

        private final List<String> messages = new ArrayList<>();

        void add(int index, String message) {
            messages.add("第 " + (index + 2) + " 行" + message);
        }

        void throwIfAny() {
            if (messages.isEmpty()) {
                return;
            }
            String head = String.join("；", messages.subList(0, Math.min(DISPLAY_LIMIT, messages.size())));
            throw new BusinessException(messages.size() <= DISPLAY_LIMIT
                    ? head : head + "……等 " + messages.size() + " 处");
        }
    }

    /**
     * 按 (source_biz_id, source_type) 捞出这批 key 命中的履约单。
     *
     * <p>用两个 IN 粗筛再在内存里按复合键收敛：MyBatis-Plus 表达复合键 IN 很别扭，
     * 而导入本来就是几百行量级，粗筛多带回来一些完全无所谓。
     */
    private Map<String, PhysicalDelivery> loadExisting(List<String[]> keyList) {
        Set<String> sourceBizIdSet = new HashSet<>();
        Set<String> sourceTypeSet = new HashSet<>();
        for (String[] key : keyList) {
            if (SolvelaStringUtil.isNotBlank(key[0])) {
                sourceBizIdSet.add(key[0]);
            }
            if (SolvelaStringUtil.isNotBlank(key[1])) {
                sourceTypeSet.add(key[1]);
            }
        }
        if (sourceBizIdSet.isEmpty() || sourceTypeSet.isEmpty()) {
            return Map.of();
        }

        List<PhysicalDelivery> list = physicalDeliveryDao.selectList(new LambdaQueryWrapper<PhysicalDelivery>()
                .in(PhysicalDelivery::getSourceBizId, sourceBizIdSet)
                .in(PhysicalDelivery::getSourceType, sourceTypeSet));

        Map<String, PhysicalDelivery> map = new HashMap<>();
        list.forEach(e -> map.put(uniqueKey(e.getSourceBizId(), e.getSourceType()), e));
        return map;
    }

    private String uniqueKey(String sourceBizId, String sourceType) {
        return sourceBizId + "|" + sourceType;
    }

    /**
     * 收件信息的明文长度上限 —— <b>由密文列宽反推出来的，不是拍脑袋定的</b>。
     *
     * <p>三列都加密落库，密文长度 = 3(前缀) + base64(12 + 明文字节数 + 16)。
     * 按 UTF-8 中文每字 3 字节算：
     * <pre>
     *   姓名 40 字 (120B) -> 密文 203 ≤ varchar(255)
     *   电话 30 位 ( 30B) -> 密文  83 ≤ varchar(255)
     *   地址 100 字(300B) -> 密文 443 ≤ varchar(512)
     * </pre>
     * 与 {@code PhysicalDeliveryAddCommand} / {@code PhysicalDeliveryUpdateCommand} 上的
     * {@code @Size} 是同一组数字，改一处要改三处，算式见 {@code PiiCipher.cipherTextLength}。
     */
    private static final int RECEIVER_NAME_MAX = 40;
    private static final int RECEIVER_PHONE_MAX = 30;
    private static final int RECEIVER_ADDRESS_MAX = 100;

    /**
     * 三个 PII 字段的长度校验，<b>写入前必过</b>，返回 null 表示通过。
     *
     * <p>为什么不是 {@code @Size} 而是写在 service 里：这三条是<b>密文列宽的硬约束</b>，
     * 不是某个页面的输入体验。管理端的 Form 上还留着同样上限的 {@code @Size}，那是为了
     * 让前端在提交前就红框提示；但只要还有第二个调用方（导入、C 端、定时任务、将来的 RPC），
     * 靠端上的注解就等于没设防 —— 真正的底线必须在领域这一层。
     *
     * <p>放开上限而不改列宽的后果：MySQL 非严格模式下<b>静默截断密文</b>，
     * 表现为「存进去了，读出来解密失败」，而且那一行救不回来。
     * 改这里或改列宽时两边一起改，密文长度算式见 {@code PiiCipher.cipherTextLength}：
     * <pre>
     *     密文长度 = 3(前缀) + base64(12 + 明文字节数 + 16)
     *     姓名  40 字符(120B) -> 203  ≤ varchar(255)
     *     电话  30 字符( 30B) ->  83  ≤ varchar(255)
     *     地址 100 字符(300B) -> 443 ≤ varchar(512)
     * </pre>
     */
    private String checkPii(String name, String phone, String address) {
        if (name != null && name.length() > RECEIVER_NAME_MAX) {
            return "收件人姓名 最多 " + RECEIVER_NAME_MAX + " 个字";
        }
        if (phone != null && phone.length() > RECEIVER_PHONE_MAX) {
            return "收件人电话 最多 " + RECEIVER_PHONE_MAX + " 位";
        }
        if (address != null && address.length() > RECEIVER_ADDRESS_MAX) {
            return "收件详细地址 最多 " + RECEIVER_ADDRESS_MAX + " 个字";
        }
        return null;
    }

    private void checkPiiLength(RowErrors errors, int index, String label, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(index, "「" + label + "」超长（最多 " + max + " 个字，当前 " + value.length() + "）");
        }
    }
}
