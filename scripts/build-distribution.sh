#!/bin/sh
set -eu

# Wiki: wiki/runbooks/version-and-distribution.md - Official distribution build entrypoint
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
allow_dirty=false
skip_jdk8=false

while [ "$#" -gt 0 ]; do
    case "$1" in
        --allow-dirty) allow_dirty=true ;;
        --skip-jdk8-smoke) skip_jdk8=true ;;
        *) echo "build-distribution: unknown option: $1" >&2; exit 1 ;;
    esac
    shift
done

if [ "$skip_jdk8" = true ] && [ "$allow_dirty" != true ]; then
    echo "build-distribution: --skip-jdk8-smoke requires --allow-dirty" >&2
    exit 1
fi

cd "$repository_root"
sh "$script_dir/version.sh" verify

git_status=$(git status --porcelain)
git_dirty=false
if [ -n "$git_status" ]; then
    git_dirty=true
fi
if [ "$allow_dirty" != true ] && [ "$git_dirty" = true ]; then
    echo "build-distribution: formal build requires a clean Git worktree" >&2
    exit 1
fi

if [ "$skip_jdk8" != true ]; then
    if [ -z "${TEST_JDK8_HOME:-}" ] \
            || [ ! -x "$TEST_JDK8_HOME/bin/java" ] \
            || [ ! -x "$TEST_JDK8_HOME/bin/javac" ]; then
        echo "build-distribution: TEST_JDK8_HOME must point to a complete JDK 8" >&2
        exit 1
    fi
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/dependency-analyzer-distribution.XXXXXX")
cleanup() {
    if [ -n "${work_dir:-}" ] \
            && [ -d "$work_dir" ]; then
        rm -rf -- "$work_dir"
    fi
}
trap cleanup EXIT HUP INT TERM

contract=build-support/version-contract.properties
analyzer_version=$(awk -F= '$1 == "current.analyzer.version" {print $2}' "$contract")
plugin_version=$(awk -F= '$1 == "current.artifact-path-plugin.version" {print $2}' "$contract")
output_timestamp=$(awk -F= -v key="analyzer.${analyzer_version}.outputTimestamp" '$1 == key {print $2}' "$contract")
if [ -z "$analyzer_version" ] || [ -z "$plugin_version" ] \
        || [ -z "$output_timestamp" ]; then
    echo "build-distribution: incomplete version contract" >&2
    exit 1
fi

maven_bin=${MAVEN_BIN:-mvn}
if [ "$skip_jdk8" = true ]; then
    "$maven_bin" -Dproject.build.outputTimestamp="$output_timestamp" clean verify
else
    TEST_JDK8_HOME="$TEST_JDK8_HOME" \
        "$maven_bin" -Dproject.build.outputTimestamp="$output_timestamp" clean verify
fi

artifact=target/dependency-analyzer.jar
java scripts/internal/DistributionTool.java inspect \
    --root "$repository_root" --jar "$artifact"
cp "$artifact" "$work_dir/first.jar"

javac -cp "$artifact" -d "$work_dir/smoke-classes" \
    scripts/internal/PackagedRuntimeSmoke.java
java -cp "$artifact:$work_dir/smoke-classes" \
    scripts.internal.PackagedRuntimeSmoke "$plugin_version"

"$maven_bin" -Dproject.build.outputTimestamp="$output_timestamp" \
    -DskipTests clean package
java scripts/internal/DistributionTool.java compare \
    "$work_dir/first.jar" "$artifact"

git_commit=$(git rev-parse HEAD)
java_version=$(java -version 2>&1 | sed -n '1p')
maven_version=$($maven_bin -version 2>&1 | sed -n '1p')
release_eligible=true
output_directory=target/distribution
if [ "$allow_dirty" = true ] || [ "$skip_jdk8" = true ]; then
    release_eligible=false
    output_directory=target/distribution-dev
fi

java scripts/internal/DistributionTool.java publish \
    --root "$repository_root" \
    --jar "$artifact" \
    --output "$output_directory" \
    --release-eligible "$release_eligible" \
    --git-commit "$git_commit" \
    --git-dirty "$git_dirty" \
    --java-version "$java_version" \
    --maven-version "$maven_version"
