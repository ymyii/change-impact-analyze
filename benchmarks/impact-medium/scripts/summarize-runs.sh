#!/bin/sh
set -eu

if [ "$#" -lt 3 ]; then
  echo "usage: $0 <run-directory> <run-directory> <run-directory> [...]" >&2
  exit 2
fi

fail() {
  echo "summary failed: $*" >&2
  exit 1
}

metadata_value() {
  key=$1
  file=$2
  awk -F '=' -v key="$key" '$1 == key { value = substr($0, length(key) + 2); found++ } END { if (found == 1) print value; else exit 1 }' "$file"
}

median() {
  values=$1
  count=$(printf '%s\n' "$values" | sed '/^$/d' | wc -l | tr -d '[:space:]')
  [ "$count" -gt 0 ] || return 1
  sorted=$(printf '%s\n' "$values" | sed '/^$/d' | sort -n)
  if [ $((count % 2)) -eq 1 ]; then
    index=$(((count + 1) / 2))
    printf '%s\n' "$sorted" | sed -n "${index}p"
  else
    left=$((count / 2))
    right=$((left + 1))
    printf '%s\n' "$sorted" | awk -v left="$left" -v right="$right" 'NR == left { a = $1 } NR == right { print (a + $1) / 2; exit }'
  fi
}

reference_algorithm=
reference_reflection_options=
reference_environment=
wall_values=
rss_values=
run_count=0

for run_dir in "$@"; do
  metadata="$run_dir/logs/run-metadata.txt"
  metrics="$run_dir/logs/metrics.tsv"
  verification="$run_dir/logs/verification.txt"
  [ -f "$metadata" ] || fail "missing metadata: $metadata"
  [ -f "$metrics" ] || fail "missing metrics: $metrics"
  [ -f "$verification" ] || fail "missing verification: $verification"
  grep -qx 'status=SUCCESS' "$verification" || fail "run did not pass verification: $run_dir"

  algorithm=$(metadata_value algorithm "$metadata") || fail "invalid algorithm metadata: $metadata"
  case "$algorithm" in rta|zero-cfa|optimized-0-1-cfa) ;; *) fail "unsupported algorithm in $metadata: $algorithm" ;; esac
  calibration=$(metadata_value calibration "$metadata") || fail "missing calibration metadata: $metadata"
  reflection_options=$(metadata_value wala_reflection_options "$metadata") \
    || fail "missing wala_reflection_options metadata: $metadata"
  [ "$calibration" = 0 ] || fail "calibration run cannot be summarized: $run_dir"
  environment=
  for key in analyzer_sha256 analyzer_java jdk8_home maven maven_repository fixture_scenario analysis_parallelism os cpu_logical; do
    value=$(metadata_value "$key" "$metadata") || fail "missing $key in $metadata"
    environment="${environment}${key}=${value}
"
  done

  if [ -z "$reference_algorithm" ]; then
    reference_algorithm=$algorithm
    reference_reflection_options=$reflection_options
    reference_environment=$environment
  else
    [ "$algorithm" = "$reference_algorithm" ] || fail "cannot mix algorithms: $reference_algorithm and $algorithm"
    [ "$reflection_options" = "$reference_reflection_options" ] \
      || fail "cannot mix WALA ReflectionOptions: $reference_reflection_options and $reflection_options"
    [ "$environment" = "$reference_environment" ] || fail "benchmark environments differ: $run_dir"
  fi

  header=$(sed -n '1p' "$metrics")
  expected_header=$(printf 'label\talgorithm\twala_reflection_options\tnodes\tedges\twall_seconds\tprocess_tree_peak_rss_kib')
  [ "$header" = "$expected_header" ] \
    || fail "unexpected metrics header: $metrics"
  row=$(sed -n '2p' "$metrics")
  [ -n "$row" ] || fail "missing metrics row: $metrics"
  row_algorithm=$(printf '%s\n' "$row" | awk -F '\t' 'NF == 7 { print $2 }')
  row_reflection_options=$(printf '%s\n' "$row" | awk -F '\t' 'NF == 7 { print $3 }')
  wall=$(printf '%s\n' "$row" | awk -F '\t' 'NF == 7 && $6 ~ /^[0-9]+([.][0-9]+)?$/ { print $6 }')
  rss=$(printf '%s\n' "$row" | awk -F '\t' 'NF == 7 && $7 ~ /^[0-9]+$/ { print $7 }')
  [ "$row_algorithm" = "$algorithm" ] || fail "metrics algorithm mismatch: $metrics"
  [ "$row_reflection_options" = "$reflection_options" ] \
    || fail "metrics WALA ReflectionOptions mismatch: $metrics"
  [ -n "$wall" ] && [ -n "$rss" ] || fail "invalid metrics values: $metrics"
  wall_values="${wall_values}${wall}
"
  rss_values="${rss_values}${rss}
"
  run_count=$((run_count + 1))
done

median_wall=$(median "$wall_values") || fail "cannot calculate wall-time median"
median_rss=$(median "$rss_values") || fail "cannot calculate RSS median"

printf 'algorithm\twala_reflection_options\truns\tmedian_wall_seconds\tmedian_process_tree_peak_rss_kib\n'
printf '%s\t%s\t%s\t%s\t%s\n' "$reference_algorithm" \
  "$reference_reflection_options" "$run_count" "$median_wall" "$median_rss"
