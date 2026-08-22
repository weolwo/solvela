package sa.ledger.logistic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.util.SmartBeanUtil;
import sa.base.common.util.SmartCollectionUtil;
import sa.base.common.util.SmartExcelUtil;
import sa.base.common.util.SmartPageUtil;
import sa.base.common.util.SmartStringUtil;
import sa.base.sonicexcel.error.SonicReadResult;
import sa.enums.DeliveryStatusEnum;
import sa.ledger.logistic.dao.PhysicalDeliveryDao;
import sa.ledger.logistic.domain.entity.PhysicalDelivery;
import sa.ledger.logistic.domain.form.PhysicalDeliveryAddForm;
import sa.ledger.logistic.domain.form.PhysicalDeliveryImportForm;
import sa.ledger.logistic.domain.form.PhysicalDeliveryQueryForm;
import sa.ledger.logistic.domain.form.PhysicalDeliveryShipImportForm;
import sa.ledger.logistic.domain.form.PhysicalDeliveryUpdateForm;
import sa.ledger.logistic.domain.vo.PhysicalDeliveryStatVO;
import sa.ledger.logistic.domain.vo.PhysicalDeliveryVO;
import sa.ledger.stat.domain.form.LedgerStatForm;
import static sa.ledger.stat.LedgerStatSupport.rate;
import static sa.ledger.stat.LedgerStatSupport.toLong;
import static sa.ledger.stat.LedgerStatSupport.toStr;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import sa.member.service.MemberService;
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

    /** 会员号 -> 账号：履约单要落展示快照，导入还要按账号反查会员号 */
    private final MemberService memberService;

    /**
     * 分页查询
     */
    public PageResult<PhysicalDeliveryVO> queryPage(PhysicalDeliveryQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PhysicalDeliveryVO> list = physicalDeliveryDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
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
    public PhysicalDeliveryStatVO stat(LedgerStatForm form) {
        PhysicalDeliveryStatVO vo = new PhysicalDeliveryStatVO();

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
        long discarded = toLong(row.get("discardedCount"));
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
        vo.setDiscardedCount(discarded);
        /*
         * 发货率的分母剔掉已作废：那些单子是被主动撤回的（页面「删除」= UPDATE status=-1），
         * 算成「没发出去」会平白拉低发货率，而运营根本没法把它们发出去。
         */
        vo.setValidCount(total - discarded);
        vo.setDeliveredRate(rate(delivered + signed, total - discarded));

        // ---- 来源分布 ----
        List<PhysicalDeliveryStatVO.SourceStatVO> sourceList = new ArrayList<>();
        for (Map<String, Object> stat : physicalDeliveryDao.selectSourceStat()) {
            PhysicalDeliveryStatVO.SourceStatVO item = new PhysicalDeliveryStatVO.SourceStatVO();
            long count = toLong(stat.get("deliveryCount"));
            item.setSourceType(toStr(stat, "sourceType"));
            item.setDeliveryCount(count);
            long sourceDiscarded = toLong(stat.get("discardedCount"));
            item.setPendingCount(toLong(stat.get("pendingCount")));
            item.setDiscardedCount(sourceDiscarded);
            item.setDeliveredRate(rate(toLong(stat.get("shippedCount")), count - sourceDiscarded));
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
    public ResponseDTO<String> add(PhysicalDeliveryAddForm addForm) {
        PhysicalDelivery physicalDelivery = SmartBeanUtil.copy(addForm, PhysicalDelivery.class);
        // 表单只收会员号，账号快照由服务端补 —— 顺带校验会员真实存在。
        // ⚠️ 这一句不能省：member_name 仍是 NOT NULL 且无默认值，
        //    漏了会以「Field 'member_name' doesn't have a default value」整条被拒。
        physicalDelivery.setMemberName(memberService.requireMemberName(addForm.getMemberId()));
        physicalDeliveryDao.insert(physicalDelivery);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(PhysicalDeliveryUpdateForm updateForm) {
        PhysicalDelivery physicalDelivery = SmartBeanUtil.copy(updateForm, PhysicalDelivery.class);
        physicalDeliveryDao.updateById(physicalDelivery);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDiscard(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        physicalDeliveryDao.discardBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> discard(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        physicalDeliveryDao.discardById(id);
        return ResponseDTO.ok();
    }

    // ------------------------------------------------------------------ Excel 导入

    /**
     * 导入 —— 新增模式：整行新建履约单，等价于批量点「新建」。
     *
     * <p>全表校验通过才落库（{@code @Transactional} + 先攒后插）：导入是一次操作，
     * "成功 470 条失败 30 条"这种半截状态没法让人判断该补哪些行，只能整批退回重来。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> importAdd(MultipartFile file) {
        SonicReadResult<PhysicalDeliveryImportForm> result = SmartExcelUtil.importExcel(file, PhysicalDeliveryImportForm.class);
        if (result.hasError()) {
            return ResponseDTO.userErrorParam("有 " + result.errors().size() + " 行数据有误：" + result.describeErrors(5));
        }
        if (result.isEmpty()) {
            return ResponseDTO.userErrorParam("数据为空");
        }

        List<PhysicalDeliveryImportForm> dataList = result.data();
        List<String> errorList = new ArrayList<>();
        Set<String> fileKeySet = new HashSet<>();

        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryImportForm row = dataList.get(i);
            String rowNo = rowLabel(i);

            if (SmartStringUtil.isBlank(row.memberName())) {
                errorList.add(rowNo + "「会员账号」不能为空");
            }
            if (SmartStringUtil.isBlank(row.sourceBizId())) {
                errorList.add(rowNo + "「来源单号」不能为空");
            }
            if (SmartStringUtil.isBlank(row.sourceType())) {
                errorList.add(rowNo + "「来源类型」不能为空");
            }
            // 🔴 收件三项要落进密文列，上限由密文列宽反推（算式见 PiiCipher.cipherTextLength）。
            //    这里逐行拦，是因为再往下就是 MySQL 非严格模式的<b>静默截断</b> ——
            //    表现是「导入成功了，但那几行读出来解密失败」，而且那几行救不回来。
            checkPiiLength(errorList, rowNo, "收件人姓名", row.receiverName(), RECEIVER_NAME_MAX);
            checkPiiLength(errorList, rowNo, "收件人电话", row.receiverPhone(), RECEIVER_PHONE_MAX);
            checkPiiLength(errorList, rowNo, "收件详细地址", row.receiverAddress(), RECEIVER_ADDRESS_MAX);
            // 文件内自查重：同一批里出现两行同键，逐行插入时才炸就说不清是哪两行冲突了
            if (SmartStringUtil.isNotBlank(row.sourceBizId()) && SmartStringUtil.isNotBlank(row.sourceType())
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
                dataList.stream().map(PhysicalDeliveryImportForm::memberName).toList());
        for (int i = 0; i < dataList.size(); i++) {
            String rowMemberName = dataList.get(i).memberName();
            if (SmartStringUtil.isNotBlank(rowMemberName) && !memberIdMap.containsKey(rowMemberName)) {
                errorList.add(rowLabel(i) + "「会员账号」" + rowMemberName + " 不存在");
            }
        }

        // 与库内已有履约单查重：撞唯一键 uk_t_biz_phy_dlv_src 的行，
        // 说明这单已经存在，该走「回填模式」而不是新增
        Map<String, PhysicalDelivery> existMap = loadExisting(dataList.stream()
                .map(r -> new String[]{r.sourceBizId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryImportForm row = dataList.get(i);
            if (SmartStringUtil.isNotBlank(row.sourceBizId()) && SmartStringUtil.isNotBlank(row.sourceType())
                    && existMap.containsKey(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errorList.add(rowLabel(i) + "对应的履约单已存在，请改用「回填物流」模式");
            }
        }

        if (!errorList.isEmpty()) {
            return ResponseDTO.userErrorParam(describe(errorList));
        }

        for (PhysicalDeliveryImportForm row : dataList) {
            // 逐个 set 而不是 SmartBeanUtil.copy：导入表单是 record，访问器叫 memberName() 不叫
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
            entity.setStatus(DeliveryStatusEnum.PENDING.getValue());
            physicalDeliveryDao.insert(entity);
        }
        return ResponseDTO.okMsg("成功导入 " + dataList.size() + " 条");
    }

    /**
     * 导入 —— 回填模式：按 (发奖提案ID, 来源类型) 匹配已有履约单，回填物流信息与状态。
     *
     * <p>匹配不到的行报错退回，<b>不新建</b>：履约单是提案发奖时生成的，
     * 凭一张 Excel 凭空造单等于绕过提案与预算，是资损口子。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> importShip(MultipartFile file) {
        SonicReadResult<PhysicalDeliveryShipImportForm> result = SmartExcelUtil.importExcel(file, PhysicalDeliveryShipImportForm.class);
        if (result.hasError()) {
            return ResponseDTO.userErrorParam("有 " + result.errors().size() + " 行数据有误：" + result.describeErrors(5));
        }
        if (result.isEmpty()) {
            return ResponseDTO.userErrorParam("数据为空");
        }

        List<PhysicalDeliveryShipImportForm> dataList = result.data();
        List<String> errorList = new ArrayList<>();
        Set<String> fileKeySet = new HashSet<>();

        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryShipImportForm row = dataList.get(i);
            String rowNo = rowLabel(i);

            if (SmartStringUtil.isBlank(row.sourceBizId())) {
                errorList.add(rowNo + "「来源单号」不能为空");
            }
            if (SmartStringUtil.isBlank(row.sourceType())) {
                errorList.add(rowNo + "「来源类型」不能为空");
            }
            if (SmartStringUtil.isNotBlank(row.sourceBizId()) && SmartStringUtil.isNotBlank(row.sourceType())
                    && !fileKeySet.add(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errorList.add(rowNo + "与文件中其它行的「来源单号 + 来源类型」重复");
            }
            // 状态列为空表示"这次不改状态"，只回填单号；填了就必须是合法值（模板里是下拉，正常填不错）
            if (row.status() != null && DeliveryStatusEnum.resolve(row.status()) == null) {
                errorList.add(rowNo + "「状态」取值非法");
            }
            if (SmartStringUtil.isBlank(row.logisticsCompany()) && SmartStringUtil.isBlank(row.logisticsNo())
                    && row.status() == null) {
                errorList.add(rowNo + "物流公司、物流单号、状态至少要填一项");
            }
        }

        Map<String, PhysicalDelivery> existMap = loadExisting(dataList.stream()
                .map(r -> new String[]{r.sourceBizId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryShipImportForm row = dataList.get(i);
            if (SmartStringUtil.isNotBlank(row.sourceBizId()) && SmartStringUtil.isNotBlank(row.sourceType())
                    && !existMap.containsKey(uniqueKey(row.sourceBizId(), row.sourceType()))) {
                errorList.add(rowLabel(i) + "找不到对应的履约单（来源单号 + 来源类型）");
            }
        }

        if (!errorList.isEmpty()) {
            return ResponseDTO.userErrorParam(describe(errorList));
        }

        for (PhysicalDeliveryShipImportForm row : dataList) {
            PhysicalDelivery exist = existMap.get(uniqueKey(row.sourceBizId(), row.sourceType()));

            PhysicalDelivery update = new PhysicalDelivery();
            update.setId(exist.getId());
            // 空值一律不覆盖：回填表通常只填变动的那几列，null 在这里是"不改"而不是"清空"
            if (SmartStringUtil.isNotBlank(row.logisticsCompany())) {
                update.setLogisticsCompany(row.logisticsCompany());
            }
            if (SmartStringUtil.isNotBlank(row.logisticsNo())) {
                update.setLogisticsNo(row.logisticsNo());
            }
            update.setStatus(row.status());
            physicalDeliveryDao.updateById(update);
        }
        return ResponseDTO.okMsg("成功回填 " + dataList.size() + " 条");
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
            if (SmartStringUtil.isNotBlank(key[0])) {
                sourceBizIdSet.add(key[0]);
            }
            if (SmartStringUtil.isNotBlank(key[1])) {
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
     * 与 {@code PhysicalDeliveryAddForm} / {@code PhysicalDeliveryUpdateForm} 上的
     * {@code @Size} 是同一组数字，改一处要改三处，算式见 {@code PiiCipher.cipherTextLength}。
     */
    private static final int RECEIVER_NAME_MAX = 40;
    private static final int RECEIVER_PHONE_MAX = 30;
    private static final int RECEIVER_ADDRESS_MAX = 100;

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
