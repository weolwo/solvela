package solvela.ledger.logistic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import solvela.base.domain.PageResult;
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

import static solvela.ledger.stat.LedgerStatSupport.rate;
import static solvela.ledger.stat.LedgerStatSupport.toLong;
import static solvela.ledger.stat.LedgerStatSupport.toStr;

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
     *
     * <p>待发货拆成两类：收件信息没补全的（想发也发不了，要催用户）和地址齐了等发货的
     * （运营今天真正能干的活）。实物是三段式履约，status=0 里天然混着这两种。
     */
    public PhysicalDeliveryStatDTO stat(LedgerStatQuery form) {
        PhysicalDeliveryStatDTO vo = new PhysicalDeliveryStatDTO();

        Map<String, Object> newRow = physicalDeliveryDao.selectNewStat(form);
        vo.setNewCount(toLong(newRow.get("newCount")));
        vo.setNewMemberCount(toLong(newRow.get("newMemberCount")));

        // ---- 履约状态：全量，不受时间范围影响 ----
        Map<String, Object> row = physicalDeliveryDao.selectStatusStat();
        long total = toLong(row.get("totalCount"));
        long pending = toLong(row.get("pendingCount"));
        long pendingNoAddress = toLong(row.get("pendingNoAddressCount"));
        long delivered = toLong(row.get("deliveredCount"));
        long signed = toLong(row.get("signedCount"));
        long returned = toLong(row.get("returnedCount"));
        long cancelled = toLong(row.get("cancelledCount"));
        long oldestMinutes = toLong(row.get("pendingOldestMinutes"));

        vo.setTotalCount(total);
        vo.setPendingCount(pending);
        vo.setPendingNoAddressCount(pendingNoAddress);
        // 地址齐了、就等发货的那一部分：这才是运营今天能干的活
        vo.setPendingReadyCount(pending - pendingNoAddress);
        // 没有积压时 SQL 里的 MIN(...) 是 NULL，被 COALESCE 兜成 0；不要解读成「等了 0 分钟」
        vo.setPendingOldestMinutes(pending == 0 ? 0L : oldestMinutes);
        vo.setDeliveredCount(delivered);
        vo.setSignedCount(signed);
        vo.setReturnedCount(returned);
        vo.setCancelledCount(cancelled);
        /*
         * 发货率的分母剔掉已作废：那些单子是被主动撤回的（页面「删除」= UPDATE status=-1），
         * 算成「没发出去」会平白拉低发货率，而运营根本没法把它们发出去。
         */
        vo.setValidCount(total - cancelled);
        vo.setDeliveredRate(rate(delivered + signed, total - cancelled));

        // ---- 来源分布 ----
        List<PhysicalDeliveryStatDTO.SourceStatDTO> sourceList = new ArrayList<>();
        for (Map<String, Object> stat : physicalDeliveryDao.selectSourceStat()) {
            PhysicalDeliveryStatDTO.SourceStatDTO item = new PhysicalDeliveryStatDTO.SourceStatDTO();
            long count = toLong(stat.get("deliveryCount"));
            item.setSourceType(toStr(stat, "sourceType"));
            item.setDeliveryCount(count);
            long sourceCancelled = toLong(stat.get("cancelledCount"));
            item.setPendingCount(toLong(stat.get("pendingCount")));
            item.setCancelledCount(sourceCancelled);
            item.setDeliveredRate(rate(toLong(stat.get("shippedCount")), count - sourceCancelled));
            sourceList.add(item);
        }
        vo.setSourceList(sourceList);

        // ---- 体检 ----
        List<String> issues = new ArrayList<>();
        if (pendingNoAddress > 0) {
            issues.add("有 " + pendingNoAddress + " 单待发货是因为「收件信息还没补全」：实物是三段式履约，"
                    + "中奖时用户还没填地址，履约单先落、收件信息后补 —— 这批单子想发也发不了，"
                    + "要去催用户填地址，不是仓库的活。剩下 " + (pending - pendingNoAddress) + " 单是地址齐了等发货的");
        }
        if (pending > 0 && oldestMinutes >= MINUTES_PER_DAY) {
            issues.add("最久的一单待发货已经压了 " + (oldestMinutes / MINUTES_PER_HOUR) + " 小时："
                    + "履约单不会自己往下走，没人发就一直是待发货，用户那边看到的就是「奖到手了但东西没来」");
        }
        long shippedNoLogisticsNo = toLong(row.get("shippedNoLogisticsNo"));
        if (shippedNoLogisticsNo > 0) {
            issues.add("有 " + shippedNoLogisticsNo + " 单状态是已发货/已签收却没有物流单号："
                    + "用户查不到件，客服也查不到，出了纠纷拿不出发货凭证");
        }
        long returnedNoLogisticsNo = toLong(row.get("returnedNoLogisticsNo"));
        if (returnedNoLogisticsNo > 0) {
            issues.add("有 " + returnedNoLogisticsNo + " 单异常退回却没有物流单号：退回这件事本身无从追溯，"
                    + "东西到底回没回来只能靠人问");
        }
        if (returned > 0) {
            issues.add("有 " + returned + " 单异常退回：这是终态，不会有任何流程再推进它们，"
                    + "东西既没到用户手上也没重新发出，需要人工决定是补发还是作废");
        }
        vo.setIssueList(issues);
        return vo;
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
     * <p>全表校验通过才落库（{@code @Transactional} + 先攒后插）：导入是一次操作，
     * "成功 470 条失败 30 条"这种半截状态没法让人判断该补哪些行，只能整批退回重来。
     */
    @Transactional(rollbackFor = Exception.class)
    public String importAdd(InputStream file) {
        SonicReadResult<PhysicalDeliveryImportRow> result = SolvelaExcelUtil.importExcel(file, PhysicalDeliveryImportRow.class);
        if (result.hasError()) {
            throw new BusinessException("有 " + result.errors().size() + " 行数据有误：" + result.describeErrors(5));
        }
        if (result.isEmpty()) {
            throw new BusinessException("数据为空");
        }

        List<PhysicalDeliveryImportRow> dataList = result.data();
        List<String> errorList = new ArrayList<>();
        Set<String> fileKeySet = new HashSet<>();

        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryImportRow row = dataList.get(i);
            String rowNo = rowLabel(i);

            if (SolvelaStringUtil.isBlank(row.memberName())) {
                errorList.add(rowNo + "「会员账号」不能为空");
            }
            if (SolvelaStringUtil.isBlank(row.sourceBizId())) {
                errorList.add(rowNo + "「来源单号」不能为空");
            }
            if (SolvelaStringUtil.isBlank(row.sourceType())) {
                errorList.add(rowNo + "「来源类型」不能为空");
            }
            // 🔴 收件三项要落进密文列，上限由密文列宽反推（算式见 PiiCipher.cipherTextLength）。
            //    这里逐行拦，是因为再往下就是 MySQL 非严格模式的<b>静默截断</b> ——
            //    表现是「导入成功了，但那几行读出来解密失败」，而且那几行救不回来。
            checkPiiLength(errorList, rowNo, "收件人姓名", row.receiverName(), RECEIVER_NAME_MAX);
            checkPiiLength(errorList, rowNo, "收件人电话", row.receiverPhone(), RECEIVER_PHONE_MAX);
            checkPiiLength(errorList, rowNo, "收件详细地址", row.receiverAddress(), RECEIVER_ADDRESS_MAX);
            // 文件内自查重：同一批里出现两行同键，逐行插入时才炸就说不清是哪两行冲突了
            if (SolvelaStringUtil.isNotBlank(row.sourceBizId()) && SolvelaStringUtil.isNotBlank(row.sourceType())
                    && !fileKeySet.add(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errorList.add(rowNo + "与文件中其它行的「来源单号 + 来源类型」重复");
            }
        }

        // 账号 -> 会员号：Excel 里运营填的是账号（没人记得住 10 位会员号），
        // 而落库的关联键是 member_id，必须在这里换一次。
        // 🔴 一次批量查完，不逐行点查：一次导入几百行就是几百次往返。
        // 🔴 查不到的账号必须逐行报错退回，不能跳过、更不能置空 ——
        //    换键之前一个拼错的账号会静默生成一张永远查不到主人的履约单，
        //    这次正好把那个口子一起堵上。
        Map<String, Long> memberIdMap = memberService.mapMemberIdByNames(
                dataList.stream().map(PhysicalDeliveryImportRow::memberName).toList());
        for (int i = 0; i < dataList.size(); i++) {
            String rowMemberName = dataList.get(i).memberName();
            if (SolvelaStringUtil.isNotBlank(rowMemberName) && !memberIdMap.containsKey(rowMemberName)) {
                errorList.add(rowLabel(i) + "「会员账号」" + rowMemberName + " 不存在");
            }
        }

        // 与库内已有履约单查重：撞唯一键 uk_t_biz_phy_dlv_src 的行，
        // 说明这单已经存在，该走「回填模式」而不是新增
        Map<String, PhysicalDelivery> existMap = loadExisting(dataList.stream()
                .map(r -> new String[]{r.sourceBizId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryImportRow row = dataList.get(i);
            if (SolvelaStringUtil.isNotBlank(row.sourceBizId()) && SolvelaStringUtil.isNotBlank(row.sourceType())
                    && existMap.containsKey(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errorList.add(rowLabel(i) + "对应的履约单已存在，请改用「回填物流」模式");
            }
        }

        if (!errorList.isEmpty()) {
            throw new BusinessException(describe(errorList));
        }

        for (PhysicalDeliveryImportRow row : dataList) {
            // 逐个 set 而不是 SolvelaBeanUtil.copy：导入表单是 record，访问器叫 memberName() 不叫
            // getMemberName()，Spring BeanUtils 认 JavaBean getter，拷过去会是一个空对象
            PhysicalDelivery entity = new PhysicalDelivery();
            entity.setMemberId(memberIdMap.get(row.memberName()));
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
            physicalDeliveryDao.insert(entity);
        }
        return "成功导入 " + dataList.size() + " 条";
    }

    /**
     * 导入 —— 回填模式：按 (发奖提案ID, 来源类型) 匹配已有履约单，回填物流信息与状态。
     *
     * <p>匹配不到的行报错退回，<b>不新建</b>：履约单是提案发奖时生成的，
     * 凭一张 Excel 凭空造单等于绕过提案与预算，是资损口子。
     */
    @Transactional(rollbackFor = Exception.class)
    public String importShip(InputStream file) {
        SonicReadResult<PhysicalDeliveryShipImportRow> result = SolvelaExcelUtil.importExcel(file, PhysicalDeliveryShipImportRow.class);
        if (result.hasError()) {
            throw new BusinessException("有 " + result.errors().size() + " 行数据有误：" + result.describeErrors(5));
        }
        if (result.isEmpty()) {
            throw new BusinessException("数据为空");
        }

        List<PhysicalDeliveryShipImportRow> dataList = result.data();
        List<String> errorList = new ArrayList<>();
        Set<String> fileKeySet = new HashSet<>();

        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryShipImportRow row = dataList.get(i);
            String rowNo = rowLabel(i);

            if (SolvelaStringUtil.isBlank(row.sourceBizId())) {
                errorList.add(rowNo + "「来源单号」不能为空");
            }
            if (SolvelaStringUtil.isBlank(row.sourceType())) {
                errorList.add(rowNo + "「来源类型」不能为空");
            }
            if (SolvelaStringUtil.isNotBlank(row.sourceBizId()) && SolvelaStringUtil.isNotBlank(row.sourceType())
                    && !fileKeySet.add(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errorList.add(rowNo + "与文件中其它行的「来源单号 + 来源类型」重复");
            }
            // 状态列为空表示"这次不改状态"，只回填单号；填了就必须是合法值（模板里是下拉，正常填不错）
            if (row.status() != null && DeliveryStatusEnum.resolve(row.status()) == null) {
                errorList.add(rowNo + "「状态」取值非法");
            }
            if (SolvelaStringUtil.isBlank(row.logisticsCompany()) && SolvelaStringUtil.isBlank(row.logisticsNo())
                    && row.status() == null) {
                errorList.add(rowNo + "物流公司、物流单号、状态至少要填一项");
            }
        }

        Map<String, PhysicalDelivery> existMap = loadExisting(dataList.stream()
                .map(r -> new String[]{r.sourceBizId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryShipImportRow row = dataList.get(i);
            if (SolvelaStringUtil.isNotBlank(row.sourceBizId()) && SolvelaStringUtil.isNotBlank(row.sourceType())
                    && !existMap.containsKey(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errorList.add(rowLabel(i) + "找不到对应的履约单（来源单号 + 来源类型）");
            }
        }

        if (!errorList.isEmpty()) {
            throw new BusinessException(describe(errorList));
        }

        for (PhysicalDeliveryShipImportRow row : dataList) {
            PhysicalDelivery exist = existMap.get(uniqueKey(row.sourceBizId(), row.sourceType()));

            PhysicalDelivery update = new PhysicalDelivery();
            update.setId(exist.getId());
            // 空值一律不覆盖：回填表通常只填变动的那几列，null 在这里是"不改"而不是"清空"
            if (SolvelaStringUtil.isNotBlank(row.logisticsCompany())) {
                update.setLogisticsCompany(row.logisticsCompany());
            }
            if (SolvelaStringUtil.isNotBlank(row.logisticsNo())) {
                update.setLogisticsNo(row.logisticsNo());
            }
            // Excel 行模型刻意保持 Integer（那是文件格式的边界，单元格里是人手填的文本），
            // 到这里做一次显式转换。合法性已经在上面的校验里保证过，null 表示不改状态。
            update.setStatus(DeliveryStatusEnum.resolve(row.status()));
            physicalDeliveryDao.updateById(update);
        }
        return "成功回填 " + dataList.size() + " 条";
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

    private void checkPiiLength(List<String> errorList, String rowNo, String label, String value, int max) {
        if (value != null && value.length() > max) {
            errorList.add(rowNo + "「" + label + "」超长（最多 " + max + " 个字，当前 " + value.length() + "）");
        }
    }

    /**
     * data() 里的下标 → Excel 里肉眼可见的行号：0 行是表头，所以第 i 条数据在第 i+2 行。
     */
    private String rowLabel(int index) {
        return "第 " + (index + 2) + " 行";
    }

    private String describe(List<String> errorList) {
        int limit = 5;
        String head = String.join("；", errorList.subList(0, Math.min(limit, errorList.size())));
        return errorList.size() <= limit ? head : head + "……等 " + errorList.size() + " 处";
    }
}
