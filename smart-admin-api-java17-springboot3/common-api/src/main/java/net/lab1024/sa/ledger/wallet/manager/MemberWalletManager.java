package net.lab1024.sa.ledger.wallet.manager;

import net.lab1024.sa.ledger.wallet.dao.MemberWalletDao;
import net.lab1024.sa.ledger.wallet.domain.entity.MemberWallet;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
/**
 * 会员钱包表  Manager
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@Service
public class MemberWalletManager extends ServiceImpl<MemberWalletDao, MemberWallet> {


}
