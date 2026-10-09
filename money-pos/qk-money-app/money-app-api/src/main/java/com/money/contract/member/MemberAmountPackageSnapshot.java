package com.money.contract.member;
import lombok.Data; import java.math.BigDecimal;
@Data public class MemberAmountPackageSnapshot { private String brandId, packageCode, packageName, pricingLevelCode; private BigDecimal purchaseAmount, benefitAmount; }
