#!/bin/sh
set -u

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
label=${1:-run-$(date '+%Y%m%d-%H%M%S')}

case "$label" in
  ''|*[!A-Za-z0-9._-]*)
    echo "label may contain only letters, numbers, dot, underscore, and hyphen" >&2
    exit 2
    ;;
esac

: "${JAVA8_HOME:?JAVA8_HOME must point to a complete JDK 8}"
: "${BENCHMARK_CALL_GRAPH_ALGORITHM:?BENCHMARK_CALL_GRAPH_ALGORITHM must be explicitly set}"
: "${BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE:?BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE must be explicitly set}"
: "${BENCHMARK_JDK_MODEL:?BENCHMARK_JDK_MODEL must be explicitly set}"

case "$BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE" in
  changed-paths|full) ;;
  *)
    echo "BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE must be changed-paths or full" >&2
    exit 2
    ;;
esac

case "$BENCHMARK_CALL_GRAPH_ALGORITHM" in
  cha) ;;
  *)
    echo "BENCHMARK_CALL_GRAPH_ALGORITHM must be cha" >&2
    exit 2
    ;;
esac

case "$BENCHMARK_JDK_MODEL" in
  jdk8|none) ;;
  *)
    echo "BENCHMARK_JDK_MODEL must be jdk8 or none" >&2
    exit 2
    ;;
esac
if [ "$BENCHMARK_CALL_GRAPH_ALGORITHM" = cha ] \
    && [ "$BENCHMARK_JDK_MODEL" != none ]; then
  echo "CHA benchmark runs must use JDK model none" >&2
  exit 2
fi

BENCHMARK_WALA_REFLECTION_OPTIONS=${BENCHMARK_WALA_REFLECTION_OPTIONS:-ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD}
case "$BENCHMARK_WALA_REFLECTION_OPTIONS" in
  FULL|APPLICATION_GET_METHOD|NO_FLOW_TO_CASTS|NO_FLOW_TO_CASTS_APPLICATION_GET_METHOD|NO_METHOD_INVOKE|NO_FLOW_TO_CASTS_NO_METHOD_INVOKE|ONE_FLOW_TO_CASTS_NO_METHOD_INVOKE|ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD|MULTI_FLOW_TO_CASTS_APPLICATION_GET_METHOD|NO_STRING_CONSTANTS|STRING_ONLY|NONE) ;;
  *)
    echo "unsupported BENCHMARK_WALA_REFLECTION_OPTIONS: $BENCHMARK_WALA_REFLECTION_OPTIONS" >&2
    exit 2
    ;;
esac

BENCHMARK_CALIBRATION=${BENCHMARK_CALIBRATION:-0}
case "$BENCHMARK_CALIBRATION" in
  0|1) ;;
  *)
    echo "BENCHMARK_CALIBRATION must be 0 or 1" >&2
    exit 2
    ;;
esac

BENCHMARK_CAPTURE_TOPOLOGY=${BENCHMARK_CAPTURE_TOPOLOGY:-0}
case "$BENCHMARK_CAPTURE_TOPOLOGY" in
  0|1) ;;
  *)
    echo "BENCHMARK_CAPTURE_TOPOLOGY must be 0 or 1" >&2
    exit 2
    ;;
esac

BENCHMARK_RUN_KIND=${BENCHMARK_RUN_KIND:-formal}
case "$BENCHMARK_RUN_KIND" in
  warmup|formal) ;;
  *)
    echo "BENCHMARK_RUN_KIND must be warmup or formal" >&2
    exit 2
    ;;
esac

BENCHMARK_ROUND=${BENCHMARK_ROUND:-0}
BENCHMARK_SAMPLE=${BENCHMARK_SAMPLE:-0}

