#!/usr/bin/env bash
# Stage 4 architecture scan. The default mode is report-only; --check-new rejects
# only findings not present in the checked-in v1 baseline.
set -u -o pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
source_root="$repo_root/money-pos/qk-money-app/money-app-biz/src/main/java"
mode="report"

case "${1:-}" in
  "") ;;
  --check-new) mode="check-new" ;;
  --help|-h)
    echo "Usage: bash scripts/architecture-scan.sh [--check-new]"
    echo "Default mode reports findings; --check-new fails only on findings new to v1."
    exit 0
    ;;
  *)
    echo "unknown option: $1 (use --help)" >&2
    exit 2
    ;;
esac

if ! command -v rg >/dev/null 2>&1; then
  echo "architecture scan requires ripgrep (rg)" >&2
  exit 2
fi

if [[ ! -d "$source_root" ]]; then
  echo "architecture source root not found: $source_root" >&2
  exit 2
fi

relative_path() {
  printf '%s\n' "${1#"$source_root"/}"
}

count_lines() {
  sed '/^$/d' | wc -l | tr -d ' '
}

new_findings() {
  local current="$1"
  local baseline="$2"
  comm -23 \
    <(printf '%s' "$current" | sed '/^$/d' | sort -u) \
    <(printf '%s' "$baseline" | sed '/^$/d' | sort -u)
}

echo "# MoneyPOS Architecture Scan Report (v1)"
echo
echo "Scope: \`money-app-biz/src/main/java\`"
if [[ "$mode" == "check-new" ]]; then
  echo "Mode: additions-only gate — baseline findings are allowed; new findings fail."
else
  echo "Mode: report only — findings do not change the exit status."
fi
echo

controller_files=$(rg -l '@(RestController|Controller)' "$source_root" || true)
controller_count=$(printf '%s\n' "$controller_files" | count_lines)
controller_mapper_files=""
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  if rg -q '^import .*Mapper;' "$file"; then
    controller_mapper_files+="$(relative_path "$file")"$'\n'
  fi
done <<< "$controller_files"
controller_mapper_count=$(printf '%s' "$controller_mapper_files" | count_lines)

echo "## Controller → Mapper"
echo
echo "- Controllers scanned: $controller_count"
echo "- Direct Mapper imports: $controller_mapper_count"
if [[ -n "$controller_mapper_files" ]]; then
  printf '%s' "$controller_mapper_files" | sort | sed 's#^#  - #'
else
  echo "  - none"
fi
echo

cross_feature_findings=""
feature_import_files=$(rg -l '^import com\.money\.feature\.[^.]+\..*(ServiceImpl|Mapper);' "$source_root/com/money/feature" || true)
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  source_feature=$(printf '%s\n' "$file" | sed -E 's#.*?/com/money/feature/([^/]+)/.*#\1#')
  while IFS= read -r import_line; do
    [[ -z "$import_line" ]] && continue
    target_feature=$(printf '%s\n' "$import_line" | sed -E 's#import com\.money\.feature\.([^.]+)\..*#\1#')
    if [[ "$source_feature" != "$target_feature" ]]; then
      cross_feature_findings+="$source_feature → $target_feature: $(relative_path "$file")"$'\n'
    fi
  done < <(rg -o '^import com\.money\.feature\.[^.]+\..*(ServiceImpl|Mapper);' "$file" || true)
done <<< "$feature_import_files"
cross_feature_count=$(printf '%s' "$cross_feature_findings" | count_lines)

echo "## Cross-Feature ServiceImpl / Mapper"
echo
echo "- Direct imports: $cross_feature_count"
if [[ -n "$cross_feature_findings" ]]; then
  printf '%s' "$cross_feature_findings" | sort | sed 's#^#  - #'
else
  echo "  - none"
fi
echo

cross_feature_application_findings=""
all_feature_import_files=$(rg -l '^import com\.money\.feature\.[^.]+\.' "$source_root/com/money/feature" || true)
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  source_feature=$(printf '%s\n' "$file" | sed -E 's#.*?/com/money/feature/([^/]+)/.*#\1#')
  while IFS= read -r import_line; do
    [[ -z "$import_line" ]] && continue
    target_feature=$(printf '%s\n' "$import_line" | sed -E 's#import com\.money\.feature\.([^.]+)\..*#\1#')
    if [[ "$source_feature" != "$target_feature" ]]; then
      cross_feature_application_findings+="$source_feature → $target_feature: $(relative_path "$file")"$'\n'
    fi
  done < <(rg '^import com\.money\.feature\.[^.]+\.' "$file" || true)
done <<< "$all_feature_import_files"
cross_feature_application_count=$(printf '%s' "$cross_feature_application_findings" | count_lines)

