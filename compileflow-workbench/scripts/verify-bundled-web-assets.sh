#!/usr/bin/env bash
set -euo pipefail

WORKBENCH="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 <bundled-workbench-jar>" >&2
  exit 2
fi

bundled_jar="$1"
web_dist="$WORKBENCH/apps/web/dist"
test -f "$bundled_jar"
test -d "$web_dist"

diff -u \
  <(cd "$web_dist" && find . -type f -print | sed 's#^\./##' | sort) \
  <(jar tf "$bundled_jar" \
    | sed -n 's#^BOOT-INF/classes/static/##p' \
    | grep -Ev '(^$|/$)' \
    | sort)
