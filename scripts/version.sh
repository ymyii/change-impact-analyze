#!/bin/sh
set -eu

# Wiki: wiki/rules/release-versioning.md - Canonical SemVer command entrypoint
# Wiki: wiki/runbooks/version-and-distribution.md - Version inspection and bump procedure
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

exec java "$repository_root/scripts/internal/VersionTool.java" \
    --root "$repository_root" "$@"
