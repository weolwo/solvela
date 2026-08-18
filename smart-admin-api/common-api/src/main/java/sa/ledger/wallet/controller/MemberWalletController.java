package sa.ledger.wallet.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sa.base.common.domain.PageResult;
import sa.base.common.domain.ResponseDTO;
import sa.ledger.stat.domain.form.LedgerStatForm;
import sa.ledger.wallet.domain.form.MemberWalletQueryForm;
import sa.ledger.wallet.domain.vo.MemberWalletStatVO;
import sa.ledger.wallet.domain.vo.MemberWalletVO;
import sa.ledger.wallet.service.MemberWalletService;

/**
 * 会员钱包表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员钱包表")
@RequestMapping("/memberWallet")
public class MemberWalletController {

    private final MemberWalletService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("memberWallet:query")
    public ResponseDTO<PageResult<MemberWalletVO>> queryPage(@RequestBody @Valid MemberWalletQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "钱包统计：资产存量（全量）+ 本期变动（默认当天，取自交易明细）")
    @PostMapping("/stat")
    @SaCheckPermission("memberWallet:query")
    public ResponseDTO<MemberWalletStatVO> stat(@RequestBody @Valid LedgerStatForm form) {
        return ResponseDTO.ok(Service.stat(form));
    }

    /*
     * 生成器给的「添加 / 更新 / 批量删除 / 单个删除」四个接口已整组移除（v3.69.0）。
     *
     * 钱包这张表尤其不能开写口子：/memberWallet/update 能<b>直接改余额且不写任何流水</b>，
     * 改完余额与 t_member_asset_transaction 就永久对不上，
     * 而统计面板的体检项也发现不了（它只查得出余额为负这种明显异常）。
     * 余额的唯一合法入口是钱包服务里那几个"改余额 + 落流水"绑在同一个事务里的方法。
     *
     * delete 原先还是物理删除，删掉一个账户等于把这个人的资产凭空抹掉。
     */
}
