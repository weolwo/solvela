package net.lab1024.sa.ledger.transaction.service;

import java.util.List;

import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartCollectionUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.ledger.transaction.dao.MemberAssetTransactionDao;
import net.lab1024.sa.ledger.transaction.domain.entity.MemberAssetTransaction;
import net.lab1024.sa.ledger.transaction.domain.form.MemberAssetTransactionAddForm;
import net.lab1024.sa.ledger.transaction.domain.form.MemberAssetTransactionQueryForm;
import net.lab1024.sa.ledger.transaction.domain.form.MemberAssetTransactionUpdateForm;
import net.lab1024.sa.ledger.transaction.domain.vo.MemberAssetTransactionVO;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 交易明细表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberAssetTransactionService {

    private final MemberAssetTransactionDao memberAssetTransactionDao;

    /**
     * 分页查询
     */
    public PageResult<MemberAssetTransactionVO> queryPage(MemberAssetTransactionQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MemberAssetTransactionVO> list = memberAssetTransactionDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MemberAssetTransactionAddForm addForm) {
        MemberAssetTransaction memberAssetTransaction = SmartBeanUtil.copy(addForm, MemberAssetTransaction.class);
        memberAssetTransactionDao.insert(memberAssetTransaction);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MemberAssetTransactionUpdateForm updateForm) {
        MemberAssetTransaction memberAssetTransaction = SmartBeanUtil.copy(updateForm, MemberAssetTransaction.class);
        memberAssetTransactionDao.updateById(memberAssetTransaction);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (SmartCollectionUtil.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        memberAssetTransactionDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        memberAssetTransactionDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
