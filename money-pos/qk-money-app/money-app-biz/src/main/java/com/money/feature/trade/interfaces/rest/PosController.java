package com.money.feature.trade.interfaces.rest;

import com.money.dto.pos.PosGoodsVO;
import com.money.dto.pos.PosMemberVO;
import com.money.dto.pos.PricingResult;
import com.money.dto.pos.SettleAccountsDTO;
import com.money.dto.pos.SettleResultVO;
import com.money.dto.pos.MemberAmountPackagePurchaseDTO;
import com.money.dto.pos.MemberAmountPackagePurchaseVO;
import com.money.dto.pos.MemberAmountPickupDTO;
import com.money.dto.pos.MemberAmountPickupVO;
import com.money.dto.pos.MemberAmountPickupPreviewDTO;
import com.money.dto.pos.MemberAmountPickupPreviewVO;
import com.money.dto.pos.MemberAmountPickupRefundDTO;
import com.money.dto.pos.MemberAmountPickupRefundVO;
import com.money.dto.pos.SettleTrialReqDTO;
import com.money.feature.trade.application.pos.PosService;
import com.money.feature.trade.application.memberpickup.DeferredQuantityPickupService;
import com.money.feature.trade.application.memberpickup.MemberQuantityPickupReceiptService;
import com.money.feature.trade.application.memberpickup.MemberAmountBenefitService;
import com.money.feature.trade.application.membertarget.MemberTargetBenefitService;
import com.money.feature.trade.application.boundary.facade.PosPricingFacade;
import com.money.service.printer.PosPrinterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "pos", description = "收银")
@RestController
@RequestMapping("/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;
    private final DeferredQuantityPickupService deferredQuantityPickupService;
    private final MemberQuantityPickupReceiptService memberQuantityPickupReceiptService;
    private final PosPrinterService posPrinterService;
    private final MemberAmountBenefitService memberAmountBenefitService;
    private final MemberTargetBenefitService memberTargetBenefitService;
    private final PosPricingFacade posPricingFacade;

    @Operation(summary = "商品列表")
    @GetMapping("/goods")
    @PreAuthorize("@rbac.hasPermission('pos:cashier', 'memberBenefit:operate', 'memberBenefit:manage')")
    public List<PosGoodsVO> listGoods(String barcode) {
        return posService.listGoods(barcode);
    }

    @Operation(summary = "会员列表")
    @GetMapping("/members")
    @PreAuthorize("@rbac.hasPermission('pos:cashier')")
    public List<PosMemberVO> listMember(String member) {
        return posService.listMember(member);
    }

    @Operation(summary = "收款 (正式落库)")
    @PostMapping("/settleAccounts")
    @PreAuthorize("@rbac.hasPermission('pos:cashier')")
    public SettleResultVO settleAccounts(@Validated @RequestBody SettleAccountsDTO settleAccountsDTO) {
        return posService.settleAccounts(settleAccountsDTO);
    }

    @Operation(summary = "会员数量权益购买（延迟提货）")
    @PostMapping("/deferred-quantity/settle")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public SettleResultVO settleDeferredQuantity(@Validated @RequestBody SettleAccountsDTO settleAccountsDTO) {
        return posService.settleDeferredQuantity(settleAccountsDTO);
    }

    @Operation(summary = "会员数量权益寄存试算（不校验实体库存）")
    @PostMapping("/deferred-quantity/trial")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public PricingResult trialDeferredQuantity(@RequestBody SettleTrialReqDTO dto) {
        return posPricingFacade.priceDeferredQuantity(dto);
    }

    @Operation(summary = "会员数量权益提货")
    @PostMapping("/deferred-quantity/pickup")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public com.money.dto.pos.DeferredQuantityPickupVO pickupDeferredQuantity(@Validated @RequestBody com.money.dto.pos.DeferredQuantityPickupDTO dto) {
        return deferredQuantityPickupService.pickup(dto);
    }

    @Operation(summary = "打印会员数量权益提货单")
    @PostMapping("/deferred-quantity/pickup-receipt")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public Boolean printDeferredQuantityPickupReceipt(@RequestParam String pickupNo) {
        posPrinterService.printMemberQuantityPickupReceipt(memberQuantityPickupReceiptService.receipt(pickupNo));
        return true;
    }

    @Operation(summary = "会员金额权益包购买")
    @PostMapping("/amount-package/purchase")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public MemberAmountPackagePurchaseVO purchaseAmountPackage(@Validated @RequestBody MemberAmountPackagePurchaseDTO dto) {
        return memberAmountBenefitService.purchase(dto);
    }

    @Operation(summary = "会员金额权益提货与补差")
    @PostMapping("/amount-package/pickup")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public MemberAmountPickupVO pickupAmountPackage(@Validated @RequestBody MemberAmountPickupDTO dto) {
        return memberAmountBenefitService.pickup(dto);
    }

    @Operation(summary = "会员金额权益提货预览")
    @PostMapping("/amount-package/pickup-preview")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public MemberAmountPickupPreviewVO previewAmountPackagePickup(@Validated @RequestBody MemberAmountPickupPreviewDTO dto) {
        return memberAmountBenefitService.preview(dto);
    }

    @Operation(summary = "会员金额权益提货整笔退款")
    @PostMapping("/amount-package/pickup-refund")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:manage')")
    public MemberAmountPickupRefundVO refundAmountPickup(@Validated @RequestBody MemberAmountPickupRefundDTO dto) {
        return memberAmountBenefitService.refund(dto);
    }

    @Operation(summary = "会员TARGET即时结算")
    @PostMapping("/target/settle")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public SettleResultVO settleTarget(@Validated @RequestBody com.money.dto.pos.MemberTargetSettleDTO dto) {
        return memberTargetBenefitService.settle(dto);
    }

    @Operation(summary = "会员TARGET补差")
    @PostMapping("/target/supplement")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public com.money.dto.pos.MemberTargetReceiptVO supplementTarget(@Validated @RequestBody com.money.dto.pos.MemberTargetAdjustmentDTO dto) {
        return memberTargetBenefitService.supplement(dto);
    }

    @Operation(summary = "会员TARGET人工豁免")
    @PostMapping("/target/waive")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:operate')")
    public com.money.dto.pos.MemberTargetReceiptVO waiveTarget(@Validated @RequestBody com.money.dto.pos.MemberTargetAdjustmentDTO dto) {
        return memberTargetBenefitService.waive(dto);
    }

    @Operation(summary = "会员TARGET达标人工确认")
    @PostMapping("/target/confirm")
    @PreAuthorize("@rbac.hasPermission('memberBenefit:manage')")
    public com.money.dto.pos.MemberTargetConfirmVO confirmTarget(@Validated @RequestBody com.money.dto.pos.MemberTargetConfirmDTO dto) {
        return memberTargetBenefitService.confirm(dto);
    }

    @Operation(summary = "收银台实时试算 (不落库/防抖调用)")
    @PostMapping("/trial")
    @PreAuthorize("@rbac.hasPermission('pos:cashier')")
    public PricingResult trialCalculate(@Validated @RequestBody SettleTrialReqDTO req) {
        return posPricingFacade.trial(req);
    }
}
