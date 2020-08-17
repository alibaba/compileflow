#!/usr/bin/env bash
if [ $# != 1 ]; then
    {
        echo "Only 1 argument for verson!"
        echo
        echo "usage: $0 <new version>"
    } 1>&2

	exit 1
fi

mvn versions:set -DgenerateBackupPoms=false -DnewVersion="$1"
