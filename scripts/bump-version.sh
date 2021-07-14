#!/usr/bin/env bash
set -eEuo pipefail

cd "$(dirname "$(readlink -f "$0")")"/.. || {
    echo "Fail to change work dir!" 1>&2
    exit 1
}

if [ $# != 1 ]; then
    {
        echo "Only 1 argument for version!"
        echo
        echo "usage: $0 <new version>"
    } 1>&2

    exit 1
fi

mvn versions:set -DgenerateBackupPoms=false -DnewVersion="$1"