ANALYZER_JAR=${ANALYZER_JAR:-$repository_root/target/dependency-analyzer.jar}
ANALYZER_JAVA=${ANALYZER_JAVA:-$(command -v java 2>/dev/null)}
MAVEN_BIN=${MAVEN_BIN:-$(command -v mvn 2>/dev/null)}
BENCHMARK_MAVEN_REPO=${BENCHMARK_MAVEN_REPO:-${HOME}/.m2/repository}
BENCHMARK_RUNTIME_ROOT=${BENCHMARK_RUNTIME_ROOT:-$repository_root/tmp-files/impact-medium-benchmark}
run_root="$BENCHMARK_RUNTIME_ROOT/$label"
fixture_root="$run_root/fixture"
logs_root="$run_root/logs"
reports_root="$run_root/reports"
BENCHMARK_PROJECT="$fixture_root/project"
BENCHMARK_CONFIG_DIR="$run_root/config"
BENCHMARK_REPORT="$reports_root/impact-report.html"
BENCHMARK_DIAGNOSTICS="$run_root/topology.json"

for executable in "$ANALYZER_JAVA" "$MAVEN_BIN"; do
  if [ -z "$executable" ] || [ ! -x "$executable" ]; then
    echo "required executable is missing: $executable" >&2
    exit 2
  fi
done

if [ ! -f "$ANALYZER_JAR" ]; then
  echo "Analyzer JAR is missing: $ANALYZER_JAR" >&2
  echo "run 'mvn package' first or set ANALYZER_JAR" >&2
  exit 2
fi

if [ -e "$run_root" ]; then
  echo "benchmark result already exists: $run_root" >&2
  exit 2
fi

mkdir -p "$logs_root" "$reports_root" "$BENCHMARK_CONFIG_DIR"

export ANALYZER_JAR ANALYZER_JAVA MAVEN_BIN JAVA8_HOME
export BENCHMARK_MAVEN_REPO BENCHMARK_PROJECT BENCHMARK_CONFIG_DIR BENCHMARK_REPORT
export BENCHMARK_CALL_GRAPH_ALGORITHM BENCHMARK_WALA_REFLECTION_OPTIONS
export BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE BENCHMARK_JDK_MODEL
export BENCHMARK_CALIBRATION BENCHMARK_CAPTURE_TOPOLOGY BENCHMARK_DIAGNOSTICS

# Wiki: wiki/runbooks/impact-benchmark.md - Stable benchmark preparation, measurement, and verification entrypoint.
"$script_dir/scripts/prepare-fixture.sh" "$fixture_root"

if command -v shasum >/dev/null 2>&1; then
  analyzer_sha256=$(shasum -a 256 "$ANALYZER_JAR" | awk '{print $1}')
elif command -v sha256sum >/dev/null 2>&1; then
  analyzer_sha256=$(sha256sum "$ANALYZER_JAR" | awk '{print $1}')
else
  analyzer_sha256=unavailable
fi

git_commit=$(git -C "$repository_root" rev-parse HEAD 2>/dev/null || echo unavailable)
if [ -n "$(git -C "$repository_root" status --porcelain --untracked-files=normal 2>/dev/null)" ]; then
  git_dirty=true
else
  git_dirty=false
fi
analyzer_java_identity=$("$ANALYZER_JAVA" -version 2>&1 | sed -n '1p' | tr '\t' ' ')
jdk_identity=$("$JAVA8_HOME/bin/java" -version 2>&1 | sed -n '1p' | tr '\t' ' ')
maven_identity=$("$MAVEN_BIN" --version 2>&1 | sed -n '1p' | tr '\t' ' ')

