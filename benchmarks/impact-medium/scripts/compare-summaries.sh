#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <candidate-summary.tsv> <baseline-summary.tsv>" >&2
  exit 2
fi

candidate_summary=$1
baseline_summary=$2
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
thresholds="$script_dir/../performance-thresholds.tsv"

fail() {
  echo "comparison failed: $*" >&2
  exit 1
}

summary_row() {
  summary=$1
  expected_header=$(printf 'algorithm\twala_reflection_options\truns\tmedian_wall_seconds\tmedian_process_tree_peak_rss_kib')
  header=$(sed -n '1p' "$summary")
  [ "$header" = "$expected_header" ] \
    || fail "unexpected summary header: $summary"
  row=$(sed -n '2p' "$summary")
  fields=$(printf '%s\n' "$row" | awk -F '\t' 'NF == 5 { print NF }')
  [ "$fields" = 5 ] || fail "invalid summary row: $summary"
  [ -z "$(sed -n '3p' "$summary")" ] \
    || fail "summary must contain exactly one data row: $summary"
  printf '%s\n' "$row"
}

[ -f "$candidate_summary" ] || fail "missing candidate summary: $candidate_summary"
[ -f "$baseline_summary" ] || fail "missing baseline summary: $baseline_summary"
[ -f "$thresholds" ] || fail "missing performance thresholds: $thresholds"

candidate=$(summary_row "$candidate_summary")
baseline=$(summary_row "$baseline_summary")
candidate_algorithm=$(printf '%s\n' "$candidate" | awk -F '\t' '{print $1}')
baseline_algorithm=$(printf '%s\n' "$baseline" | awk -F '\t' '{print $1}')
candidate_reflection=$(printf '%s\n' "$candidate" | awk -F '\t' '{print $2}')
baseline_reflection=$(printf '%s\n' "$baseline" | awk -F '\t' '{print $2}')
candidate_wall=$(printf '%s\n' "$candidate" | awk -F '\t' '{print $4}')
baseline_wall=$(printf '%s\n' "$baseline" | awk -F '\t' '{print $4}')
candidate_rss=$(printf '%s\n' "$candidate" | awk -F '\t' '{print $5}')
baseline_rss=$(printf '%s\n' "$baseline" | awk -F '\t' '{print $5}')

[ "$candidate_reflection" = "$baseline_reflection" ] \
  || fail "WALA ReflectionOptions differ: $candidate_reflection and $baseline_reflection"

threshold=$(awk -F '\t' -v candidate="$candidate_algorithm" \
  -v baseline="$baseline_algorithm" '
    $0 !~ /^#/ && NF == 4 && $1 == candidate && $2 == baseline {
      print $3 "\t" $4
      found++
    }
    END { if (found != 1) exit 1 }
  ' "$thresholds") \
  || fail "no unique threshold for $candidate_algorithm relative to $baseline_algorithm"
max_wall_ratio=$(printf '%s\n' "$threshold" | awk -F '\t' '{print $1}')
max_rss_ratio=$(printf '%s\n' "$threshold" | awk -F '\t' '{print $2}')

awk -v candidate="$candidate_wall" -v baseline="$baseline_wall" \
  -v maximum="$max_wall_ratio" \
  'BEGIN { exit !(baseline > 0 && candidate <= baseline * maximum) }' \
  || fail "wall ratio exceeds $max_wall_ratio: $candidate_wall / $baseline_wall"
awk -v candidate="$candidate_rss" -v baseline="$baseline_rss" \
  -v maximum="$max_rss_ratio" \
  'BEGIN { exit !(baseline > 0 && candidate <= baseline * maximum) }' \
  || fail "RSS ratio exceeds $max_rss_ratio: $candidate_rss / $baseline_rss"

wall_ratio=$(awk -v candidate="$candidate_wall" -v baseline="$baseline_wall" \
  'BEGIN { printf "%.4f", candidate / baseline }')
rss_ratio=$(awk -v candidate="$candidate_rss" -v baseline="$baseline_rss" \
  'BEGIN { printf "%.4f", candidate / baseline }')

printf 'candidate_algorithm\tbaseline_algorithm\twala_reflection_options\twall_ratio\tmax_wall_ratio\trss_ratio\tmax_rss_ratio\tstatus\n'
printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\tPASS\n' \
  "$candidate_algorithm" "$baseline_algorithm" "$candidate_reflection" \
  "$wall_ratio" "$max_wall_ratio" "$rss_ratio" "$max_rss_ratio"
