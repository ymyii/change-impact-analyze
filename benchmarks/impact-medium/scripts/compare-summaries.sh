#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <baseline-summary.tsv> <candidate-summary.tsv>" >&2
  exit 2
fi

baseline=$1
candidate=$2
expected_header=$(printf 'dependency_analysis_scope\talgorithm\twala_reflection_options\tsamples\tmin_total_wall_seconds\tmedian_total_wall_seconds\tmax_total_wall_seconds\tmin_call_graph_seconds\tmedian_call_graph_seconds\tmax_call_graph_seconds\tmin_peak_heap_used_mib\tmedian_peak_heap_used_mib\tmax_peak_heap_used_mib\tmin_peak_heap_committed_mib\tmedian_peak_heap_committed_mib\tmax_peak_heap_committed_mib\tmin_heap_max_mib\tmedian_heap_max_mib\tmax_heap_max_mib\tmin_process_tree_peak_rss_kib\tmedian_process_tree_peak_rss_kib\tmax_process_tree_peak_rss_kib\tentrypoint_count\tcg_node_count\tcg_edge_count\treal_external_artifact_count\tno_op_external_artifact_count\treal_external_method_node_count\tno_op_method_node_count\tfactory_method_node_count\tdangerous_transfer_count\tsuccessful_samples\twall_vs_zero_cfa\theap_vs_zero_cfa\tnode_vs_zero_cfa\tedge_vs_zero_cfa')

for summary in "$baseline" "$candidate"; do
  [ -f "$summary" ] || {
    echo "missing summary: $summary" >&2
    exit 1
  }
  [ "$(sed -n '1p' "$summary")" = "$expected_header" ] || {
    echo "unexpected summary Schema: $summary" >&2
    exit 1
  }
done

awk -F '\t' '
  BEGIN {
    OFS = "\t"
    print "algorithm", "wala_reflection_options", "metric", "baseline", "candidate", "absolute_change", "ratio"
    metric[1] = "median_total_wall_seconds"; column[1] = 6
    metric[2] = "median_call_graph_seconds"; column[2] = 9
    metric[3] = "median_peak_heap_used_mib"; column[3] = 12
    metric[4] = "median_process_tree_peak_rss_kib"; column[4] = 21
    metric[5] = "cg_node_count"; column[5] = 24
    metric[6] = "cg_edge_count"; column[6] = 25
  }
  NR == FNR {
    if (FNR > 1) {
      scope[$2] = $1
      reflection[$2] = $3
      baseline_seen[$2] = 1
      for (metric_index = 1; metric_index <= 6; metric_index++) {
        old[$2, metric_index] = $(column[metric_index])
      }
    }
    next
  }
  FNR > 1 {
    algorithm = $2
    candidate_seen[algorithm] = 1
    if (!(algorithm in baseline_seen)) {
      print "candidate algorithm is missing from baseline: " algorithm > "/dev/stderr"
      failed = 1
      next
    }
    if (scope[algorithm] != $1) {
      print "dependency scope differs for " algorithm > "/dev/stderr"
      failed = 1
      next
    }
    if (reflection[algorithm] != $3) {
      print "WALA ReflectionOptions differ for " algorithm > "/dev/stderr"
      failed = 1
      next
    }
    for (metric_index = 1; metric_index <= 6; metric_index++) {
      before = old[algorithm, metric_index]
      after = $(column[metric_index])
      change = after - before
      relative = before == 0 ? "UNAVAILABLE" : sprintf("%.6f", after / before)
      printf "%s\t%s\t%s\t%.6f\t%.6f\t%.6f\t%s\n", algorithm, $3, metric[metric_index], before, after, change, relative
    }
  }
  END {
    for (algorithm in baseline_seen) {
      if (!(algorithm in candidate_seen)) {
        print "baseline algorithm is missing from candidate: " algorithm > "/dev/stderr"
        failed = 1
      }
    }
    exit failed
  }
' "$baseline" "$candidate"