cat >"$logs_root/run-metadata.txt" <<EOF
label=$label
algorithm=$BENCHMARK_CALL_GRAPH_ALGORITHM
jdk_model=$BENCHMARK_JDK_MODEL
dependency_analysis_scope=$BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE
ssa_equivalence=fixed-enabled
impact_path_pruning_extensions=cha-local-receiver-inference
wala_reflection_options=$BENCHMARK_WALA_REFLECTION_OPTIONS
calibration=$BENCHMARK_CALIBRATION
fixture_scenario=impact-medium-v1
analysis_parallelism=2
run_kind=$BENCHMARK_RUN_KIND
round=$BENCHMARK_ROUND
sample=$BENCHMARK_SAMPLE
capture_topology=$BENCHMARK_CAPTURE_TOPOLOGY
os=$(uname -a)
cpu_logical=$(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.logicalcpu 2>/dev/null || echo unavailable)
architecture=$(uname -m)
analyzer_jar=$ANALYZER_JAR
analyzer_sha256=$analyzer_sha256
git_commit=$git_commit
git_dirty=$git_dirty
analyzer_java_identity=$analyzer_java_identity
jdk_identity=$jdk_identity
maven_identity=$maven_identity
analyzer_java=$ANALYZER_JAVA
jdk8_home=$JAVA8_HOME
maven=$MAVEN_BIN
maven_repository=$BENCHMARK_MAVEN_REPO
project=$BENCHMARK_PROJECT
baseline=impact-baseline
target=impact-target
EOF

(
  case "$(uname -s)" in
    Darwin)
      /usr/bin/time -lp -o "$logs_root/time.txt" \
        "$script_dir/scripts/invoke-impact.sh" \
        >"$logs_root/stdout.log" \
        2>"$logs_root/stderr.log"
      ;;
    Linux)
      /usr/bin/time -v -o "$logs_root/time.txt" \
        "$script_dir/scripts/invoke-impact.sh" \
        >"$logs_root/stdout.log" \
        2>"$logs_root/stderr.log"
      ;;
    *)
      echo "unsupported OS for resource collection: $(uname -s)" \
        >"$logs_root/stderr.log"
      false
      ;;
  esac
  analysis_result=$?
  printf '%s\n' "$analysis_result" >"$logs_root/exit-code.txt"
  exit "$analysis_result"
) &
measurement_pid=$!

