package solvela.ledger;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.enums.WalletStatusEnum;
import solvela.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberWalletTest {

    @Test
    @DisplayName("新钱包落为正常、可用；不能再定一次")
    void 新钱包() {
        MemberWallet wallet = new MemberWallet();
        wallet.openNormal();
        assertEquals(WalletStatusEnum.NORMAL, wallet.getStatus());
        assertDoesNotThrow(wallet::checkAvailable);
        assertThrows(IllegalStateException.class, wallet::openNormal);
    }

    @Test
    @DisplayName("🔴 读库出来是冻结的钱包不可用；没有 setter 时 MyBatis 仍能写入 status")
    void 冻结不可用() {
        MemberWallet wallet = new MemberWallet();
        MetaObject meta = SystemMetaObject.forObject(wallet);
        assertTrue(meta.hasSetter("status"), "MyBatis 认为 status 不可写，读库时会静默丢掉这一列");
        meta.setValue("status", WalletStatusEnum.FROZEN);
        assertThrows(BusinessException.class, wallet::checkAvailable);
    }
}
