#!/bin/sh
set -u

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
: "${JAVA8_HOME:?JAVA8_HOME must point to a complete JDK 8}"

runtime_root=${BENCHMARK_RUNTIME_ROOT:-$repository_root/tmp-files/impact-medium-benchmark}
matrix_label=${1:-scope-matrix-$(date '+%Y%m%d-%H%M%S')}
case "$matrix_label" in
  ''|*[!A-Za-z0-9._-]*)
    echo "matrix label may contain only letters, numbers, dot, underscore, and hyphen" >&2
    exit 2
    ;;
esac

changed_label="$matrix_label-changed-paths"
full_label="$matrix_label-full"
changed_candidate="$runtime_root/$changed_label-candidate-results"
full_candidate="$runtime_root/$full_label-candidate-results"
changed_report="$runtime_root/benchmark-report-changed-paths.html"
full_report="$runtime_root/benchmark-report-full.html"
tracked_results="$script_dir/results"

changed_result=0
full_result=0

# Wiki: wiki/rules/benchmark-scenario-coverage.md - Capability benchmark coverage gate
BENCHMARK_RUNTIME_ROOT="$runtime_root" \
BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE=changed-paths \
BENCHMARK_DEFER_PUBLISH=1 \
  "$script_dir/run-suite.sh" "$changed_label" || changed_result=$?

BENCHMARK_RUNTIME_ROOT="$runtime_root" \
BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE=full \
BENCHMARK_DEFER_PUBLISH=1 \
  "$script_dir/run-suite.sh" "$full_label" || full_result=$?

comparison_result=0
if [ -f "$changed_candidate/summary.tsv" ] \
    && [ -f "$full_candidate/summary.tsv" ] \
    && [ -f "$changed_report" ] && [ -f "$full_report" ]; then
  python3 "$script_dir/scripts/add-scope-comparison.py" \
    --changed-summary "$changed_candidate/summary.tsv" \
    --full-summary "$full_candidate/summary.tsv" \
    --changed-report "$changed_report" \
    --full-report "$full_report" || comparison_result=$?
else
  comparison_result=1
fi

if [ "$changed_result" -ne 0 ] || [ "$full_result" -ne 0 ] \
    || [ "$comparison_result" -ne 0 ]; then
  echo "scope matrix failed; tracked snapshots were preserved" >&2
  echo "changed-paths report: $changed_report" >&2
  echo "full report: $full_report" >&2
  exit 1
fi

"$script_dir/scripts/publish-scope-matrix.sh" \
  "$changed_candidate" "$full_candidate" "$tracked_results"

echo "scope matrix completed: $matrix_label"
echo "changed-paths report: $changed_report"
echo "full report: $full_report"
echo "tracked TSV: $tracked_results/{changed-paths,full}"
