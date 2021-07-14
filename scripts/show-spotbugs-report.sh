#!/usr/bin/env bash
set -eEuo pipefail

cd "$(dirname "$(readlink -f "$0")")"/.. || {
    echo "Fail to change work dir!" 1>&2
    exit 1
}

# SpotBugs Maven Plugin – spotbugs:check
# https://spotbugs.github.io/spotbugs-maven-plugin/check-mojo.html
# SpotBugs Maven Plugin – spotbugs:gui
# https://spotbugs.github.io/spotbugs-maven-plugin/gui-mojo.html

./mvnw -Plint -D spotbugs.failOnError=false clean test-compile spotbugs:gui
