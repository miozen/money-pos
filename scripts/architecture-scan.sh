#!/usr/bin/env bash
# Stage 4 architecture scan. The default mode is report-only; --check-new rejects
# structural findings outside the v1 baseline and shared-Entity findings outside
# the checked-in ownership/compatibility baselines.
set -u -o pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
source_root="${MONEY_ARCHITECTURE_SOURCE_ROOT:-$repo_root/money-pos/qk-money-app/money-app-biz/src/main/java}"
entity_owner_file="${MONEY_ARCHITECTURE_ENTITY_OWNERSHIP_FILE:-$repo_root/scripts/architecture-baseline/shared-entity-owners.tsv}"
entity_bridge_file="${MONEY_ARCHITECTURE_ENTITY_BRIDGE_FILE:-$repo_root/scripts/architecture-baseline/shared-entity-cross-domain-baseline.tsv}"
entity_wildcard_file="${MONEY_ARCHITECTURE_ENTITY_WILDCARD_FILE:-$repo_root/scripts/architecture-baseline/shared-entity-wildcard-baseline.tsv}"
mode="report"

case "${1:-}" in
  "") ;;
  --check-new) mode="check-new" ;;
  --help|-h)
    echo "Usage: bash scripts/architecture-scan.sh [--check-new]"
    echo "Default mode reports findings; --check-new fails on new structural or shared-Entity findings."
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

for baseline_file in "$entity_owner_file" "$entity_bridge_file" "$entity_wildcard_file"; do
  if [[ ! -r "$baseline_file" ]]; then
    echo "architecture shared-entity baseline is not readable: $baseline_file" >&2
    exit 2
  fi
done

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

baseline_rows() {
  sed -E '/^[[:space:]]*($|#)/d; s/\r$//' "$1"
}

declare -A entity_owners
while IFS=$'\t' read -r entity_name entity_owner; do
  [[ -z "$entity_name" || -z "$entity_owner" ]] && continue
  entity_owners["$entity_name"]="$entity_owner"
done < <(baseline_rows "$entity_owner_file")

declare -A entity_bridge_baseline
while IFS=$'\t' read -r source_feature entity_owner entity_path entity_name import_form; do
  [[ -z "$source_feature" || -z "$entity_owner" || -z "$entity_path" || -z "$entity_name" || -z "$import_form" ]] && continue
  entity_bridge_baseline["$source_feature|$entity_owner|$entity_path|$entity_name|$import_form"]=1
done < <(baseline_rows "$entity_bridge_file")

declare -A entity_wildcard_baseline
while IFS= read -r entity_path; do
  [[ -z "$entity_path" ]] && continue
  entity_wildcard_baseline["$entity_path"]=1
done < <(baseline_rows "$entity_wildcard_file")

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
echo "- Classification: ownership and compatibility-bridge audit; default mode remains report-only."
echo
echo "Reference baseline: docs/MoneyPOS-Architecture-Scan-Baseline-v1.md"

