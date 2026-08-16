#!/bin/sh
set -eu

if [ "$#" -ne 8 ]; then
  echo "usage: $0 <overall-report.html> <exit-code.txt> <fixture-project> <call-graph-algorithm> <wala-reflection-options> <dependency-analysis-scope> <jdk-model> <result-refinement-algorithms>" >&2
  exit 2
fi

report=$1
exit_code_file=$2
project_root=$3
algorithm=$4
reflection_options=$5
dependency_scope=$6
jdk_model=$7
result_refinements=$8
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
expected_results="$script_dir/../expected-results.tsv"

case "$algorithm" in
  cha) ;;
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

case "$jdk_model" in
  none) ;;
  *) fail "unsupported JDK model: $jdk_model" ;;
esac
[ "$algorithm" != cha ] || [ "$jdk_model" = none ] \
  || fail "CHA must use JDK model none"

case "$dependency_scope" in
  changed-paths|full) ;;
  *) fail "unsupported dependency analysis scope: $dependency_scope" ;;
esac

case "$result_refinements" in
  none|ssa-equivalence|cha-local-receiver-inference|cha-local-receiver-inference,ssa-equivalence) ;;
  *) fail "unsupported result refinement selection: $result_refinements" ;;
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
    required_change_kinds="CLASS_REMOVED METHOD_BODY_CHANGED FIELD_DESCRIPTOR_CHANGED SERVICE_PROVIDER_REGISTRATION_REMOVED"
    hidden_change_kinds="CLASS_ADDED METHOD_ADDED FIELD_ADDED METHOD_REMOVED METHOD_DESCRIPTOR_CHANGED FIELD_REMOVED"
    impact_member_text="Method implementation changed"
    grep -q '<th>Status</th><td>Completed with coverage limitations</td>' "$report" \
      || fail "changed-paths Overall status is not INCONCLUSIVE"
    ! grep -q 'DEPENDENCY_BODY_BOUNDARY_REACHED' "$module_dir"/*.html \
      || fail "pruned CHA external target produced boundary evidence"
    grep -q '<th>Ancestor-retained external types / methods</th><td>3 /' \
      "$module_dir"/*.html \
      || fail "CHA external ancestor retention metrics are missing"
    grep -Eq '<th>Pruned external method targets</th><td>[1-9][0-9]*</td>' \
      "$module_dir"/*.html \
      || fail "CHA pruned external target metric is missing"
    for retained_method in \
        ExternalAncestor inheritedPublic helper \
        ExternalGrandParent grandMethod \
        ExternalContract interfaceMethod \
        AncestorRetentionUseCase abstractMethod; do
      grep -R -q --include='*-impact.html' --include='*.js' \
        "$retained_method" "$module_dir" \
        || fail "CHA ancestor-retained path element is missing: $retained_method"
    done
    grep -q 'path-a.*path-c.*scenario-api:jar:2.0.0' \
      "$module_dir"/*.html \
      || fail "path-a/path-c dependency path is missing"
    grep -q 'path-x.*path-y.*scenario-api:jar:2.0.0' \
      "$module_dir"/*.html \
      || fail "path-x/path-y dependency path is missing"
    grep -q 'path-sibling' "$module_dir"/*.html \
      || fail "unselected sibling policy evidence is missing"
    grep -q 'seed-downstream' "$module_dir"/*.html \
      || fail "seed downstream policy evidence is missing"
    ;;
  full)
    required_change_kinds="CLASS_REMOVED METHOD_REMOVED METHOD_DESCRIPTOR_CHANGED METHOD_BODY_CHANGED FIELD_REMOVED FIELD_DESCRIPTOR_CHANGED SERVICE_PROVIDER_REGISTRATION_REMOVED"
    hidden_change_kinds="CLASS_ADDED METHOD_ADDED FIELD_ADDED"
    impact_member_text="Method removed"
    grep -q '<th>Status</th><td>Completed with coverage limitations</td>' "$report" \
      || fail "full CHA Overall status is not INCONCLUSIVE"
    ;;
esac
grep -q '<th>Raw changed members</th><td>22</td>' "$report" \
  || fail "raw changed member count is not 22"
grep -E -q '<th>JAR comparisons in parallel</th><td>[0-9]+ \(configured 2\)</td>' "$report" \
  || fail "JAR comparison parallelism is missing or not pinned to 2"
grep -E -q '<th>Impact queries in parallel</th><td>[0-9]+ \(configured 2\)</td>' "$report" \
  || fail "Impact Query parallelism is missing or not pinned to 2"
grep -E -q '<th>Code comparisons in parallel</th><td>[0-9]+ \(configured 2\)</td>' "$report" \
  || fail "code comparison parallelism is missing or not pinned to 2"
! grep -q '<th>Modules analyzed in parallel</th>' "$report" \
  || fail "removed Module parallelism row is present"
grep -F -q "<th>Algorithm</th><td>$algorithm</td>" "$report" \
  || fail "Call Graph algorithm does not match requested $algorithm"
! grep -F -q '<th>k-object depth</th>' "$report" \
  || fail "CHA report unexpectedly exposes k-object depth"
grep -F -q "<th>WALA ReflectionOptions</th><td>not applied by cha (configured: $reflection_options)</td>" "$report" \
  || fail "CHA ReflectionOptions not-applied state is missing"
grep -F -q "<th>Requested dependency scope</th><td>$dependency_scope</td>" "$report" \
  || fail "dependency scope does not match requested $dependency_scope"
grep -F -q "<th>JDK method model</th><td>$jdk_model</td>" "$report" \
  || fail "JDK method model does not match requested $jdk_model"
grep -R -q --include='*-impact.html' --include='*.js' \
  'RecursiveCallUseCase' "$module_dir" \
  || fail "recursive call impact chain is missing"
grep -R -q --include='*-impact.html' --include='*.js' \
  'Structural reference chains' "$module_dir" \
  || fail "structural reference chain is missing"
grep -R -q --include='*-impact.html' --include='*.js' \
  'CLASS_FOR_NAME_LOCAL_CONSTANT' "$module_dir" \
  || fail "Class.forName local-constant evidence is missing"
grep -R -q --include='*-impact.html' --include='*.js' \
  'SERVICE_LOADER_PROVIDER' "$module_dir" \
  || fail "ServiceLoader provider evidence is missing"
if [ "$algorithm" = cha ]; then
  grep -R -q --include='*-impact.html' --include='*.js' \
    'ObjectDispatchUseCase' "$module_dir" \
    || fail "CHA Object dispatch Diff-related path is missing"
  grep -R -q --include='*-impact.html' --include='*.js' \
    'hashCode' "$module_dir" \
    || fail "CHA Object.hashCode Diff-related path is missing"
  grep -R -q --include='*-impact.html' --include='*.js' \
    'toString' "$module_dir" \
    || fail "CHA Object.toString Diff-related path is missing"
  ! grep -R -q --include='*-impact.html' --include='*.js' \
    'UnrelatedObjectOverride' "$module_dir" \
    || fail "CHA unrelated Object override entered impact paths"
  grep -q 'CLASS_FOR_NAME_LOCAL_CONSTANT_UNRESOLVED' "$module_dir"/*.html \
    || fail "unsupported Class.forName limitation is missing"
  grep -q 'SERVICE_LOADER_LOCAL_CONSTANT_UNRESOLVED' "$module_dir"/*.html \
    || fail "unsupported ServiceLoader limitation is missing"
fi
grep -F -q "<th>Result refinement algorithms</th><td>$result_refinements (experimental)</td>" "$report" \
  || fail "result refinement selection does not match $result_refinements"
case ",$result_refinements," in
  *,ssa-equivalence,*)
    grep -q '<th>SSA equivalence</th><td>enabled (experimental)</td>' "$report" \
      || fail "SSA equivalence is not enabled"
    ;;
  *)
    grep -q '<th>SSA equivalence</th><td>disabled (experimental)</td>' "$report" \
      || fail "SSA equivalence disabled state is missing"
    ;;
esac
case ",$result_refinements," in
  *,cha-local-receiver-inference,*)
    grep -q '<th>CHA local receiver inference</th><td>applied (experimental)</td>' "$report" \
      || fail "CHA local receiver inference applied state is missing"
    grep -R -q --include='*-impact.html' --include='*.js' \
      'ChaLocalReceiverUseCase\$ChangedReceiver' "$module_dir" \
      || fail "ChangedReceiver impact path is missing"
    ! grep -R -q --include='*-impact.html' --include='*.js' \
      'unrelatedReceiverPath' "$module_dir" \
      || fail "infeasible unrelated receiver caller remains in impact paths"
    grep -E -q '<th>CHA receiver edges checked / pruned / unknown</th><td>[0-9]+ / [1-9][0-9]* / [0-9]+</td>' "$module_dir"/*.html \
      || fail "CHA local receiver pruned-edge count is missing"
    ;;
  *)
    grep -q '<th>CHA local receiver inference</th><td>disabled (experimental)</td>' "$report" \
      || fail "CHA local receiver disabled state is missing"
    ;;
esac

dependency_count=$(awk '
  /<dependencies>/ { in_dependencies = 1; next }
  /<\/dependencies>/ { in_dependencies = 0 }
  in_dependencies && /<dependency>/ { count++ }
  END { print count + 0 }
' "$project_root/application/pom.xml")
[ "$dependency_count" -eq 40 ] \
  || fail "expected 40 direct dependencies; found $dependency_count"
! grep -R -q 'scope-conflict-marker' "$module_dir" \
  || fail "test-scope conflict marker entered the analysis report"
[ ! -f "$BENCHMARK_MAVEN_REPO/com/acme/impact/scope/scope-conflict-marker/1.0.0/scope-conflict-marker-1.0.0.jar" ] \
  || fail "test-scope conflict marker binary was requested"
if find "$project_root" -type f \( \
    -name 'dep-tree-cia-*' -o \
    -name 'resolved-artifacts-cia-*' -o \
    -name 'module-*.json' \) -print | grep -q .; then
  fail "dependency evidence temporary files leaked into the fixture repository"
fi

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

grep -q 'View code changes' $changes_pages \
  || fail "code comparison controls are missing"
grep -q 'Decompiled Java representation' $changes_pages \
  || fail "decompiled Java evidence is missing"
grep -q "$impact_member_text" $changes_pages \
  || fail "impacted member is missing: $impact_member_text"
grep -q 'Structural impact' $changes_pages \
  || fail "structural impact badge is missing"

[ -f "$expected_results" ] || fail "missing expected results: $expected_results"
expected=$(awk -F '\t' -v requested_scope="$dependency_scope" -v requested_refinements="$result_refinements" '
  $0 !~ /^#/ && NF == 3 && $1 == requested_scope && $2 == requested_refinements { print $3; found++ }
  END { if (found > 1) exit 2 }
' "$expected_results") || fail "duplicate expected result for $dependency_scope/$result_refinements"

if [ -n "$expected" ]; then
  expected_impact=$expected
  if [ "$expected_impact" = PENDING ]; then
    [ "${BENCHMARK_CALIBRATION:-0}" = 1 ] \
      || fail "pending semantic baseline for $dependency_scope/$jdk_model/$algorithm; run an explicitly authorized calibration matrix"
    echo "expected_count=PENDING_CALIBRATION"
  else
    grep -F -q "<th>Impact / structural records</th><td>$expected_impact /" "$report" \
      || fail "impact call chains do not match $jdk_model/$algorithm/$result_refinements baseline $expected_impact"
  fi
elif [ "${BENCHMARK_CALIBRATION:-0}" != 1 ]; then
  fail "no locked expected count for $jdk_model/$algorithm/$result_refinements; rerun only for review with BENCHMARK_CALIBRATION=1"
else
  echo "expected_count=UNLOCKED_CALIBRATION"
fi

echo "status=SUCCESS"
echo "direct_dependencies=$dependency_count"
echo "raw_change_kinds=10"
echo "dependency_analysis_scope=$dependency_scope"
echo "jdk_model=$jdk_model"
echo "result_refinement_algorithms=$result_refinements"
echo "visible_change_kinds=$visible_change_kind_count"
if [ -n "$expected" ] && [ "$expected_impact" != PENDING ]; then
  echo "impact_call_chains=$expected_impact"
fi
echo "structural_impact=present"
echo "overall_report=$report"
echo "module_reports=$module_dir"
