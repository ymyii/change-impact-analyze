#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <candidate-results-directory> <tracked-results-directory>" >&2
  exit 2
fi

candidate_dir=$1
tracked_results=$2
names="samples.tsv summary.tsv topology.tsv"

for name in $names; do
  [ -f "$candidate_dir/$name" ] || {
    echo "missing candidate snapshot: $candidate_dir/$name" >&2
    exit 1
  }
done

mkdir -p "$tracked_results"
publish_directory=$(mktemp -d "$tracked_results/.publish.XXXXXX")
cleanup() {
  rm -rf "$publish_directory"
}
trap cleanup EXIT HUP INT TERM

for name in $names; do
  cp "$candidate_dir/$name" "$publish_directory/$name"
done
for name in $names; do
  mv "$publish_directory/$name" "$tracked_results/$name"
done

rmdir "$publish_directory"
trap - EXIT HUP INT TERM
