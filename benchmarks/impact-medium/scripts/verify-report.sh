#!/bin/sh
set -eu

if [ "$#" -ne 7 ]; then
  echo "usage: $0 <overall-report.html> <exit-code.txt> <fixture-project> <call-graph-algorithm> <wala-reflection-options> <dependency-analysis-scope> <jdk-model>" >&2
  exit 2
fi

report=$1
exit_code_file=$2
project_root=$3
algorithm=$4
reflection_options=$5
dependency_scope=$6
jdk_model=$7
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
grep -F -q '<th>Dependency includes</th><td>[com.acme.impact:scenario-api]</td>' "$report" \
  || fail "dependency include selector is missing"
grep -F -q '<th>Dependency excludes</th><td>[]</td>' "$report" \
  || fail "dependency exclude selector state is missing"
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
grep -q '"schemaVersion":4' "$module_dir"/*-impact.html \
  || fail "Affected Paths Schema 4 manifest is missing"
grep -q 'id="path-search-form"' "$module_dir"/*-impact.html \
  || fail "Affected Paths explicit Search form is missing"
grep -q 'id="path-dependency-include"' "$module_dir"/*-impact.html \
  || fail "Affected Paths dependency include control is missing"
grep -q 'id="path-dependency-exclude"' "$module_dir"/*-impact.html \
  || fail "Affected Paths dependency exclude control is missing"
find "$module_dir" -type f -name 'source-index-*.js' -print \
  | grep -q . || fail "Affected Paths source-range index is missing"
! grep -q 'searchInput.addEventListener("input"' "$module_dir"/*-impact.html \
  || fail "Affected Paths search still scans while typing"
if [ "$algorithm" = cha ]; then
  ! grep -R -q --include='*-impact.html' --include='*.js' \
    'UnrelatedObjectOverride' "$module_dir" \
    || fail "CHA unrelated Object override entered impact paths"
  grep -q 'CHA does not expand JDK-declared virtual or interface dispatch' \
    "$module_dir"/*.html \
    || fail "CHA JDK dispatch limitation is missing"
  grep -E -q '<th>JDK-declared dispatch targets pruned</th><td>[1-9][0-9]*</td>' \
    "$module_dir"/*.html \
    || fail "CHA JDK dispatch pruning metric is missing"
  grep -q 'CLASS_FOR_NAME_LOCAL_CONSTANT_UNRESOLVED' "$module_dir"/*.html \
    || fail "unsupported Class.forName limitation is missing"
  grep -q 'SERVICE_LOADER_LOCAL_CONSTANT_UNRESOLVED' "$module_dir"/*.html \
    || fail "unsupported ServiceLoader limitation is missing"
fi
! grep -q '<th>Result refinement algorithms</th>' "$report" \
  || fail "removed result refinement selection remains in report"
grep -q '<th>Method body equivalence order</th><td>Decompiled Java first; normalized SSA on miss</td>' "$report" \
  || fail "Java-first method body equivalence order is missing"
for extension in \
    cha-local-receiver-inference; do
  grep -E -q "<th>$extension</th><td>applied \(experimental\); edges checked / pruned / unknown: [0-9]+ / [0-9]+ / [0-9]+</td>" \
    "$module_dir"/*.html \
    || fail "fixed CHA pruning extension metrics are missing: $extension"
done
grep -R -q --include='*-impact.html' --include='*.js' \
  'ChaLocalReceiverUseCase\$ChangedReceiver' "$module_dir" \
  || fail "ChangedReceiver impact path is missing"
! grep -R -q --include='*-impact.html' --include='*.js' \
  'unrelatedReceiverPath' "$module_dir" \
  || fail "infeasible unrelated receiver caller remains in impact paths"

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
expected=$(awk -F '\t' -v requested_scope="$dependency_scope" '
  $0 !~ /^#/ && NF == 2 && $1 == requested_scope { print $2; found++ }
  END { if (found > 1) exit 2 }
' "$expected_results") || fail "duplicate expected result for $dependency_scope"

if [ -n "$expected" ]; then
  expected_impact=$expected
  if [ "$expected_impact" = PENDING ]; then
    [ "${BENCHMARK_CALIBRATION:-0}" = 1 ] \
      || fail "pending semantic baseline for $dependency_scope/$jdk_model/$algorithm; run an explicitly authorized calibration matrix"
    echo "expected_count=PENDING_CALIBRATION"
  else
    grep -F -q "<th>Impact / structural records</th><td>$expected_impact /" "$report" \
      || fail "impact call chains do not match $jdk_model/$algorithm baseline $expected_impact"
  fi
elif [ "${BENCHMARK_CALIBRATION:-0}" != 1 ]; then
  fail "no locked expected count for $jdk_model/$algorithm; rerun only for review with BENCHMARK_CALIBRATION=1"
else
  echo "expected_count=UNLOCKED_CALIBRATION"
fi

echo "status=SUCCESS"
echo "direct_dependencies=$dependency_count"
echo "raw_change_kinds=10"
echo "dependency_analysis_scope=$dependency_scope"
echo "jdk_model=$jdk_model"
echo "ssa_equivalence=second-stage-on-java-miss"
echo "decompiled_java_equivalence=first-stage-short-circuit"
echo "impact_path_pruning_extensions=cha-local-receiver-inference"
echo "visible_change_kinds=$visible_change_kind_count"
if [ -n "$expected" ] && [ "$expected_impact" != PENDING ]; then
  echo "impact_call_chains=$expected_impact"
fi
echo "structural_impact=present"
echo "overall_report=$report"
echo "module_reports=$module_dir"
