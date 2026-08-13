#!/bin/sh
set -u

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
: "${BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE:?BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE must be explicitly set}"
scope=$BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE
case "$scope" in
  changed-paths|full) ;;
  *)
    echo "BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE must be changed-paths or full" >&2
    exit 2
    ;;
esac
suite_label=${1:-$scope-suite-$(date '+%Y%m%d-%H%M%S')}

case "$suite_label" in
  ''|*[!A-Za-z0-9._-]*)
    echo "suite label may contain only letters, numbers, dot, underscore, and hyphen" >&2
    exit 2
    ;;
esac

: "${JAVA8_HOME:?JAVA8_HOME must point to a complete JDK 8}"

BENCHMARK_RUNTIME_ROOT=${BENCHMARK_RUNTIME_ROOT:-$repository_root/tmp-files/impact-medium-benchmark}
BENCHMARK_WALA_REFLECTION_OPTIONS=${BENCHMARK_WALA_REFLECTION_OPTIONS:-ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD}
report="$BENCHMARK_RUNTIME_ROOT/benchmark-report-$scope.html"
candidate_dir="$BENCHMARK_RUNTIME_ROOT/$suite_label-candidate-results"
run_list="$BENCHMARK_RUNTIME_ROOT/$suite_label-run-list.txt"
tracked_results="$script_dir/results/$scope"
BENCHMARK_DEFER_PUBLISH=${BENCHMARK_DEFER_PUBLISH:-0}
case "$BENCHMARK_DEFER_PUBLISH" in
  0|1) ;;
  *)
    echo "BENCHMARK_DEFER_PUBLISH must be 0 or 1" >&2
    exit 2
    ;;
esac

if [ -e "$run_list" ] || [ -e "$candidate_dir" ]; then
  echo "suite output already exists for label: $suite_label" >&2
  exit 2
fi

mkdir -p "$BENCHMARK_RUNTIME_ROOT"
: >"$run_list"
suite_failed=0

run_one() {
  algorithm=$1
  run_kind=$2
  round=$3
  sample=$4
  capture=$5
  jdk_model=$6
  label="$suite_label-$run_kind-$jdk_model-$algorithm-$sample"
  run_directory="$BENCHMARK_RUNTIME_ROOT/$label"
  printf '%s\n' "$run_directory" >>"$run_list"
  echo "[$run_kind] algorithm=$algorithm round=$round sample=$sample"
  BENCHMARK_RUNTIME_ROOT="$BENCHMARK_RUNTIME_ROOT" \
  BENCHMARK_CALL_GRAPH_ALGORITHM="$algorithm" \
  BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE="$scope" \
  BENCHMARK_WALA_REFLECTION_OPTIONS="$BENCHMARK_WALA_REFLECTION_OPTIONS" \
  BENCHMARK_JDK_MODEL="$jdk_model" \
  BENCHMARK_RUN_KIND="$run_kind" \
  BENCHMARK_ROUND="$round" \
  BENCHMARK_SAMPLE="$sample" \
  BENCHMARK_CAPTURE_TOPOLOGY="$capture" \
    "$script_dir/run-benchmark.sh" "$label" || suite_failed=1
}

# One scope: 5 warm-up + 25 formal + 4 non-CHA none controls.
for algorithm in cha rta zero-cfa optimized-0-1-cfa k-obj; do
  if [ "$algorithm" = cha ]; then
    run_one "$algorithm" warmup 0 0 1 none
  else
    run_one "$algorithm" warmup 0 0 1 jdk8
  fi
done

for round in 1 2 3 4 5; do
  case "$round" in
    1) order="cha rta zero-cfa optimized-0-1-cfa k-obj" ;;
    2) order="rta zero-cfa optimized-0-1-cfa k-obj cha" ;;
    3) order="zero-cfa optimized-0-1-cfa k-obj cha rta" ;;
    4) order="optimized-0-1-cfa k-obj cha rta zero-cfa" ;;
    5) order="k-obj cha rta zero-cfa optimized-0-1-cfa" ;;
  esac
  for algorithm in $order; do
    if [ "$algorithm" = cha ]; then
      run_one "$algorithm" formal "$round" "$round" 0 none
    else
      run_one "$algorithm" formal "$round" "$round" 0 jdk8
    fi
  done
done

for algorithm in rta zero-cfa optimized-0-1-cfa k-obj; do
  run_one "$algorithm" control 0 0 0 none
done

set --
while IFS= read -r run_directory; do
  set -- "$@" "$run_directory"
done <"$run_list"

report_result=0
python3 "$script_dir/scripts/generate-report.py" \
  --output-html "$report" \
  --candidate-dir "$candidate_dir" \
  --scope "$scope" \
  "$@" || report_result=$?

if [ "$suite_failed" -ne 0 ] || [ "$report_result" -ne 0 ]; then
  echo "benchmark suite failed; tracked TSV snapshots were not changed" >&2
  echo "failure report: $report" >&2
  exit 1
fi

if [ "$BENCHMARK_DEFER_PUBLISH" -eq 0 ]; then
  "$script_dir/scripts/publish-results.sh" "$candidate_dir" "$tracked_results"
fi

echo "benchmark suite completed: $suite_label"
echo "HTML report: $report"
echo "candidate TSV: $candidate_dir"
if [ "$BENCHMARK_DEFER_PUBLISH" -eq 0 ]; then
  echo "tracked TSV: $tracked_results"
else
  echo "tracked TSV publication: deferred"
fi