echo "## Cross-Feature Implementation Imports"
echo
echo "- Direct imports: $cross_feature_application_count"
if [[ -n "$cross_feature_application_findings" ]]; then
  printf '%s' "$cross_feature_application_findings" | sort -u | sed 's#^#  - #'
else
  echo "  - none"
fi
echo

platform_findings=$(rg -l '^import com\.money\.feature\.' "$source_root/com/money/platform" 2>/dev/null || true)
platform_count=$(printf '%s\n' "$platform_findings" | count_lines)
platform_relative_findings=""
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  platform_relative_findings+="$(relative_path "$file")"$'\n'
done <<< "$platform_findings"

echo "## platform → feature"
echo
echo "- Direct imports: $platform_count"
if [[ -n "$platform_findings" ]]; then
  printf '%s' "$platform_relative_findings" | sort | sed 's#^#  - #'
else
  echo "  - none"
fi
echo

legacy_entity_files=$(rg -l '^import com\.money\.entity\.' "$source_root/com/money/feature" || true)
legacy_entity_count=$(printf '%s\n' "$legacy_entity_files" | count_lines)

echo "## Legacy Shared Entity Imports"
echo
echo "- Feature files importing \`com.money.entity\`: $legacy_entity_count"
echo "- Classification: tracked compatibility debt; not a blocking rule until ownership and DTO slices are migrated."
echo
echo "Reference baseline: docs/MoneyPOS-Architecture-Scan-Baseline-v1.md"

if [[ "$mode" == "report" ]]; then
  echo "Result: report generated; exit status remains 0 by design."
  exit 0
fi

# v1 baseline: later migrations may remove these entries, but only these original
# Controller-to-Mapper findings remain allowed. The other two structural rules
# were clean in v1 and therefore allow no findings.
baseline_controller_mapper_files=$'com/money/controller/PosCouponRuleController.java\ncom/money/controller/SysStrategyController.java\ncom/money/feature/gms/interfaces/rest/GmsBrandConfigController.java\ncom/money/feature/gms/interfaces/rest/GmsGoodsExcelController.java\ncom/money/feature/gms/interfaces/rest/GmsStockLogController.java\ncom/money/feature/ums/interfaces/rest/UmsMemberController.java\ncom/money/feature/ums/interfaces/rest/UmsMemberImportController.java\n'
baseline_cross_feature_application=$'fin → ums: com/money/feature/fin/application/dashboard/FinanceDashboardServiceImpl.java\nhome → gms: com/money/feature/home/application/DecisionEngineServiceImpl.java\nhome → gms: com/money/feature/home/application/HomeServiceImpl.java\ntrade → fin: com/money/feature/trade/interfaces/rest/OmsOrderController.java\ntrade → gms: com/money/feature/trade/application/pos/PosServiceImpl.java\nums → gms: com/money/feature/ums/application/member/UmsMemberAssetExcelExportService.java\nums → gms: com/money/feature/ums/application/member/UmsMemberExcelTemplateService.java\nums → gms: com/money/feature/ums/application/member/UmsMemberProfileService.java\n'
new_controller_mapper=$(new_findings "$controller_mapper_files" "$baseline_controller_mapper_files")
new_cross_feature=$(new_findings "$cross_feature_findings" "")
new_cross_feature_application=$(new_findings "$cross_feature_application_findings" "$baseline_cross_feature_application")
new_platform=$(new_findings "$platform_relative_findings" "")

echo
echo "## Additions-only gate"
echo

gate_failed=0
if [[ -n "$new_controller_mapper" ]]; then
  gate_failed=1
  echo "- New Controller → Mapper findings:"
  printf '%s\n' "$new_controller_mapper" | sed 's#^#  - #'
fi
if [[ -n "$new_cross_feature" ]]; then
  gate_failed=1
  echo "- New cross-Feature ServiceImpl / Mapper findings:"
  printf '%s\n' "$new_cross_feature" | sed 's#^#  - #'
fi
if [[ -n "$new_cross_feature_application" ]]; then
  gate_failed=1
  echo "- New cross-Feature implementation imports:"
  printf '%s\n' "$new_cross_feature_application" | sed 's#^#  - #'
fi
if [[ -n "$new_platform" ]]; then
  gate_failed=1
  echo "- New platform → feature findings:"
  printf '%s\n' "$new_platform" | sed 's#^#  - #'
fi

if [[ "$gate_failed" -eq 1 ]]; then
  echo "Result: gate failed because new structural findings were detected." >&2
  exit 1
fi

echo "- No new structural findings relative to v1."
echo "- Shared Entity imports remain report-only."
echo "Result: additions-only gate passed."
