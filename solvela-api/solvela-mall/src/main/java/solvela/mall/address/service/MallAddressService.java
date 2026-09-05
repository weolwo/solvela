package solvela.mall.address.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.exception.BusinessException;
import solvela.mall.MallAddress;
import solvela.mall.address.dao.MallAddressDao;

import java.util.List;

/**
 * 收货地址簿。
 *
 * <h3>🔴 三列是密文，加解密钉在 JDBC 边界上</h3>
 * {@code receiverName} / {@code receiverPhone} / {@code detailAddress} 在
 * {@link MallAddress} 上挂了 {@code PiiTypeHandler}，所以本类里拿到的一律是<b>明文</b>，
 * 写下去自动变密文 —— 写入路径没有「忘记加密」这个选项。
 *
 * <p>脱敏是<b>展示层</b>的事（接入层解密后截成 138****8000），
 * 这一层不做，也不存第二份脱敏值 —— 存两份就又回到「同一份个人信息存两处」。
 *
 * <h3>「最多一条默认」由服务端保证</h3>
 * 设默认是<b>一个事务做完两件事</b>（旧的取消、新的置上）。让调用方调两次的话，
 * 中间断网会留下两个默认地址，或者一个都没有。
 */
@Service
@RequiredArgsConstructor
public class MallAddressService {

    /**
     * 一个会员最多存几条地址。
     *
     * <p>不设上限的话，一个脚本就能往这张表里灌几十万行<b>密文 PII</b> ——
     * 既是存储问题也是合规问题。二十条对真实用户绰绰有余。
     */
    private static final int MAX_PER_MEMBER = 20;

    private final MallAddressDao mallAddressDao;

    /**
     * 我的地址，<b>默认那条排最前</b>，其余按更新时间倒序（刚改过的更可能是要用的）。
     *
     * <p>这个顺序就是「默认」对调用方的全部含义：兑换页取第 0 条作预选。
     */
    public List<MallAddress> listByMember(Long memberId) {
        if (memberId == null) {
            return List.of();
        }
        return mallAddressDao.selectList(new LambdaQueryWrapper<MallAddress>()
                .eq(MallAddress::getMemberId, memberId)
                .orderByDesc(MallAddress::getIsDefault)
                .orderByDesc(MallAddress::getUpdateTime)
                .orderByDesc(MallAddress::getId));
    }

    /**
     * 取一条。<b>必须带 memberId</b> —— 只按 addressId 查等于「谁知道 id 谁就能看到别人的地址」，
     * 而 id 是自增的、可枚举。
     *
     * @return 不存在或不属于这个会员，一律 null（不给探测者任何区分信号）
     */
    public MallAddress getOwned(Long addressId, Long memberId) {
        if (addressId == null || memberId == null) {
            return null;
        }
        return mallAddressDao.selectOne(new LambdaQueryWrapper<MallAddress>()
                .eq(MallAddress::getId, addressId)
                .eq(MallAddress::getMemberId, memberId));
    }

    /**
     * 新增。<b>第一条地址自动成为默认</b> —— 让用户为了用它还要多点一次「设为默认」是无谓的。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallAddress create(MallAddress form) {
        long count = mallAddressDao.selectCount(new LambdaQueryWrapper<MallAddress>()
                .eq(MallAddress::getMemberId, form.getMemberId()));
        if (count >= MAX_PER_MEMBER) {
            throw new BusinessException("最多只能保存 " + MAX_PER_MEMBER + " 条收货地址");
        }
        form.setId(null);
        form.setIsDefault(count == 0);
        mallAddressDao.insert(form);
        return form;
    }

    /**
     * 编辑。
     *
     * <p>⚠️ {@code receiverPhone} 为空表示<b>不修改手机号</b>：列表下发的是脱敏值，
     * 端上不该把 {@code 138****8000} 回填给用户改 —— 一提交就把星号存进库了。
     *
     * <p>{@code isDefault} 不在这里改，走 {@link #setDefault} —— 那件事要动两行。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallAddress update(Long addressId, MallAddress form) {
        MallAddress existing = getOwned(addressId, form.getMemberId());
        if (existing == null) {
            throw new BusinessException("收货地址不存在");
        }
        existing.setReceiverName(form.getReceiverName());
        if (form.getReceiverPhone() != null && !form.getReceiverPhone().isBlank()) {
            existing.setReceiverPhone(form.getReceiverPhone());
        }
        existing.setProvince(form.getProvince());
        existing.setCity(form.getCity());
        existing.setDistrict(form.getDistrict());
        existing.setDetailAddress(form.getDetailAddress());
        mallAddressDao.updateById(existing);
        return existing;
    }

    /**
     * 删除。
     *
     * <p>🔴 <b>删掉的如果是默认地址，要把剩下的第一条置默认</b> ——
     * 不能让账号进入「一条地址都不默认」的状态，那会让兑换页每次都要用户重选，
     * 而用户根本不知道自己刚才删掉的是默认那条。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long addressId, Long memberId) {
        MallAddress existing = getOwned(addressId, memberId);
        if (existing == null) {
            // 已经不在了就是想要的结果，不报错 —— 重复点删除是常态
            return;
        }
        mallAddressDao.deleteById(addressId);
        if (!Boolean.TRUE.equals(existing.getIsDefault())) {
            return;
        }
        List<MallAddress> rest = listByMember(memberId);
        if (!rest.isEmpty()) {
            setDefault(rest.getFirst().getId(), memberId);
        }
    }

    /**
     * 设为默认。<b>一个事务做完两件事</b>，理由见类注释。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long addressId, Long memberId) {
        if (getOwned(addressId, memberId) == null) {
            throw new BusinessException("收货地址不存在");
        }
        // 先全置 0 再置 1：不能先置 1 —— 中间那一瞬有两条默认，
        // 而这个方法是可能被并发调的（用户连点两条）
        mallAddressDao.update(null, new LambdaUpdateWrapper<MallAddress>()
                .eq(MallAddress::getMemberId, memberId)
                .set(MallAddress::getIsDefault, false));
        mallAddressDao.update(null, new LambdaUpdateWrapper<MallAddress>()
                .eq(MallAddress::getId, addressId)
                .eq(MallAddress::getMemberId, memberId)
                .set(MallAddress::getIsDefault, true));
    }
}
