package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.mall.clientapi.MallClientFacade;
import solvela.marketing.api.MallAddressCmd;
import solvela.marketing.api.MallAddressView;
import solvela.marketing.api.MallApi;
import solvela.marketing.api.MallCategoryView;
import solvela.marketing.api.MallCommodityBriefView;
import solvela.marketing.api.MallCommodityDetailView;
import solvela.marketing.api.MallCommodityPageCmd;
import solvela.marketing.api.MallCommodityPageView;
import solvela.marketing.api.MallOrderView;
import solvela.marketing.api.MallRedeemCmd;
import solvela.marketing.api.MallRedeemResult;

import java.util.List;

/**
 * {@link MallApi} 的 HTTP 薄壳。
 *
 * <p>照 {@code MemberAuthInternalController} 的形状：implements 接口，
 * 路径与方法只在契约里定义一次。控制器里不写业务 —— 装配与可见性判断都在
 * {@link MallClientFacade}。
 *
 * <p>⚠️ 本进程里有两个 {@code MallApi} 类型的 bean（本类与 {@code MallClientFacade}），
 * 所以<b>进程内不要按接口类型注入</b>，要注入就注入实现类。按接口注入的是网关，
 * 那边只有 HTTP 代理一个实现，不存在歧义。
 */
@RestController
@RequiredArgsConstructor
public class MallInternalController implements MallApi {

    private final MallClientFacade mallClientFacade;

    @Override
    public List<MallCategoryView> listCategories() {
        return mallClientFacade.listCategories();
    }

    @Override
    public MallCommodityPageView pageCommodity(MallCommodityPageCmd cmd) {
        return mallClientFacade.pageCommodity(cmd);
    }

    @Override
    public MallCommodityDetailView getCommodity(Long commodityId, Long memberId) {
        return mallClientFacade.getCommodity(commodityId, memberId);
    }

    @Override
    public List<MallCommodityBriefView> listFavorites(Long memberId) {
        return mallClientFacade.listFavorites(memberId);
    }

    @Override
    public void addFavorite(Long commodityId, Long memberId) {
        mallClientFacade.addFavorite(commodityId, memberId);
    }

    @Override
    public void removeFavorite(Long commodityId, Long memberId) {
        mallClientFacade.removeFavorite(commodityId, memberId);
    }

    @Override
    public MallRedeemResult redeem(MallRedeemCmd cmd) {
        return mallClientFacade.redeem(cmd);
    }

    @Override
    public List<MallOrderView> listMyOrders(Long memberId, int limit) {
        return mallClientFacade.listMyOrders(memberId, limit);
    }

    @Override
    public List<MallAddressView> listAddresses(Long memberId) {
        return mallClientFacade.listAddresses(memberId);
    }

    @Override
    public MallAddressView getAddress(Long addressId, Long memberId) {
        return mallClientFacade.getAddress(addressId, memberId);
    }

    @Override
    public MallAddressView createAddress(MallAddressCmd cmd) {
        return mallClientFacade.createAddress(cmd);
    }

    @Override
    public MallAddressView updateAddress(Long addressId, MallAddressCmd cmd) {
        return mallClientFacade.updateAddress(addressId, cmd);
    }

    @Override
    public void deleteAddress(Long addressId, Long memberId) {
        mallClientFacade.deleteAddress(addressId, memberId);
    }

    @Override
    public void setDefaultAddress(Long addressId, Long memberId) {
        mallClientFacade.setDefaultAddress(addressId, memberId);
    }
}