entity_owner_local_findings=""
entity_bridge_findings=""
entity_gate_findings=""
entity_wildcard_findings=""
entity_import_files=$(rg -l '^[[:space:]]*import com\.money\.entity\.' "$source_root/com/money/feature" --glob '*.java' || true)
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  entity_path=$(relative_path "$file")
  source_feature=$(printf '%s\n' "$entity_path" | sed -E 's#^com/money/feature/([^/]+)/.*#\1#')

  while IFS= read -r entity_name; do
    [[ -z "$entity_name" ]] && continue
    entity_owner="${entity_owners[$entity_name]:-}"
    if [[ -z "$entity_owner" ]]; then
      entity_gate_findings+="unregistered Entity: $source_feature → unknown: $entity_path [$entity_name; single]"$'\n'
    elif [[ "$source_feature" == "$entity_owner" ]]; then
      entity_owner_local_findings+="$source_feature: $entity_path [$entity_name]"$'\n'
    else
      entity_key="$source_feature|$entity_owner|$entity_path|$entity_name|single"
      if [[ -n "${entity_bridge_baseline[$entity_key]:-}" ]]; then
        entity_bridge_findings+="$source_feature → $entity_owner: $entity_path [$entity_name; single]"$'\n'
      else
        entity_gate_findings+="new cross-owner Entity: $source_feature → $entity_owner: $entity_path [$entity_name; single]"$'\n'
      fi
    fi
  done < <(sed -nE 's/^[[:space:]]*import com\.money\.entity\.([A-Za-z0-9_]+);/\1/p' "$file")

  if rg -q '^[[:space:]]*import com\.money\.entity\.\*;' "$file"; then
    if [[ -z "${entity_wildcard_baseline[$entity_path]:-}" ]]; then
      entity_gate_findings+="new wildcard Entity import: $source_feature → unknown: $entity_path [*; wildcard]"$'\n'
    else
      entity_wildcard_findings+="$source_feature: $entity_path [wildcard baseline]"$'\n'
      for entity_name in "${!entity_owners[@]}"; do
        if ! rg -q -w "$entity_name" "$file"; then
          continue
        fi
        entity_owner="${entity_owners[$entity_name]}"
        if [[ "$source_feature" == "$entity_owner" ]]; then
          entity_owner_local_findings+="$source_feature: $entity_path [$entity_name; wildcard]"$'\n'
          continue
        fi
        entity_key="$source_feature|$entity_owner|$entity_path|$entity_name|wildcard"
        if [[ -n "${entity_bridge_baseline[$entity_key]:-}" ]]; then
          entity_bridge_findings+="$source_feature → $entity_owner: $entity_path [$entity_name; wildcard]"$'\n'
        else
          entity_gate_findings+="new cross-owner Entity: $source_feature → $entity_owner: $entity_path [$entity_name; wildcard]"$'\n'
        fi
      done
    fi
  fi
done <<< "$entity_import_files"

entity_owner_local_count=$(printf '%s' "$entity_owner_local_findings" | count_lines)
entity_bridge_count=$(printf '%s' "$entity_bridge_findings" | count_lines)
entity_wildcard_count=$(printf '%s' "$entity_wildcard_findings" | count_lines)
entity_gate_count=$(printf '%s' "$entity_gate_findings" | count_lines)

echo "## Shared Entity Ownership"
echo
echo "- Owner-local Entity uses: $entity_owner_local_count"
echo "- Documented cross-owner compatibility bridges: $entity_bridge_count"
echo "- Existing wildcard import files: $entity_wildcard_count"
echo "- New or unregistered Entity findings: $entity_gate_count"
if [[ -n "$entity_bridge_findings" ]]; then
  echo "- Existing cross-owner bridges:"
  printf '%s' "$entity_bridge_findings" | sort -u | sed 's#^#  - #'
fi
if [[ -n "$entity_gate_findings" ]]; then
  echo "- New or unregistered Entity findings:"
  printf '%s' "$entity_gate_findings" | sort -u | sed 's#^#  - #'
fi
echo

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
if [[ "$mode" == "report" ]]; then
  echo "- Shared Entity ownership findings are report-only in this mode."
  echo "Result: report generated; exit status remains 0 by design."
  exit 0
fi

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
if [[ -n "$entity_gate_findings" ]]; then
  gate_failed=1
  echo "- New or unregistered shared Entity findings:"
  printf '%s' "$entity_gate_findings" | sort -u | sed 's#^#  - #'
  echo "  Use an Entity-free owner contract; only a separately reviewed baseline update may retain a compatibility bridge."
fi

if [[ "$gate_failed" -eq 1 ]]; then
  echo "Result: gate failed because new architecture findings were detected." >&2
  exit 1
fi

echo "- No new structural findings relative to v1."
echo "- No new or unregistered shared Entity imports relative to the ownership baseline."
echo "- Shared Entity additions-only gate passed."
echo "Result: additions-only gate passed."
