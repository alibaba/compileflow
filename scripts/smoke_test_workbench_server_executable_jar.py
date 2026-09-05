#!/usr/bin/env python3
"""Smoke-test dynamic compilation from the packaged Server executable JAR."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
FLOW_CODE = "fatjar.nested"
FLOW_FIXTURE = REPOSITORY_ROOT / "scripts/fixtures/executable-jar-flow.tbbpm.xml"
PRE_ACTION_CONVERGENCE_CODES = frozenset(("CF_EXEC_011", "CF_EXEC_012"))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, help="Packaged compileflow-workbench-server executable JAR")
    parser.add_argument("--java", type=Path, help="Java executable to use")
    parser.add_argument("--startup-timeout", type=float, default=45.0)
    return parser.parse_args()


def resolve_jar(configured: Path | None) -> Path:
    if configured is not None:
        candidate = configured.resolve()
        if not candidate.is_file():
            raise RuntimeError(f"Executable JAR does not exist: {candidate}")
        return candidate

    target = REPOSITORY_ROOT / "compileflow-workbench-server/target"
    candidates = sorted(
        path
        for path in target.glob("compileflow-workbench-server-*.jar")
        if not any(marker in path.name for marker in ("-javadoc", "-sources", "-tests"))
    )
    if len(candidates) != 1:
        names = ", ".join(path.name for path in candidates) or "none"
        raise RuntimeError(f"Expected one executable Server JAR in {target}, found: {names}")
    return candidates[0].resolve()


def resolve_java(configured: Path | None) -> str:
    if configured is not None:
        candidate = configured.resolve()
        if not candidate.is_file():
            raise RuntimeError(f"Java executable does not exist: {candidate}")
        return str(candidate)
    java_home = os.environ.get("JAVA_HOME")
    return str(Path(java_home) / "bin/java") if java_home else "java"


def reserve_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


def request_json(
    url: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
) -> dict[str, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = dict(headers or {})
    if body is not None:
        request_headers.setdefault("Content-Type", "application/json")
    request = Request(
        url,
        data=body,
        headers=request_headers,
        method="POST" if body is not None else "GET",
    )
    try:
        with urlopen(request, timeout=10) as response:
            parsed = json.load(response)
    except HTTPError as failure:
        response_body = failure.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"HTTP {failure.code} from {url}: {response_body}"
        ) from failure
    if not isinstance(parsed, dict):
        raise RuntimeError(f"Expected JSON object from {url}, got {type(parsed).__name__}")
    return parsed


def wait_until_ready(process: subprocess.Popen[bytes], base_url: str, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        return_code = process.poll()
        if return_code is not None:
            raise RuntimeError(f"Server exited before becoming ready (exit={return_code})")
        try:
            health = request_json(f"{base_url}/actuator/health")
            if health.get("status") == "UP":
                return
        except (URLError, TimeoutError, RuntimeError) as failure:
            last_error = failure
        time.sleep(0.25)
    raise RuntimeError(f"Server did not become ready within {timeout}s: {last_error}")


def wait_until_executed(
    process: subprocess.Popen[bytes],
    url: str,
    payload: dict[str, Any],
    timeout: float,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout
    last_response: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        return_code = process.poll()
        if return_code is not None:
            raise RuntimeError(
                f"Server exited while waiting for deployment convergence (exit={return_code})"
            )
        response = request_json(url, payload)
        if response.get("success") is True:
            return response
        if response.get("errorCode") not in PRE_ACTION_CONVERGENCE_CODES:
            raise RuntimeError(f"Flow execution failed: {response}")
        last_response = response
        time.sleep(0.25)
    raise RuntimeError(
        f"Deployment did not converge within {timeout}s; last response: {last_response}"
    )


def stop_process(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def run_smoke_test(jar: Path, java: str, startup_timeout: float) -> None:
    if not FLOW_FIXTURE.is_file():
        raise RuntimeError(f"Flow fixture does not exist: {FLOW_FIXTURE}")
    required_database_settings = (
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
    )
    missing_settings = [
        name for name in required_database_settings if not os.environ.get(name)
    ]
    if missing_settings:
        raise RuntimeError(
            "Executable Server smoke tests require PostgreSQL; missing environment "
            "variables: " + ", ".join(missing_settings)
        )
    port = reserve_port()
    base_url = f"http://127.0.0.1:{port}"

    with tempfile.TemporaryDirectory(prefix="compileflow-workbench-server-smoke-") as directory:
        log_path = Path(directory) / "server.log"
        with log_path.open("wb") as log:
            process = subprocess.Popen(
                [
                    java,
                    "-jar",
                    str(jar),
                    "--spring.profiles.active=dev",
                    "--spring.main.banner-mode=off",
                    f"--server.port={port}",
                ],
                cwd=REPOSITORY_ROOT,
                stdout=log,
                stderr=subprocess.STDOUT,
            )
            try:
                wait_until_ready(process, base_url, startup_timeout)
                created = request_json(
                    f"{base_url}/api/processes",
                    {
                        "code": FLOW_CODE,
                        "name": "Executable JAR nested dependency",
                        "type": "TBBPM",
                        "xml": FLOW_FIXTURE.read_text(encoding="utf-8"),
                        "description": "Executable JAR delivery smoke fixture",
                        "tags": ["delivery-smoke"],
                    },
                )
                revision = created.get("revision")
                if (
                    created.get("code") != FLOW_CODE
                    or created.get("type") != "TBBPM"
                    or not isinstance(revision, int)
                    or isinstance(revision, bool)
                    or revision < 0
                ):
                    raise RuntimeError(f"Unexpected flow creation response: {created}")

                published = request_json(
                    f"{base_url}/api/processes/{FLOW_CODE}/publish",
                    {
                        "expectedRevision": revision,
                        "changelog": "Executable JAR delivery smoke",
                    },
                    {"Idempotency-Key": "executable-jar-smoke-publish"},
                )
                version = published.get("version")
                if (
                    published.get("processCode") != FLOW_CODE
                    or published.get("modelType") != "TBBPM"
                    or not isinstance(version, str)
                    or not version
                ):
                    raise RuntimeError(f"Unexpected publication response: {published}")

                deployment = request_json(
                    f"{base_url}/api/deployments",
                    {
                        "processCode": FLOW_CODE,
                        "version": version,
                        "alias": "dev",
                        "strategy": "all_at_once",
                        "expectedRouteRevision": 0,
                    },
                    {"Idempotency-Key": "executable-jar-smoke-deployment"},
                )
                route_revision = deployment.get("routeRevision")
                if (
                    deployment.get("processCode") != FLOW_CODE
                    or deployment.get("version") != version
                    or deployment.get("alias") != "dev"
                    or deployment.get("status") != "completed"
                    or not isinstance(route_revision, int)
                    or isinstance(route_revision, bool)
                    or route_revision <= 0
                ):
                    raise RuntimeError(f"Unexpected deployment response: {deployment}")

                executed = wait_until_executed(
                    process,
                    f"{base_url}/api/processes/{FLOW_CODE}/execute",
                    {
                        "params": {},
                        "routing": {
                            "alias": "dev",
                        },
                    },
                    15.0,
                )
                result = executed.get("result")
                if not isinstance(result, dict) or result.get("message") != "NESTED-OK":
                    raise RuntimeError(f"Unexpected flow result: {executed}")
                routing = executed.get("routing")
                if (
                    not isinstance(routing, dict)
                    or routing.get("namespace") != "default"
                    or routing.get("requestedAlias") != "dev"
                    or routing.get("effectiveVersion") != version
                    or routing.get("alias") != "dev"
                    or routing.get("routeRevision") != route_revision
                ):
                    raise RuntimeError(
                        f"Published execution lost routing attribution: {executed}"
                    )

                health = request_json(f"{base_url}/actuator/health")
                if health.get("status") != "UP":
                    raise RuntimeError(f"Server became unhealthy after compilation: {health}")
            except BaseException:
                stop_process(process)
                log.flush()
                print(log_path.read_text(encoding="utf-8", errors="replace"), file=sys.stderr)
                raise
            finally:
                stop_process(process)
            log.flush()
            runtime_log = log_path.read_text(encoding="utf-8", errors="replace")
            forbidden_messages = ("version-router failed", "No active version found")
            unexpected = [message for message in forbidden_messages if message in runtime_log]
            if unexpected:
                raise RuntimeError(
                    "Managed deployment emitted version-routing failures: "
                    + ", ".join(unexpected)
                )

    print(f"Executable JAR smoke test passed: java={java}, jar={jar.name}, result=NESTED-OK")


def main() -> int:
    args = parse_args()
    try:
        run_smoke_test(resolve_jar(args.jar), resolve_java(args.java), args.startup_timeout)
    except (OSError, RuntimeError, URLError, TimeoutError) as failure:
        print(f"Executable JAR smoke test failed: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