printf 'epoch,pids,total_rss_kib,total_cpu_percent\n' >"$logs_root/process-tree.csv"
while kill -0 "$measurement_pid" 2>/dev/null; do
  sample_epoch=$(date +%s)
  sample=$(ps -axo pid=,ppid=,rss=,%cpu= | awk -v root="$measurement_pid" '
    {
      parent[$1] = $2
      rss[$1] = $3
      cpu[$1] = $4
    }
    END {
      selected[root] = 1
      for (pass = 0; pass < 32; pass++) {
        for (pid in parent) {
          if (selected[parent[pid]]) {
            selected[pid] = 1
          }
        }
      }
      count = 0
      total_rss = 0
      total_cpu = 0
      for (pid in selected) {
        if (pid in rss) {
          count++
          total_rss += rss[pid]
          total_cpu += cpu[pid]
        }
      }
      printf "%d,%d,%.1f", count, total_rss, total_cpu
    }
  ')
  printf '%s,%s\n' "$sample_epoch" "$sample" >>"$logs_root/process-tree.csv"
  sleep 0.25
done

wait "$measurement_pid"
analysis_result=$?

verification_result=0
"$script_dir/scripts/verify-report.sh" \
  "$BENCHMARK_REPORT" \
  "$logs_root/exit-code.txt" \
  "$BENCHMARK_PROJECT" \
  "$BENCHMARK_CALL_GRAPH_ALGORITHM" \
  "$BENCHMARK_WALA_REFLECTION_OPTIONS" \
  "$BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE" \
  "$BENCHMARK_JDK_MODEL" \
  >"$logs_root/verification.txt" \
  2>&1 || verification_result=$?

cat "$logs_root/verification.txt"

module_dir=${BENCHMARK_REPORT%.html}-modules
set -- "$module_dir"/*.html
module_page=
for page in "$@"; do
  case "$page" in
    *-changes.html|*-impact.html) ;;
    *) module_page=$page; break ;;
  esac
done

nodes=
edges=
entrypoints=
call_graph_millis=
real_external_artifacts=
no_op_external_artifacts=
real_external_method_nodes=
no_op_method_nodes=
factory_method_nodes=
dangerous_transfers=
if [ -n "$module_page" ] && [ -f "$module_page" ]; then
  nodes=$(sed -n 's/.*<th>Call Graph nodes<\/th><td>\([0-9][0-9]*\)<\/td>.*/\1/p' "$module_dir"/*.html | awk '{ total += $1 } END { print total + 0 }')
  edges=$(sed -n 's/.*<th>Call Graph edges<\/th><td>\([0-9][0-9]*\)<\/td>.*/\1/p' "$module_dir"/*.html | awk '{ total += $1 } END { print total + 0 }')
  entrypoints=$(sed -n 's/.*<th>Entry methods<\/th><td>\([0-9][0-9]*\)<\/td>.*/\1/p' "$module_dir"/*.html | awk '{ total += $1 } END { print total + 0 }')
  call_graph_millis=$(sed -n 's/.*<td>call-graph<\/td><td>\([0-9][0-9]*\)<\/td>.*/\1/p' "$module_dir"/*.html | awk '{ total += $1 } END { print total + 0 }')
fi
real_external_artifacts=$(sed -n 's/.*<th>Real-IR \/ no-op external artifacts<\/th><td>\([0-9][0-9]*\) \/ \([0-9][0-9]*\)<\/td>.*/\1/p' "$BENCHMARK_REPORT" | head -n 1)
no_op_external_artifacts=$(sed -n 's/.*<th>Real-IR \/ no-op external artifacts<\/th><td>\([0-9][0-9]*\) \/ \([0-9][0-9]*\)<\/td>.*/\2/p' "$BENCHMARK_REPORT" | head -n 1)
no_op_method_nodes=$(sed -n 's/.*<th>No-op \/ factory method nodes<\/th><td>\([0-9][0-9]*\) \/ \([0-9][0-9]*\)<\/td>.*/\1/p' "$BENCHMARK_REPORT" | head -n 1)
factory_method_nodes=$(sed -n 's/.*<th>No-op \/ factory method nodes<\/th><td>\([0-9][0-9]*\) \/ \([0-9][0-9]*\)<\/td>.*/\2/p' "$BENCHMARK_REPORT" | head -n 1)
dangerous_transfers=$(sed -n 's/.*<th>Dangerous dependency transfers<\/th><td>\([0-9][0-9]*\)<\/td>.*/\1/p' "$BENCHMARK_REPORT" | head -n 1)
if [ -n "$module_dir" ] && [ -d "$module_dir" ]; then
  real_external_method_nodes=$(sed -n 's/.*<th>Real \/ no-op \/ factory method nodes<\/th><td>\([0-9][0-9]*\) \/ \([0-9][0-9]*\) \/ \([0-9][0-9]*\)<\/td>.*/\1/p' "$module_dir"/*.html | awk '{ total += $1 } END { print total + 0 }')
fi
wall_seconds=$(awk '
  /^real[[:space:]]+[0-9.]+$/ { print $2; exit }
  /^Elapsed \(wall clock\) time/ {
    value = $NF
    split(value, part, ":")
    if (length(part) == 3) print part[1] * 3600 + part[2] * 60 + part[3]
    else if (length(part) == 2) print part[1] * 60 + part[2]
    else print value
    exit
  }
' "$logs_root/time.txt")
peak_rss_kib=$(awk -F ',' 'NR > 1 && $3 ~ /^[0-9]+$/ && $3 > peak { peak = $3 } END { print peak + 0 }' "$logs_root/process-tree.csv")

runtime_summary=$(grep 'Runtime metrics summary;' "$logs_root/stderr.log" | tail -n 1 || true)
heap_samples=$(printf '%s\n' "$runtime_summary" | sed -n 's/.*samples=\([0-9][0-9]*\);.*/\1/p')
peak_heap_used_mib=$(printf '%s\n' "$runtime_summary" | sed -n 's/.*peakHeapUsedMiB=\([0-9.-][0-9.-]*\);.*/\1/p')
peak_heap_committed_mib=$(printf '%s\n' "$runtime_summary" | sed -n 's/.*peakHeapCommittedMiB=\([0-9.-][0-9.-]*\);.*/\1/p')
heap_max_mib=$(printf '%s\n' "$runtime_summary" | sed -n 's/.*heapMaxMiB=\([0-9.-][0-9.-]*\).*/\1/p')
if [ -n "$call_graph_millis" ]; then
  call_graph_seconds=$(awk -v value="$call_graph_millis" 'BEGIN { printf "%.3f", value / 1000 }')
else
  call_graph_seconds=
fi

status=FAILED
if [ "$analysis_result" -eq 0 ] && [ "$verification_result" -eq 0 ]; then
  status=SUCCESS
fi

printf 'label\trun_kind\tround\tsample\tdependency_analysis_scope\talgorithm\tjdk_model\twala_reflection_options\ttotal_wall_seconds\tcall_graph_seconds\tpeak_heap_used_mib\tpeak_heap_committed_mib\theap_max_mib\theap_sample_count\tprocess_tree_peak_rss_kib\tentrypoint_count\tcg_node_count\tcg_edge_count\treal_external_artifact_count\tno_op_external_artifact_count\treal_external_method_node_count\tno_op_method_node_count\tfactory_method_node_count\tdangerous_transfer_count\tstatus\texit_code\tanalyzer_sha256\tgit_commit\tgit_dirty\tos\tarchitecture\tanalyzer_java\tjdk\tmaven\n' >"$logs_root/metrics.tsv"
printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
  "$label" "$BENCHMARK_RUN_KIND" "$BENCHMARK_ROUND" "$BENCHMARK_SAMPLE" \
  "$BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE" \
  "$BENCHMARK_CALL_GRAPH_ALGORITHM" "$BENCHMARK_JDK_MODEL" \
  "$BENCHMARK_WALA_REFLECTION_OPTIONS" \
  "$wall_seconds" "$call_graph_seconds" "$peak_heap_used_mib" \
  "$peak_heap_committed_mib" "$heap_max_mib" "$heap_samples" \
  "$peak_rss_kib" "$entrypoints" "$nodes" "$edges" \
  "$real_external_artifacts" "$no_op_external_artifacts" \
  "$real_external_method_nodes" "$no_op_method_nodes" \
  "$factory_method_nodes" "$dangerous_transfers" "$status" \
  "$analysis_result" "$analyzer_sha256" "$git_commit" "$git_dirty" \
  "$(uname -s) $(uname -r)" "$(uname -m)" "$analyzer_java_identity" \
  "$jdk_identity" "$maven_identity" >>"$logs_root/metrics.tsv"

if [ "$status" != SUCCESS ]; then
  echo "impact benchmark failed; analyzer_exit=$analysis_result; verification_exit=$verification_result" >&2
  echo "logs: $logs_root" >&2
  [ "$analysis_result" -ne 0 ] && exit "$analysis_result"
  exit "$verification_result"
fi

if [ -z "$nodes" ] || [ -z "$edges" ] || [ -z "$entrypoints" ] \
  || [ -z "$wall_seconds" ] || [ -z "$call_graph_seconds" ] \
  || [ -z "$peak_heap_used_mib" ] || [ -z "$heap_samples" ] \
  || [ -z "$real_external_artifacts" ] \
  || [ -z "$no_op_external_artifacts" ] \
  || [ -z "$real_external_method_nodes" ] \
  || [ -z "$no_op_method_nodes" ] \
  || [ -z "$factory_method_nodes" ] \
  || [ -z "$dangerous_transfers" ]; then
  echo "unable to extract complete benchmark metrics" >&2
  exit 1
fi

echo "benchmark completed: $run_root"
