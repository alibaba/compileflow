#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQUIRED_CROSS_MODULE_TESTS=(
  "$ROOT/compileflow-core/src/test/java/com/alibaba/compileflow/engine/core/runtime/resolution/ClassLoaderScopeTest.java"
  "$ROOT/compileflow-core/src/test/java/com/alibaba/compileflow/engine/core/routing/DeterministicAliasSelectorTest.java"
)
SERVER_TESTS=()
TEST_SOURCE_ROOTS=(
  "$ROOT/compileflow-workbench-server/src/test/java"
  "$ROOT/compileflow-spring-boot-autoconfigure/src/test/java"
)
for test_file in "${REQUIRED_CROSS_MODULE_TESTS[@]}"; do
  test -f "$test_file"
  test_name="${test_file##*/}"
  SERVER_TESTS+=("${test_name%.java}")
done
while IFS= read -r test_file; do
  test_name="${test_file##*/}"
  SERVER_TESTS+=("${test_name%.java}")
done < <(
  find "${TEST_SOURCE_ROOTS[@]}" -type f -name '*Test.java' -print |
    LC_ALL=C sort
)
SERVER_TESTS_ARG="$(IFS=,; printf '%s' "${SERVER_TESTS[*]}")"

exec "$ROOT/mvnw" test \
  -f "$ROOT/pom.xml" \
  -pl compileflow-workbench-server -am \
  -Dtest="$SERVER_TESTS_ARG" \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -B -V --no-transfer-progress
