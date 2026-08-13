#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "usage: JAVA8_HOME=/path/to/jdk8 $0 <runtime-fixture-dir>" >&2
  exit 2
fi

: "${JAVA8_HOME:?JAVA8_HOME must point to a complete JDK 8}"

runtime_fixture_root=$1
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
benchmark_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
artifact_sources="$benchmark_root/fixtures/artifacts"
application_source="$benchmark_root/fixtures/application/baseline"
target_overlay="$benchmark_root/fixtures/application/target-overlay"
reactor_fixture="$benchmark_root/fixtures/reactor"
project_root="$runtime_fixture_root/project"
work_root="$runtime_fixture_root/work"
benchmark_maven_repo=${BENCHMARK_MAVEN_REPO:-${HOME}/.m2/repository}
javac_bin="$JAVA8_HOME/bin/javac"
jar_bin="$JAVA8_HOME/bin/jar"

if [ -e "$runtime_fixture_root" ]; then
  echo "runtime fixture already exists: $runtime_fixture_root" >&2
  exit 2
fi

for executable in "$javac_bin" "$jar_bin" "$JAVA8_HOME/bin/java"; do
  if [ ! -x "$executable" ]; then
    echo "required JDK 8 executable is missing: $executable" >&2
    exit 2
  fi
done

if ! "$JAVA8_HOME/bin/java" -version 2>&1 | grep -q 'version "1\.8'; then
  echo "JAVA8_HOME is not JDK 8: $JAVA8_HOME" >&2
  exit 2
fi

for executable in git find sort sed; do
  if ! command -v "$executable" >/dev/null 2>&1; then
    echo "required executable is missing: $executable" >&2
    exit 2
  fi
done

mkdir -p "$project_root" "$work_root" "$benchmark_maven_repo"

install_artifact() {
  group_id=$1
  artifact_id=$2
  version=$3
  source_jar=$4
  group_path=$(printf '%s' "$group_id" | tr '.' '/')
  artifact_dir="$benchmark_maven_repo/$group_path/$artifact_id/$version"
  destination_jar="$artifact_dir/$artifact_id-$version.jar"
  destination_pom="$artifact_dir/$artifact_id-$version.pom"

  mkdir -p "$artifact_dir"
  cp "$source_jar" "$destination_jar"
  # Generated local-repository metadata; source POMs are intentionally dependency-free.
  cat >"$destination_pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group_id</groupId>
  <artifactId>$artifact_id</artifactId>
  <version>$version</version>
</project>
EOF
}

install_pom_only() {
  group_id=$1
  artifact_id=$2
  version=$3
  group_path=$(printf '%s' "$group_id" | tr '.' '/')
  artifact_dir="$benchmark_maven_repo/$group_path/$artifact_id/$version"
  destination_pom="$artifact_dir/$artifact_id-$version.pom"

  mkdir -p "$artifact_dir"
  cat >"$destination_pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group_id</groupId>
  <artifactId>$artifact_id</artifactId>
  <version>$version</version>
</project>
EOF
}

install_artifact_with_dependencies() {
  group_id=$1
  artifact_id=$2
  version=$3
  source_jar=$4
  shift 4
  install_artifact "$group_id" "$artifact_id" "$version" "$source_jar"
  group_path=$(printf '%s' "$group_id" | tr '.' '/')
  destination_pom="$benchmark_maven_repo/$group_path/$artifact_id/$version/$artifact_id-$version.pom"
  {
    printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>'
    printf '%s\n' '<project xmlns="http://maven.apache.org/POM/4.0.0">'
    printf '  <modelVersion>4.0.0</modelVersion>\n'
    printf '  <groupId>%s</groupId>\n' "$group_id"
    printf '  <artifactId>%s</artifactId>\n' "$artifact_id"
    printf '  <version>%s</version>\n' "$version"
    printf '  <dependencies>\n'
    while [ "$#" -ge 3 ]; do
      printf '    <dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version></dependency>\n' "$1" "$2" "$3"
      shift 3
    done
    printf '  </dependencies>\n</project>\n'
  } >"$destination_pom"
}

