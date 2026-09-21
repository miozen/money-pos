#!/usr/bin/env bash
set -u -o pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
scanner="$repo_root/scripts/architecture-scan.sh"
test_root=$(mktemp -d "${TMPDIR:-/tmp}/money-pos-architecture-scan.XXXXXX")
trap 'rm -rf "$test_root"' EXIT

fixture_source="$test_root/src/main/java"

write_java() {
  local relative_path="$1"
  local content="$2"
  local target="$fixture_source/$relative_path"
  mkdir -p "$(dirname "$target")"
  printf '%s\n' "$content" > "$target"
}

run_case() {
  local name="$1"
  local expected_status="$2"
  local expected_text="$3"
  local output_file="$test_root/$name.out"
  local actual_status

  if MONEY_ARCHITECTURE_SOURCE_ROOT="$fixture_source" "$scanner" --check-new >"$output_file" 2>&1; then
    actual_status=0
  else
    actual_status=$?
  fi

  if [[ "$actual_status" != "$expected_status" ]]; then
    echo "$name: expected exit $expected_status, got $actual_status" >&2
    sed -n '1,240p' "$output_file" >&2
    exit 1
  fi
  if ! rg -Fq "$expected_text" "$output_file"; then
    echo "$name: expected output containing: $expected_text" >&2
    sed -n '1,240p' "$output_file" >&2
    exit 1
  fi
}

write_java 'com/money/feature/sys/application/OwnerLocal.java' $'package fixture;\nimport com.money.entity.SysStrategy;\nclass OwnerLocal { SysStrategy strategy; }'
run_case owner_local 0 'Shared Entity additions-only gate passed.'

write_java 'com/money/feature/trade/application/coupon/CouponRuleManagementService.java' $'package fixture;\nimport com.money.entity.PosCouponRule;\nclass CouponRuleManagementService { PosCouponRule rule; }'
run_case existing_bridge 0 'Documented cross-owner compatibility bridges: 1'

write_java 'com/money/feature/trade/application/NewCrossOwner.java' $'package fixture;\nimport com.money.entity.PosCouponRule;\nclass NewCrossOwner { PosCouponRule rule; }'
run_case new_cross_owner 1 'new cross-owner Entity: trade → ums'
rm -f "$fixture_source/com/money/feature/trade/application/NewCrossOwner.java"

write_java 'com/money/feature/gms/application/NewWildcard.java' $'package fixture;\nimport com.money.entity.*;\nclass NewWildcard { GmsGoods goods; }'
run_case new_wildcard 1 'new wildcard Entity import: gms → unknown'
rm -f "$fixture_source/com/money/feature/gms/application/NewWildcard.java"

write_java 'com/money/feature/gms/application/product/GmsGoodsExcelManager.java' $'package fixture;\nimport com.money.entity.*;\nclass GmsGoodsExcelManager { SysBrandConfig config; }'
run_case existing_wildcard_bridge 0 'Documented cross-owner compatibility bridges: 2'

write_java 'com/money/feature/gms/application/product/GmsGoodsExcelManager.java' $'package fixture;\nimport com.money.entity.*;\nclass GmsGoodsExcelManager { SysStrategy strategy; }'
run_case wildcard_new_cross_owner 1 'new cross-owner Entity: gms → sys'

write_java 'com/money/feature/gms/application/UnknownEntityUse.java' $'package fixture;\nimport com.money.entity.UnknownEntity;\nclass UnknownEntityUse { UnknownEntity value; }'
run_case unregistered_entity 1 'unregistered Entity: gms → unknown'

echo 'architecture-scan script fixtures passed.'
