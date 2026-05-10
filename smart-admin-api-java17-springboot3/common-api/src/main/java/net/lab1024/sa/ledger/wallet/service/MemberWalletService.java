package net.lab1024.sa.ledger.wallet.service;

import java.util.List;

import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.ledger.wallet.dao.MemberWalletDao;
import net.lab1024.sa.ledger.wallet.domain.entity.MemberWallet;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletAddForm;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import net.lab1024.sa.ledger.wallet.domain.form.MemberWalletUpdateForm;
import net.lab1024.sa.ledger.wallet.domain.vo.MemberWalletVO;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
 * 会员钱包表 Service
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberWalletService {

    private final MemberWalletDao memberWalletDao;

    /**
     * 分页查询
     */
    public PageResult<MemberWalletVO> queryPage(MemberWalletQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<MemberWalletVO> list = memberWalletDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     */
    public ResponseDTO<String> add(MemberWalletAddForm addForm) {
        MemberWallet memberWallet = SmartBeanUtil.copy(addForm, MemberWallet.class);
        memberWalletDao.insert(memberWallet);
        return ResponseDTO.ok();
    }

    /**
     * 更新
     *
     */
    public ResponseDTO<String> update(MemberWalletUpdateForm updateForm) {
        MemberWallet memberWallet = SmartBeanUtil.copy(updateForm, MemberWallet.class);
        memberWalletDao.updateById(memberWallet);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除
     */
    public ResponseDTO<String> batchDelete(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)){
            return ResponseDTO.ok();
        }

        memberWalletDao.deleteBatchIds(idList);
        return ResponseDTO.ok();
    }

    /**
     * 单个删除
     */
    public ResponseDTO<String> delete(Long id) {
        if (null == id){
            return ResponseDTO.ok();
        }

        memberWalletDao.deleteById(id);
        return ResponseDTO.ok();
    }
}