compile_sources() {
  source_root=$1
  classes_root=$2
  output_jar=$3
  compile_classpath=${4:-}
  source_list="$classes_root.sources"

  mkdir -p "$classes_root"
  find "$source_root" -type f -name '*.java' -print | LC_ALL=C sort >"$source_list"
  if [ -n "$compile_classpath" ]; then
    "$javac_bin" -source 8 -target 8 -classpath "$compile_classpath" -d "$classes_root" "@$source_list"
  else
    "$javac_bin" -source 8 -target 8 -d "$classes_root" "@$source_list"
  fi
  find "$source_root" -type f ! -name '*.java' -print | while IFS= read -r resource; do
    relative_resource=${resource#"$source_root"/}
    mkdir -p "$classes_root/$(dirname "$relative_resource")"
    cp "$resource" "$classes_root/$relative_resource"
  done
  "$jar_bin" cf "$output_jar" -C "$classes_root" .
}

scenario_v1_jar="$work_root/scenario-api-1.0.0.jar"
scenario_v2_jar="$work_root/scenario-api-2.0.0.jar"
bridge_jar="$work_root/legacy-impact-bridge-1.0.0.jar"
downstream_jar="$work_root/seed-downstream-1.0.0.jar"
path_a_jar="$work_root/path-a-1.0.0.jar"
path_c_jar="$work_root/path-c-1.0.0.jar"
path_x_jar="$work_root/path-x-1.0.0.jar"
path_y_jar="$work_root/path-y-1.0.0.jar"
path_sibling_jar="$work_root/path-sibling-1.0.0.jar"
sink_jar="$work_root/external-sink-1.0.0.jar"
factory_jar="$work_root/external-factory-1.0.0.jar"
plain_jar="$work_root/external-plain-1.0.0.jar"

compile_sources \
  "$artifact_sources/seed-downstream" \
  "$work_root/classes-seed-downstream" \
  "$downstream_jar"
compile_sources \
  "$artifact_sources/scenario-api-v1" \
  "$work_root/classes-scenario-v1" \
  "$scenario_v1_jar"
compile_sources \
  "$artifact_sources/scenario-api-v2" \
  "$work_root/classes-scenario-v2" \
  "$scenario_v2_jar"
compile_sources \
  "$artifact_sources/legacy-impact-bridge" \
  "$work_root/classes-bridge" \
  "$bridge_jar" \
  "$scenario_v1_jar"

for path_name in path-a path-c path-x path-y path-sibling; do
  path_variable=$(printf '%s' "$path_name" | tr '-' '_')
  eval "path_jar=\${${path_variable}_jar}"
  compile_sources \
    "$artifact_sources/$path_name" \
    "$work_root/classes-$path_name" \
    "$path_jar"
done

compile_sources \
  "$artifact_sources/external-sink" \
  "$work_root/classes-external-sink" \
  "$sink_jar" \
  "$scenario_v1_jar"
compile_sources \
  "$artifact_sources/external-factory" \
  "$work_root/classes-external-factory" \
  "$factory_jar" \
  "$scenario_v1_jar"
compile_sources \
  "$artifact_sources/external-plain" \
  "$work_root/classes-external-plain" \
  "$plain_jar"

install_artifact com.acme.impact.downstream seed-downstream 1.0.0 "$downstream_jar"
install_artifact_with_dependencies com.acme.impact scenario-api 1.0.0 \
  "$scenario_v1_jar" \
  com.acme.impact.downstream seed-downstream 1.0.0
install_artifact_with_dependencies com.acme.impact scenario-api 2.0.0 \
  "$scenario_v2_jar" \
  com.acme.impact.downstream seed-downstream 1.0.0
install_artifact com.acme.impact legacy-impact-bridge 1.0.0 "$bridge_jar"
install_artifact_with_dependencies com.acme.impact.path path-c 1.0.0 \
  "$path_c_jar" com.acme.impact scenario-api 1.0.0
install_artifact_with_dependencies com.acme.impact.path path-a 1.0.0 \
  "$path_a_jar" com.acme.impact.path path-c 1.0.0 \
  com.acme.impact.path path-sibling 1.0.0
install_artifact_with_dependencies com.acme.impact.path path-y 1.0.0 \
  "$path_y_jar" com.acme.impact scenario-api 1.0.0
install_artifact_with_dependencies com.acme.impact.path path-x 1.0.0 \
  "$path_x_jar" com.acme.impact.path path-y 1.0.0
install_artifact_with_dependencies com.acme.impact.path path-sibling 1.0.0 \
  "$path_sibling_jar" com.acme.benchmark.vendor vendor-lib-35 1.0.0
install_artifact com.acme.impact.boundary external-sink 1.0.0 "$sink_jar"
install_artifact com.acme.impact.boundary external-factory 1.0.0 \
  "$factory_jar"
install_pom_only com.acme.impact.scope scope-conflict-marker 1.0.0
install_artifact_with_dependencies \
  com.acme.impact.boundary external-plain 1.0.0 "$plain_jar" \
  com.acme.impact.scope scope-conflict-marker 1.0.0 \
  com.acme.benchmark.vendor vendor-lib-34 1.0.0

vendor_source_root="$work_root/vendor-sources"
vendor_classes_root="$work_root/vendor-classes"
vendor_template="$artifact_sources/vendor/VendorMarker.java.template"
vendor_source_list="$work_root/vendor-sources.list"
mkdir -p "$vendor_source_root" "$vendor_classes_root"

vendor_number=1
while [ "$vendor_number" -le 35 ]; do
  vendor_index=$(printf '%02d' "$vendor_number")
  vendor_package_dir="$vendor_source_root/com/acme/benchmark/vendor/lib$vendor_index"
  mkdir -p "$vendor_package_dir"
  sed \
    -e "s/@INDEX@/$vendor_index/g" \
    -e "s/@ARTIFACT@/vendor-lib-$vendor_index/g" \
    "$vendor_template" >"$vendor_package_dir/VendorMarker$vendor_index.java"
  vendor_number=$((vendor_number + 1))
done

find "$vendor_source_root" -type f -name '*.java' -print | LC_ALL=C sort >"$vendor_source_list"
"$javac_bin" -source 8 -target 8 -d "$vendor_classes_root" "@$vendor_source_list"

vendor_number=1
while [ "$vendor_number" -le 35 ]; do
  vendor_index=$(printf '%02d' "$vendor_number")
  vendor_artifact="vendor-lib-$vendor_index"
  vendor_jar="$work_root/$vendor_artifact-1.0.0.jar"
  "$jar_bin" cf "$vendor_jar" \
    -C "$vendor_classes_root" "com/acme/benchmark/vendor/lib$vendor_index"
  install_artifact com.acme.benchmark.vendor "$vendor_artifact" 1.0.0 "$vendor_jar"
  vendor_number=$((vendor_number + 1))
done

cp "$reactor_fixture/root-pom.xml" "$project_root/pom.xml"
mkdir -p "$project_root/application" \
  "$project_root/reactor-path/src/main/java/com/acme/benchmark/reactor"
cp -R "$application_source/." "$project_root/application/"
cp "$reactor_fixture/reactor-path-pom.xml" \
  "$project_root/reactor-path/pom.xml"
cp "$reactor_fixture/ReactorPath.java" \
  "$project_root/reactor-path/src/main/java/com/acme/benchmark/reactor/ReactorPath.java"
git -C "$project_root" init -q -b main
git -C "$project_root" config user.name "Impact Benchmark"
git -C "$project_root" config user.email "impact-benchmark@example.invalid"
git -C "$project_root" add -A
GIT_AUTHOR_DATE='2026-01-01T00:00:00Z' \
GIT_COMMITTER_DATE='2026-01-01T00:00:00Z' \
  git -C "$project_root" commit -q -m 'baseline: scenario api 1.0.0'
git -C "$project_root" tag impact-baseline

git -C "$project_root" rm -q \
  application/src/main/java/com/acme/benchmark/BenchmarkApplication.java \
  application/src/main/java/com/acme/benchmark/ImpactFacade.java
cp -R "$target_overlay/." "$project_root/application/"
sed \
  's#<scenario.api.version>1.0.0</scenario.api.version>#<scenario.api.version>2.0.0</scenario.api.version>#' \
  "$project_root/pom.xml" >"$work_root/target-pom.xml"
cp "$work_root/target-pom.xml" "$project_root/pom.xml"
git -C "$project_root" add -A
GIT_AUTHOR_DATE='2026-01-02T00:00:00Z' \
GIT_COMMITTER_DATE='2026-01-02T00:00:00Z' \
  git -C "$project_root" commit -q -m 'target: upgrade scenario api to 2.0.0'
git -C "$project_root" tag impact-target

dependency_count=$(awk '
  /<dependencies>/ { in_dependencies = 1; next }
  /<\/dependencies>/ { in_dependencies = 0 }
  in_dependencies && /<dependency>/ { count++ }
  END { print count + 0 }
' "$project_root/application/pom.xml")

if [ "$dependency_count" -ne 42 ]; then
  echo "fixture must contain 42 direct dependencies; found $dependency_count" >&2
  exit 1
fi

# Wiki: wiki/runbooks/impact-benchmark.md - Fixture preparation contract and generated runtime layout.
cat >"$runtime_fixture_root/fixture-metadata.txt" <<EOF
project=$project_root
baseline=impact-baseline
target=impact-target
direct_dependencies=$dependency_count
maven_repository=$benchmark_maven_repo
EOF

echo "fixture prepared: $project_root"
echo "direct dependencies: $dependency_count"
