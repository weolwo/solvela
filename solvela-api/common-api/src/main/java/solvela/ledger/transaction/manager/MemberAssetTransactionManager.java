package solvela.ledger.transaction.manager;

import solvela.ledger.transaction.dao.MemberAssetTransactionDao;
import solvela.ledger.MemberAssetTransaction;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 交易明细表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberAssetTransactionManager extends ServiceImpl<MemberAssetTransactionDao, MemberAssetTransaction> {


}
