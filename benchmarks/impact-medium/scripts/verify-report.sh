#!/bin/sh
set -eu

if [ "$#" -ne 6 ]; then
  echo "usage: $0 <overall-report.html> <exit-code.txt> <fixture-project> <call-graph-algorithm> <wala-reflection-options> <dependency-analysis-scope>" >&2
  exit 2
fi

report=$1
exit_code_file=$2
project_root=$3
algorithm=$4
reflection_options=$5
dependency_scope=$6
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

case "$dependency_scope" in
  changed-paths|full) ;;
  *) fail "unsupported dependency analysis scope: $dependency_scope" ;;
esac

[ -f "$exit_code_file" ] || fail "missing exit code file: $exit_code_file"
exit_code=$(tr -d '[:space:]' <"$exit_code_file")
[ "$exit_code" = 0 ] || fail "impact exit code is $exit_code"
[ -f "$report" ] || fail "missing Overall report: $report"

module_dir=${report%.html}-modules
set -- "$module_dir"/*-changes.html
[ "$#" -eq 2 ] && [ -f "$1" ] && [ -f "$2" ] \
  || fail "expected exactly two Dependency Changes pages"
changes_pages="$1 $2"

case "$dependency_scope" in
  changed-paths)
    required_change_kinds="CLASS_REMOVED METHOD_BODY_CHANGED FIELD_DESCRIPTOR_CHANGED"
    hidden_change_kinds="CLASS_ADDED METHOD_ADDED FIELD_ADDED METHOD_REMOVED METHOD_DESCRIPTOR_CHANGED FIELD_REMOVED"
    final_member_text="Method implementation changed"
    grep -q '<th>Status</th><td>Completed with coverage limitations</td>' "$report" \
      || fail "changed-paths Overall status is not INCONCLUSIVE"
    grep -q 'CHANGED_INSTANCE_TO_NO_OP_DEPENDENCY' "$module_dir"/*.html \
      || fail "dangerous transfer evidence is missing"
    grep -q 'FLOW_TO_CAST_FACTORY' "$module_dir"/*.html \
      || fail "flow-to-cast factory evidence is missing"
    grep -q 'path-a.*path-c.*scenario-api' "$module_dir"/*.html \
      || fail "path-a/path-c dependency path is missing"
    grep -q 'path-x.*path-y.*scenario-api' "$module_dir"/*.html \
      || fail "path-x/path-y dependency path is missing"
    grep -q 'path-sibling' "$module_dir"/*.html \
      || fail "unselected sibling policy evidence is missing"
    grep -q 'seed-downstream' "$module_dir"/*.html \
      || fail "seed downstream policy evidence is missing"
    ;;
  full)
    required_change_kinds="CLASS_REMOVED METHOD_REMOVED METHOD_DESCRIPTOR_CHANGED METHOD_BODY_CHANGED FIELD_REMOVED FIELD_DESCRIPTOR_CHANGED"
    hidden_change_kinds="CLASS_ADDED METHOD_ADDED FIELD_ADDED"
    final_member_text="Method removed"
    grep -q '<th>Status</th><td>Completed</td>' "$report" \
      || fail "full Overall status is not Completed"
    grep -q '<th>No-op / factory method nodes</th><td>0 / 0</td>' "$report" \
      || fail "full mode unexpectedly produced no-op or factory nodes"
    grep -q '<th>Dangerous dependency transfers</th><td>0</td>' "$report" \
      || fail "full mode unexpectedly produced dangerous transfer evidence"
    ;;
esac
grep -q '<th>Raw changed members</th><td>18</td>' "$report" \
  || fail "raw changed member count is not 18"
grep -F -q "<th>Algorithm</th><td>$algorithm</td>" "$report" \
  || fail "Call Graph algorithm does not match requested $algorithm"
grep -F -q "<th>WALA ReflectionOptions</th><td>$reflection_options</td>" "$report" \
  || fail "WALA ReflectionOptions does not match requested $reflection_options"
grep -F -q "<th>Requested dependency scope</th><td>$dependency_scope</td>" "$report" \
  || fail "dependency scope does not match requested $dependency_scope"
grep -q 'Structural reference chains' "$module_dir"/*-impact.html \
  || fail "structural reference chain is missing"
grep -q 'View candidate chains filtered as equivalent' "$module_dir"/*-impact.html \
  || fail "filtered candidate chain is missing"

dependency_count=$(awk '
  /<dependencies>/ { in_dependencies = 1; next }
  /<\/dependencies>/ { in_dependencies = 0 }
  in_dependencies && /<dependency>/ { count++ }
  END { print count + 0 }
' "$project_root/application/pom.xml")
[ "$dependency_count" -eq 42 ] \
  || fail "expected 42 direct dependencies; found $dependency_count"

visible_change_kind_count=0
for change_kind in $required_change_kinds; do
  grep -q "<td>$change_kind</td>" $changes_pages \
    || fail "missing Raw ChangePointKind: $change_kind"
  visible_change_kind_count=$((visible_change_kind_count + 1))
done

for hidden_kind in $hidden_change_kinds; do
  ! grep -q "<td>$hidden_kind</td>" $changes_pages \
    || fail "no-path change should be hidden: $hidden_kind"
done

grep -q 'Equivalent (filtered)' $changes_pages \
  || fail "filtered change badge is missing"
grep -q 'View code changes' $changes_pages \
  || fail "code comparison controls are missing"
grep -q 'Decompiled Java representation' $changes_pages \
  || fail "decompiled Java evidence is missing"
grep -q "$final_member_text" $changes_pages \
  || fail "final impacted member is missing: $final_member_text"
grep -q 'Structural impact' $changes_pages \
  || fail "structural impact badge is missing"

[ -f "$expected_results" ] || fail "missing expected results: $expected_results"
expected=$(awk -F '\t' -v requested_scope="$dependency_scope" -v requested_algorithm="$algorithm" '
  $0 !~ /^#/ && NF == 4 && $1 == requested_scope && $2 == requested_algorithm { print $3 "\t" $4; found++ }
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
echo "dependency_analysis_scope=$dependency_scope"
echo "visible_change_kinds=$visible_change_kind_count"
if [ -n "$expected" ]; then
  echo "candidate_final_call_chains=$expected_candidate/$expected_final"
fi
echo "structural_impact=present"
echo "overall_report=$report"
echo "module_reports=$module_dir"
