#!/usr/bin/env bash
# Targeted delivery verification — run from compileflow-workbench/ via pnpm verify:delivery
set -euo pipefail

WB="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(cd "$WB/.." && pwd)"

assembly_only=false
if [[ "${1:-}" == "--assembly-only" ]]; then
  assembly_only=true
  shift
fi
if [[ "$#" -ne 0 ]]; then
  echo "Usage: $0 [--assembly-only]" >&2
  exit 2
fi

echo "==> Repository: architecture boundaries"
(cd "$ROOT" && python3 scripts/check_architecture_boundaries.py)

echo "==> Workbench: generated server contract"
(cd "$WB" && pnpm check:workbench-server-contract)

echo "==> Workbench: type-check"
(cd "$WB" && pnpm type-check)

echo "==> Learn: examples catalog sync check"
(cd "$WB" && pnpm sync:examples-catalog:check)

echo "==> Development gateway: unit + integration tests"
(cd "$WB" && pnpm --filter @compileflow/workbench-dev-gateway test)

echo "==> Web: build configuration unit tests"
(cd "$WB/apps/web" && pnpm exec vitest run src/shared/config/__tests__/buildConfig.test.ts)

echo "==> Web: operate API truth strategy tests"
(cd "$WB/apps/web" && pnpm exec vitest run src/operate/api/__tests__/truthStrategy.test.ts)

echo "==> Web: production build and bundle budgets"
(cd "$WB" && pnpm --filter @compileflow/workbench-web build)

echo "==> compileflow-workbench-server: build current reactor artifacts"
(cd "$ROOT" && ./mvnw clean install -pl compileflow-workbench-server -am \
  -DskipTests -Djacoco.skip=true \
  -B -V --no-transfer-progress)

if [[ "$assembly_only" == false ]]; then
  echo "==> compileflow-workbench-server: checkstyle"
  (cd "$ROOT" && ./mvnw checkstyle:check -pl compileflow-workbench-server -am \
    -B -V --no-transfer-progress)

  echo "==> Java public API: Javadoc style"
  (cd "$ROOT" && ./mvnw checkstyle:check \
    -pl compileflow-api,compileflow-deploy/compileflow-deploy-api \
    -Dcheckstyle.config.location=checkstyle-javadoc.xml \
    -B -V --no-transfer-progress)

  echo "==> compileflow-workbench-server: SpotBugs"
  (cd "$ROOT" && ./mvnw spotbugs:check -pl compileflow-workbench-server -am \
    -B -V --no-transfer-progress)
  (cd "$ROOT" && python3 scripts/verify_spotbugs_reports.py \
    compileflow-api \
    compileflow-core \
    compileflow-tbbpm \
    compileflow-bpmn \
    compileflow-deploy/compileflow-deploy-api \
    compileflow-deploy/compileflow-deploy-control-plane \
    compileflow-deploy/compileflow-deploy-runtime \
    compileflow-spring-boot-autoconfigure \
    compileflow-workbench-server)

  echo "==> Java public API: Javadocs"
  (cd "$ROOT" && ./mvnw javadoc:javadoc \
    -pl compileflow-api,compileflow-deploy/compileflow-deploy-api \
    -B -V --no-transfer-progress)

  echo "==> Deployment: unit + H2 repository tests"
  (cd "$ROOT" && ./mvnw test \
    -pl compileflow-deploy/compileflow-deploy-api,compileflow-deploy/compileflow-deploy-control-plane,compileflow-deploy/compileflow-deploy-runtime \
    -B -V --no-transfer-progress)

  echo "==> compileflow-workbench-server: targeted JVM tests"
  "$ROOT/scripts/run_server_targeted_tests.sh"

  echo "==> Distributed deployment: JDBC outbox and multi-node runtime contract"
  test -f "$ROOT/compileflow-integration-tests/src/test/java/com/alibaba/compileflow/engine/test/feature/deployment/control/DeploymentRuntimeChainIntegrationTest.java"
  (cd "$ROOT" && ./mvnw test -pl compileflow-integration-tests -am \
    -Dtest=DeploymentRuntimeChainIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -B -V --no-transfer-progress)
  test -s "$ROOT/compileflow-integration-tests/target/surefire-reports/TEST-com.alibaba.compileflow.engine.test.feature.deployment.control.DeploymentRuntimeChainIntegrationTest.xml"
else
  echo "==> Java/server quality gates: owned by workbench-server-ci; assembly-only mode"
fi

echo "==> Workbench: package the bundled single-process distribution"
(cd "$ROOT" && ./mvnw package -pl compileflow-workbench-server \
  -Pworkbench-bundled -DskipTests \
  -B -V --no-transfer-progress)
bundled_jars=("$ROOT"/compileflow-workbench-server/target/compileflow-workbench-all-in-one-*.jar)
test "${#bundled_jars[@]}" -eq 1
jar tf "${bundled_jars[0]}" | grep -qx 'BOOT-INF/classes/static/index.html'
jar tf "${bundled_jars[0]}" | grep -qx 'META-INF/LICENSE'
jar tf "${bundled_jars[0]}" | grep -qx 'META-INF/NOTICE'

echo "==> Workbench: bundled web manifest matches the production build"
"$WB/scripts/verify-bundled-web-assets.sh" "${bundled_jars[0]}"

echo "==> All delivery checks passed"
