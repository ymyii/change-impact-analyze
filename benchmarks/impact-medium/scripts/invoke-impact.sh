#!/bin/sh
set -eu

: "${ANALYZER_JAVA:?ANALYZER_JAVA is required}"
: "${ANALYZER_JAR:?ANALYZER_JAR is required}"
: "${MAVEN_BIN:?MAVEN_BIN is required}"
: "${JAVA8_HOME:?JAVA8_HOME is required}"
: "${BENCHMARK_MAVEN_REPO:?BENCHMARK_MAVEN_REPO is required}"
: "${BENCHMARK_CONFIG_DIR:?BENCHMARK_CONFIG_DIR is required}"
: "${BENCHMARK_PROJECT:?BENCHMARK_PROJECT is required}"
: "${BENCHMARK_REPORT:?BENCHMARK_REPORT is required}"
: "${BENCHMARK_CALL_GRAPH_ALGORITHM:?BENCHMARK_CALL_GRAPH_ALGORITHM is required}"
: "${BENCHMARK_JDK_MODEL:?BENCHMARK_JDK_MODEL is required}"
: "${BENCHMARK_WALA_REFLECTION_OPTIONS:?BENCHMARK_WALA_REFLECTION_OPTIONS is required}"
: "${BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE:?BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE is required}"
: "${BENCHMARK_CAPTURE_TOPOLOGY:?BENCHMARK_CAPTURE_TOPOLOGY is required}"

case "$BENCHMARK_CALL_GRAPH_ALGORITHM" in
  rta|zero-cfa|optimized-0-1-cfa|1-object-1-call-site) ;;
  *)
    echo "unsupported call graph algorithm: $BENCHMARK_CALL_GRAPH_ALGORITHM" >&2
    exit 2
    ;;
esac

case "$BENCHMARK_JDK_MODEL" in
  jdk8|none) ;;
  *)
    echo "unsupported JDK model: $BENCHMARK_JDK_MODEL" >&2
    exit 2
    ;;
esac

set -- \
  --maven "$MAVEN_BIN" \
  --java-home "$JAVA8_HOME" \
  --config-dir "$BENCHMARK_CONFIG_DIR" \
  "--maven-arg=-Dmaven.repo.local=$BENCHMARK_MAVEN_REPO" \
  --maven-arg=-o \
  -vv impact \
  --path "$BENCHMARK_PROJECT" \
  --baseline impact-baseline \
  --target impact-target \
  --output "$BENCHMARK_REPORT" \
  --format html \
  --analysis-target spring-backend \
  --analysis-parallelism 2 \
  --call-graph-algorithm "$BENCHMARK_CALL_GRAPH_ALGORITHM" \
  --wala-reflection-options "$BENCHMARK_WALA_REFLECTION_OPTIONS" \
  --dependency-analysis-scope "$BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE" \
  --include-change-kinds CLASS_ADDED,CLASS_REMOVED,METHOD_ADDED,METHOD_REMOVED,METHOD_DESCRIPTOR_CHANGED,METHOD_BODY_CHANGED,FIELD_ADDED,FIELD_REMOVED,FIELD_DESCRIPTOR_CHANGED \
  --call-graph-timeout-seconds 120

# jdk8 intentionally exercises the CLI default; none is the semantic control.
if [ "$BENCHMARK_JDK_MODEL" = none ]; then
  set -- "$@" --jdk-model none
fi

if [ "$BENCHMARK_CAPTURE_TOPOLOGY" = 1 ]; then
  : "${BENCHMARK_DIAGNOSTICS:?BENCHMARK_DIAGNOSTICS is required when topology capture is enabled}"
  set -- "$@" --call-graph-diagnostics-output "$BENCHMARK_DIAGNOSTICS"
fi

# Wiki: wiki/runbooks/impact-benchmark.md - Canonical impact benchmark CLI invocation.
exec "$ANALYZER_JAVA" -jar "$ANALYZER_JAR" "$@"
