#!/bin/sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <changed-paths-candidate> <full-candidate> <tracked-results-directory>" >&2
  exit 2
fi

changed_candidate=$1
full_candidate=$2
tracked_results=$3
tracked_parent=$(CDPATH= cd -- "$(dirname -- "$tracked_results")" && pwd)
tracked_name=$(basename -- "$tracked_results")
names="samples.tsv summary.tsv topology.tsv"

for candidate in "$changed_candidate" "$full_candidate"; do
  for name in $names; do
    [ -f "$candidate/$name" ] || {
      echo "missing candidate snapshot: $candidate/$name" >&2
      exit 1
    }
  done
done

publish_directory=$(mktemp -d "$tracked_parent/.${tracked_name}.publish.XXXXXX")
backup_directory="$tracked_parent/.${tracked_name}.previous.$$"
published=0
cleanup() {
  if [ "$published" -eq 0 ] && [ -d "$backup_directory" ] \
      && [ ! -e "$tracked_results" ]; then
    mv "$backup_directory" "$tracked_results"
  fi
  [ ! -d "$publish_directory" ] || rm -rf "$publish_directory"
  [ ! -d "$backup_directory" ] || rm -rf "$backup_directory"
}
trap cleanup EXIT HUP INT TERM

mkdir "$publish_directory/changed-paths" "$publish_directory/full"
for name in $names; do
  cp "$changed_candidate/$name" "$publish_directory/changed-paths/$name"
  cp "$full_candidate/$name" "$publish_directory/full/$name"
done

if [ -e "$tracked_results" ]; then
  mv "$tracked_results" "$backup_directory"
fi
mv "$publish_directory" "$tracked_results"
published=1
if [ -d "$backup_directory" ]; then
  rm -rf "$backup_directory"
fi
trap - EXIT HUP INT TERM
