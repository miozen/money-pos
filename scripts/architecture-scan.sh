#!/usr/bin/env bash
# Stage 4 architecture scan. This is intentionally report-only: findings never fail the build.
set -u -o pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
source_root="$repo_root/money-pos/qk-money-app/money-app-biz/src/main/java"

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

echo "# MoneyPOS Architecture Scan Report (v1)"
echo
echo "Scope: \`money-app-biz/src/main/java\`"
echo "Mode: report only — findings do not change the exit status."
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

platform_findings=$(rg -l '^import com\.money\.feature\.' "$source_root/com/money/platform" 2>/dev/null || true)
platform_count=$(printf '%s\n' "$platform_findings" | count_lines)

echo "## platform → feature"
echo
echo "- Direct imports: $platform_count"
if [[ -n "$platform_findings" ]]; then
  while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    echo "  - $(relative_path "$file")"
  done <<< "$platform_findings"
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
echo "Result: report generated; exit status remains 0 by design."
exit 0
