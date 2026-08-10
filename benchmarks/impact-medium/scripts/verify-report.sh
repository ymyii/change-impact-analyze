#!/bin/sh
set -eu

if [ "$#" -ne 5 ]; then
  echo "usage: $0 <overall-report.html> <exit-code.txt> <fixture-project> <call-graph-algorithm> <wala-reflection-options>" >&2
  exit 2
fi

report=$1
exit_code_file=$2
project_root=$3
algorithm=$4
reflection_options=$5
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
expected_results="$script_dir/../expected-results.tsv"

case "$algorithm" in
  rta|zero-cfa|optimized-0-1-cfa|1-object-1-call-site) ;;
  *)
    echo "unsupported call graph algorithm: $algorithm" >&2
    exit 2
    ;;
esac

case "${BENCHMARK_CALIBRATION:-0}" in
  0|1) ;;
  *)
    echo "BENCHMARK_CALIBRATION must be 0 or 1" >&2
    exit 2
    ;;
esac

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
grep -F -q "<th>Algorithm</th><td>$algorithm</td>" "$report" \
  || fail "Call Graph algorithm does not match requested $algorithm"
grep -F -q "<th>WALA ReflectionOptions</th><td>$reflection_options</td>" "$report" \
  || fail "WALA ReflectionOptions does not match requested $reflection_options"
grep -q 'Structural reference chains' "$impact_page" \
  || fail "structural reference chain is missing"
grep -q 'View candidate chains filtered as equivalent' "$impact_page" \
  || fail "filtered candidate chain is missing"

dependency_count=$(awk '
  /<dependencies>/ { in_dependencies = 1; next }
  /<\/dependencies>/ { in_dependencies = 0 }
  in_dependencies && /<dependency>/ { count++ }
  END { print count + 0 }
' "$project_root/pom.xml")
[ "$dependency_count" -eq 42 ] \
  || fail "expected 42 direct dependencies; found $dependency_count"

visible_change_kind_count=0
for change_kind in \
  CLASS_REMOVED \
  METHOD_REMOVED \
  METHOD_DESCRIPTOR_CHANGED \
  METHOD_BODY_CHANGED \
  FIELD_REMOVED \
  FIELD_DESCRIPTOR_CHANGED; do
  grep -q "<td>$change_kind</td>" "$changes_page" \
    || fail "missing Raw ChangePointKind: $change_kind"
  visible_change_kind_count=$((visible_change_kind_count + 1))
done

for hidden_kind in CLASS_ADDED METHOD_ADDED FIELD_ADDED; do
  ! grep -q "<td>$hidden_kind</td>" "$changes_page" \
    || fail "no-path change should be hidden: $hidden_kind"
done

grep -q 'Equivalent (filtered)' "$changes_page" \
  || fail "filtered change badge is missing"
grep -q 'View code changes' "$changes_page" \
  || fail "code comparison controls are missing"
grep -q 'Decompiled Java representation' "$changes_page" \
  || fail "decompiled Java evidence is missing"
grep -q 'Method removed' "$changes_page" \
  || fail "final impacted member is missing"
grep -q 'Structural impact' "$changes_page" \
  || fail "structural impact badge is missing"

[ -f "$expected_results" ] || fail "missing expected results: $expected_results"
expected=$(awk -F '\t' -v requested="$algorithm" '
  $0 !~ /^#/ && NF == 3 && $1 == requested { print $2 "\t" $3; found++ }
  END { if (found > 1) exit 2 }
' "$expected_results") || fail "duplicate expected result for $algorithm"

if [ -n "$expected" ]; then
  expected_candidate=$(printf '%s\n' "$expected" | awk -F '\t' '{print $1}')
  expected_final=$(printf '%s\n' "$expected" | awk -F '\t' '{print $2}')
  grep -F -q "<th>Candidate / final call chains</th><td>$expected_candidate / $expected_final</td>" "$report" \
    || fail "candidate/final call chains do not match $algorithm baseline $expected_candidate / $expected_final"
elif [ "${BENCHMARK_CALIBRATION:-0}" != 1 ]; then
  fail "no locked expected count for $algorithm; rerun only for review with BENCHMARK_CALIBRATION=1"
else
  echo "expected_count=UNLOCKED_CALIBRATION"
fi

echo "status=SUCCESS"
echo "direct_dependencies=$dependency_count"
echo "raw_change_kinds=9"
echo "visible_change_kinds=$visible_change_kind_count"
if [ -n "$expected" ]; then
  echo "candidate_final_call_chains=$expected_candidate/$expected_final"
fi
echo "structural_impact=present"
echo "overall_report=$report"
echo "module_report=$module_page"
echo "impact_report=$impact_page"
echo "changes_report=$changes_page"
