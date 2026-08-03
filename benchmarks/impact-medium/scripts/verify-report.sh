#!/bin/sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <overall-report.html> <exit-code.txt> <fixture-project>" >&2
  exit 2
fi

report=$1
exit_code_file=$2
project_root=$3

fail() {
  echo "verification failed: $*" >&2
  exit 1
}

[ -f "$exit_code_file" ] || fail "missing exit code file: $exit_code_file"
exit_code=$(tr -d '[:space:]' <"$exit_code_file")
[ "$exit_code" = 0 ] || fail "impact exit code is $exit_code"
[ -f "$report" ] || fail "missing Overall report: $report"

module_dir=${report%.html}-modules
set -- "$module_dir"/*-changes.html
[ "$#" -eq 1 ] && [ -f "$1" ] || fail "expected exactly one Dependency Changes page"
changes_page=$1
module_stem=${changes_page%-changes.html}
module_page="$module_stem.html"
impact_page="$module_stem-impact.html"

[ -f "$module_page" ] || fail "missing Module Index page: $module_page"
[ -f "$impact_page" ] || fail "missing Affected Call Chains page: $impact_page"

grep -q '<th>Status</th><td>Completed</td>' "$report" \
  || fail "Overall status is not Completed"
grep -q '<th>Raw changed members</th><td>9</td>' "$report" \
  || fail "raw changed member count is not 9"
grep -q '<th>Candidate / final call chains</th><td>6 / 6</td>' "$report" \
  || fail "candidate/final call chains are not 6 / 6"
grep -q 'Class structure references' "$impact_page" \
  || fail "class structure reference is missing"

dependency_count=$(awk '
  /<dependencies>/ { in_dependencies = 1; next }
  /<\/dependencies>/ { in_dependencies = 0 }
  in_dependencies && /<dependency>/ { count++ }
  END { print count + 0 }
' "$project_root/pom.xml")
[ "$dependency_count" -eq 42 ] \
  || fail "expected 42 direct dependencies; found $dependency_count"

change_kind_count=0
for change_kind in \
  CLASS_ADDED \
  CLASS_REMOVED \
  METHOD_ADDED \
  METHOD_REMOVED \
  METHOD_DESCRIPTOR_CHANGED \
  METHOD_BODY_CHANGED \
  FIELD_ADDED \
  FIELD_REMOVED \
  FIELD_DESCRIPTOR_CHANGED; do
  grep -q "<td>$change_kind</td>" "$changes_page" \
    || fail "missing Raw ChangePointKind: $change_kind"
  change_kind_count=$((change_kind_count + 1))
done

echo "status=SUCCESS"
echo "direct_dependencies=$dependency_count"
echo "raw_change_kinds=$change_kind_count"
echo "candidate_final_call_chains=6/6"
echo "structural_impact=present"
echo "overall_report=$report"
echo "module_report=$module_page"
echo "impact_report=$impact_page"
echo "changes_report=$changes_page"
