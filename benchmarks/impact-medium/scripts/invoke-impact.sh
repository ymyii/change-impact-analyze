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

# Wiki: wiki/runbooks/impact-benchmark.md - Canonical impact benchmark CLI invocation.
exec "$ANALYZER_JAVA" \
  -jar "$ANALYZER_JAR" \
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
  --module-parallelism 2 \
  --include-change-kinds CLASS_ADDED,CLASS_REMOVED,METHOD_ADDED,METHOD_REMOVED,METHOD_DESCRIPTOR_CHANGED,METHOD_BODY_CHANGED,FIELD_ADDED,FIELD_REMOVED,FIELD_DESCRIPTOR_CHANGED \
  --call-graph-timeout-seconds 120
