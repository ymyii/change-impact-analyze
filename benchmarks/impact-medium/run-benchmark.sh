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

# Wiki: wiki/runbooks/impact-benchmark.md - Stable benchmark preparation, measurement, and verification entrypoint.
"$script_dir/scripts/prepare-fixture.sh" "$fixture_root"

if command -v shasum >/dev/null 2>&1; then
  analyzer_sha256=$(shasum -a 256 "$ANALYZER_JAR" | awk '{print $1}')
elif command -v sha256sum >/dev/null 2>&1; then
  analyzer_sha256=$(sha256sum "$ANALYZER_JAR" | awk '{print $1}')
else
  analyzer_sha256=unavailable
fi

cat >"$logs_root/run-metadata.txt" <<EOF
label=$label
os=$(uname -a)
analyzer_jar=$ANALYZER_JAR
analyzer_sha256=$analyzer_sha256
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
  >"$logs_root/verification.txt" \
  2>&1 || verification_result=$?

cat "$logs_root/verification.txt"

if [ "$analysis_result" -ne 0 ]; then
  echo "impact benchmark failed with exit code $analysis_result" >&2
  echo "logs: $logs_root" >&2
  exit "$analysis_result"
fi

if [ "$verification_result" -ne 0 ]; then
  echo "impact report verification failed" >&2
  echo "logs: $logs_root" >&2
  exit "$verification_result"
fi

echo "benchmark completed: $run_root"
