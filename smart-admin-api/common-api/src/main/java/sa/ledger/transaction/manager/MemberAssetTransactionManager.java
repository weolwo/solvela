package sa.ledger.transaction.manager;

import sa.ledger.transaction.dao.MemberAssetTransactionDao;
import sa.ledger.transaction.domain.entity.MemberAssetTransaction;

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
