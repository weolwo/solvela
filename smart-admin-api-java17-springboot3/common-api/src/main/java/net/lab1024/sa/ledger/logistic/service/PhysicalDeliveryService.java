package net.lab1024.sa.ledger.logistic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartExcelUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.common.util.SmartStringUtil;
import net.lab1024.sa.base.sonicexcel.error.SonicReadResult;
import net.lab1024.sa.enums.DeliveryStatusEnum;
import net.lab1024.sa.ledger.logistic.dao.PhysicalDeliveryDao;
import net.lab1024.sa.ledger.logistic.domain.entity.PhysicalDelivery;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryAddForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryImportForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryQueryForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryShipImportForm;
import net.lab1024.sa.ledger.logistic.domain.form.PhysicalDeliveryUpdateForm;
import net.lab1024.sa.ledger.logistic.domain.vo.PhysicalDeliveryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
     * 分页查询
     */
    public PageResult<PhysicalDeliveryVO> queryPage(PhysicalDeliveryQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PhysicalDeliveryVO> list = physicalDeliveryDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(PhysicalDeliveryAddForm addForm) {
        PhysicalDelivery physicalDelivery = SmartBeanUtil.copy(addForm, PhysicalDelivery.class);
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
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        physicalDeliveryDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        physicalDeliveryDao.deleteById(id);
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
                errorList.add(rowNo + "「会员名」不能为空");
            }
            if (row.proposalId() == null) {
                errorList.add(rowNo + "「发奖提案ID」不能为空");
            }
            if (SmartStringUtil.isBlank(row.sourceType())) {
                errorList.add(rowNo + "「来源类型」不能为空");
            }
            // 文件内自查重：同一批里出现两行同键，逐行插入时才炸就说不清是哪两行冲突了
            if (row.proposalId() != null && SmartStringUtil.isNotBlank(row.sourceType())
                    && !fileKeySet.add(uniqueKey(row.proposalId(), row.sourceType()))) {
                errorList.add(rowNo + "与文件中其它行的「发奖提案ID + 来源类型」重复");
            }
        }

        // 与库内已有履约单查重：撞唯一键 uk_t_biz_phy_dlv_prop_pool 的行，
        // 说明这单已经存在，该走「回填模式」而不是新增
        Map<String, PhysicalDelivery> existMap = loadExisting(dataList.stream()
                .map(r -> new Object[]{r.proposalId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryImportForm row = dataList.get(i);
            if (row.proposalId() != null && SmartStringUtil.isNotBlank(row.sourceType())
                    && existMap.containsKey(uniqueKey(row.proposalId(), row.sourceType()))) {
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
            entity.setMemberName(row.memberName());
            entity.setProposalId(row.proposalId());
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

            if (row.proposalId() == null) {
                errorList.add(rowNo + "「发奖提案ID」不能为空");
            }
            if (SmartStringUtil.isBlank(row.sourceType())) {
                errorList.add(rowNo + "「来源类型」不能为空");
            }
            if (row.proposalId() != null && SmartStringUtil.isNotBlank(row.sourceType())
                    && !fileKeySet.add(uniqueKey(row.proposalId(), row.sourceType()))) {
                errorList.add(rowNo + "与文件中其它行的「发奖提案ID + 来源类型」重复");
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
                .map(r -> new Object[]{r.proposalId(), r.sourceType()}).toList());
        for (int i = 0; i < dataList.size(); i++) {
            PhysicalDeliveryShipImportForm row = dataList.get(i);
            if (row.proposalId() != null && SmartStringUtil.isNotBlank(row.sourceType())
                    && !existMap.containsKey(uniqueKey(row.proposalId(), row.sourceType()))) {
                errorList.add(rowLabel(i) + "找不到对应的履约单（发奖提案ID + 来源类型）");
            }
        }

        if (!errorList.isEmpty()) {
            return ResponseDTO.userErrorParam(describe(errorList));
        }

        for (PhysicalDeliveryShipImportForm row : dataList) {
            PhysicalDelivery exist = existMap.get(uniqueKey(row.proposalId(), row.sourceType()));

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
     * 按 (proposal_id, source_type) 捞出这批 key 命中的履约单。
     *
     * <p>用两个 IN 粗筛再在内存里按复合键收敛：MyBatis-Plus 表达复合键 IN 很别扭，
     * 而导入本来就是几百行量级，粗筛多带回来一些完全无所谓。
     */
    private Map<String, PhysicalDelivery> loadExisting(List<Object[]> keyList) {
        Set<Long> proposalIdSet = new HashSet<>();
        Set<String> sourceTypeSet = new HashSet<>();
        for (Object[] key : keyList) {
            if (key[0] != null) {
                proposalIdSet.add((Long) key[0]);
            }
            if (key[1] != null) {
                sourceTypeSet.add((String) key[1]);
            }
        }
        if (proposalIdSet.isEmpty() || sourceTypeSet.isEmpty()) {
            return Map.of();
        }

        List<PhysicalDelivery> list = physicalDeliveryDao.selectList(new LambdaQueryWrapper<PhysicalDelivery>()
                .in(PhysicalDelivery::getProposalId, proposalIdSet)
                .in(PhysicalDelivery::getSourceType, sourceTypeSet));

        Map<String, PhysicalDelivery> map = new HashMap<>();
        list.forEach(e -> map.put(uniqueKey(e.getProposalId(), e.getSourceType()), e));
        return map;
    }

    private String uniqueKey(Long proposalId, String sourceType) {
        return proposalId + "|" + sourceType;
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
