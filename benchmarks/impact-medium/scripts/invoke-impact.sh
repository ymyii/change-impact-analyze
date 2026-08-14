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
: "${BENCHMARK_RESULT_REFINEMENT_ALGORITHMS:?BENCHMARK_RESULT_REFINEMENT_ALGORITHMS is required}"
: "${BENCHMARK_CAPTURE_TOPOLOGY:?BENCHMARK_CAPTURE_TOPOLOGY is required}"

case "$BENCHMARK_CALL_GRAPH_ALGORITHM" in
  cha|rta|zero-cfa|optimized-0-1-cfa|k-obj) ;;
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
  --wala-reflection-options "$BENCHMARK_WALA_REFLECTION_OPTIONS" \
  --dependency-analysis-scope "$BENCHMARK_DEPENDENCY_ANALYSIS_SCOPE" \
  --result-refinement-algorithms "$BENCHMARK_RESULT_REFINEMENT_ALGORITHMS" \
  --include-change-kinds CLASS_ADDED,CLASS_REMOVED,CLASS_ACCESS_NARROWED,METHOD_ADDED,METHOD_REMOVED,METHOD_DESCRIPTOR_CHANGED,METHOD_BODY_CHANGED,METHOD_ACCESS_NARROWED,FIELD_ADDED,FIELD_REMOVED,FIELD_DESCRIPTOR_CHANGED,FIELD_ACCESS_NARROWED,SERVICE_PROVIDER_REGISTRATION_REMOVED \
  --call-graph-timeout-seconds 120

# CHA intentionally omits the algorithm and JDK-model options to exercise
# cha + none defaults. Result refinement is explicit for every benchmark run.
if [ "$BENCHMARK_CALL_GRAPH_ALGORITHM" != cha ]; then
  set -- "$@" --call-graph-algorithm "$BENCHMARK_CALL_GRAPH_ALGORITHM"
fi
if [ "$BENCHMARK_CALL_GRAPH_ALGORITHM" != cha ] \
    && [ "$BENCHMARK_JDK_MODEL" = none ]; then
  set -- "$@" --jdk-model none
fi

if [ "$BENCHMARK_CAPTURE_TOPOLOGY" = 1 ]; then
  : "${BENCHMARK_DIAGNOSTICS:?BENCHMARK_DIAGNOSTICS is required when topology capture is enabled}"
  set -- "$@" --call-graph-diagnostics-output "$BENCHMARK_DIAGNOSTICS"
fi

# Wiki: wiki/runbooks/impact-benchmark.md - Canonical impact benchmark CLI invocation.
exec "$ANALYZER_JAVA" -jar "$ANALYZER_JAR" "$@"
